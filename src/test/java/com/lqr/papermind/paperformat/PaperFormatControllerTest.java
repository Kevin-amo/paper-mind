package com.lqr.papermind.paperformat;

import com.lqr.papermind.auth.entity.SysUser;
import com.lqr.papermind.auth.security.SecurityUserPrincipal;
import com.lqr.papermind.paperformat.controller.AdminPaperFormatTemplateController;
import com.lqr.papermind.paperformat.controller.PaperFormatController;
import com.lqr.papermind.paperformat.controller.ReviewPaperFormatController;
import com.lqr.papermind.paperformat.dto.AdminPaperFormatTemplateUpdateRequest;
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

/**
 * 论文格式控制器测试类，验证控制器层的参数传递和委托逻辑
 */
class PaperFormatControllerTest {

    /** 测试上传模板应正确传递当前用户和管理员标志 */
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

    /** 测试评审预检应正确传递任务ID和模板ID */
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

    /** 测试管理员模板控制器的增删改查操作应正确委托 */
    @Test
    void adminTemplateControllerShouldDelegateCrudOperationsAsAdmin() throws Exception {
        PaperFormatService service = mock(PaperFormatService.class);
        AdminPaperFormatTemplateController controller = new AdminPaperFormatTemplateController(service);
        UUID adminId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "school.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "x".getBytes());
        PaperFormatTemplateResponse expected = template(templateId, adminId);
        AdminPaperFormatTemplateUpdateRequest updateRequest = new AdminPaperFormatTemplateUpdateRequest("Updated", "School", false);
        when(service.listAdminTemplates()).thenReturn(List.of(expected));
        when(service.getTemplate(adminId, true, templateId)).thenReturn(expected);
        when(service.uploadTemplate(adminId, true, file, "School Template", "School")).thenReturn(expected);
        when(service.updateAdminTemplate(templateId, updateRequest)).thenReturn(expected);
        when(service.confirmAdminTemplate(templateId)).thenReturn(expected);
        when(service.unpublishAdminTemplate(templateId)).thenReturn(expected);

        assertThat(controller.listTemplates()).containsExactly(expected);
        assertThat(controller.getTemplate(principal(adminId, "ADMIN"), templateId)).isEqualTo(expected);
        assertThat(controller.uploadTemplate(principal(adminId, "ADMIN"), file, "School Template", "School")).isEqualTo(expected);
        assertThat(controller.updateTemplate(templateId, updateRequest)).isEqualTo(expected);
        assertThat(controller.confirmTemplate(templateId)).isEqualTo(expected);
        assertThat(controller.deleteTemplate(templateId)).isEqualTo(expected);

        verify(service).listAdminTemplates();
        verify(service).getTemplate(adminId, true, templateId);
        verify(service).uploadTemplate(adminId, true, file, "School Template", "School");
        verify(service).updateAdminTemplate(templateId, updateRequest);
        verify(service).confirmAdminTemplate(templateId);
        verify(service).unpublishAdminTemplate(templateId);
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
