package com.lqr.papermind.paperformat;

import com.lqr.papermind.auth.entity.SysUser;
import com.lqr.papermind.auth.security.SecurityUserPrincipal;
import com.lqr.papermind.paperformat.controller.PaperFormatController;
import com.lqr.papermind.paperformat.controller.ReviewPaperFormatController;
import com.lqr.papermind.paperformat.dto.CreateFormatCheckRequest;
import com.lqr.papermind.paperformat.dto.PaperFormatCheckJobResponse;
import com.lqr.papermind.paperformat.dto.PaperFormatTemplateResponse;
import com.lqr.papermind.paperformat.service.PaperFormatService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperFormatControllerTest {

    @Test
    void uploadTemplateShouldDelegateCurrentUserAndAdminFlag() throws Exception {
        PaperFormatService service = mock(PaperFormatService.class);
        PaperFormatController controller = new PaperFormatController(service);
        UUID userId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "template.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "x".getBytes());
        PaperFormatTemplateResponse expected = template(UUID.randomUUID(), userId);
        when(service.uploadTemplate(userId, false, file, "T", "S")).thenReturn(expected);

        PaperFormatTemplateResponse response = controller.uploadTemplate(principal(userId, "USER"), file, "T", "S");

        assertThat(response).isEqualTo(expected);
        verify(service).uploadTemplate(userId, false, file, "T", "S");
    }

    @Test
    void reviewPrecheckShouldDelegateTaskAndTemplate() {
        PaperFormatService service = mock(PaperFormatService.class);
        ReviewPaperFormatController controller = new ReviewPaperFormatController(service);
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        PaperFormatCheckJobResponse expected = check(UUID.randomUUID(), templateId);
        when(service.createReviewPrecheck(userId, false, taskId, templateId)).thenReturn(expected);

        PaperFormatCheckJobResponse response = controller.createReviewPrecheck(principal(userId, "REVIEWER"), taskId, new CreateFormatCheckRequest(templateId, null));

        assertThat(response).isEqualTo(expected);
        verify(service).createReviewPrecheck(userId, false, taskId, templateId);
    }

    private PaperFormatTemplateResponse template(UUID templateId, UUID ownerUserId) {
        return new PaperFormatTemplateResponse(
                templateId,
                ownerUserId,
                "T",
                "S",
                "template.docx",
                "docx",
                "storage/template.docx",
                "READY",
                Map.of(),
                Map.of(),
                true,
                true,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private PaperFormatCheckJobResponse check(UUID checkId, UUID templateId) {
        return new PaperFormatCheckJobResponse(
                checkId,
                templateId,
                null,
                "source-a",
                null,
                "USER_SELF_CHECK",
                "PASSED",
                Map.of("ERROR", 0),
                List.of(),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private SecurityUserPrincipal principal(UUID userId, String role) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setUsername("user");
        user.setPasswordHash("{noop}password");
        user.setStatus("ACTIVE");
        return new SecurityUserPrincipal(user, List.of(role));
    }
}
