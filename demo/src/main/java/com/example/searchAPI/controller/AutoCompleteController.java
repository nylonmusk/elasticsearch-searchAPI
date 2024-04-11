package com.example.searchAPI.controller;

import com.example.searchAPI.config.ElasticConfiguration;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/autocomplete")
public class AutoCompleteController {

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

    @GetMapping("/")
    public List<String> autoComplete(@RequestParam(value = "keyword", required = true) String keyword,
                                     @RequestParam(value = "option", required = true) String option) {
        List<String> suggestions = new ArrayList<>();
        SearchRequest searchRequest = new SearchRequest(index);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        // Query for both title and content
        BoolQueryBuilder combinedFieldsQuery = QueryBuilders.boolQuery();
        switch (option) {
            case "prefix":
                combinedFieldsQuery.should(QueryBuilders.prefixQuery("content", keyword));
                combinedFieldsQuery.should(QueryBuilders.prefixQuery("title", keyword));
                break;
            case "suffix":
                // Note: Elasticsearch doesn't support suffix queries directly, so this part might need adjustment.
                // Using wildcardQuery as an example for a "suffix-like" functionality.
                combinedFieldsQuery.should(QueryBuilders.wildcardQuery("content", "*" + keyword));
                combinedFieldsQuery.should(QueryBuilders.wildcardQuery("title", "*" + keyword));
                break;
            case "contains":
                combinedFieldsQuery.should(QueryBuilders.matchQuery("content", keyword));
                combinedFieldsQuery.should(QueryBuilders.matchQuery("title", keyword));
                break;
            default:
                throw new IllegalArgumentException("Invalid option: " + option);
        }

        boolQuery.must(combinedFieldsQuery);
        searchSourceBuilder.query(boolQuery);
        searchSourceBuilder.size(10);
        searchRequest.source(searchSourceBuilder);

        try (ElasticConfiguration elasticConfiguration = new ElasticConfiguration(host, port, username, protocol)) {
            SearchResponse searchResponse = elasticConfiguration.getElasticClient().search(searchRequest, RequestOptions.DEFAULT);
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                suggestions.add(hit.getSourceAsString());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return suggestions;
    }
}
