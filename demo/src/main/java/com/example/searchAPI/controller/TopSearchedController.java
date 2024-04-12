package com.example.searchAPI.controller;

import com.example.searchAPI.config.ElasticConfiguration;
import com.example.searchAPI.constant.topsearched.TopSearched;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/topsearched")
public class TopSearchedController {


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

    @GetMapping("/")
    public List<String> topSearched(@RequestParam String period, @RequestParam Integer N) {
        List<String> topSearches = new ArrayList<>();
        try (ElasticConfiguration elasticConfiguration = new ElasticConfiguration(host, port, username, protocol)) {
            SearchRequest searchRequest = new SearchRequest(topSearchedIndex);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

            QueryBuilder queryBuilder = buildDateRangeQuery(period);

            searchSourceBuilder.query(queryBuilder);
            searchSourceBuilder.aggregation(
                    AggregationBuilders.terms("top_search_terms").field("keyword.keyword").size(N)
            );

            searchRequest.source(searchSourceBuilder);
            SearchResponse response = elasticConfiguration.getElasticClient().search(searchRequest, RequestOptions.DEFAULT);

            Terms terms = response.getAggregations().get("top_search_terms");
            topSearches = terms.getBuckets().stream()
                    .map(bucket -> bucket.getKeyAsString() + " (" + bucket.getDocCount() + ")")
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return topSearches;
    }

    private QueryBuilder buildDateRangeQuery(String period) {
        if (period.trim().equalsIgnoreCase(TopSearched.ALL.get()) || period.isEmpty()) {
            return QueryBuilders.matchAllQuery();
        } else {
            String[] dates = period.split(TopSearched.DELIMETER.get());
            return QueryBuilders.rangeQuery("searchedDate")
                    .gte(dates[0].trim())
                    .lte(dates[1].trim());
        }
    }
}