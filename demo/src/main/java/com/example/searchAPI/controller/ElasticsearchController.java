package com.example.searchAPI.controller;

import com.example.searchAPI.config.ElasticConfiguration;
import com.example.searchAPI.constant.search.Category;
import com.example.searchAPI.constant.search.Period;
import com.example.searchAPI.constant.Sort;
import com.example.searchAPI.constant.search.SpecificSearch;
import com.example.searchAPI.validator.ForbiddenWordValidator;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RestController
@RequestMapping("/api")
public class ElasticsearchController {

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

    @GetMapping("/search")
    public List<String> search(@RequestParam(required = true) List<String> fieldDesignation,
                               @RequestParam(required = true) String period,
                               @RequestParam(required = true) String keyword,
                               @RequestParam(required = true) int maxDocument,
                               @RequestParam(required = true) int nowPage,
                               @RequestParam(required = true) String sortOption,
                               @RequestParam(required = true) List<String> categories,
                               @RequestParam(required = true) List<Integer> categoryMaxCounts) {

        try (ElasticConfiguration elasticConfiguration = new ElasticConfiguration(host, port, username, protocol)) {
            SearchRequest searchRequest = new SearchRequest(index);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            // 금칙어 처리 적용
            checkForbiddenWord(keyword);

            // 고급(상세) 검색
            // 필드 지정
            advancedSearchInSpecificFields(fieldDesignation, keyword, sourceBuilder);

            // 카테고리 필터링 및 카테고리별 최대 출력 건수 조절
            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

            for (int i = 0; i < categories.size(); i++) {
                String category = categories.get(i);
                int maxCount = categoryMaxCounts.get(i);

                // 새로운 소스 빌더에 출력 건수 설정
                sourceBuilder.size(maxCount);

                // 각 반복에서 새로운 필터 적용
                BoolQueryBuilder tempBoolQuery = QueryBuilders.boolQuery();
                tempBoolQuery.filter(QueryBuilders.termQuery(Category.CATEGORY.get(), category));

                // 이전 설정을 초기화하지 않고 계속 누적하도록 함
                boolQueryBuilder.should(tempBoolQuery);

                // 최종 boolQueryBuilder 및 sourceBuilder 설정을 사용하여 검색 실행
                sourceBuilder.query(boolQueryBuilder);
                searchRequest.source(sourceBuilder);
            }

            // 조회 기간 필터링
            setDateRange(period, boolQueryBuilder, sourceBuilder);

            // 최대 문서수, 현재 페이지
            setPage(maxDocument, nowPage, sourceBuilder);

            // 정확도순, 날짜순(최신순, 오래된순) 정렬
            sort(sortOption, sourceBuilder, searchRequest);

            // 하이라이팅 설정
//            HighlightBuilder highlightBuilder = new HighlightBuilder();
//            HighlightBuilder.Field highlightTitle = new HighlightBuilder.Field("title");
//            highlightTitle.highlighterType("unified");
//            highlightBuilder.field(highlightTitle);
//            highlightBuilder.requireFieldMatch(false);
//            sourceBuilder.highlighter(highlightBuilder);

            HighlightBuilder highlightBuilder = new HighlightBuilder();
            highlightBuilder.preTags("<b>");
            highlightBuilder.postTags("</b>");
            for (String searchField : fieldDesignation) {
                HighlightBuilder.Field highlightField = new HighlightBuilder.Field(searchField);
                highlightBuilder.field(highlightField);
            }
            highlightBuilder.requireFieldMatch(false);
            sourceBuilder.highlighter(highlightBuilder);

            // 하이라이팅 설정을 쿼리에 추가
            searchRequest.source(sourceBuilder);

            // 쿼리 실행
            SearchResponse searchResponse = elasticConfiguration.getElasticClient().search(searchRequest, RequestOptions.DEFAULT);

            // 결과 반환
            SearchHits hits = searchResponse.getHits();
            List<String> searchResults = new ArrayList<>();

            for (SearchHit hit : hits.getHits()) {
                Map<String, HighlightField> highlightFields = hit.getHighlightFields();
                StringBuilder fragmentString = new StringBuilder();

                for (String field : highlightFields.keySet()) {
                    HighlightField highlight = highlightFields.get(field);
                    Text[] fragments = highlight.fragments();
                    for (Text fragment : fragments) {
                        fragmentString.append(fragment.string());
                    }
                }
                String result = hit.getSourceAsString();
                searchResults.add(result + "\n" + fragmentString.toString());
            }
            return searchResults;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void setPage(int maxDocument, int nowPage, SearchSourceBuilder sourceBuilder) {
        sourceBuilder.from(nowPage);
        sourceBuilder.size(maxDocument);
    }

    @RequestMapping("/autoComplete")
    public List<String> autoComplete(@RequestParam(value = "keyword", required = true) String keyword,
                                     @RequestParam(value = "option", required = true) String option) {

        List<String> suggestions = new ArrayList<>();

        try (ElasticConfiguration elasticConfiguration = new ElasticConfiguration(host, port, username, protocol)) {
            SearchRequest searchRequest = new SearchRequest(index);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
            switch (option) {
                case "prefix":
                    boolQuery.must(QueryBuilders.prefixQuery("content", keyword));
                    break;
                case "suffix":
                    boolQuery.must(QueryBuilders.matchPhrasePrefixQuery("content", keyword));
                    break;
                case "contains":
                    boolQuery.must(QueryBuilders.matchQuery("content", keyword));
                    break;
                default:
                    throw new IllegalArgumentException("Invalid option: " + option);
            }

            boolQuery.should(QueryBuilders.matchQuery("title", keyword));
            searchSourceBuilder.query(boolQuery);
            searchSourceBuilder.size(10);
            searchRequest.source(searchSourceBuilder);
            SearchResponse searchResponse = elasticConfiguration.getElasticClient().search(searchRequest, RequestOptions.DEFAULT);
            SearchHit[] searchHits = searchResponse.getHits().getHits();
            for (SearchHit hit : searchHits) {
                suggestions.add(hit.getSourceAsString());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return suggestions;
    }


    @RequestMapping("/topSearched")
    public List<String> topSearched(@RequestParam(required = true) String period,
                                    @RequestParam(required = true) int N) {
        try (ElasticConfiguration elasticConfiguration = new ElasticConfiguration(host, port, username, protocol)) {
            // 인기 검색어 기능 구현
            List<String> list = new ArrayList<>();

            list.add("aaa");
            list.add("<em>aaa</em>");
            return list;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void sort(String sortOption, SearchSourceBuilder sourceBuilder, SearchRequest searchRequest) {
        SortOrder sortOrder = SortOrder.DESC;
        if (sortOption.equals(Sort.ACCURACY.get())) {
            sourceBuilder.sort(SortBuilders.scoreSort().order(sortOrder));
        } else if (sortOption.equals(Sort.LATEST.get())) {
            sourceBuilder.sort(SortBuilders.fieldSort("writeDate").order(SortOrder.DESC));
        } else if (sortOption.equals(Sort.EARLIEST.get())) {
            sourceBuilder.sort(SortBuilders.fieldSort("writeDate").order(SortOrder.ASC));
        }
        searchRequest.source(sourceBuilder);
    }

    private void advancedSearchInSpecificFields(List<String> fieldDesignation, String keyword, SearchSourceBuilder sourceBuilder) {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        for (String searchField : fieldDesignation) {
            QueryBuilder queryBuilder;
            String keywordWithoutOperator = keyword;
            if (keyword.startsWith(SpecificSearch.INCLUDE.get())) {
                keywordWithoutOperator = keyword.substring(1);
                queryBuilder = QueryBuilders.matchQuery(searchField, keywordWithoutOperator).boost(2.0f);
                boolQueryBuilder.must(queryBuilder);
            } else if (keyword.startsWith(SpecificSearch.EXCLUDE.get())) {
                keywordWithoutOperator = keyword.substring(1);
                queryBuilder = QueryBuilders.boolQuery().mustNot(QueryBuilders.matchQuery(searchField, keywordWithoutOperator)).boost(2.0f);
                boolQueryBuilder.should(queryBuilder);
            } else if (keyword.startsWith(SpecificSearch.EQUAL.get()) && keyword.endsWith(SpecificSearch.EQUAL.get())) {
                keywordWithoutOperator = keyword.substring(1, keyword.length() - 1);
                queryBuilder = QueryBuilders.matchPhraseQuery(searchField, keywordWithoutOperator).boost(2.0f);
                boolQueryBuilder.must(queryBuilder);
            } else {
                queryBuilder = QueryBuilders.matchQuery(searchField, keywordWithoutOperator);
                boolQueryBuilder.should(queryBuilder);
            }
        }
        sourceBuilder.query(boolQueryBuilder);
    }


    private void setDateRange(String period, BoolQueryBuilder boolQueryBuilder, SearchSourceBuilder sourceBuilder) {
        LocalDate startDate = null;
        LocalDate endDate = LocalDate.now();
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

        if (startDate != null) {
            RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("writeDate")
                    .format("yyyy-MM-dd")
                    .from(startDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    .to(endDate.format(DateTimeFormatter.ISO_LOCAL_DATE));

            boolQueryBuilder.filter(rangeQueryBuilder);
        }
        sourceBuilder.query(boolQueryBuilder);
    }

    private void checkForbiddenWord(String keyword) {
        ForbiddenWordValidator forbiddenWordValidator = new ForbiddenWordValidator("C:\\Users\\mayfarm\\Documents\\forbidden_words.json");
        if (forbiddenWordValidator.isForbiddenWord(keyword)) {
            throw new RuntimeException("금칙어가 포함되어 있습니다.");
        }
    }
}
