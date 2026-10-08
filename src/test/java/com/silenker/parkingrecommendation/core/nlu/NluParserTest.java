package com.silenker.parkingrecommendation.core.nlu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.silenker.parkingrecommendation.model.dto.ParsedConditionsDTO;
import com.silenker.parkingrecommendation.service.IAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NluParser + ConditionClassifier unit tests.
 * No MySQL dependency, no real LLM network dependency.
 */
class NluParserTest {

    private ObjectMapper objectMapper;
    private ConditionClassifier classifier;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        classifier = new ConditionClassifier();
    }

    // ==================== Scenario 1: explicit height + must charge ====================
    @Test
    @DisplayName("T01: explicit height and must charge -> height hard + charging hard")
    void explicitHeightAndCharging() {
        String fakeResponse =
                "{"
                + "\"hard_conditions\": ["
                + "  {\"type\": \"height\", \"value\": \"2.3\", \"unit\": \"m\", \"source\": \"explicit\"},"
                + "  {\"type\": \"charging\", \"value\": \"true\", \"source\": \"explicit\"}"
                + "],"
                + "\"soft_preferences\": [],"
                + "\"confidence\": 0.95,"
                + "\"ambiguous_terms\": [],"
                + "\"requires_confirmation\": [],"
                + "\"inferred_defaults\": []"
                + "}";

        IAiService fakeAi = query -> fakeResponse;
        NluParser parser = new NluParser(fakeAi, objectMapper);

        ParsedConditionsDTO result = parser.parse("height 2.3m, must charge");

        assertNotNull(result);
        assertEquals(2, result.getHardConditions().size());

        ParsedConditionsDTO.HardCondition height = result.getHardConditions().get(0);
        assertEquals("height", height.getType());
        assertEquals("2.3", height.getValue());

        ParsedConditionsDTO.HardCondition charging = result.getHardConditions().get(1);
        assertEquals("charging", charging.getType());
        assertEquals("true", charging.getValue());

        assertEquals(0.95, result.getConfidence(), 0.01);
    }

    // ==================== Scenario 2: weak charging -> soft preference ====================
    @Test
    @DisplayName("T06: weak charging -> charging soft preference")
    void weakChargingAsSoftPreference() {
        String fakeResponse =
                "{"
                + "\"hard_conditions\": [],"
                + "\"soft_preferences\": ["
                + "  {\"type\": \"charging\", \"weight\": 0.5, \"source\": \"explicit\"}"
                + "],"
                + "\"confidence\": 0.85,"
                + "\"ambiguous_terms\": [],"
                + "\"requires_confirmation\": [],"
                + "\"inferred_defaults\": []"
                + "}";

        IAiService fakeAi = query -> fakeResponse;
        NluParser parser = new NluParser(fakeAi, objectMapper);

        ParsedConditionsDTO result = parser.parse("prefer charging if possible");
        result = classifier.classify(result, "prefer charging if possible");

        assertNotNull(result);
        assertTrue(result.getSoftPreferences().stream()
                .anyMatch(sp -> "charging".equals(sp.getType())));
        assertTrue(result.getHardConditions().stream()
                .noneMatch(hc -> "charging".equals(hc.getType())));
    }

    // ==================== Scenario 3: vague height -> no guessing ====================
    @Test
    @DisplayName("T05: vague height -> no guessing, ambiguous non-empty, confirmation non-empty")
    void vagueHeightNotGuessed() {
        String fakeResponse =
                "{"
                + "\"hard_conditions\": ["
                + "  {\"type\": \"height\", \"value\": \"2.1\", \"unit\": \"m\", \"source\": \"inferred\"}"
                + "],"
                + "\"soft_preferences\": [],"
                + "\"confidence\": 0.4,"
                + "\"ambiguous_terms\": [\"quite tall\"],"
                + "\"requires_confirmation\": [\"height\"],"
                + "\"inferred_defaults\": [\"guessed height\"]"
                + "}";

        IAiService fakeAi = query -> fakeResponse;
        NluParser parser = new NluParser(fakeAi, objectMapper);

        ParsedConditionsDTO result = parser.parse("my car is quite tall, need charging");
        result = classifier.classify(result, "my car is quite tall, need charging");

        assertNotNull(result);
        // Height should not have a concrete number (ConditionClassifier removes guesses)
        boolean hasHeightWithNumber = result.getHardConditions().stream()
                .anyMatch(hc -> "height".equals(hc.getType()) && hc.getValue() != null);
        assertFalse(hasHeightWithNumber, "Should not guess concrete height number");
        assertFalse(result.getAmbiguousTerms().isEmpty(), "ambiguous_terms should not be empty");
        assertTrue(result.getRequiresConfirmation().contains("height"),
                "requires_confirmation should contain height");
    }

    // ==================== Scenario 4: AI returns markdown fence ====================
    @Test
    @DisplayName("AI returns ```json fence -> correctly extract JSON")
    void extractJsonFromFence() {
        String fencedResponse =
                "Here is the parsed result:\n"
                + "```json\n"
                + "{"
                + "\"hard_conditions\": ["
                + "  {\"type\": \"height\", \"value\": \"2.0\", \"unit\": \"m\", \"source\": \"explicit\"}"
                + "],"
                + "\"soft_preferences\": [],"
                + "\"confidence\": 0.9,"
                + "\"ambiguous_terms\": [],"
                + "\"requires_confirmation\": [],"
                + "\"inferred_defaults\": []"
                + "}\n"
                + "```";

        IAiService fakeAi = query -> fencedResponse;
        NluParser parser = new NluParser(fakeAi, objectMapper);

        ParsedConditionsDTO result = parser.parse("car height 2m");

        assertNotNull(result);
        assertEquals(1, result.getHardConditions().size());
        assertEquals("height", result.getHardConditions().get(0).getType());
    }

    // ==================== Scenario 5: AI returns extra text + JSON ====================
    @Test
    @DisplayName("AI returns extra text + JSON -> correctly extract")
    void extractJsonFromExtraText() {
        String responseWithExtraText =
                "I analyzed the user's parking request.\n\n"
                + "{\"hard_conditions\": [{\"type\": \"height\", \"value\": \"2.0\", \"unit\": \"m\", \"source\": \"explicit\"}], "
                + "\"soft_preferences\": [], \"confidence\": 0.88, \"ambiguous_terms\": [], "
                + "\"requires_confirmation\": [], \"inferred_defaults\": []}\n\n"
                + "This covers the requirements.";

        IAiService fakeAi = query -> responseWithExtraText;
        NluParser parser = new NluParser(fakeAi, objectMapper);

        ParsedConditionsDTO result = parser.parse("car height 2m");

        assertNotNull(result);
        assertEquals(1, result.getHardConditions().size());
    }

    // ==================== Scenario 6: AI returns invalid JSON ====================
    @Test
    @DisplayName("AI returns invalid JSON -> low confidence fallback")
    void invalidJsonFallback() {
        IAiService fakeAi = query -> "This is not valid JSON at all {{{";
        NluParser parser = new NluParser(fakeAi, objectMapper);

        ParsedConditionsDTO result = parser.parse("any spot");

        assertNotNull(result);
        assertEquals(0.0, result.getConfidence(), 0.01);
        assertNotNull(result.getHardConditions());
        assertTrue(result.getHardConditions().isEmpty());
        assertNotNull(result.getSoftPreferences());
        assertTrue(result.getSoftPreferences().isEmpty());
    }

    // ==================== Scenario 7: AI returns null ====================
    @Test
    @DisplayName("AI returns null -> low confidence fallback")
    void nullAiResponseFallback() {
        IAiService fakeAi = query -> null;
        NluParser parser = new NluParser(fakeAi, objectMapper);

        ParsedConditionsDTO result = parser.parse("any spot");

        assertNotNull(result);
        assertEquals(0.0, result.getConfidence(), 0.01);
        assertNotNull(result.getHardConditions());
        assertTrue(result.getHardConditions().isEmpty());
    }

    // ==================== Scenario 8: AI throws exception ====================
    @Test
    @DisplayName("AI throws exception -> low confidence fallback")
    void aiExceptionFallback() {
        IAiService fakeAi = query -> {
            throw new RuntimeException("Connection refused");
        };
        NluParser parser = new NluParser(fakeAi, objectMapper);

        ParsedConditionsDTO result = parser.parse("any spot");

        assertNotNull(result);
        assertEquals(0.0, result.getConfidence(), 0.01);
        assertNotNull(result.getHardConditions());
        assertTrue(result.getHardConditions().isEmpty());
    }

    // ==================== Scenario 9: low confidence -> requires_confirmation ====================
    @Test
    @DisplayName("Low confidence -> requires_confirmation")
    void lowConfidenceTriggersConfirmation() {
        String fakeResponse =
                "{"
                + "\"hard_conditions\": ["
                + "  {\"type\": \"charging\", \"value\": \"true\", \"source\": \"explicit\"}"
                + "],"
                + "\"soft_preferences\": [],"
                + "\"confidence\": 0.5,"
                + "\"ambiguous_terms\": [],"
                + "\"requires_confirmation\": [],"
                + "\"inferred_defaults\": []"
                + "}";

        IAiService fakeAi = query -> fakeResponse;
        NluParser parser = new NluParser(fakeAi, objectMapper);

        ParsedConditionsDTO result = parser.parse("need charging");
        result = classifier.classify(result, "need charging");

        assertNotNull(result);
        assertTrue(result.getRequiresConfirmation().contains("charging"),
                "Low confidence charging should be in requires_confirmation");
    }

    // ==================== Scenario 10: inferred_defaults serialization ====================
    @Test
    @DisplayName("inferred_defaults correct serialization/deserialization")
    void inferredDefaultsSerialization() throws Exception {
        String fakeResponse =
                "{"
                + "\"hard_conditions\": [],"
                + "\"soft_preferences\": [],"
                + "\"confidence\": 0.8,"
                + "\"ambiguous_terms\": [],"
                + "\"requires_confirmation\": [],"
                + "\"inferred_defaults\": [\"no price limit\", \"no spot type preference\"]"
                + "}";

        IAiService fakeAi = query -> fakeResponse;
        NluParser parser = new NluParser(fakeAi, objectMapper);

        ParsedConditionsDTO result = parser.parse("any spot");

        assertNotNull(result.getInferredDefaults());
        assertEquals(2, result.getInferredDefaults().size());
        assertEquals("no price limit", result.getInferredDefaults().get(0));
        assertEquals("no spot type preference", result.getInferredDefaults().get(1));

        // Serialize and deserialize
        String json = objectMapper.writeValueAsString(result);
        ParsedConditionsDTO deserialized = objectMapper.readValue(json, ParsedConditionsDTO.class);
        assertNotNull(deserialized.getInferredDefaults());
        assertEquals(2, deserialized.getInferredDefaults().size());

        // Verify snake_case serialization
        assertTrue(json.contains("inferred_defaults"), "JSON should contain inferred_defaults key");
    }

    // ==================== Scenario 11: confidence boundary validation ====================
    @Test
    @DisplayName("Confidence boundary validation (negative->0, >1->1)")
    void confidenceBoundaryValidation() {
        // Negative confidence
        String negativeConf =
                "{\"hard_conditions\":[],\"soft_preferences\":[],"
                + "\"confidence\":-0.5,\"ambiguous_terms\":[],"
                + "\"requires_confirmation\":[],\"inferred_defaults\":[]}";
        IAiService fakeAi1 = query -> negativeConf;
        NluParser parser1 = new NluParser(fakeAi1, objectMapper);
        ParsedConditionsDTO result1 = parser1.parse("test");
        assertEquals(0.0, result1.getConfidence(), 0.001, "Negative confidence should be clamped to 0");

        // Confidence > 1
        String highConf =
                "{\"hard_conditions\":[],\"soft_preferences\":[],"
                + "\"confidence\":1.5,\"ambiguous_terms\":[],"
                + "\"requires_confirmation\":[],\"inferred_defaults\":[]}";
        IAiService fakeAi2 = query -> highConf;
        NluParser parser2 = new NluParser(fakeAi2, objectMapper);
        ParsedConditionsDTO result2 = parser2.parse("test");
        assertEquals(1.0, result2.getConfidence(), 0.001, "Confidence > 1 should be clamped to 1");
    }

    // ==================== Scenario 12: multi-condition query ====================
    @Test
    @DisplayName("T12: multi-condition -> qualification hard, height hard, charging hard, price soft")
    void multiConditionQuery() {
        String fakeResponse =
                "{"
                + "\"hard_conditions\": ["
                + "  {\"type\": \"qualification\", \"value\": \"visitor\", \"source\": \"explicit\"},"
                + "  {\"type\": \"height\", \"value\": \"2.5\", \"unit\": \"m\", \"source\": \"explicit\"},"
                + "  {\"type\": \"charging\", \"value\": \"true\", \"source\": \"explicit\"}"
                + "],"
                + "\"soft_preferences\": ["
                + "  {\"type\": \"price\", \"weight\": 0.7, \"source\": \"explicit\"}"
                + "],"
                + "\"confidence\": 0.92,"
                + "\"ambiguous_terms\": [],"
                + "\"requires_confirmation\": [],"
                + "\"inferred_defaults\": []"
                + "}";

        IAiService fakeAi = query -> fakeResponse;
        NluParser parser = new NluParser(fakeAi, objectMapper);

        String query = "visitor, car height 2.5m, need charging, budget under 10 yuan";
        ParsedConditionsDTO result = parser.parse(query);
        result = classifier.classify(result, query);

        assertNotNull(result);
        assertEquals(3, result.getHardConditions().size());
        assertTrue(result.getHardConditions().stream()
                .anyMatch(hc -> "qualification".equals(hc.getType())));
        assertTrue(result.getHardConditions().stream()
                .anyMatch(hc -> "height".equals(hc.getType())));
        assertTrue(result.getHardConditions().stream()
                .anyMatch(hc -> "charging".equals(hc.getType())));

        assertEquals(1, result.getSoftPreferences().size());
        assertEquals("price", result.getSoftPreferences().get(0).getType());
    }

    // ==================== Scenario 13: casual query ====================
    @Test
    @DisplayName("T02: casual query -> no auto-filled hard conditions")
    void casualQueryNoHardConditions() {
        String fakeResponse =
                "{"
                + "\"hard_conditions\": [],"
                + "\"soft_preferences\": ["
                + "  {\"type\": \"distance_to_elevator\", \"weight\": 0.3, \"source\": \"inferred\"},"
                + "  {\"type\": \"price\", \"weight\": 0.3, \"source\": \"inferred\"}"
                + "],"
                + "\"confidence\": 0.6,"
                + "\"ambiguous_terms\": [],"
                + "\"requires_confirmation\": [],"
                + "\"inferred_defaults\": [\"no restrictions\"]"
                + "}";

        IAiService fakeAi = query -> fakeResponse;
        NluParser parser = new NluParser(fakeAi, objectMapper);

        String query = "just find me any spot";
        ParsedConditionsDTO result = parser.parse(query);
        result = classifier.classify(result, query);

        assertNotNull(result);
        assertTrue(result.getHardConditions().isEmpty(),
                "Casual query should not auto-fill hard conditions");
    }

    // ==================== ConditionClassifier charging tests ====================
    @Nested
    @DisplayName("ConditionClassifier charging classification")
    class ChargingClassificationTests {

        @Test
        @DisplayName("Strong charging misclassified as soft should be corrected to hard")
        void strongChargingCorrectedToHard() {
            ParsedConditionsDTO dto = new ParsedConditionsDTO();
            dto.setHardConditions(new ArrayList<>());
            dto.setSoftPreferences(new ArrayList<>());
            ParsedConditionsDTO.SoftPreference sp = new ParsedConditionsDTO.SoftPreference();
            sp.setType("charging");
            sp.setWeight(0.5);
            sp.setSource("explicit");
            dto.getSoftPreferences().add(sp);
            dto.setConfidence(0.8);
            dto.setAmbiguousTerms(new ArrayList<>());
            dto.setRequiresConfirmation(new ArrayList<>());
            dto.setInferredDefaults(new ArrayList<>());

            ParsedConditionsDTO result = classifier.classify(dto, "I need to charge");

            assertTrue(result.getHardConditions().stream()
                            .anyMatch(hc -> "charging".equals(hc.getType())),
                    "Strong charging should be corrected to hard condition");
            assertTrue(result.getSoftPreferences().stream()
                            .noneMatch(sp2 -> "charging".equals(sp2.getType())),
                    "Strong charging should not be in soft preferences");
        }

        @Test
        @DisplayName("Weak charging misclassified as hard should be corrected to soft")
        void weakChargingCorrectedToSoft() {
            ParsedConditionsDTO dto = new ParsedConditionsDTO();
            dto.setHardConditions(new ArrayList<>());
            ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
            hc.setType("charging");
            hc.setValue("true");
            hc.setSource("explicit");
            dto.getHardConditions().add(hc);
            dto.setSoftPreferences(new ArrayList<>());
            dto.setConfidence(0.8);
            dto.setAmbiguousTerms(new ArrayList<>());
            dto.setRequiresConfirmation(new ArrayList<>());
            dto.setInferredDefaults(new ArrayList<>());

            ParsedConditionsDTO result = classifier.classify(dto, "prefer charging if possible");

            assertTrue(result.getSoftPreferences().stream()
                            .anyMatch(sp -> "charging".equals(sp.getType())),
                    "Weak charging should be corrected to soft preference");
            assertTrue(result.getHardConditions().stream()
                            .noneMatch(hc2 -> "charging".equals(hc2.getType())),
                    "Weak charging should not be in hard conditions");
        }
    }

    // ==================== ConditionClassifier qualification and accessibility ====================
    @Nested
    @DisplayName("ConditionClassifier qualification and accessibility")
    class QualificationAndAccessibilityTests {

        @Test
        @DisplayName("Visitor -> hard condition qualification")
        void visitorQualification() {
            ParsedConditionsDTO dto = new ParsedConditionsDTO();
            dto.setHardConditions(new ArrayList<>());
            dto.setSoftPreferences(new ArrayList<>());
            dto.setConfidence(0.8);
            dto.setAmbiguousTerms(new ArrayList<>());
            dto.setRequiresConfirmation(new ArrayList<>());
            dto.setInferredDefaults(new ArrayList<>());

            ParsedConditionsDTO result = classifier.classify(dto, "I am a visitor");

            assertTrue(result.getHardConditions().stream()
                            .anyMatch(hc -> "qualification".equals(hc.getType()) && "visitor".equals(hc.getValue())),
                    "Visitor should be recognized as hard condition qualification");
        }

        @Test
        @DisplayName("Accessible spot required -> hard condition accessibility")
        void accessibilityRequired() {
            ParsedConditionsDTO dto = new ParsedConditionsDTO();
            dto.setHardConditions(new ArrayList<>());
            dto.setSoftPreferences(new ArrayList<>());
            dto.setConfidence(0.8);
            dto.setAmbiguousTerms(new ArrayList<>());
            dto.setRequiresConfirmation(new ArrayList<>());
            dto.setInferredDefaults(new ArrayList<>());

            ParsedConditionsDTO result = classifier.classify(dto, "need accessible spot");

            assertTrue(result.getHardConditions().stream()
                            .anyMatch(hc -> "accessibility".equals(hc.getType()) && "true".equals(hc.getValue())),
                    "Accessible should be recognized as hard condition accessibility");
        }
    }

    // ==================== JSON extraction edge cases ====================
    @Nested
    @DisplayName("JSON extraction edge cases")
    class JsonExtractionTests {

        @Test
        @DisplayName("Empty fence -> fallback to low confidence")
        void emptyFenceReturnsNull() {
            IAiService fakeAi = query -> "```json\n```";
            NluParser parser = new NluParser(fakeAi, objectMapper);

            ParsedConditionsDTO result = parser.parse("test");

            assertNotNull(result);
            assertEquals(0.0, result.getConfidence(), 0.01);
        }

        @Test
        @DisplayName("Empty string response -> low confidence fallback")
        void emptyStringResponseFallback() {
            IAiService fakeAi = query -> "";
            NluParser parser = new NluParser(fakeAi, objectMapper);

            ParsedConditionsDTO result = parser.parse("test");

            assertNotNull(result);
            assertEquals(0.0, result.getConfidence(), 0.01);
        }

        @Test
        @DisplayName("Empty query -> low confidence fallback")
        void emptyQueryFallback() {
            IAiService fakeAi = query -> "irrelevant";
            NluParser parser = new NluParser(fakeAi, objectMapper);

            ParsedConditionsDTO result = parser.parse("");

            assertNotNull(result);
            assertEquals(0.0, result.getConfidence(), 0.01);
        }

        @Test
        @DisplayName("Null query -> low confidence fallback")
        void nullQueryFallback() {
            IAiService fakeAi = query -> "irrelevant";
            NluParser parser = new NluParser(fakeAi, objectMapper);

            ParsedConditionsDTO result = parser.parse(null);

            assertNotNull(result);
            assertEquals(0.0, result.getConfidence(), 0.01);
        }
    }

    // ==================== sanitize method tests ====================
    @Nested
    @DisplayName("sanitize field cleanup tests")
    class SanitizeTests {

        @Test
        @DisplayName("Null arrays -> empty lists")
        void nullArraysToEmptyLists() {
            ParsedConditionsDTO dto = new ParsedConditionsDTO();
            dto.setConfidence(0.5);

            IAiService fakeAi = query -> "{}";
            NluParser parser = new NluParser(fakeAi, objectMapper);
            parser.sanitize(dto);

            assertNotNull(dto.getHardConditions());
            assertNotNull(dto.getSoftPreferences());
            assertNotNull(dto.getAmbiguousTerms());
            assertNotNull(dto.getRequiresConfirmation());
            assertNotNull(dto.getInferredDefaults());
            assertTrue(dto.getHardConditions().isEmpty());
            assertTrue(dto.getSoftPreferences().isEmpty());
        }

        @Test
        @DisplayName("Invalid condition type -> filtered")
        void invalidConditionTypeFiltered() {
            ParsedConditionsDTO dto = new ParsedConditionsDTO();
            dto.setHardConditions(new ArrayList<>());
            ParsedConditionsDTO.HardCondition valid = new ParsedConditionsDTO.HardCondition();
            valid.setType("height");
            valid.setValue("2.0");
            dto.getHardConditions().add(valid);

            ParsedConditionsDTO.HardCondition invalid = new ParsedConditionsDTO.HardCondition();
            invalid.setType("unknown_type");
            invalid.setValue("something");
            dto.getHardConditions().add(invalid);

            dto.setSoftPreferences(new ArrayList<>());
            dto.setConfidence(0.8);
            dto.setAmbiguousTerms(new ArrayList<>());
            dto.setRequiresConfirmation(new ArrayList<>());
            dto.setInferredDefaults(new ArrayList<>());

            IAiService fakeAi = query -> "{}";
            NluParser parser = new NluParser(fakeAi, objectMapper);
            parser.sanitize(dto);

            assertEquals(1, dto.getHardConditions().size());
            assertEquals("height", dto.getHardConditions().get(0).getType());
        }

        @Test
        @DisplayName("Soft preference weight clamped to [0, 1]")
        void softPreferenceWeightClamped() {
            ParsedConditionsDTO dto = new ParsedConditionsDTO();
            dto.setHardConditions(new ArrayList<>());
            dto.setSoftPreferences(new ArrayList<>());

            ParsedConditionsDTO.SoftPreference sp = new ParsedConditionsDTO.SoftPreference();
            sp.setType("price");
            sp.setWeight(2.0);
            dto.getSoftPreferences().add(sp);

            dto.setConfidence(0.8);
            dto.setAmbiguousTerms(new ArrayList<>());
            dto.setRequiresConfirmation(new ArrayList<>());
            dto.setInferredDefaults(new ArrayList<>());

            IAiService fakeAi = query -> "{}";
            NluParser parser = new NluParser(fakeAi, objectMapper);
            parser.sanitize(dto);

            assertEquals(1.0, dto.getSoftPreferences().get(0).getWeight(), 0.001);
        }
    }

    // ==================== NLU-P0-01: Negation expression tests ====================
    @Nested
    @DisplayName("NLU-P0-01: Negation expressions should not trigger strong charging")
    class NegationChargingTests {

        @Test
        @DisplayName("不需要充电 should NOT trigger strong charging")
        void negatedChargingChinese() {
            ParsedConditionsDTO dto = new ParsedConditionsDTO();
            dto.setHardConditions(new ArrayList<>());
            dto.setSoftPreferences(new ArrayList<>());
            dto.setConfidence(0.8);
            dto.setAmbiguousTerms(new ArrayList<>());
            dto.setRequiresConfirmation(new ArrayList<>());
            dto.setInferredDefaults(new ArrayList<>());

            ParsedConditionsDTO result = classifier.classify(dto, "不需要充电");

            assertTrue(result.getHardConditions().stream()
                            .noneMatch(hc -> "charging".equals(hc.getType())),
                    "不需要充电 should not add charging as hard condition");
        }

        @Test
        @DisplayName("不要充电 should NOT trigger strong charging")
        void negatedChargingNoWant() {
            ParsedConditionsDTO dto = new ParsedConditionsDTO();
            dto.setHardConditions(new ArrayList<>());
            dto.setSoftPreferences(new ArrayList<>());
            dto.setConfidence(0.8);
            dto.setAmbiguousTerms(new ArrayList<>());
            dto.setRequiresConfirmation(new ArrayList<>());
            dto.setInferredDefaults(new ArrayList<>());

            ParsedConditionsDTO result = classifier.classify(dto, "不要充电");

            assertTrue(result.getHardConditions().stream()
                            .noneMatch(hc -> "charging".equals(hc.getType())),
                    "不要充电 should not add charging as hard condition");
        }

        @Test
        @DisplayName("don't need charging should NOT trigger strong charging")
        void negatedChargingEnglish() {
            ParsedConditionsDTO dto = new ParsedConditionsDTO();
            dto.setHardConditions(new ArrayList<>());
            dto.setSoftPreferences(new ArrayList<>());
            dto.setConfidence(0.8);
            dto.setAmbiguousTerms(new ArrayList<>());
            dto.setRequiresConfirmation(new ArrayList<>());
            dto.setInferredDefaults(new ArrayList<>());

            ParsedConditionsDTO result = classifier.classify(dto, "don't need charging");

            assertTrue(result.getHardConditions().stream()
                            .noneMatch(hc -> "charging".equals(hc.getType())),
                    "don't need charging should not add charging as hard condition");
        }

        @Test
        @DisplayName("得充电 (no negation) in query with existing soft charging -> corrected to hard")
        void strongChargingStillWorks() {
            // classify() corrects misclassifications — charging must already be in soft preferences
            ParsedConditionsDTO dto = new ParsedConditionsDTO();
            dto.setHardConditions(new ArrayList<>());
            dto.setSoftPreferences(new ArrayList<>());
            ParsedConditionsDTO.SoftPreference sp = new ParsedConditionsDTO.SoftPreference();
            sp.setType("charging");
            sp.setWeight(0.5);
            sp.setSource("explicit");
            dto.getSoftPreferences().add(sp);
            dto.setConfidence(0.8);
            dto.setAmbiguousTerms(new ArrayList<>());
            dto.setRequiresConfirmation(new ArrayList<>());
            dto.setInferredDefaults(new ArrayList<>());

            ParsedConditionsDTO result = classifier.classify(dto, "我得充电");

            assertTrue(result.getHardConditions().stream()
                            .anyMatch(hc -> "charging".equals(hc.getType())),
                    "得充电 should trigger correction of charging to hard condition");
        }
    }

    // ==================== NLU-P1-01: Confidence reduction on correction ====================
    @Nested
    @DisplayName("NLU-P1-01: Confidence reduced on classifier correction")
    class ConfidenceReductionTests {

        @Test
        @DisplayName("Charging correction reduces confidence to 0.74 and adds requiresConfirmation")
        void chargingCorrectionReducesConfidence() {
            ParsedConditionsDTO dto = new ParsedConditionsDTO();
            dto.setHardConditions(new ArrayList<>());
            dto.setSoftPreferences(new ArrayList<>());
            ParsedConditionsDTO.SoftPreference sp = new ParsedConditionsDTO.SoftPreference();
            sp.setType("charging");
            sp.setWeight(0.5);
            sp.setSource("explicit");
            dto.getSoftPreferences().add(sp);
            dto.setConfidence(0.9);
            dto.setAmbiguousTerms(new ArrayList<>());
            dto.setRequiresConfirmation(new ArrayList<>());
            dto.setInferredDefaults(new ArrayList<>());

            ParsedConditionsDTO result = classifier.classify(dto, "必须充电");

            assertEquals(0.74, result.getConfidence(), 0.001,
                    "Confidence should be reduced to 0.74 on correction");
            assertTrue(result.getRequiresConfirmation().contains("charging"),
                    "charging should be in requiresConfirmation after correction");
        }

        @Test
        @DisplayName("Qualification correction reduces confidence")
        void qualificationCorrectionReducesConfidence() {
            ParsedConditionsDTO dto = new ParsedConditionsDTO();
            dto.setHardConditions(new ArrayList<>());
            dto.setSoftPreferences(new ArrayList<>());
            dto.setConfidence(0.9);
            dto.setAmbiguousTerms(new ArrayList<>());
            dto.setRequiresConfirmation(new ArrayList<>());
            dto.setInferredDefaults(new ArrayList<>());

            ParsedConditionsDTO result = classifier.classify(dto, "我是访客");

            assertEquals(0.74, result.getConfidence(), 0.001,
                    "Confidence should be reduced on qualification correction");
            assertTrue(result.getRequiresConfirmation().contains("qualification"),
                    "qualification should be in requiresConfirmation after correction");
        }

        @Test
        @DisplayName("Accessibility correction reduces confidence")
        void accessibilityCorrectionReducesConfidence() {
            ParsedConditionsDTO dto = new ParsedConditionsDTO();
            dto.setHardConditions(new ArrayList<>());
            dto.setSoftPreferences(new ArrayList<>());
            dto.setConfidence(0.9);
            dto.setAmbiguousTerms(new ArrayList<>());
            dto.setRequiresConfirmation(new ArrayList<>());
            dto.setInferredDefaults(new ArrayList<>());

            ParsedConditionsDTO result = classifier.classify(dto, "需要无障碍车位");

            assertEquals(0.74, result.getConfidence(), 0.001,
                    "Confidence should be reduced on accessibility correction");
            assertTrue(result.getRequiresConfirmation().contains("accessibility"),
                    "accessibility should be in requiresConfirmation after correction");
        }
    }

    // ==================== NLU-P1-01b: Input length limit ====================
    @Nested
    @DisplayName("NLU-P1-01b: Input length limit")
    class InputLengthLimitTests {

        @Test
        @DisplayName("Query > 2000 chars returns low confidence result")
        void longQueryReturnsLowConfidence() {
            IAiService fakeAi = query -> "{}";
            NluParser parser = new NluParser(fakeAi, objectMapper);

            // Create a query longer than 2000 chars
            StringBuilder longQuery = new StringBuilder();
            while (longQuery.length() <= 2000) {
                longQuery.append("我需要充电和限高2.3米的车位");
            }

            ParsedConditionsDTO result = parser.parse(longQuery.toString());

            assertNotNull(result);
            assertEquals(0.0, result.getConfidence(), 0.001,
                    "Long query should return low confidence");
            assertTrue(result.getHardConditions().isEmpty(),
                    "Long query should return empty hard conditions");
        }

        @Test
        @DisplayName("Query exactly 2000 chars is accepted")
        void queryAt2000CharsIsAccepted() {
            String fakeResponse = "{\"hard_conditions\":[],\"soft_preferences\":[],"
                    + "\"confidence\":0.8,\"ambiguous_terms\":[],"
                    + "\"requires_confirmation\":[],\"inferred_defaults\":[]}";
            IAiService fakeAi = query -> fakeResponse;
            NluParser parser = new NluParser(fakeAi, objectMapper);

            // Create a query of exactly 2000 chars
            StringBuilder query2000 = new StringBuilder();
            while (query2000.length() < 2000) {
                query2000.append("a");
            }
            assertEquals(2000, query2000.length());

            ParsedConditionsDTO result = parser.parse(query2000.toString());

            assertNotNull(result);
            assertEquals(0.8, result.getConfidence(), 0.001,
                    "2000-char query should be accepted and parsed normally");
        }
    }

    // ==================== NLU-P1-02: sanitize value type validation ====================
    @Nested
    @DisplayName("NLU-P1-02: sanitize value type validation")
    class SanitizeValueTypeTests {

        @Test
        @DisplayName("Non-numeric height value is removed")
        void nonNumericHeightRemoved() {
            ParsedConditionsDTO dto = new ParsedConditionsDTO();
            dto.setHardConditions(new ArrayList<>());
            ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
            hc.setType("height");
            hc.setValue("abc");
            dto.getHardConditions().add(hc);
            dto.setSoftPreferences(new ArrayList<>());
            dto.setConfidence(0.8);
            dto.setAmbiguousTerms(new ArrayList<>());
            dto.setRequiresConfirmation(new ArrayList<>());
            dto.setInferredDefaults(new ArrayList<>());

            IAiService fakeAi = query -> "{}";
            NluParser parser = new NluParser(fakeAi, objectMapper);
            parser.sanitize(dto);

            assertTrue(dto.getHardConditions().isEmpty(),
                    "Non-numeric height value should be removed");
        }

        @Test
        @DisplayName("Negative height value is removed")
        void negativeHeightRemoved() {
            ParsedConditionsDTO dto = new ParsedConditionsDTO();
            dto.setHardConditions(new ArrayList<>());
            ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
            hc.setType("height");
            hc.setValue("-1.5");
            dto.getHardConditions().add(hc);
            dto.setSoftPreferences(new ArrayList<>());
            dto.setConfidence(0.8);
            dto.setAmbiguousTerms(new ArrayList<>());
            dto.setRequiresConfirmation(new ArrayList<>());
            dto.setInferredDefaults(new ArrayList<>());

            IAiService fakeAi = query -> "{}";
            NluParser parser = new NluParser(fakeAi, objectMapper);
            parser.sanitize(dto);

            assertTrue(dto.getHardConditions().isEmpty(),
                    "Negative height value should be removed");
        }

        @Test
        @DisplayName("charging value='required' is removed")
        void chargingValueRequiredRemoved() {
            ParsedConditionsDTO dto = new ParsedConditionsDTO();
            dto.setHardConditions(new ArrayList<>());
            ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
            hc.setType("charging");
            hc.setValue("required");
            dto.getHardConditions().add(hc);
            dto.setSoftPreferences(new ArrayList<>());
            dto.setConfidence(0.8);
            dto.setAmbiguousTerms(new ArrayList<>());
            dto.setRequiresConfirmation(new ArrayList<>());
            dto.setInferredDefaults(new ArrayList<>());

            IAiService fakeAi = query -> "{}";
            NluParser parser = new NluParser(fakeAi, objectMapper);
            parser.sanitize(dto);

            assertTrue(dto.getHardConditions().isEmpty(),
                    "charging value='required' should be removed by sanitize");
        }

        @Test
        @DisplayName("charging value='yes' is removed")
        void chargingValueYesRemoved() {
            ParsedConditionsDTO dto = new ParsedConditionsDTO();
            dto.setHardConditions(new ArrayList<>());
            ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
            hc.setType("charging");
            hc.setValue("yes");
            dto.getHardConditions().add(hc);
            dto.setSoftPreferences(new ArrayList<>());
            dto.setConfidence(0.8);
            dto.setAmbiguousTerms(new ArrayList<>());
            dto.setRequiresConfirmation(new ArrayList<>());
            dto.setInferredDefaults(new ArrayList<>());

            IAiService fakeAi = query -> "{}";
            NluParser parser = new NluParser(fakeAi, objectMapper);
            parser.sanitize(dto);

            assertTrue(dto.getHardConditions().isEmpty(),
                    "charging value='yes' should be removed by sanitize");
        }

        @Test
        @DisplayName("charging value='true' is kept")
        void chargingValueTrueKept() {
            ParsedConditionsDTO dto = new ParsedConditionsDTO();
            dto.setHardConditions(new ArrayList<>());
            ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
            hc.setType("charging");
            hc.setValue("true");
            dto.getHardConditions().add(hc);
            dto.setSoftPreferences(new ArrayList<>());
            dto.setConfidence(0.8);
            dto.setAmbiguousTerms(new ArrayList<>());
            dto.setRequiresConfirmation(new ArrayList<>());
            dto.setInferredDefaults(new ArrayList<>());

            IAiService fakeAi = query -> "{}";
            NluParser parser = new NluParser(fakeAi, objectMapper);
            parser.sanitize(dto);

            assertEquals(1, dto.getHardConditions().size(),
                    "charging value='true' should be kept");
        }

        @Test
        @DisplayName("Empty spot_type value is removed")
        void emptySpotTypeRemoved() {
            ParsedConditionsDTO dto = new ParsedConditionsDTO();
            dto.setHardConditions(new ArrayList<>());
            ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
            hc.setType("spot_type");
            hc.setValue("");
            dto.getHardConditions().add(hc);
            dto.setSoftPreferences(new ArrayList<>());
            dto.setConfidence(0.8);
            dto.setAmbiguousTerms(new ArrayList<>());
            dto.setRequiresConfirmation(new ArrayList<>());
            dto.setInferredDefaults(new ArrayList<>());

            IAiService fakeAi = query -> "{}";
            NluParser parser = new NluParser(fakeAi, objectMapper);
            parser.sanitize(dto);

            assertTrue(dto.getHardConditions().isEmpty(),
                    "Empty spot_type value should be removed");
        }

        @Test
        @DisplayName("Valid numeric height value is kept")
        void validHeightKept() {
            ParsedConditionsDTO dto = new ParsedConditionsDTO();
            dto.setHardConditions(new ArrayList<>());
            ParsedConditionsDTO.HardCondition hc = new ParsedConditionsDTO.HardCondition();
            hc.setType("height");
            hc.setValue("2.3");
            dto.getHardConditions().add(hc);
            dto.setSoftPreferences(new ArrayList<>());
            dto.setConfidence(0.8);
            dto.setAmbiguousTerms(new ArrayList<>());
            dto.setRequiresConfirmation(new ArrayList<>());
            dto.setInferredDefaults(new ArrayList<>());

            IAiService fakeAi = query -> "{}";
            NluParser parser = new NluParser(fakeAi, objectMapper);
            parser.sanitize(dto);

            assertEquals(1, dto.getHardConditions().size(),
                    "Valid numeric height should be kept");
            assertEquals("2.3", dto.getHardConditions().get(0).getValue());
        }
    }
}
