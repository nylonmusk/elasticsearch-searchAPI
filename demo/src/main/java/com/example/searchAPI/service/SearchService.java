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
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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

    @GetMapping("/")
    public List<String> search(SearchCriteria criteria) {

        try (ElasticConfiguration elasticConfiguration = new ElasticConfiguration(host, port, username, protocol)) {
            SearchRequest searchRequest = new SearchRequest(index);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

            String keyword = criteria.getKeyword();

            checkForbiddenWord(keyword);
            keyword = advancedSearchInSpecificFields(criteria.getFieldDesignation(), keyword, sourceBuilder);
            setDateRange(criteria.getPeriod(), sourceBuilder);
            setPage(criteria.getMaxDocument(), criteria.getNowPage(), sourceBuilder);
            sort(criteria.getSortOption(), sourceBuilder, searchRequest, criteria.getFieldDesignation(), keyword);
            setupHighlighting(sourceBuilder);

            System.out.println("--------------------------------------------");
            System.out.println(sourceBuilder.toString());
            System.out.println(searchRequest.toString());
//            searchRequest.source(sourceBuilder);
//            SearchResponse searchResponse = elasticConfiguration.getElasticClient().search(searchRequest, RequestOptions.DEFAULT);
//            SearchHits hits = searchResponse.getHits();
//
//            List<String> results = new ArrayList<>();
//            for (SearchHit hit : hits) {
//                String result = hit.getSourceAsString(); // 원본 결과
//                Map<String, HighlightField> highlightFields = hit.getHighlightFields();
//
//                // 모든 필드에 대한 하이라이팅 처리
//                for (Map.Entry<String, HighlightField> entry : highlightFields.entrySet()) {
//                    HighlightField field = entry.getValue();
//                    if (field != null) {
//                        Text[] fragments = field.fragments();
//                        String highlightedText = Arrays.stream(fragments)
//                                .map(Text::string)
//                                .collect(Collectors.joining(" "));
//                        // 원본 결과에 하이라이팅된 텍스트를 대체합니다.
//                        result = result.replace(field.getName(), highlightedText);
//                    }
//                }
//                results.add(result);
//            }
//
//            return results;
            searchRequest.source(sourceBuilder);
            SearchResponse searchResponse = elasticConfiguration.getElasticClient().search(searchRequest, RequestOptions.DEFAULT);
            SearchHits hits = searchResponse.getHits();

            List<String> results = new ArrayList<>();
            for (SearchHit hit : hits.getHits()) {
                Map<String, Object> sourceAsMap = hit.getSourceAsMap(); // 원본 문서를 Map 형태로 가져옵니다.
                Map<String, HighlightField> highlightFields = hit.getHighlightFields(); // 하이라이트된 필드를 가져옵니다.

                // 원본 문서의 모든 필드를 순회하며 하이라이트된 결과가 있으면 대체합니다.
                Map<String, String> finalDocument = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : sourceAsMap.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();

                    // 하이라이트 처리된 필드인 경우 하이라이트된 텍스트로 대체
                    if (highlightFields.containsKey(key) && highlightFields.get(key).fragments().length > 0) {
                        Text[] fragments = highlightFields.get(key).fragments();
                        String highlightedText = Arrays.stream(fragments)
                                .map(Text::string)
                                .collect(Collectors.joining(" "));
                        finalDocument.put(key, highlightedText);
                    } else {
                        finalDocument.put(key, value.toString());
                    }
                }

                // 최종 결과 문자열 구성
                String result = finalDocument.entrySet().stream()
                        .map(entry -> entry.getKey() + ": " + entry.getValue())
                        .collect(Collectors.joining("\n"));
                results.add(result);
            }

            return results;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void setPage(int maxDocument, int nowPage, SearchSourceBuilder sourceBuilder) {
        sourceBuilder.from(nowPage);
        sourceBuilder.size(maxDocument);
    }

    private void sort(String sortOption, SearchSourceBuilder sourceBuilder, SearchRequest searchRequest, List<String> fieldDesignation, String keyword) {
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

    private String advancedSearchInSpecificFields(List<String> fieldDesignation, String keyword, SearchSourceBuilder sourceBuilder) {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        String keywordWithoutOperator = keyword;

        // 모든 문서를 대상으로 검색할 때 기본적으로 매칭되어야 할 조건을 추가합니다.
        boolQueryBuilder.must(QueryBuilders.matchAllQuery());

        for (String searchField : fieldDesignation) {
            if (keyword.startsWith(AdvancedSearch.INCLUDE.get())) {
                keywordWithoutOperator = keyword.substring(1);
                boolQueryBuilder.must(QueryBuilders.matchQuery(searchField, keywordWithoutOperator)).boost(2.0f);
            } else if (keyword.startsWith(AdvancedSearch.EXCLUDE.get())) {
                keywordWithoutOperator = keyword.substring(1);
                boolQueryBuilder.mustNot(QueryBuilders.matchQuery(searchField, keywordWithoutOperator)).boost(2.0f);
            } else if (keyword.startsWith(AdvancedSearch.EQUAL.get()) && keyword.endsWith(AdvancedSearch.EQUAL.get())) {
                keywordWithoutOperator = keyword.substring(1, keyword.length() - 1);
                boolQueryBuilder.filter(QueryBuilders.matchPhraseQuery(searchField, keywordWithoutOperator)).boost(2.0f);
            } else {
                boolQueryBuilder.should(QueryBuilders.matchQuery(searchField, keyword)).boost(2.0f);
            }
            sourceBuilder.query(boolQueryBuilder);
        }
        return keywordWithoutOperator;
    }

    private void setDateRange(String period, SearchSourceBuilder sourceBuilder) {
        LocalDate startDate = null;
        LocalDate endDate = LocalDate.now();

        if (Period.ALL.get().equalsIgnoreCase(period)) return;

        if (period.contains("~")) {
            startDate = LocalDate.parse(period.split("~")[0]);
            endDate = LocalDate.parse(period.split("~")[1]);
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

}