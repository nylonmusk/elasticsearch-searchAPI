package com.example.searchAPI.service;

import com.example.searchAPI.config.ElasticConfiguration;
import com.example.searchAPI.constant.search.AdvancedSearch;
import com.example.searchAPI.constant.search.Category;
import com.example.searchAPI.constant.search.Period;
import com.example.searchAPI.constant.search.Sort;
import com.example.searchAPI.constant.topsearched.TopSearched;
import com.example.searchAPI.controller.ElasticsearchController;
import com.example.searchAPI.model.SearchCriteria;
import com.example.searchAPI.validator.ForbiddenWordValidator;
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

            Map<String, List<String>> categorizedResults = new HashMap<>();
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

                        sourceAsMap.forEach((key, value) -> {
                            if (criteria.getFieldDesignation().contains(key) && highlightFields.containsKey(key) && highlightFields.get(key).fragments().length > 0) {
                                String highlightedText = Arrays.stream(highlightFields.get(key).fragments()).map(Text::string).collect(Collectors.joining(" "));
                                documentResults.put(key, highlightedText);
                            } else {
                                documentResults.put(key, value.toString());
                            }
                        });

                        // 결과 문자열 조합
                        StringBuilder result = new StringBuilder("{");
                        documentResults.forEach((key, value) -> result.append(String.format("\"%s\":\"%s\",", key, value)));
                        result.deleteCharAt(result.length() - 1);
                        result.append("}");

                        resultsForCategory.add(result.toString());
                    }
                }
            }

            List<String> finalResults = new ArrayList<>();
            categorizedResults.values().forEach(finalResults::addAll);
            return finalResults;
        } catch (Exception e) {
            return Collections.singletonList(e.getMessage());
        }
    }

    private void setPage(int maxDocument, int nowPage, SearchSourceBuilder sourceBuilder) {
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

        if (Period.ALL.get().equalsIgnoreCase(period)) return;

        if (period.contains(Period.DELIMETER.get())) {
            startDate = LocalDate.parse(period.split(Period.DELIMETER.get())[0]);
            endDate = LocalDate.parse(period.split(Period.DELIMETER.get())[1]);
        } else if (Period.DAY.get().equalsIgnoreCase(period)) {
            startDate = endDate.minusDays(1);
        } else if (Period.WEEK.get().equalsIgnoreCase(period)) {
            startDate = endDate.minusWeeks(1);
        } else if (Period.MONTH.get().equalsIgnoreCase(period)) {
            startDate = endDate.minusMonths(1);
        } else if (Period.YEAR.get().equalsIgnoreCase(period)) {
            startDate = endDate.minusYears(1);
        }

        RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(Period.TARGET.get()).format(Period.FORMAT.get()).from(startDate.format(DateTimeFormatter.ISO_LOCAL_DATE)).to(endDate.format(DateTimeFormatter.ISO_LOCAL_DATE));

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

        String[] parts = keyword.split("\\s+");

        for (String part : parts) {
            if (part.startsWith(AdvancedSearch.EXCLUDE.get())) {
                String excludeKeyword = part.substring(1);
                for (String field : fieldDesignation) {
                    boolQueryBuilder.mustNot(QueryBuilders.matchQuery(field, excludeKeyword)).boost(2.0f);
                }
            } else if (part.startsWith(AdvancedSearch.INCLUDE.get())) {
                String includeKeyword = part.substring(1);
                for (String field : fieldDesignation) {
                    boolQueryBuilder.must(QueryBuilders.matchQuery(field, includeKeyword)).boost(2.0f);
                }
            } else if (part.startsWith(AdvancedSearch.EQUAL.get()) && part.endsWith(AdvancedSearch.EQUAL.get())) {
                String exactMatchKeyword = part.substring(1, part.length() - 1);
                for (String field : fieldDesignation) {
                    boolQueryBuilder.must(QueryBuilders.matchPhraseQuery(field, exactMatchKeyword)).boost(2.0f);
                }
            } else {
                for (String field : fieldDesignation) {
                    boolQueryBuilder.should(QueryBuilders.matchQuery(field, part)).boost(2.0f);
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
            if (!part.startsWith(AdvancedSearch.EXCLUDE.get())) {
                if (part.startsWith(AdvancedSearch.EQUAL.get()) && part.endsWith(AdvancedSearch.EQUAL.get())) {
                    termsToIndex.add(part.substring(1, part.length() - 1));
                } else if (!part.startsWith(AdvancedSearch.INCLUDE.get())) {
                    termsToIndex.add(part);
                }
            }
        }
        return termsToIndex;
    }
}