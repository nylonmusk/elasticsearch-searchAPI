package com.example.searchAPI.service;

import com.example.searchAPI.config.ElasticConfiguration;
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
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            System.out.println(keyword);
            // 금칙어 처리 적용
            checkForbiddenWord(keyword);

            // 고급(상세) 검색
            // 필드 지정
            keyword = advancedSearchInSpecificFields(criteria.getFieldDesignation(), keyword, sourceBuilder);
            System.out.println("--------------------------------------------");
            System.out.println(sourceBuilder.toString());
            System.out.println(searchRequest.toString());

            // 정확도순, 날짜순(최신순, 오래된순) 정렬
            sort(criteria.getSortOption(), sourceBuilder, searchRequest, criteria.getFieldDesignation(), keyword);
//            sort(criteria.getSortOption(), sourceBuilder, criteria.getFieldDesignation(), keyword);
            System.out.println("--------------------------------------------");
            System.out.println(sourceBuilder.toString());
            System.out.println(searchRequest.toString());

            // 조회 기간 필터링
            setDateRange(criteria.getPeriod(), sourceBuilder);

            System.out.println("--------------------------------------------");
            System.out.println(sourceBuilder.toString());
            System.out.println(searchRequest.toString());
            // 최대 문서수, 현재 페이지
            setPage(criteria.getMaxDocument(), criteria.getNowPage(), sourceBuilder);

            System.out.println("--------------------------------------------");
            System.out.println(sourceBuilder.toString());
            System.out.println(searchRequest.toString());


            searchRequest.source(sourceBuilder);
            SearchResponse searchResponse = elasticConfiguration.getElasticClient().search(searchRequest, RequestOptions.DEFAULT);
            SearchHits hits = searchResponse.getHits();

            Map<String, List<String>> categorizedResults = new HashMap<>();

            for (SearchHit hit : hits) {
                String category = (String) hit.getSourceAsMap().get(Category.CATEGORY.get());
                // 해당 카테고리의 최대 출력 건수를 가져옴
                int index = criteria.getCategories().indexOf(category);
                int maxCount = criteria.getCategoryMaxCounts().get(index >= 0 ? index : 0);

                List<String> resultsForCategory = categorizedResults.computeIfAbsent(category, k -> new ArrayList<>());

                // 카테고리별 최대 출력 건수를 초과하지 않는 경우에만 결과를 추가
                if (resultsForCategory.size() < maxCount) {
                    Map<String, HighlightField> highlightFields = hit.getHighlightFields();
                    StringBuilder fragmentString = new StringBuilder();

                    // 하이라이트 처리
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

            // 최종 결과를 하나의 리스트로 합치기
            List<String> finalResults = new ArrayList<>();
            categorizedResults.values().forEach(finalResults::addAll);

            return finalResults;

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
            // 기존에 구성된 쿼리를 가져옵니다.
            QueryBuilder existingQuery = sourceBuilder.query();

            // 최소 점수가 1.0을 초과하는 문서만 가져오도록 minScore를 설정합니다.
            // 기존 쿼리(existingQuery)가 FunctionScoreQuery 형태인지 확인하고, 그렇지 않다면 새로운 FunctionScoreQuery를 생성합니다.
            if (!(existingQuery instanceof FunctionScoreQueryBuilder)) {
                FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(
                        existingQuery,
                        new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{} // 필요한 경우 추가 점수 함수를 여기에 구성할 수 있습니다.
                ).boostMode(CombineFunction.MULTIPLY); // 기본 점수와 function_score 쿼리의 결과에 따라 점수를 조정합니다.
                sourceBuilder.query(functionScoreQueryBuilder);
            }

            sourceBuilder.minScore(1.01f);
        } else if (sortOption.equals(Sort.LATEST.get())) {
            sourceBuilder.sort(SortBuilders.fieldSort(Sort.TARGET.get()).order(SortOrder.DESC));
        } else if (sortOption.equals(Sort.EARLIEST.get())) {
            sourceBuilder.sort(SortBuilders.fieldSort(Sort.TARGET.get()).order(SortOrder.ASC));
        }
        searchRequest.source(sourceBuilder);
    }



