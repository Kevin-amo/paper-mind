package com.lqr.papermind.review.consensus;

import com.lqr.papermind.review.entity.ReviewReportEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ConsensusCalculatorTest {

    private final ConsensusCalculator calculator = new ConsensusCalculator();

    @Test
    void calculateShouldAverageOverallAndCriterionScores() {
        ReviewReportEntity first = report(UUID.randomUUID(), UUID.randomUUID(), 80, "建议通过",
                List.of(
                        Map.of("code", "LOGIC", "score", 70),
                        Map.of("code", "NOVELTY", "score", "88")
                ));
        ReviewReportEntity second = report(UUID.randomUUID(), UUID.randomUUID(), 90, "建议修改后通过",
                List.of(
                        Map.of("code", "LOGIC", "score", 95),
                        Map.of("code", "NOVELTY", "score", 92)
                ));

        ConsensusCalculator.Result result = calculator.calculate(List.of(first, second));

        assertThat(result.finalScore()).isEqualTo(85);
        assertThat(result.scoreSummary())
                .containsEntry("overallAverage", 85)
                .containsEntry("participantCount", 2)
                .containsEntry("overallMin", 80)
                .containsEntry("overallMax", 90);
        assertThat(result.scoreSummary().get("criteria")).asList()
                .anySatisfy(criterion -> assertThat((Map<String, Object>) criterion)
                        .containsEntry("criterionCode", "LOGIC")
                        .containsEntry("average", 83)
                        .containsEntry("minScore", 70)
                        .containsEntry("maxScore", 95)
                        .containsEntry("participantCount", 2));
        assertThat(result.disagreementItems())
                .anySatisfy(item -> assertThat(item)
                        .containsEntry("type", "CRITERION_SCORE")
                        .containsEntry("criterionCode", "LOGIC")
                        .containsEntry("minScore", 70)
                        .containsEntry("maxScore", 95)
                        .containsEntry("threshold", 20));
        assertThat(result.commentSummary().get("recommendations")).asList()
                .hasSize(2)
                .anySatisfy(recommendation -> assertThat((Map<String, Object>) recommendation)
                        .containsEntry("reportId", first.getId())
                        .containsEntry("reviewerUserId", first.getReviewerUserId())
                        .containsEntry("finalRecommendation", "建议通过"));
    }

    @Test
    void calculateShouldFlagOverallDisagreementAtThreshold() {
        ReviewReportEntity first = report(UUID.randomUUID(), UUID.randomUUID(), 75, "建议通过", List.of());
        ReviewReportEntity second = report(UUID.randomUUID(), UUID.randomUUID(), 90, "建议复核", List.of());

        ConsensusCalculator.Result result = calculator.calculate(List.of(first, second));

        // 总分分歧达到阈值时，finalScore 应为 null，提示需要人工协调；
        // overallAverage 仍保存在 scoreSummary 中供参考。
        assertThat(result.finalScore()).isNull();
        assertThat(result.scoreSummary()).containsEntry("overallAverage", 83);
        assertThat(result.disagreementItems())
                .anySatisfy(item -> assertThat(item)
                        .containsEntry("type", "OVERALL_SCORE")
                        .containsEntry("minScore", 75)
                        .containsEntry("maxScore", 90)
                        .containsEntry("threshold", 15));
    }

    @Test
    void calculateShouldNullFinalScoreWhenOverallDisagreementExtreme() {
        // 用户报告的场景：0 分和 88 分，分差 88 >> 阈值 15
        ReviewReportEntity first = report(UUID.randomUUID(), UUID.randomUUID(), 0, "建议拒收", List.of());
        ReviewReportEntity second = report(UUID.randomUUID(), UUID.randomUUID(), 88, "建议通过", List.of());

        ConsensusCalculator.Result result = calculator.calculate(List.of(first, second));

        // finalScore 不应为平均值 44，而应为 null
        assertThat(result.finalScore()).isNull();
        assertThat(result.scoreSummary()).containsEntry("overallAverage", 44);
        assertThat(result.scoreSummary()).containsEntry("overallMin", 0);
        assertThat(result.scoreSummary()).containsEntry("overallMax", 88);
        assertThat(result.disagreementItems())
                .anySatisfy(item -> assertThat(item)
                        .containsEntry("type", "OVERALL_SCORE")
                        .containsEntry("minScore", 0)
                        .containsEntry("maxScore", 88)
                        .containsEntry("threshold", 15));
    }

    @Test
    void calculateShouldReturnZeroForEmptyReports() {
        ConsensusCalculator.Result result = calculator.calculate(List.of());

        assertThat(result.finalScore()).isZero();
        assertThat(result.scoreSummary())
                .containsEntry("overallAverage", 0)
                .containsEntry("participantCount", 0);
        assertThat(result.scoreSummary().get("criteria")).asList().isEmpty();
        assertThat(result.disagreementItems()).isEmpty();
    }

    @Test
    void calculateShouldTreatNullReportsAsEmpty() {
        ConsensusCalculator.Result result = calculator.calculate(null);

        assertThat(result.finalScore()).isZero();
        assertThat(result.scoreSummary())
                .containsEntry("overallAverage", 0)
                .containsEntry("participantCount", 0);
        assertThat(result.scoreSummary().get("criteria")).asList().isEmpty();
        assertThat(result.commentSummary().get("recommendations")).asList().isEmpty();
        assertThat(result.disagreementItems()).isEmpty();
    }

    @Test
    void calculateShouldIgnoreNullReportsAndInvalidScores() {
        ReviewReportEntity valid = report(UUID.randomUUID(), UUID.randomUUID(), 50, null,
                Arrays.asList(
                        "not-a-map",
                        Map.of("code", "LOGIC", "score", "not-a-number"),
                        Map.of("code", "LOGIC", "score", 77)
                ));

        ConsensusCalculator.Result result = calculator.calculate(Arrays.asList(null, valid));

        assertThat(result.finalScore()).isEqualTo(50);
        assertThat(result.scoreSummary())
                .containsEntry("overallAverage", 50)
                .containsEntry("participantCount", 1)
                .containsEntry("overallMin", 50)
                .containsEntry("overallMax", 50);
        assertThat(result.scoreSummary().get("criteria")).asList()
                .anySatisfy(criterion -> assertThat((Map<String, Object>) criterion)
                        .containsEntry("criterionCode", "LOGIC")
                        .containsEntry("average", 77)
                        .containsEntry("minScore", 77)
                        .containsEntry("maxScore", 77)
                        .containsEntry("participantCount", 1));
        assertThat(result.commentSummary().get("recommendations")).asList()
                .hasSize(1)
                .anySatisfy(recommendation -> assertThat((Map<String, Object>) recommendation)
                        .containsEntry("reportId", valid.getId())
                        .containsEntry("reviewerUserId", valid.getReviewerUserId())
                        .containsEntry("finalRecommendation", ""));
        assertThat(result.disagreementItems()).isEmpty();
    }

    @Test
    void calculateWithWeightsShouldIgnoreWeightsAndUseReportTotalScore() {
        // POLICY weight=20, score=80 => 80*20=1600
        // INNOVATION weight=30, score=60 => 60*30=1800
        // Weighted average = (1600+1800)/(20+30) = 3400/50 = 68
        ReviewReportEntity report = report(UUID.randomUUID(), UUID.randomUUID(), 70, "建议通过",
                List.of(
                        Map.of("code", "POLICY", "score", 80),
                        Map.of("code", "INNOVATION", "score", 60)
                ));
        Map<String, Integer> weights = Map.of("POLICY", 20, "INNOVATION", 30);

        ConsensusCalculator.Result result = calculator.calculate(List.of(report), weights);

        assertThat(result.finalScore()).isEqualTo(70);
        assertThat(result.scoreSummary()).containsEntry("overallAverage", 70);
    }

    @Test
    void calculateWithWeightsShouldAverageReportTotalsAcrossMultipleReports() {
        // Report 1: POLICY=80*20=1600, INNOVATION=60*30=1800 => weighted=3400/50=68
        // Report 2: POLICY=90*20=1800, INNOVATION=70*30=2100 => weighted=3900/50=78
        // Average of 68 and 78 = 73
        ReviewReportEntity first = report(UUID.randomUUID(), UUID.randomUUID(), 70, "建议通过",
                List.of(
                        Map.of("code", "POLICY", "score", 80),
                        Map.of("code", "INNOVATION", "score", 60)
                ));
        ReviewReportEntity second = report(UUID.randomUUID(), UUID.randomUUID(), 80, "建议修改后通过",
                List.of(
                        Map.of("code", "POLICY", "score", 90),
                        Map.of("code", "INNOVATION", "score", 70)
                ));
        Map<String, Integer> weights = Map.of("POLICY", 20, "INNOVATION", 30);

        ConsensusCalculator.Result result = calculator.calculate(List.of(first, second), weights);

        assertThat(result.finalScore()).isEqualTo(75);
        assertThat(result.scoreSummary())
                .containsEntry("overallAverage", 75)
                .containsEntry("overallMin", 70)
                .containsEntry("overallMax", 80);
    }

    @Test
    void calculateWithWeightsShouldAverageReportTotalsWhenNoWeightedScoresPresent() {
        ReviewReportEntity first = report(UUID.randomUUID(), UUID.randomUUID(), 80, "寤鸿閫氳繃", List.of());
        ReviewReportEntity second = report(UUID.randomUUID(), UUID.randomUUID(), 90, "寤鸿淇敼鍚庨€氳繃", List.of());
        Map<String, Integer> weights = Map.of("POLICY", 20);

        ConsensusCalculator.Result result = calculator.calculate(List.of(first, second), weights);

        assertThat(result.finalScore()).isEqualTo(85);
    }

    @Test
    void calculateWithWeightsShouldFallBackToTotalScoreWhenNoScores() {
        ReviewReportEntity report = report(UUID.randomUUID(), UUID.randomUUID(), 75, "建议通过", List.of());
        Map<String, Integer> weights = Map.of("POLICY", 20);

        ConsensusCalculator.Result result = calculator.calculate(List.of(report), weights);

        assertThat(result.finalScore()).isEqualTo(75);
    }

    @Test
    void calculateWithEmptyWeightsShouldFallBackToTotalScore() {
        ReviewReportEntity report = report(UUID.randomUUID(), UUID.randomUUID(), 85, "建议通过",
                List.of(Map.of("code", "POLICY", "score", 85)));
        Map<String, Integer> weights = Map.of();

        ConsensusCalculator.Result result = calculator.calculate(List.of(report), weights);

        assertThat(result.finalScore()).isEqualTo(85);
    }

    @Test
    void calculateWithWeightsShouldDetectDisagreement() {
        ReviewReportEntity first = report(UUID.randomUUID(), UUID.randomUUID(), 60, "建议通过",
                List.of(Map.of("code", "POLICY", "score", 60)));
        ReviewReportEntity second = report(UUID.randomUUID(), UUID.randomUUID(), 90, "建议复核",
                List.of(Map.of("code", "POLICY", "score", 90)));
        Map<String, Integer> weights = Map.of("POLICY", 100);

        ConsensusCalculator.Result result = calculator.calculate(List.of(first, second), weights);

        assertThat(result.disagreementItems())
                .anySatisfy(item -> assertThat(item)
                        .containsEntry("type", "OVERALL_SCORE")
                        .containsEntry("minScore", 60)
                        .containsEntry("maxScore", 90));
    }

    private ReviewReportEntity report(UUID id, UUID reviewerUserId, Integer totalScore, String finalRecommendation, Object scores) {
        ReviewReportEntity report = new ReviewReportEntity();
        report.setId(id);
        report.setReviewerUserId(reviewerUserId);
        report.setTotalScore(totalScore);
        report.setFinalRecommendation(finalRecommendation);
        report.setScores(scores);
        return report;
    }
}
