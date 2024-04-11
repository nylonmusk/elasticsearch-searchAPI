package com.example.searchAPI.service;

import com.example.searchAPI.config.ElasticConfiguration;
import com.example.searchAPI.constant.search.AdvancedSearch;
import com.example.searchAPI.constant.search.Category;
import com.example.searchAPI.constant.search.Period;
import com.example.searchAPI.constant.search.Sort;
import com.example.searchAPI.model.SearchCriteria;
import com.example.searchAPI.validator.ForbiddenWordValidator;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.*;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.TopHitsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class SearchService {

    @Value("${search.index}")
    private String index;

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

    public List<String> search(SearchCriteria criteria) {

        try (ElasticConfiguration elasticConfiguration = new ElasticConfiguration(host, port, username, protocol)) {
            SearchRequest searchRequest = new SearchRequest(index);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

            checkForbiddenWord(criteria.getKeyword());
            buildAdvancedSearchQuery(criteria.getKeyword(), sourceBuilder, criteria.getFieldDesignation());
            setDateRange(criteria.getPeriod(), sourceBuilder);
            setPage(criteria.getMaxDocument(), criteria.getNowPage(), sourceBuilder);
            sort(criteria.getSortOption(), sourceBuilder, searchRequest);
            setupHighlighting(sourceBuilder);


//            setupAggregations(sourceBuilder, criteria.getCategories(), criteria.getCategoryMaxCounts());
            searchRequest.source(sourceBuilder);
            SearchResponse searchResponse = elasticConfiguration.getElasticClient().search(searchRequest, RequestOptions.DEFAULT);
            SearchHits hits = searchResponse.getHits();

            Map<String, List<String>> categorizedResults = new HashMap<>();
            List<String> results = new ArrayList<>();

            if (criteria.getCategories().contains(Category.ALL.get()) || criteria.getCategories().isEmpty()) {
                for (SearchHit hit : hits.getHits()) {
                    // 예시로는 _source를 String으로 변환하여 추가합니다.
                    // 실제로는 hit의 필드 중 필요한 정보를 추출하여 추가해야 할 수 있습니다.
                    String sourceAsString = hit.getSourceAsString();
                    results.add(sourceAsString);
                }
                return results;
            }


// 특정 카테고리에 대한 결과만 포함하는 경우
            for (SearchHit hit : hits.getHits()) {
                String category = (String) hit.getSourceAsMap().get("ctgry"); // 카테고리 필드를 기준으로
                // 카테고리가 criteria에 포함되어 있는지 확인
                if (criteria.getCategories().contains(category)) {
                    int index = criteria.getCategories().indexOf(category);
                    int maxCount = criteria.getCategoryMaxCounts().get(index);

                    List<String> resultsForCategory = categorizedResults.computeIfAbsent(category, k -> new ArrayList<>());

                    // 카테고리별 최대 출력 건수를 초과하지 않는 경우에만 결과를 추가
                    if (resultsForCategory.size() < maxCount) {
                        Map<String, HighlightField> highlightFields = hit.getHighlightFields();
                        StringBuilder fragmentString = new StringBuilder();

                        // 하이라이트 처리된 필드를 가져와서 처리
                        for (String field : highlightFields.keySet()) {
                            HighlightField highlight = highlightFields.get(field);
                            Text[] fragments = highlight.fragments();
                            for (Text fragment : fragments) {
                                fragmentString.append(fragment.string());
                            }
                        }

                        // 원본 문서 정보와 하이라이트된 텍스트를 조합
                        String result = hit.getSourceAsString() + "\nHighlight: " + fragmentString.toString();
                        resultsForCategory.add(result);

                    }
                }
            }

// 최종 결과를 하나의 리스트로 합치기
            List<String> finalResults = new ArrayList<>();
            categorizedResults.values().forEach(finalResults::addAll);

            return finalResults;


//            searchRequest.source(sourceBuilder);
//            SearchResponse searchResponse = elasticConfiguration.getElasticClient().search(searchRequest, RequestOptions.DEFAULT);
//            SearchHits hits = searchResponse.getHits();
//
//
//            List<String> results = new ArrayList<>();
//            for (SearchHit hit : hits.getHits()) {
//                Map<String, Object> sourceAsMap = hit.getSourceAsMap(); // 원본 문서를 Map 형태로 가져옵니다.
//                Map<String, HighlightField> highlightFields = hit.getHighlightFields(); // 하이라이트된 필드를 가져옵니다.
//
//                // 원본 문서의 모든 필드를 순회하며 하이라이트된 결과가 있으면 대체합니다.
//                Map<String, String> finalDocument = new LinkedHashMap<>();
//                for (Map.Entry<String, Object> entry : sourceAsMap.entrySet()) {
//                    String key = entry.getKey();
//                    Object value = entry.getValue();
//
//                    // 하이라이트 처리된 필드인 경우 하이라이트된 텍스트로 대체
//                    if (highlightFields.containsKey(key) && highlightFields.get(key).fragments().length > 0) {
//                        Text[] fragments = highlightFields.get(key).fragments();
//                        String highlightedText = Arrays.stream(fragments)
//                                .map(Text::string)
//                                .collect(Collectors.joining(" "));
//                        finalDocument.put(key, highlightedText);
//                    } else {
//                        finalDocument.put(key, value.toString());
//                    }
//                }
//
//                // 최종 결과 문자열 구성
//                String result = finalDocument.entrySet().stream()
//                        .map(entry -> entry.getKey() + ": " + entry.getValue())
//                        .collect(Collectors.joining("\n"));
//                results.add(result);
//            }
//            return results;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
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
                FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(
                        existingQuery,
                        new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{}
                ).boostMode(CombineFunction.MULTIPLY);
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

//    private String advancedSearchInSpecificFields(List<String> fieldDesignation, String keyword, SearchSourceBuilder sourceBuilder) {
//        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
//        String keywordWithoutOperator = keyword;
//
//        // 모든 문서를 대상으로 검색할 때 기본적으로 매칭되어야 할 조건을 추가합니다.
//        boolQueryBuilder.must(QueryBuilders.matchAllQuery());
//
//        for (String searchField : fieldDesignation) {
//            if (keyword.startsWith(AdvancedSearch.INCLUDE.get())) {
//                keywordWithoutOperator = keyword.substring(1);
//                boolQueryBuilder.must(QueryBuilders.matchQuery(searchField, keywordWithoutOperator)).boost(2.0f);
//            } else if (keyword.startsWith(AdvancedSearch.EXCLUDE.get())) {
//                keywordWithoutOperator = keyword.substring(1);
//                boolQueryBuilder.mustNot(QueryBuilders.matchQuery(searchField, keywordWithoutOperator)).boost(2.0f);
//            } else if (keyword.startsWith(AdvancedSearch.EQUAL.get()) && keyword.endsWith(AdvancedSearch.EQUAL.get())) {
//                keywordWithoutOperator = keyword.substring(1, keyword.length() - 1);
//                boolQueryBuilder.filter(QueryBuilders.matchPhraseQuery(searchField, keywordWithoutOperator)).boost(2.0f);
//            } else {
//                boolQueryBuilder.should(QueryBuilders.matchQuery(searchField, keyword)).boost(2.0f);
//            }
//            sourceBuilder.query(boolQueryBuilder);
//        }
//        return keywordWithoutOperator;
//    }

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

        RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(Period.TARGET.get())
                .format(Period.FORMAT.get())
                .from(startDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .to(endDate.format(DateTimeFormatter.ISO_LOCAL_DATE));

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
        highlightBuilder.requireFieldMatch(false); // 필드 일치 요구사항을 비활성화합니다.
        highlightBuilder.field("*") // 모든 필드를 대상으로 하이라이팅을 적용합니다.
                .preTags("<b>")
                .postTags("</b>");
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

    private void setupAggregations(SearchSourceBuilder sourceBuilder, List<String> categories, List<Integer> categoryMaxCounts) {
        if (!categories.contains(Category.ALL.get()) && !categories.isEmpty()) {
            for (int i = 0; i < categories.size(); i++) {
                TopHitsAggregationBuilder topHitsAgg = AggregationBuilders.topHits(categories.get(i)).size(categoryMaxCounts.get(i));
                TermsAggregationBuilder termsAgg = AggregationBuilders.terms(categories.get(i)).field(Category.CATEGORY.get()).size(categoryMaxCounts.get(i));
                termsAgg.subAggregation(topHitsAgg);
                sourceBuilder.aggregation(termsAgg);
                System.out.println(sourceBuilder);
            }
        }
    }


    private List<String> processSearchResults(SearchResponse searchResponse, String originalQuery) {
        List<String> results = new ArrayList<>();
        String[] queryParts = originalQuery.split("\\s+");

        for (SearchHit hit : searchResponse.getHits()) {
            String sourceString = hit.getSourceAsString(); // or use hit.getSourceAsMap() for more complex data structures

            // Iterate through each part of the query
            for (String part : queryParts) {
                if (part.startsWith("+")) {
                    // +포함 키워드: 포함되는 경우 하이라이트 처리
                    String keyword = part.substring(1);
                    sourceString = sourceString.replaceAll("(?i)(" + Pattern.quote(keyword) + ")", "<b>$1</b>");
                } else if (part.startsWith("-")) {
                    // -제외 키워드: 제외 처리는 여기서 하지 않고 검색 쿼리에서 처리
                    continue;
                } else if (part.startsWith("\"") && part.endsWith("\"")) {
                    // ""일치 키워드: 정확히 일치하는 경우만 하이라이트 처리
                    String keyword = part.substring(1, part.length() - 1);
                    sourceString = sourceString.replaceAll("(?i)\\b(" + Pattern.quote(keyword) + ")\\b", "<b>$1</b>");
                } else {
                    // 일반 키워드: 포함되는 경우 하이라이트 처리
                    sourceString = sourceString.replaceAll("(?i)(" + Pattern.quote(part) + ")", "<b>$1</b>");
                }
            }

            results.add(sourceString);
        }

        return results;
    }

}