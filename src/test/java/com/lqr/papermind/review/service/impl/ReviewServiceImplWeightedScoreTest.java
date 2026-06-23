package com.lqr.papermind.review.service.impl;

import com.lqr.papermind.review.dto.ReviewCriterionResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewServiceImplWeightedScoreTest {

    @Test
    void calculateTotalScoreWithWeightsShouldComputeWeightedAverage() throws Exception {
        ReviewServiceImpl service = createMinimalService();
        // POLICY weight=20 score=80, INNOVATION weight=30 score=60
        // Weighted = (80*20 + 60*30) / (20+30) = 3400/50 = 68
        List<Map<String, Object>> scores = List.of(
                Map.of("code", "POLICY", "score", 80),
                Map.of("code", "INNOVATION", "score", 60)
        );
        List<ReviewCriterionResponse> criteria = List.of(
                criterion("POLICY", 20),
                criterion("INNOVATION", 30)
        );

        int result = invokeCalculateTotalScore(service, scores, criteria);

        assertThat(result).isEqualTo(68);
    }

    @Test
    void calculateTotalScoreWithEqualWeightsShouldMatchSimpleAverage() throws Exception {
        ReviewServiceImpl service = createMinimalService();
        // All weights equal => should be same as simple average
        List<Map<String, Object>> scores = List.of(
                Map.of("code", "A", "score", 80),
                Map.of("code", "B", "score", 60),
                Map.of("code", "C", "score", 70)
        );
        List<ReviewCriterionResponse> criteria = List.of(
                criterion("A", 20),
                criterion("B", 20),
                criterion("C", 20)
        );

        int result = invokeCalculateTotalScore(service, scores, criteria);

        // Simple average: (80+60+70)/3 = 70
        assertThat(result).isEqualTo(70);
    }

    @Test
    void calculateTotalScoreWithNullCriteriaShouldFallBackToSimpleAverage() throws Exception {
        ReviewServiceImpl service = createMinimalService();
        List<Map<String, Object>> scores = List.of(
                Map.of("code", "A", "score", 80),
                Map.of("code", "B", "score", 60)
        );

        int result = invokeCalculateTotalScore(service, scores, null);

        // Simple average: (80+60)/2 = 70
        assertThat(result).isEqualTo(70);
    }

    @Test
    void calculateTotalScoreWithEmptyScoresShouldReturnZero() throws Exception {
        ReviewServiceImpl service = createMinimalService();
        List<ReviewCriterionResponse> criteria = List.of(criterion("A", 20));

        int result = invokeCalculateTotalScore(service, List.of(), criteria);

        assertThat(result).isZero();
    }

    @Test
    void calculateTotalScoreWithUnknownCodeShouldUseDefaultWeight() throws Exception {
        ReviewServiceImpl service = createMinimalService();
        // "UNKNOWN" code not in criteria => default weight 1
        List<Map<String, Object>> scores = List.of(
                Map.of("code", "POLICY", "score", 80),
                Map.of("code", "UNKNOWN", "score", 60)
        );
        List<ReviewCriterionResponse> criteria = List.of(
                criterion("POLICY", 20)
        );

        int result = invokeCalculateTotalScore(service, scores, criteria);

        // (80*20 + 60*1) / (20+1) = 1660/21 ≈ 79
        assertThat(result).isEqualTo(79);
    }

    @Test
    void calculateTotalScoreWithSingleHighWeightShouldDominated() throws Exception {
        ReviewServiceImpl service = createMinimalService();
        // POLICY weight=90 score=100, REF weight=10 score=0
        // Weighted = (100*90 + 0*10) / (90+10) = 9000/100 = 90
        List<Map<String, Object>> scores = List.of(
                Map.of("code", "POLICY", "score", 100),
                Map.of("code", "REF", "score", 0)
        );
        List<ReviewCriterionResponse> criteria = List.of(
                criterion("POLICY", 90),
                criterion("REF", 10)
        );

        int result = invokeCalculateTotalScore(service, scores, criteria);

        assertThat(result).isEqualTo(90);
    }

    @Test
    void calculateTotalScoreWithSixDimensionWeightsShouldNormalizeCodes() throws Exception {
        ReviewServiceImpl service = createMinimalService();
        List<Map<String, Object>> scores = List.of(
                Map.of("code", " policy ", "score", 100),
                Map.of("code", "MATCH", "score", 95),
                Map.of("code", "Innovation", "score", 75),
                Map.of("code", "logic", "score", 90),
                Map.of("code", "language", "score", 85),
                Map.of("code", "REFERENCE", "score", 60)
        );
        List<ReviewCriterionResponse> criteria = List.of(
                criterion("POLICY", 20),
                criterion("MATCH", 20),
                criterion("INNOVATION", 20),
                criterion("LOGIC", 15),
                criterion("LANGUAGE", 15),
                criterion("REFERENCE", 10)
        );

        int result = invokeCalculateTotalScore(service, scores, criteria);

        assertThat(result).isEqualTo(86);
    }

    private ReviewServiceImpl createMinimalService() {
        // Use the constructor that accepts ReviewOutputParser and ReferenceFormatChecker
        try {
            var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return new ReviewServiceImpl(
                    null, null, null, null,
                    null, null, null, null, null, null,
                    new com.lqr.papermind.review.assessment.ReviewOutputParser(objectMapper),
                    new com.lqr.papermind.review.risk.ReferenceFormatChecker(),
                    null, null, null, objectMapper
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int invokeCalculateTotalScore(ReviewServiceImpl service, Object scores, List<ReviewCriterionResponse> criteria) throws Exception {
        Method method = ReviewServiceImpl.class.getDeclaredMethod("calculateTotalScore", Object.class, List.class);
        method.setAccessible(true);
        return (int) method.invoke(service, scores, criteria);
    }

    private ReviewCriterionResponse criterion(String code, int weight) {
        return new ReviewCriterionResponse(
                UUID.randomUUID(), code, code, null, 100, weight, 1, null, true, null, true, 0,
                OffsetDateTime.now(), OffsetDateTime.now()
        );
    }
}
