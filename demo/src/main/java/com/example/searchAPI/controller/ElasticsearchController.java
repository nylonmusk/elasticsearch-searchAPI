package com.example.searchAPI.controller;

import com.example.searchAPI.config.ElasticConfiguration;
import com.example.searchAPI.constant.Category;
import com.example.searchAPI.constant.Period;
import com.example.searchAPI.constant.Sort;

import com.example.searchAPI.validator.ForbiddenWordValidator;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ElasticsearchController {

    @GetMapping("/search")
    public String search(@RequestParam(required = true) List<String> fieldDesignation,
                         @RequestParam(required = true) String period,
                         @RequestParam(required = true) String keyword,
                         @RequestParam(required = true) int maxDocument,
                         @RequestParam(required = true) int nowPage,
                         @RequestParam(required = true) String sortOption,
                         @RequestParam(required = true) List<String> categories,
                         @RequestParam(required = true) List<Integer> categoryMaxCounts) {

        try (ElasticConfiguration elasticConfiguration = new ElasticConfiguration("localhost", 9200, "", "http")) {
            SearchRequest searchRequest = new SearchRequest("search");
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

            // 금칙어 처리 적용
            ForbiddenWordValidator forbiddenWordValidator = new ForbiddenWordValidator("C:\\Users\\mayfarm\\Documents\\forbidden_words.json");
            if (forbiddenWordValidator.isForbiddenWord(keyword)) {
                throw new RuntimeException("금칙어가 포함되어 있습니다.");
            }

            // 고급(상세) 검색
            // 필드 지정
            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

            for (String searchField : fieldDesignation) {
                QueryBuilder queryBuilder;
                String keywordWithoutOperator = keyword;
                if (keyword.startsWith("+")) {
                    keywordWithoutOperator = keyword.substring(1);
                    queryBuilder = QueryBuilders.matchQuery(searchField, keywordWithoutOperator);
                } else if (keyword.startsWith("-")) {
                    keywordWithoutOperator = keyword.substring(1);
                    queryBuilder = QueryBuilders.boolQuery().mustNot(QueryBuilders.matchQuery(searchField, keywordWithoutOperator));
                } else if (keyword.startsWith("\"") && keyword.endsWith("\"")) {
                    keywordWithoutOperator = keyword.substring(1, keyword.length() - 1);
                    queryBuilder = QueryBuilders.matchPhraseQuery(searchField, keywordWithoutOperator);
                } else {
                    queryBuilder = QueryBuilders.matchQuery(searchField, keywordWithoutOperator);
                }
                boolQueryBuilder.should(queryBuilder);
            }
            sourceBuilder.query(boolQueryBuilder);


            System.out.println(sourceBuilder.toString());


            // 카테고리 필터링 및 카테고리별 최대 출력 건수 조절
            for (int i = 0; i < categories.size(); i++) {
                String category = categories.get(i);
                int maxCount = categoryMaxCounts.get(i);

                boolQueryBuilder.filter(QueryBuilders.termQuery(Category.CATEGORY.get(), category));
                sourceBuilder.size(maxCount);

                System.out.println(category);
                System.out.println(maxCount);
            }

            sourceBuilder.query(boolQueryBuilder);
            System.out.println(sourceBuilder.toString());
//            for (String key : categories.keySet()) {
//                sourceBuilder.query(QueryBuilders.termQuery(Category.CATEGORY.get(), key));
//                sourceBuilder.size(categories.get(key));
//            }

            // 조회 기간 필터링
            LocalDate startDate = null;
            LocalDate endDate = LocalDate.now(); // 기본적으로 오늘 날짜를 종료일로 설정

            if (Period.DAY.get().equalsIgnoreCase(period)) {
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
                        .format("yyyy.MM.dd")
                        .from(startDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        .to(endDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
                sourceBuilder.query(rangeQueryBuilder);
            }

            System.out.println(sourceBuilder.toString());

            // 최대 문서수, 현재 페이지
            sourceBuilder.from((nowPage - 1) * maxDocument);
            sourceBuilder.size(maxDocument);

            // 정확도순, 날짜순(최신순, 오래된순) 정렬
            SortOrder sortOrder = SortOrder.DESC;
            if (sortOption.equals(Sort.ACCURACY.get())) {
                sourceBuilder.sort(SortBuilders.scoreSort().order(sortOrder));
            } else if (sortOption.equals(Sort.LATEST.get())) {
                sourceBuilder.sort(SortBuilders.fieldSort("writeDate").order(SortOrder.DESC));
            } else if (sortOption.equals(Sort.EARLIEST.get())) {
                sourceBuilder.sort(SortBuilders.fieldSort("writeDate").order(SortOrder.ASC));
            }
            System.out.println(nowPage);
            System.out.println(maxDocument);
            // 필드 지정 및 하이라이팅 설정
            for (String field : fieldDesignation) {
                // Match Phrase Query를 사용하여 정확한 일치 검색
                sourceBuilder.query(QueryBuilders.matchPhraseQuery(field, keyword));

                // 해당 필드에서만 하이라이팅 설정
                HighlightBuilder highlightBuilder = new HighlightBuilder();
                highlightBuilder.field(field);
                highlightBuilder.requireFieldMatch(false);
                highlightBuilder.preTags("<strong>");
                highlightBuilder.postTags("</strong>");
                sourceBuilder.highlighter(highlightBuilder);
                System.out.println(field);
            }

            searchRequest.source(sourceBuilder);
            System.out.println(searchRequest.toString());
            // Execute search request
            SearchResponse searchResponse = elasticConfiguration.getElasticClient().search(searchRequest, RequestOptions.DEFAULT);
            System.out.println(searchResponse);
            return searchRequest.toString();
            // Process search response as needed

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    @RequestMapping("/api/autoComplete")
    public void autoComplete(@RequestParam(value = "키워드", required = true) String keyword,
                             @RequestParam(value = "일치옵션", required = true) String option) {

        try (ElasticConfiguration elasticConfiguration = new ElasticConfiguration("localhost", 9200, "", "http")) {

        } catch (Exception e) {

        }

    }


    @RequestMapping("/api/topSearched")
    public void topSearched(@RequestParam(value = "조회 기간", required = true) String period) {
        try (ElasticConfiguration elasticConfiguration = new ElasticConfiguration("localhost", 9200, "", "http")) {

        } catch (Exception e) {

        }
    }

//
//    @GetMapping("/zz")
//    public String zz() {
//        System.out.println("Zz");
//        return "Stirng";
//    }
}

