package com.worm.server.client;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
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
import com.worm.server.dto.PlanResponse;

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

        public CompletableFuture<AgentResponse> process(String message, String sessionId, List<String> mentions,
                        String pendingTask, Map<String, String> collectedInfo, boolean inInfoCollection) {
                logger.info("Sending to agent: message={}, sessionId={}, mentions={}, pendingTask={}, inInfoCollection={}",
                                message, sessionId, mentions, pendingTask, inInfoCollection);

                Map<String, Object> body = new HashMap<>();
                body.put("message", message);
                if (sessionId != null && !sessionId.isBlank()) {
                        body.put("session_id", sessionId);
                }
                if (mentions != null && !mentions.isEmpty()) {
                        body.put("mentions", mentions);
                }
                if (pendingTask != null && !pendingTask.isBlank()) {
                        body.put("pending_task", pendingTask);
                }
                if (collectedInfo != null && !collectedInfo.isEmpty()) {
                        body.put("collected_info", collectedInfo);
                }
                body.put("in_info_collection", inInfoCollection);

                return webClient.post()
                                .uri("/agent/process")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(body)
                                .retrieve()
                                .bodyToMono(AgentResponse.class)
                                .doOnSuccess(response -> logger.info("Agent response: type={}, content={}, requiresConfirmation={}, suggestedTask={}",
                                                response.getType(), response.getContent(),
                                                response.getMeta() != null ? response.getMeta().getRequiresConfirmation() : null,
                                                response.getMeta() != null ? response.getMeta().getSuggestedTask() : null))
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

        public CompletableFuture<PlanResponse> plan(String task, String message, String sessionId) {
                logger.info("Requesting plan: task={}, sessionId={}", task, sessionId);

                Map<String, String> body = new HashMap<>();
                body.put("task", task);
                body.put("message", message);
                if (sessionId != null && !sessionId.isBlank()) {
                        body.put("session_id", sessionId);
                }

                return webClient.post()
                                .uri("/agent/plan")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(body)
                                .retrieve()
                                .bodyToMono(PlanResponse.class)
                                .doOnSuccess(plan -> logger.info("Plan received: task={}, steps={}",
                                                plan.getTask(), plan.getSteps() != null ? plan.getSteps().size() : 0))
                                .doOnError(WebClientResponseException.class,
                                                ex -> logger.warn("Plan request returned status {}: {}",
                                                                ex.getStatusCode(),
                                                                ex.getResponseBodyAsString()))
                                .onErrorResume(ex -> {
                                        logger.error("Plan request failed", ex);
                                        return Mono.empty();
                                })
                                .timeout(Duration.ofSeconds(30))
                                .toFuture();
        }

        public CompletableFuture<PlanResponse> execute(PlanResponse plan, String sessionId) {
                logger.info("Executing plan: task={}, steps={}, sessionId={}",
                        plan.getTask(), plan.getSteps() != null ? plan.getSteps().size() : 0, sessionId);

                Map<String, Object> body = new HashMap<>();
                body.put("task", plan.getTask());
                body.put("message", plan.getMessage());
                body.put("steps", plan.getSteps());
                body.put("callback_url", "http://localhost:8080/api/agent/progress");
                if (sessionId != null && !sessionId.isBlank()) {
                        body.put("session_id", sessionId);
                }

                return webClient.post()
                                .uri("/agent/execute")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(body)
                                .retrieve()
                                .bodyToMono(PlanResponse.class)
                                .doOnSuccess(result -> logger.info("Execution complete: task={}, steps={}",
                                                result.getTask(),
                                                result.getSteps() != null ? result.getSteps().size() : 0))
                                .doOnError(WebClientResponseException.class,
                                                ex -> logger.warn("Execute request returned status {}: {}",
                                                                ex.getStatusCode(), ex.getResponseBodyAsString()))
                                .onErrorResume(ex -> {
                                        logger.error("Execute request failed", ex);
                                        return Mono.empty();
                                })
                                .timeout(Duration.ofSeconds(120))
                                .toFuture();
        }

        private AgentResponse fallback() {
                AgentResponse r = new AgentResponse();
                r.setType("ignore");
                r.setContent("");
                return r;
        }
}