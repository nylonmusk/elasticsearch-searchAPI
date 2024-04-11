package com.example.searchAPI.config;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;

import java.io.Closeable;
import java.io.IOException;

public class ElasticConfiguration implements Closeable {

    private final RestHighLevelClient elasticClient;

    public ElasticConfiguration(String host, int port, String user, String protocol) {
        elasticClient = createElasticsearchClient(host, port, user, protocol);
    }

    @Bean
    public RestHighLevelClient getElasticClient() {
        return elasticClient;
    }

    private RestHighLevelClient createElasticsearchClient(String host, int port, String user, String protocol) {
        RestClientBuilder restClientBuilder = RestClient.builder(new HttpHost(host, port, protocol));
        if (user != null && !user.isEmpty()) {
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user));
            restClientBuilder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }
        return new RestHighLevelClient(restClientBuilder);
    }

    @Override
    public void close() throws IOException {
        elasticClient.close();
    }
}