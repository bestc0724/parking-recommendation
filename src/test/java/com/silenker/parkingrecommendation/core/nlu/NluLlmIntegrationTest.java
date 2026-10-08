package com.silenker.parkingrecommendation.core.nlu;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
import com.silenker.parkingrecommendation.service.IAiService;
import com.silenker.parkingrecommendation.service.impl.AiServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real LLM API smoke test.
 * Only runs when AI_API_KEY environment variable is set.
 * Default mvn test should NOT call real API.
 */
@Tag("llm-integration")
class NluLlmIntegrationTest {

    private NluParser nluParser;

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

        nluParser = new NluParser(aiService, objectMapper);
    }

    @Test
    @DisplayName("Smoke: basic parse returns ParsedConditionsDTO")
    void smokeTestBasicParse() {
        ParsedConditionsDTO result = nluParser.parse("car height 2.3m, must charge");

        assertNotNull(result, "Real API should return non-null result");
        assertNotNull(result.getHardConditions(), "hard_conditions should not be null");
        assertNotNull(result.getSoftPreferences(), "soft_preferences should not be null");
        assertNotNull(result.getConfidence(), "confidence should not be null");
        assertTrue(result.getConfidence() >= 0.0 && result.getConfidence() <= 1.0,
                "confidence should be in [0, 1], actual: " + result.getConfidence());

        System.out.println("=== Real LLM Smoke Test Result ===");
        System.out.println("hard_conditions: " + result.getHardConditions().size());
        System.out.println("soft_preferences: " + result.getSoftPreferences().size());
        System.out.println("confidence: " + result.getConfidence());
        System.out.println("ambiguous_terms: " + result.getAmbiguousTerms());
        System.out.println("requires_confirmation: " + result.getRequiresConfirmation());
        System.out.println("inferred_defaults: " + result.getInferredDefaults());
    }

    @Test
    @DisplayName("Smoke: vague query returns low confidence")
    void smokeTestVagueQuery() {
        ParsedConditionsDTO result = nluParser.parse("just park anywhere");

        assertNotNull(result, "Real API should return non-null result");
        assertNotNull(result.getConfidence());
        assertTrue(result.getConfidence() < 0.75,
                "Vague query confidence should be below 0.75, actual: " + result.getConfidence());
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
