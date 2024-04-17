package com.example.searchAPI.service;

import com.example.searchAPI.config.ElasticConfiguration;
import com.example.searchAPI.constant.topsearched.TopSearched;
import com.example.searchAPI.validator.GenericValidator;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class TopSearchedService {

    @Getter
    @NoArgsConstructor
    public static class TopSearchedData {
        String keyword;
        long count;

        TopSearchedData(String keyword, long count) {
            this.keyword = keyword;
            this.count = count;
        }
    }

    @Autowired
    private ElasticConfiguration elasticConfiguration;

    @Value("${topsearched.index}")
    private String topSearchedIndex;

    @Value("${topsearched.field.date}")
    private String date;

    @Value("${topsearched.field.keyword}")
    private String keyword;

    private static final String topSearchedBucketName = "top_search_terms";

    public List<TopSearchedData> topSearched(String period, Integer N) {
        try {
            SearchRequest searchRequest = new SearchRequest(topSearchedIndex);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            isValidParameter(period);
            QueryBuilder queryBuilder = buildDateRangeQuery(period);
            searchSourceBuilder.query(queryBuilder);
            setTopSearchedSize(N, searchSourceBuilder, keyword);
            searchRequest.source(searchSourceBuilder);
            SearchResponse response = elasticConfiguration.getElasticClient().search(searchRequest, RequestOptions.DEFAULT);
            System.out.println(searchRequest.source());
            Terms terms = response.getAggregations().get(topSearchedBucketName);
            List<TopSearchedData> topSearchedData = new ArrayList<>();

            for (Terms.Bucket bucket : terms.getBuckets()) {
                topSearchedData.add(new TopSearchedData(bucket.getKeyAsString(), bucket.getDocCount()));
            }

            return topSearchedData;
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    private static void setTopSearchedSize(Integer N, SearchSourceBuilder searchSourceBuilder, String keyword) {
        if (N != null) {
            searchSourceBuilder.aggregation(
                    AggregationBuilders.terms(topSearchedBucketName).field(keyword).size(N)
            );
        } else {
            searchSourceBuilder.aggregation(
                    AggregationBuilders.terms(topSearchedBucketName).field(keyword)
            );
        }
    }

    private QueryBuilder buildDateRangeQuery(String period) {
        if (period.trim().equalsIgnoreCase(TopSearched.ALL.get()) || period.isEmpty()) {
            return QueryBuilders.matchAllQuery();
        } else {
            String[] dates = period.split(TopSearched.DELIMETER.get());
            return QueryBuilders.rangeQuery(date)
                    .gte(dates[0].trim())
                    .lte(dates[1].trim());
        }
    }

    private void isValidParameter(String period) {
        if (!GenericValidator.isNullOrEmpty(period) && !checkIsValidPeriod(period)) {
            throw new IllegalArgumentException("올바른 날짜 형식을 입력하세요. (전체 기간: 'all' 또는 공백 입력   특정 기간 선택: 'yyyy.MM.dd~yyyy.MM.dd' 형식으로 입력해주세요.)");
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
