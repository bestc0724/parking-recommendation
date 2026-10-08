package com.silenker.parkingrecommendation.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silenker.parkingrecommendation.service.IAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements IAiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.openai.base-url}")
    private String baseUrl;

    @Value("${ai.openai.api-key}")
    private String apiKey;

    @Override
    public String chat(String userMessage) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                    "model", "mimo-v2.5-pro",
                    "messages", List.of(
                            Map.of("role", "user", "content", userMessage)
                    ),
                    "temperature", 0.3
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String url = baseUrl + "/chat/completions";

            log.info("Calling AI API: {}", url);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            // 检查响应体
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                log.error("AI API returned empty or non-2xx: status={}, body={}", response.getStatusCode(), response.getBody());
                return null;
            }

            String responseBody = response.getBody();
            log.debug("AI API raw response: {}", responseBody);

            JsonNode root = objectMapper.readTree(responseBody);

            // 检查是否有错误
            if (root.has("error")) {
                log.error("AI API returned error: {}", root.path("error").toString());
                return null;
            }

            // 提取 content
            JsonNode choices = root.path("choices");
            if (choices.isMissingNode() || choices.isEmpty()) {
                log.error("AI API returned no choices: {}", responseBody);
                return null;
            }

            String content = choices.path(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                log.error("AI API returned empty content: {}", responseBody);
                return null;
            }

            log.info("AI response: {}", content);
            return content;
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("AI API timeout or connection error", e);
            return null;
        } catch (Exception e) {
            log.error("AI API call failed", e);
            return null;
        }
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                    "model", "mimo-v2.5-pro",
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMessage)
                    ),
                    "temperature", 0.3,
                    "max_tokens", 300
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String url = baseUrl + "/chat/completions";

            log.info("Calling AI API with system prompt: {}", url);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                log.error("AI API returned empty or non-2xx: status={}", response.getStatusCode());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("error")) {
                log.error("AI API returned error: {}", root.path("error").toString());
                return null;
            }

            JsonNode choices = root.path("choices");
            if (choices.isMissingNode() || choices.isEmpty()) return null;

            String content = choices.path(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) return null;

            log.info("AI response: {}", content);
            return content;
        } catch (Exception e) {
            log.error("AI API call failed", e);
            return null;
        }
    }
}