//    private void sort(String sortOption, SearchSourceBuilder sourceBuilder, SearchRequest searchRequest, List<String> fieldDesignation, String keyword) {
//        if (sortOption.equals(Sort.ACCURACY.get())) {
//            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
//
//            // 기존에 구성된 쿼리가 있으면 그 쿼리와 결합합니다.
//            QueryBuilder existingQuery = sourceBuilder.query();
//            if (existingQuery instanceof BoolQueryBuilder) {
//                ((BoolQueryBuilder) existingQuery).should(boolQuery);
//            } else if (existingQuery != null) {
//                boolQuery.must(existingQuery);
//            }
//            // FunctionScoreQueryBuilder를 구성하고, boost_mode를 설정합니다.
//            FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(
//                    boolQuery,
//                    new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
//                            // 여기에 필요한 경우 추가 점수 함수를 구성할 수 있습니다.
//                    }
//            ).boostMode(CombineFunction.MULTIPLY); // 기본 점수와 function_score 쿼리의 결과에 따라 점수를 조정합니다.
//            sourceBuilder.query(functionScoreQueryBuilder);
//        } else if (sortOption.equals(Sort.LATEST.get())) {
//            sourceBuilder.sort(SortBuilders.fieldSort(Sort.TARGET.get()).order(SortOrder.DESC));
//        } else if (sortOption.equals(Sort.EARLIEST.get())) {
//            sourceBuilder.sort(SortBuilders.fieldSort(Sort.TARGET.get()).order(SortOrder.ASC));
//        }
//            searchRequest.source(sourceBuilder);
//
//    }


//    private void sort(String sortOption, SearchSourceBuilder sourceBuilder, SearchRequest searchRequest, List<String> fieldDesignation, String keyword) {
//        if (sortOption.equals(Sort.ACCURACY.get())) {
//            // BoolQuery를 생성하고, fieldDesignation 리스트에 있는 모든 필드에 대해 matchQuery를 추가합니다.
//            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
//            for (String field : fieldDesignation) {
//                boolQuery.should(QueryBuilders.matchQuery(field, keyword));
//            }
//
//            // function_score 쿼리 생성
//            FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(
//                            boolQuery,
//                            new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
//                            })
//                    .scoreMode(FunctionScoreQuery.ScoreMode.SUM) // 쿼리가 매치된 필드의 스코어 합산
//                    .boostMode(CombineFunction.SUM); // 기본 스코어와 function_score 쿼리의 결과를 합산
//            sourceBuilder.query(functionScoreQueryBuilder);
//        } else if (sortOption.equals(Sort.LATEST.get())) {
//            sourceBuilder.sort(SortBuilders.fieldSort(Sort.TARGET.get()).order(SortOrder.DESC));
//        } else if (sortOption.equals(Sort.EARLIEST.get())) {
//            sourceBuilder.sort(SortBuilders.fieldSort(Sort.TARGET.get()).order(SortOrder.ASC));
//        }
//        searchRequest.source(sourceBuilder);
//    }

    private String advancedSearchInSpecificFields(List<String> fieldDesignation, String keyword, SearchSourceBuilder sourceBuilder) {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        String keywordWithoutOperator = keyword;

        // 모든 문서를 대상으로 검색할 때 기본적으로 매칭되어야 할 조건을 추가합니다.
        boolQueryBuilder.must(QueryBuilders.matchAllQuery());

        for (String searchField : fieldDesignation) {
            if (keyword.startsWith("+")) {
                // +키워드: 해당 필드에 키워드가 포함된 문서만 검색
                keywordWithoutOperator = keyword.substring(1);
                boolQueryBuilder.must(QueryBuilders.matchQuery(searchField, keywordWithoutOperator)).boost(2.0f);
            } else if (keyword.startsWith("-")) {
                // -키워드: 해당 필드에 키워드가 포함되지 않은 문서만 검색
                keywordWithoutOperator = keyword.substring(1);
                boolQueryBuilder.mustNot(QueryBuilders.matchQuery(searchField, keywordWithoutOperator)).boost(2.0f);
            } else if (keyword.startsWith("\"") && keyword.endsWith("\"")) {
                // ""키워드: 해당 필드에 키워드가 정확히 일치하는 문서만 검색
                keywordWithoutOperator = keyword.substring(1, keyword.length() - 1);
                boolQueryBuilder.filter(QueryBuilders.matchPhraseQuery(searchField, keywordWithoutOperator)).boost(2.0f);
            } else {
                // 기본 검색: 해당 필드에 키워드가 포함된 문서 검색
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
}