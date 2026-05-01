package com.worm.server.client;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.worm.server.dto.AgentResponse;

import reactor.core.publisher.Mono;

@Component
public class AgentClient {

        private static final Logger logger = LoggerFactory.getLogger(AgentClient.class);

        private final WebClient webClient;

        public AgentClient(@Value("${agent.base-url:http://localhost:8000}") String baseUrl,
                        WebClient.Builder webClientBuilder) {
                this.webClient = webClientBuilder
                                .baseUrl(baseUrl)
                                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                                .build();
        }

        public CompletableFuture<AgentResponse> process(String message) {
                logger.info("Sending to agent: message={}", message);

                return webClient.post()
                                .uri("/agent/process")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("message", message))
                                .retrieve()
                                .bodyToMono(AgentResponse.class)
                                .doOnSuccess(response -> logger.info("Agent response: type={}, content={}",
                                                response.getType(), response.getContent()))
                                .doOnError(WebClientResponseException.class,
                                                ex -> logger.warn("Agent returned status {}: {}", ex.getStatusCode(),
                                                                ex.getResponseBodyAsString()))
                                .onErrorResume(ex -> {
                                        logger.error("Agent call failed", ex);
                                        return Mono.just(fallback());
                                })
                                .timeout(Duration.ofSeconds(30))
                                .toFuture();
        }

        private AgentResponse fallback() {
                AgentResponse r = new AgentResponse();
                r.setType("ignore");
                r.setContent("");
                return r;
        }
}