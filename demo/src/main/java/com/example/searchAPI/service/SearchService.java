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
import org.elasticsearch.index.query.*;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {

    @Value("${search.index}")
    private String index;

    @Value("${topsearched.index}")
    private String topSearchedIndex;

    @Value("${search.host}")
    private String host;

    @Value("${search.port}")
    private int port;

    @Value("${search.username}")
    private String username;

    @Value("${search.protocol}")
    private String protocol;

    @Value("${search.forbiddenPath}")
    private String forbiddenPath;

    private final Logger logger = LoggerFactory.getLogger(ElasticsearchController.class);

    public List<String> search(SearchCriteria criteria) {

        try (ElasticConfiguration elasticConfiguration = new ElasticConfiguration(host, port, username, protocol)) {
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
                        // 모든 필드에 대해 하이라이트 처리 및 값 저장
                        sourceAsMap.forEach((key, value) -> {
                            if (highlightFields.containsKey(key) && highlightFields.get(key).fragments().length > 0) {
                                String highlightedText = Arrays.stream(highlightFields.get(key).fragments()).map(Text::string).collect(Collectors.joining(" "));
                                documentResults.put(key, highlightedText);
                            } else {
                                documentResults.put(key, value.toString());
                            }
                        });
                    } else {
                        // 지정된 필드로 검색 조건 확인 및 하이라이트 처리
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
                        // 검색 조건 필드 외의 다른 모든 필드도 결과에 포함
                        sourceAsMap.forEach((key, value) -> {
                            if (!documentResults.containsKey(key)) {
                                documentResults.put(key, value.toString());
                            }
                        });
                    }

                    // 결과 문자열 조합
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
        } else {
            BoolQueryBuilder newBoolQuery = QueryBuilders.boolQuery().filter(rangeQueryBuilder);
            if (existingQuery != null) {
                newBoolQuery.must(existingQuery);
            }
            sourceBuilder.query(newBoolQuery);
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

        String[] parts = keyword.split("\\s+");

        for (String part : parts) {

            if (part.isEmpty()) continue;

            if (part.startsWith(AdvancedSearch.EXCLUDE.get())) {
                String excludeKeyword = part.substring(1).trim();
                if (searchAllFields) {
                    boolQueryBuilder.mustNot(QueryBuilders.multiMatchQuery(excludeKeyword, "*")).boost(2.0f);
                } else {
                    for (String field : fieldDesignation) {
                        boolQueryBuilder.mustNot(QueryBuilders.matchQuery(field, excludeKeyword)).boost(2.0f);
                    }
                }
            } else if (part.startsWith(AdvancedSearch.EQUAL.get()) && part.endsWith(AdvancedSearch.EQUAL.get())) {
                String exactMatchKeyword = part.substring(1, part.length() - 1).trim();
                if (searchAllFields) {
                    boolQueryBuilder.must(QueryBuilders.multiMatchQuery(exactMatchKeyword, "*").type(MatchQuery.Type.PHRASE)).boost(2.0f);
                } else {
                    for (String field : fieldDesignation) {
                        boolQueryBuilder.must(QueryBuilders.termQuery(field, exactMatchKeyword)).boost(2.0f);
                    }
                }
            } else if (part.startsWith(AdvancedSearch.INCLUDE.get())) {
                String includeKeyword = part.substring(1).trim();
                if (searchAllFields) {
                    boolQueryBuilder.must(QueryBuilders.multiMatchQuery(includeKeyword, "*")).boost(2.0f);
                } else {
                    for (String field : fieldDesignation) {
                        boolQueryBuilder.must(QueryBuilders.matchQuery(field, includeKeyword)).boost(2.0f);
                    }
                }
            } else {
                if (searchAllFields) {
                    boolQueryBuilder.should(QueryBuilders.multiMatchQuery(part, "*")).boost(2.0f);
                } else {
                    for (String field : fieldDesignation) {
                        boolQueryBuilder.should(QueryBuilders.matchQuery(field, part)).boost(2.0f);
                    }
                }
            }
        }
        sourceBuilder.query(boolQueryBuilder);
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
        String[] parts = keyword.split("\\s+");

        for (String part : parts) {
            if (part.startsWith(AdvancedSearch.EXCLUDE.get())) {
                continue;
            }

            if (part.startsWith(AdvancedSearch.INCLUDE.get()) && !part.substring(1).isEmpty()) {
                termsToIndex.add(part.substring(1));
            } else if (part.startsWith(AdvancedSearch.EQUAL.get()) && part.endsWith(AdvancedSearch.EQUAL.get()) && !part.substring(1, part.length() - 1).isEmpty()) {
                termsToIndex.add(part.substring(1, part.length() - 1));
            } else if (!part.equals("+") && !part.equals("\"\"") && !part.isEmpty()) {
                termsToIndex.add(part);
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
}