package com.example.searchAPI.service;

import com.example.searchAPI.config.ElasticConfiguration;
import com.example.searchAPI.constant.autocomplete.Option;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AutoCompleteService {

    @Value("${autocomplete.index}")
    private String autocompleteIndex;

    @Value("${autocomplete.field}")
    private String field;

    @Value("${search.host}")
    private String host;

    @Value("${search.port}")
    private int port;

    @Value("${search.username}")
    private String username;

    @Value("${search.protocol}")
    private String protocol;

    public List<String> autoComplete(String keyword, String option) {
        try (ElasticConfiguration elasticConfiguration = new ElasticConfiguration(host, port, username, protocol)) {
            SearchRequest searchRequest = new SearchRequest(autocompleteIndex);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

            HighlightBuilder highlightBuilder = new HighlightBuilder();
            highlightBuilder.field(field);
            highlightBuilder.preTags("<b>");
            highlightBuilder.postTags("</b>");
            highlightBuilder.numOfFragments(0);
            searchSourceBuilder.highlighter(highlightBuilder);
            searchSourceBuilder.size(100);

            getQueryBuilder(keyword, option, searchSourceBuilder, field);

            searchRequest.source(searchSourceBuilder);

            SearchResponse searchResponse = elasticConfiguration.getElasticClient().search(searchRequest, RequestOptions.DEFAULT);
            Map<String, Integer> frequencyMap = new HashMap<>();
            Pattern pattern = Pattern.compile("<b>(.*?)</b>");

            for (SearchHit hit : searchResponse.getHits().getHits()) {
                HighlightField highlightField = hit.getHighlightFields().get(field);
                if (highlightField != null) {
                    for (Text fragment : highlightField.fragments()) {
                        Matcher matcher = pattern.matcher(fragment.string());
                        while (matcher.find()) {
                            String matched = matcher.group(1);
                            frequencyMap.put(matched, frequencyMap.getOrDefault(matched, 0) + 1);
                        }
                    }
                }
            }

            return frequencyMap.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        } catch (IOException | IllegalArgumentException e) {
            return Collections.singletonList(e.getMessage());
        }
    }

    private static void getQueryBuilder(String keyword, String option, SearchSourceBuilder searchSourceBuilder, String field) {
        option = option.trim();
        if (option.isEmpty()) return;

        if (option.equals(Option.PREFIX.get())) {
            searchSourceBuilder.query(QueryBuilders.prefixQuery(field, keyword));
        } else if (option.equals(Option.SUFFIX.get())) {
            searchSourceBuilder.query(QueryBuilders.wildcardQuery(field, "*" + keyword));
        } else if (option.equals(Option.CONTAINS.get())) {
            searchSourceBuilder.query(QueryBuilders.wildcardQuery(field, "*" + keyword + "*"));
        } else {
            throw new IllegalArgumentException("정확한 option을 입력하세요.");
        }
    }
}
