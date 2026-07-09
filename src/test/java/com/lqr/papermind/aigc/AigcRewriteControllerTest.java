package com.lqr.papermind.aigc;

import com.lqr.papermind.aigc.controller.AigcRewriteController;
import com.lqr.papermind.aigc.dto.AigcQualityScoreResponse;
import com.lqr.papermind.aigc.dto.AigcRewriteRequest;
import com.lqr.papermind.aigc.dto.AigcRewriteResponse;
import com.lqr.papermind.aigc.service.AigcRewriteService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AigcRewriteController 单元测试。
 *
 * <p>覆盖：参数校验（paragraph 为空/太短、rewriteStrength 非法）和控制器委托。</p>
 */
class AigcRewriteControllerTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private static final String VALID_PARAGRAPH = "这是一段足够长的测试文本，用于验证润色功能是否能够正常工作并返回结构化的改写结果。";

    // === 参数校验测试 ===

    @Test
    void paragraphBlank_returnsValidationError() {
        AigcRewriteRequest request = new AigcRewriteRequest(
                "   ", null, null, null, null);
        Set<ConstraintViolation<AigcRewriteRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("paragraph"));
    }

    @Test
    void paragraphTooShort_returnsValidationError() {
        AigcRewriteRequest request = new AigcRewriteRequest(
                "太短了", null, null, null, null);
        Set<ConstraintViolation<AigcRewriteRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("paragraph"));
    }

    @Test
    void rewriteStrengthInvalid_returnsValidationError() {
        AigcRewriteRequest request = new AigcRewriteRequest(
                VALID_PARAGRAPH, null, "ultra", null, null);
        Set<ConstraintViolation<AigcRewriteRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("rewriteStrength"));
    }

    @Test
    void rewriteStrengthNull_passesValidation() {
        AigcRewriteRequest request = new AigcRewriteRequest(
                VALID_PARAGRAPH, null, null, null, null);
        Set<ConstraintViolation<AigcRewriteRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void validRequest_passesValidation() {
        AigcRewriteRequest request = new AigcRewriteRequest(
                VALID_PARAGRAPH, "计算机科学", "standard", List.of("深度学习", "Transformer"), "保持学术语气");
        Set<ConstraintViolation<AigcRewriteRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    // === 控制器委托测试 ===

    @Test
    void rewrite_delegatesToService() {
        AigcRewriteService service = mock(AigcRewriteService.class);
        AigcRewriteController controller = new AigcRewriteController(service);

        AigcRewriteRequest request = new AigcRewriteRequest(
                VALID_PARAGRAPH, null, null, null, null);
        AigcRewriteResponse expected = new AigcRewriteResponse(
                "LOW",
                Collections.emptyList(),
                "改写后的文本",
                Collections.emptyList(),
                Collections.emptyList(),
                AigcQualityScoreResponse.defaultScore());

        when(service.rewrite(request)).thenReturn(expected);

        AigcRewriteResponse result = controller.rewrite(request);

        assertThat(result).isEqualTo(expected);
        verify(service).rewrite(request);
    }
}
