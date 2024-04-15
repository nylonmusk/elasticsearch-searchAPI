package com.example.searchAPI.service;

import com.example.searchAPI.config.ElasticConfiguration;
import com.example.searchAPI.constant.topsearched.TopSearched;
import com.example.searchAPI.validator.GenericValidator;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TopSearchedService {

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

    @Value("${topsearched.field.date}")
    private String date;

    @Value("${topsearched.field.keyword}")
    private String keyword;

    public List<String> topSearched(String period, Integer N) {
        try (ElasticConfiguration elasticConfiguration = new ElasticConfiguration(host, port, username, protocol)) {
            SearchRequest searchRequest = new SearchRequest(topSearchedIndex);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            isValidParameter(period, N);
            QueryBuilder queryBuilder = buildDateRangeQuery(period);
            searchSourceBuilder.query(queryBuilder);
            setTopSearchedSize(N, searchSourceBuilder, keyword);
            searchRequest.source(searchSourceBuilder);
            SearchResponse response = elasticConfiguration.getElasticClient().search(searchRequest, RequestOptions.DEFAULT);

            Terms terms = response.getAggregations().get("top_search_terms");
            return terms.getBuckets().stream()
                    .map(bucket -> bucket.getKeyAsString() + " (" + bucket.getDocCount() + ")")
                    .collect(Collectors.toList());
        } catch (IOException | IllegalArgumentException e) {
            List<String> errorMessages = new ArrayList<>();
            errorMessages.add(e.getMessage());
            return errorMessages;
        }
    }

    private static void setTopSearchedSize(Integer N, SearchSourceBuilder searchSourceBuilder, String keyword) {
        if (N != null) {
            searchSourceBuilder.aggregation(
                    AggregationBuilders.terms("top_search_terms").field(keyword).size(N)
            );
        } else {
            searchSourceBuilder.aggregation(
                    AggregationBuilders.terms("top_search_terms").field(keyword)
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

    private void isValidParameter(String period, Integer N) {
        if (N != null && !GenericValidator.isInteger(N)) {
            throw new IllegalArgumentException("올바른 크기를 입력하세요.)");
        }
        if (!GenericValidator.isNullOrEmpty(period) && !checkIsValidPeriod(period)) {
            throw new IllegalArgumentException("올바른 날짜 형식을 입력하세요. (전체 기간: 'all' 또는 공백 입력   특정 기간 선택: 'yyyy.MM.dd~yyyy.MM.dd' 형식으로 입력해주세요.)");
        }
    }

    private boolean checkIsValidPeriod(String period) {
        String trimmedPeriod = period.trim();
        return (trimmedPeriod.equals(TopSearched.ALL.get()) || trimmedPeriod.matches(TopSearched.DATE_PATTERN.get()));
    }
}
