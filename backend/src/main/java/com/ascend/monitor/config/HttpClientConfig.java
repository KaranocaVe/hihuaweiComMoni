package com.ascend.monitor.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {

    @Bean
    RestClient ascendRestClient(MonitorProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));

        return RestClient.builder()
                .baseUrl(properties.sourceBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.REFERER, properties.sourcePageUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, "AscendComMoni/1.0 public-leaderboard-monitor")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/plain, */*")
                .build();
    }
}
