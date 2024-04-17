package com.example.searchAPI.service;

import com.example.searchAPI.config.ElasticConfiguration;
import com.example.searchAPI.constant.search.*;
import com.example.searchAPI.constant.topsearched.TopSearched;
import com.example.searchAPI.controller.ElasticsearchController;
import com.example.searchAPI.model.SearchCriteria;
import com.example.searchAPI.validator.ForbiddenWordValidator;
import com.example.searchAPI.validator.GenericValidator;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.search.MatchQuery;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SearchService {

    @Autowired
    private ElasticConfiguration elasticConfiguration;

    @Value("${search.index}")
    private String index;

    @Value("${topsearched.index}")
    private String topSearchedIndex;

    @Value("${search.forbiddenPath}")
    private String forbiddenPath;

    private final Logger logger = LoggerFactory.getLogger(ElasticsearchController.class);

    public List<String> search(SearchCriteria criteria) {
        try {
            SearchRequest searchRequest = new SearchRequest(index);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

            checkForbiddenWord(criteria.getKeyword());
            indexSearchTerm(elasticConfiguration, criteria);
            buildAdvancedSearchQuery(criteria.getKeyword(), sourceBuilder, criteria.getFieldDesignation());
            setDateRange(criteria.getPeriod(), sourceBuilder);
            setPage(criteria.getMaxDocument(), criteria.getNowPage(), sourceBuilder);
            sort(criteria.getSortOption(), sourceBuilder, searchRequest);
            setupHighlighting(sourceBuilder);

            searchRequest.source(sourceBuilder);
            SearchResponse searchResponse = elasticConfiguration.getElasticClient().search(searchRequest, RequestOptions.DEFAULT);
            SearchHits hits = searchResponse.getHits();

            List<String> results = processSearchResultsByAllCategories(criteria, hits);
            if (results != null) return results;

            Map<String, List<String>> categorizedResults = new HashMap<>();
            processSearchResultsByCategory(criteria, hits, categorizedResults);

            List<String> finalResults = new ArrayList<>();
            categorizedResults.values().forEach(finalResults::addAll);
            return finalResults;
        } catch (Exception e) {
            return Collections.singletonList(e.getMessage());
        }
    }

    private static List<String> processSearchResultsByAllCategories(SearchCriteria criteria, SearchHits hits) {
        List<String> results = new ArrayList<>();
        if (criteria.getCategories().contains(Category.ALL.get()) || criteria.getCategories().isEmpty()) {
            for (SearchHit hit : hits.getHits()) {
                Map<String, HighlightField> highlightFields = hit.getHighlightFields();
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();
                Map<String, String> finalDocument = new HashMap<>();

                sourceAsMap.forEach((key, value) -> {
                    if (highlightFields.containsKey(key) && highlightFields.get(key).fragments().length > 0) {
                        String highlightedText = Arrays.stream(highlightFields.get(key).fragments()).map(Text::string).collect(Collectors.joining(" "));
                        finalDocument.put(key, highlightedText);
                    } else {
                        finalDocument.put(key, value.toString());
                    }
                });

                StringBuilder result = new StringBuilder("{");
                finalDocument.forEach((key, value) -> result.append(String.format("\"%s\":\"%s\",", key, value)));
                if (!finalDocument.isEmpty()) {
                    result.deleteCharAt(result.length() - 1);
                }
                result.append("}");

                results.add(result.toString());
            }
            return results;
        }
        return null;
    }

    private void processSearchResultsByCategory(SearchCriteria criteria, SearchHits hits, Map<String, List<String>> categorizedResults) {
        boolean searchAllFields = criteria.getFieldDesignation().isEmpty() || (criteria.getFieldDesignation().size() == 1 && Category.ALL.get().equals(criteria.getFieldDesignation().get(0)));

        for (SearchHit hit : hits.getHits()) {
            String category = (String) hit.getSourceAsMap().get(Category.CATEGORY.get());
            if (criteria.getCategories().contains(category)) {
                int index = criteria.getCategories().indexOf(category);
                int maxCount = criteria.getCategoryMaxCounts().get(index);
                List<String> resultsForCategory = categorizedResults.computeIfAbsent(category, k -> new ArrayList<>());

                if (resultsForCategory.size() < maxCount) {
                    Map<String, Object> sourceAsMap = hit.getSourceAsMap();
                    Map<String, HighlightField> highlightFields = hit.getHighlightFields();

                    Map<String, String> documentResults = new HashMap<>();

                    if (searchAllFields) {
                        sourceAsMap.forEach((key, value) -> {
                            if (highlightFields.containsKey(key) && highlightFields.get(key).fragments().length > 0) {
                                String highlightedText = Arrays.stream(highlightFields.get(key).fragments()).map(Text::string).collect(Collectors.joining(" "));
                                documentResults.put(key, highlightedText);
                            } else {
                                documentResults.put(key, value.toString());
                            }
                        });
                    } else {
                        criteria.getFieldDesignation().forEach(field -> {
                            if (sourceAsMap.containsKey(field)) {
                                if (highlightFields.containsKey(field) && highlightFields.get(field).fragments().length > 0) {
                                    String highlightedText = Arrays.stream(highlightFields.get(field).fragments()).map(Text::string).collect(Collectors.joining(" "));
                                    documentResults.put(field, highlightedText);
                                } else {
                                    documentResults.put(field, sourceAsMap.get(field).toString());
                                }
                            }
                        });

                        sourceAsMap.forEach((key, value) -> {
                            if (!documentResults.containsKey(key)) {
                                documentResults.put(key, value.toString());
                            }
                        });
                    }

                    StringBuilder result = new StringBuilder("{");
                    documentResults.forEach((key, value) -> result.append(String.format("\"%s\":\"%s\",", key, value)));
                    if (!documentResults.isEmpty()) {
                        result.deleteCharAt(result.length() - 1);
                    }
                    result.append("}");

                    resultsForCategory.add(result.toString());
                }
            }
        }
    }

    private void setPage(Integer maxDocument, Integer nowPage, SearchSourceBuilder sourceBuilder) {
        int from = (nowPage - 1) * maxDocument;
        sourceBuilder.from(from);
        sourceBuilder.size(maxDocument);
    }

    private void sort(String sortOption, SearchSourceBuilder sourceBuilder, SearchRequest searchRequest) {
        if (sortOption.equals(Sort.ACCURACY.get())) {
            QueryBuilder existingQuery = sourceBuilder.query();

            if (!(existingQuery instanceof FunctionScoreQueryBuilder)) {
                FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(existingQuery, new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{}).boostMode(CombineFunction.MULTIPLY);
                sourceBuilder.query(functionScoreQueryBuilder);
            }
            sourceBuilder.minScore(2.0f);
        } else if (sortOption.equals(Sort.LATEST.get())) {
            sourceBuilder.sort(SortBuilders.fieldSort(Sort.TARGET.get()).order(SortOrder.DESC));
        } else if (sortOption.equals(Sort.EARLIEST.get())) {
            sourceBuilder.sort(SortBuilders.fieldSort(Sort.TARGET.get()).order(SortOrder.ASC));
        }
        searchRequest.source(sourceBuilder);
    }

    private void setDateRange(String period, SearchSourceBuilder sourceBuilder) {
        LocalDate startDate = null;
        LocalDate endDate = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Period.DATE_FORMAT.get());

        isValid(period);

        if (Period.ALL.get().equalsIgnoreCase(period)) return;

        if (period.contains(Period.DELIMETER.get())) {
            startDate = LocalDate.parse(period.split(Period.DELIMETER.get())[0].trim(), formatter);
            endDate = LocalDate.parse(period.split(Period.DELIMETER.get())[1].trim(), formatter);
        } else if (Period.DAY.get().equalsIgnoreCase(period)) {
            startDate = endDate.minusDays(1);
        } else if (Period.WEEK.get().equalsIgnoreCase(period)) {
            startDate = endDate.minusWeeks(1);
        } else if (Period.MONTH.get().equalsIgnoreCase(period)) {
            startDate = endDate.minusMonths(1);
        } else if (Period.YEAR.get().equalsIgnoreCase(period)) {
            startDate = endDate.minusYears(1);
        }

        RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(Period.TARGET.get()).format(Period.DATE_FORMAT.get()).from(startDate.format(formatter)).to(endDate.format(formatter));

        QueryBuilder existingQuery = sourceBuilder.query();

        if (existingQuery instanceof BoolQueryBuilder) {
            ((BoolQueryBuilder) existingQuery).filter(rangeQueryBuilder);
        }
    }

    private void checkForbiddenWord(String keyword) {
        ForbiddenWordValidator forbiddenWordValidator = new ForbiddenWordValidator(forbiddenPath);
        if (forbiddenWordValidator.isForbiddenWord(keyword)) {
            throw new RuntimeException("금칙어가 포함되어 있습니다.");
        }
    }

    private void setupHighlighting(SearchSourceBuilder sourceBuilder) {
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.requireFieldMatch(false);
        highlightBuilder.field(new HighlightBuilder.Field("*").fragmentSize(10000).numOfFragments(0));
        highlightBuilder.preTags("<b>").postTags("</b>");
        sourceBuilder.highlighter(highlightBuilder);
    }

    private void buildAdvancedSearchQuery(String keyword, SearchSourceBuilder sourceBuilder, List<String> fieldDesignation) {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        boolean searchAllFields = fieldDesignation.isEmpty() || (fieldDesignation.size() == 1 && Field.ALL.get().equals(fieldDesignation.get(0)));
        List<String> keywords = getKeywords(keyword);

        for (String word : keywords) {

            if (word.isEmpty()) continue;

            if (word.startsWith(AdvancedSearch.EXCLUDE.get())) {
                String excludeKeyword = word.substring(1);
                if (searchAllFields) {
                    boolQueryBuilder.mustNot(QueryBuilders.multiMatchQuery(excludeKeyword, "*")).boost(2.0f);
                } else {
                    for (String field : fieldDesignation) {
                        boolQueryBuilder.mustNot(QueryBuilders.matchQuery(field, excludeKeyword)).boost(2.0f);
                    }
                }
            } else if (word.startsWith(AdvancedSearch.EQUAL.get()) && word.endsWith(AdvancedSearch.EQUAL.get())) {
                String exactMatchKeyword = word.substring(1, word.length() - 1);
                if (searchAllFields) {
                    boolQueryBuilder.must(QueryBuilders.multiMatchQuery(exactMatchKeyword, "*").type(MatchQuery.Type.PHRASE)).boost(2.0f);
                } else {
                    for (String field : fieldDesignation) {
                        boolQueryBuilder.must(QueryBuilders.termQuery(field, exactMatchKeyword)).boost(2.0f);
                    }
                }
            } else if (word.startsWith(AdvancedSearch.INCLUDE.get())) {
                String includeKeyword = word.substring(1);
                if (searchAllFields) {
                    boolQueryBuilder.must(QueryBuilders.multiMatchQuery(includeKeyword, "*")).boost(2.0f);
                } else {
                    for (String field : fieldDesignation) {
                        boolQueryBuilder.must(QueryBuilders.matchQuery(field, includeKeyword)).boost(2.0f);
                    }
                }
            } else {
                if (searchAllFields) {
                    boolQueryBuilder.should(QueryBuilders.multiMatchQuery(word, "*")).boost(2.0f);
                } else {
                    for (String field : fieldDesignation) {
                        boolQueryBuilder.should(QueryBuilders.matchQuery(field, word)).boost(2.0f);
                    }
                }
            }
        }
        sourceBuilder.query(boolQueryBuilder);
        logger.info(sourceBuilder.query().toString());
    }

    private void indexSearchTerm(ElasticConfiguration elasticConfiguration, SearchCriteria criteria) throws IOException {
        SimpleDateFormat dateFormat = new SimpleDateFormat(TopSearched.DATE_FORMAT.get());
        String formattedDate = dateFormat.format(new Date());

        List<String> termsToIndex = parseAndFilterKeywords(criteria.getKeyword());
        for (String term : termsToIndex) {
            IndexRequest indexRequest = new IndexRequest(topSearchedIndex);
            indexRequest.source(String.format("{\"keyword\": \"%s\", \"searchedDate\": \"%s\"}", term, dateFormat.format(new Date())), XContentType.JSON);
            IndexResponse response = elasticConfiguration.getElasticClient().index(indexRequest, RequestOptions.DEFAULT);
            logger.info("Search log Id: {}, keyword: {}, searchedDate: {}", response.getId(), term, formattedDate);
        }
    }

    private List<String> parseAndFilterKeywords(String keyword) {
        List<String> termsToIndex = new ArrayList<>();
        List<String> keywords = getKeywords(keyword);

        for (String word : keywords) {
            if (word.startsWith(AdvancedSearch.EXCLUDE.get()) || word.isEmpty()) {
                continue;
            }

            if (word.startsWith(AdvancedSearch.INCLUDE.get()) && !word.substring(1).isEmpty()) {
                termsToIndex.add(word.substring(1).trim());
            } else if (word.startsWith(AdvancedSearch.EQUAL.get()) && word.endsWith(AdvancedSearch.EQUAL.get()) && !word.substring(1, word.length() - 1).isEmpty()) {
                termsToIndex.add(word.substring(1, word.length() - 1).trim());
            } else if (!word.equals("+") && !word.equals("\"\"") && !word.isEmpty()) {
                termsToIndex.add(word.trim());
            }
        }
        return termsToIndex;
    }

    private void isValid(String period) {
        for (Period specificPeriod : Period.values()) {
            if (period.equals(specificPeriod.get())) return;
        }

        if (!GenericValidator.isNullOrEmpty(period) && !checkIsValidPeriod(period)) {
            throw new IllegalArgumentException("올바른 날짜 형식을 입력하세요. (전체 기간: 'all' 또는 공백 입력   특정 기간 선택: 'year', 'month', 'week', 'day', 'yyyy.MM.dd~yyyy.MM.dd' 형식으로 입력해주세요.)");
        }
    }

    private boolean checkIsValidPeriod(String period) {
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TopSearched.DATE_FORMAT.get());

        if (period.contains(TopSearched.DELIMETER.get())) {
            String[] periods = period.split(TopSearched.DELIMETER.get());
            if (periods.length != 2) {
                return false;
            }

            try {
                LocalDate startDate = LocalDate.parse(periods[0].trim(), formatter);
                LocalDate endDate = LocalDate.parse(periods[1].trim(), formatter);

                return (startDate.isEqual(today) || startDate.isBefore(today)) &&
                        (endDate.isEqual(today) || endDate.isBefore(today)) &&
                        (startDate.isEqual(endDate) || startDate.isBefore(endDate)) &&
                        periods[0].trim().matches(TopSearched.DATE_PATTERN.get()) &&
                        periods[1].trim().matches(TopSearched.DATE_PATTERN.get());
            } catch (DateTimeParseException e) {
                return false;
            }
        }

        return period.trim().equals(TopSearched.ALL.get());
    }

    private static List<String> getKeywords(String keyword) {
        List<String> keywords = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < keyword.length(); i++) {

            if (i >= keyword.length() - 1) {
                builder.append(keyword.charAt(i));
                keywords.add(builder.toString().trim());
            }

            if (keyword.charAt(i) == '+' || keyword.charAt(i) == '-') {
                keywords.add(builder.toString().trim());
                builder.setLength(0);
                builder.append(keyword.charAt(i));
            } else if (keyword.charAt(i) == '\"') {
                keywords.add(builder.toString().trim());
                builder.setLength(0);
                builder.append(keyword.charAt(i++));
                while (keyword.charAt(i) != '\"') {
                    builder.append(keyword.charAt(i++));
                }
                builder.append(keyword.charAt(i));
                while (keyword.charAt(i) == ' ') {
                    i++;
                }
                keywords.add(builder.toString().trim());
                builder.setLength(0);
            } else {
                builder.append(keyword.charAt(i));
            }
        }
        return keywords;
    }
}