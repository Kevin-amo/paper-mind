package com.lqr.papermind.paperformat.service;

import com.lqr.papermind.paperformat.dto.AdminPaperFormatTemplateUpdateRequest;
import com.lqr.papermind.paperformat.dto.CreateFormatCheckRequest;
import com.lqr.papermind.paperformat.dto.PaperFormatCheckJobResponse;
import com.lqr.papermind.paperformat.dto.PaperFormatTemplateResponse;
import com.lqr.papermind.paperformat.dto.PatchFormatSpecRequest;
import com.lqr.papermind.paperformat.model.FormatViolation;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public interface PaperFormatService {
    /** 模板状态：就绪 */
    String TEMPLATE_STATUS_READY = "READY";
    /** 模板状态：解析失败 */
    String TEMPLATE_STATUS_FAILED = "FAILED";
    /** 模板状态：需要人工确认 */
    String TEMPLATE_STATUS_NEED_CONFIRM = "NEED_CONFIRM";
    /** 模板状态：解析中 */
    String TEMPLATE_STATUS_PARSING = "PARSING";
    /** 检查范围：用户自检 */
    String CHECK_SCOPE_USER_SELF_CHECK = "USER_SELF_CHECK";
    /** 检查范围：评审前预检 */
    String CHECK_SCOPE_REVIEW_PRECHECK = "REVIEW_PRECHECK";

    /**
     * 上传论文格式模板文件（docx），解析其中的格式规则并保存
     *
     * @param currentUserId 当前登录用户ID
     * @param admin         是否为管理员
     * @param file          上传的docx文件
     * @param name          模板名称
     * @param schoolName    学校名称
     * @return 模板响应对象
     * @throws IOException 文件读取异常
     */
    PaperFormatTemplateResponse uploadTemplate(UUID currentUserId, boolean admin, MultipartFile file, String name, String schoolName) throws IOException;

    /**
     * 查询当前用户可见的格式模板列表（管理员看全部，普通用户看自己的及公开模板）
     *
     * @param currentUserId 当前登录用户ID
     * @param admin         是否为管理员
     * @return 模板列表
     */
    List<PaperFormatTemplateResponse> listTemplates(UUID currentUserId, boolean admin);

    /**
     * 查询所有管理员模板列表
     *
     * @return 管理员模板列表
     */
    List<PaperFormatTemplateResponse> listAdminTemplates();

    /**
     * 根据模板ID获取单个模板详情
     *
     * @param currentUserId 当前登录用户ID
     * @param admin         是否为管理员
     * @param templateId    模板ID
     * @return 模板响应对象
     */
    PaperFormatTemplateResponse getTemplate(UUID currentUserId, boolean admin, UUID templateId);

    /**
     * 管理员更新模板基本信息（名称、学校名称、是否公开）
     *
     * @param templateId 模板ID
     * @param request    更新请求
     * @return 更新后的模板响应对象
     */
    PaperFormatTemplateResponse updateAdminTemplate(UUID templateId, AdminPaperFormatTemplateUpdateRequest request);

    /**
     * 管理员确认模板，将其状态设置为就绪
     *
     * @param templateId 模板ID
     * @return 更新后的模板响应对象
     */
    PaperFormatTemplateResponse confirmAdminTemplate(UUID templateId);

    /**
     * 管理员取消模板公开发布
     *
     * @param templateId 模板ID
     * @return 更新后的模板响应对象
     */
    PaperFormatTemplateResponse unpublishAdminTemplate(UUID templateId);

    /**
     * 更新模板的格式规则（FormatSpec），支持用户自定义模板修改
     *
     * @param currentUserId 当前登录用户ID
     * @param admin         是否为管理员
     * @param templateId    模板ID
     * @param request       包含新的格式规则和确认状态的请求
     * @return 更新后的模板响应对象
     */
    PaperFormatTemplateResponse updateTemplateSpec(UUID currentUserId, boolean admin, UUID templateId, PatchFormatSpecRequest request);

    /**
     * 创建一次格式检查任务，对比文档与模板的格式规则
     *
     * @param currentUserId 当前登录用户ID
     * @param admin         是否为管理员
     * @param request       检查请求（包含模板ID和文档sourceId）
     * @param scope         检查范围（用户自检或评审预检）
     * @param reviewTaskId  评审任务ID（仅评审预检时使用）
     * @return 格式检查任务响应对象
     */
    PaperFormatCheckJobResponse createCheck(UUID currentUserId, boolean admin, CreateFormatCheckRequest request, String scope, UUID reviewTaskId);

    /**
     * 根据检查任务ID获取格式检查结果
     *
     * @param currentUserId 当前登录用户ID
     * @param admin         是否为管理员
     * @param checkId       检查任务ID
     * @return 格式检查任务响应对象
     */
    PaperFormatCheckJobResponse getCheck(UUID currentUserId, boolean admin, UUID checkId);

    /**
     * 为评审任务创建格式预检
     *
     * @param currentUserId 当前登录用户ID
     * @param admin         是否为管理员
     * @param taskId        评审任务ID
     * @param templateId    模板ID
     * @return 格式检查任务响应对象
     */
    PaperFormatCheckJobResponse createReviewPrecheck(UUID currentUserId, boolean admin, UUID taskId, UUID templateId);

    /**
     * 获取评审任务最新的格式预检结果
     *
     * @param currentUserId 当前登录用户ID
     * @param admin         是否为管理员
     * @param taskId        评审任务ID
     * @return 最新的格式检查任务响应对象
     */
    PaperFormatCheckJobResponse getLatestReviewPrecheck(UUID currentUserId, boolean admin, UUID taskId);

    /**
     * 获取评审任务最新预检中的所有错误级别违规项
     *
     * @param taskId 评审任务ID
     * @return 错误级别违规列表
     */
    List<FormatViolation> latestReviewPrecheckErrors(UUID taskId);
}
