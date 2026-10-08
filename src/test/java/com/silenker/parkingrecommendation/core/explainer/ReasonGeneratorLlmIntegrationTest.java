package com.silenker.parkingrecommendation.core.explainer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
import com.silenker.parkingrecommendation.model.dto.RecommendResultDTO;
import com.silenker.parkingrecommendation.service.IAiService;
import com.silenker.parkingrecommendation.service.impl.AiServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real LLM API smoke test for ReasonGenerator.
 * Only runs when AI_API_KEY environment variable is set.
 * Default mvn test should NOT call real API.
 */
@Tag("llm-integration")
class ReasonGeneratorLlmIntegrationTest {

    private ReasonGenerator reasonGenerator;

    @BeforeEach
    void setUp() {
        String apiKey = System.getenv("AI_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(),
                "Skipping: AI_API_KEY environment variable not set");

        String baseUrl = System.getenv().getOrDefault("AI_BASE_URL", "https://api.xiaomimimo.com/v1");
        String model = System.getenv().getOrDefault("AI_MODEL", "mimo-v2.5-pro");
        double temperature = Double.parseDouble(System.getenv().getOrDefault("AI_TEMPERATURE", "0.2"));

        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMs = Integer.parseInt(System.getenv().getOrDefault("AI_TIMEOUT_MS", "15000"));
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        RestTemplate restTemplate = new RestTemplate(factory);

        AiServiceImpl aiService = new AiServiceImpl(restTemplate, objectMapper);
        injectField(aiService, "baseUrl", baseUrl);
        injectField(aiService, "apiKey", apiKey);
        injectField(aiService, "model", model);
        injectField(aiService, "temperature", temperature);

        reasonGenerator = new ReasonGenerator(aiService);
    }

    @Test
    @DisplayName("Smoke: explanation is non-empty")
    void smokeTestExplanationNonEmpty() {
        RecommendResultDTO rec = new RecommendResultDTO();
        rec.setSpotId("S001");
        rec.setHeightLimit(new BigDecimal("2.50"));
        rec.setHasCharging(true);
        rec.setSpotType("standard");
        rec.setDistanceToElevator(15);
        rec.setPricePerHour(new BigDecimal("8.00"));
        rec.setScore(0.85);
        rec.setScoreDetails(Map.of("distance_to_elevator", 0.7, "price", 0.4));

        ParsedConditionsDTO conditions = new ParsedConditionsDTO();
        conditions.setHardConditions(new ArrayList<>());
        conditions.setSoftPreferences(new ArrayList<>());
        conditions.setConfidence(0.9);
        conditions.setAmbiguousTerms(new ArrayList<>());
        conditions.setRequiresConfirmation(new ArrayList<>());
        conditions.setInferredDefaults(new ArrayList<>());

        String result = reasonGenerator.generate(rec, conditions, "我车高2.3米，得充电");

        assertNotNull(result, "Real API should return non-null explanation");
        assertFalse(result.isBlank(), "Explanation should not be blank");

        System.out.println("=== Real LLM Explanation Smoke Test ===");
        System.out.println("Explanation: " + result);
        // Do NOT output API key
    }

    @Test
    @DisplayName("Smoke: explanation contains spot ID")
    void smokeTestContainsSpotId() {
        RecommendResultDTO rec = new RecommendResultDTO();
        rec.setSpotId("S001");
        rec.setHeightLimit(new BigDecimal("2.50"));
        rec.setHasCharging(true);
        rec.setSpotType("standard");
        rec.setDistanceToElevator(15);
        rec.setPricePerHour(new BigDecimal("8.00"));
        rec.setScore(0.85);
        rec.setScoreDetails(Map.of("distance_to_elevator", 0.7, "price", 0.4));

        ParsedConditionsDTO conditions = new ParsedConditionsDTO();
        conditions.setHardConditions(new ArrayList<>());
        conditions.setSoftPreferences(new ArrayList<>());
        conditions.setConfidence(0.9);
        conditions.setAmbiguousTerms(new ArrayList<>());
        conditions.setRequiresConfirmation(new ArrayList<>());
        conditions.setInferredDefaults(new ArrayList<>());

        String result = reasonGenerator.generate(rec, conditions, "我车高2.3米，得充电");

        assertTrue(result.contains("S001"), "Explanation should contain spot ID S001");
    }

    @Test
    @DisplayName("Smoke: explanation contains time disclaimer")
    void smokeTestContainsTimeDisclaimer() {
        RecommendResultDTO rec = new RecommendResultDTO();
        rec.setSpotId("S002");
        rec.setHeightLimit(new BigDecimal("2.80"));
        rec.setHasCharging(false);
        rec.setSpotType("standard");
        rec.setDistanceToElevator(30);
        rec.setPricePerHour(new BigDecimal("5.00"));
        rec.setScore(0.65);
        rec.setScoreDetails(Map.of("distance_to_elevator", 0.4, "price", 0.8));

        ParsedConditionsDTO conditions = new ParsedConditionsDTO();
        conditions.setHardConditions(new ArrayList<>());
        conditions.setSoftPreferences(new ArrayList<>());
        conditions.setConfidence(0.8);
        conditions.setAmbiguousTerms(new ArrayList<>());
        conditions.setRequiresConfirmation(new ArrayList<>());
        conditions.setInferredDefaults(new ArrayList<>());

        String result = reasonGenerator.generate(rec, conditions, "找个便宜车位");

        assertTrue(result.contains("基于当前时刻数据"),
                "Explanation should contain time disclaimer. Actual: " + result);
    }

    private static void injectField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject field: " + fieldName, e);
        }
    }
}
