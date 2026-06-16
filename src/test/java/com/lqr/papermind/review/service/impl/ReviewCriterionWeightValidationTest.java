package com.lqr.papermind.review.service.impl;

import com.lqr.papermind.review.dto.ReviewCriterionWeightBatchRequest;
import com.lqr.papermind.review.entity.ReviewCriterionEntity;
import com.lqr.papermind.review.mapper.ReviewCriterionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewCriterionWeightValidationTest {

    @Test
    void batchUpdateWeightsShouldRejectWhenSumNot100() {
        ReviewCriterionMapper criterionMapper = mock(ReviewCriterionMapper.class);
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        ReviewCriterionEntity entity1 = new ReviewCriterionEntity();
        entity1.setId(id1);
        entity1.setCode("POLICY");
        entity1.setWeight(20);
        entity1.setEnabled(true);

        ReviewCriterionEntity entity2 = new ReviewCriterionEntity();
        entity2.setId(id2);
        entity2.setCode("INNOVATION");
        entity2.setWeight(30);
        entity2.setEnabled(true);

        when(criterionMapper.selectById(id1)).thenReturn(entity1);
        when(criterionMapper.selectById(id2)).thenReturn(entity2);
        when(criterionMapper.selectList(any())).thenReturn(List.of());

        ReviewServiceImpl service = new ReviewServiceImpl(
                null, null, null, criterionMapper, null,
                null, null, null, null, null, null, null,
                new com.lqr.papermind.review.risk.ReferenceFormatChecker(),
                null, null, null, new com.fasterxml.jackson.databind.ObjectMapper()
        );

        ReviewCriterionWeightBatchRequest request = new ReviewCriterionWeightBatchRequest(
                List.of(
                        new ReviewCriterionWeightBatchRequest.WeightItem(id1, 40),
                        new ReviewCriterionWeightBatchRequest.WeightItem(id2, 50)
                )
        );

        assertThatThrownBy(() -> service.batchUpdateWeights(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("90");
                });
    }

    @Test
    void batchUpdateWeightsShouldAcceptWhenSumIs100() {
        ReviewCriterionMapper criterionMapper = mock(ReviewCriterionMapper.class);
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        ReviewCriterionEntity entity1 = new ReviewCriterionEntity();
        entity1.setId(id1);
        entity1.setCode("POLICY");
        entity1.setWeight(20);
        entity1.setEnabled(true);

        ReviewCriterionEntity entity2 = new ReviewCriterionEntity();
        entity2.setId(id2);
        entity2.setCode("INNOVATION");
        entity2.setWeight(30);
        entity2.setEnabled(true);

        when(criterionMapper.selectById(id1)).thenReturn(entity1);
        when(criterionMapper.selectById(id2)).thenReturn(entity2);
        when(criterionMapper.selectList(any())).thenReturn(List.of());
        when(criterionMapper.updateById(any(ReviewCriterionEntity.class))).thenReturn(1);

        ReviewServiceImpl service = new ReviewServiceImpl(
                null, null, null, criterionMapper, null,
                null, null, null, null, null, null, null,
                new com.lqr.papermind.review.risk.ReferenceFormatChecker(),
                null, null, null, new com.fasterxml.jackson.databind.ObjectMapper()
        );

        ReviewCriterionWeightBatchRequest request = new ReviewCriterionWeightBatchRequest(
                List.of(
                        new ReviewCriterionWeightBatchRequest.WeightItem(id1, 50),
                        new ReviewCriterionWeightBatchRequest.WeightItem(id2, 50)
                )
        );

        // Should not throw - the sum is 100
        var result = service.batchUpdateWeights(request);
        assertThat(result).isNotNull();
    }

    @Test
    void batchUpdateWeightsShouldRejectNonExistentId() {
        ReviewCriterionMapper criterionMapper = mock(ReviewCriterionMapper.class);
        UUID id1 = UUID.randomUUID();
        when(criterionMapper.selectById(id1)).thenReturn(null);

        ReviewServiceImpl service = new ReviewServiceImpl(
                null, null, null, criterionMapper, null,
                null, null, null, null, null, null, null,
                new com.lqr.papermind.review.risk.ReferenceFormatChecker(),
                null, null, null, new com.fasterxml.jackson.databind.ObjectMapper()
        );

        ReviewCriterionWeightBatchRequest request = new ReviewCriterionWeightBatchRequest(
                List.of(new ReviewCriterionWeightBatchRequest.WeightItem(id1, 100))
        );

        assertThatThrownBy(() -> service.batchUpdateWeights(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }
}
