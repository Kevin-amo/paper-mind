package com.lqr.papermind.paperformat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.papermind.paperformat.entity.PaperFormatTemplateEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * 论文格式模板数据访问接口，提供模板的查询操作
 */
public interface PaperFormatTemplateMapper extends BaseMapper<PaperFormatTemplateEntity> {

    /**
     * 查询用户可见的模板列表（自己的模板 + 已发布的公共模板）
     *
     * @param ownerUserId 用户ID
     * @return 可见模板列表
     */
    @Select("""
            select *
            from public.paper_format_template
            where owner_user_id = #{ownerUserId}
               or (public_template = true and status = 'READY' and confirmed = true)
            order by updated_at desc, created_at desc
            """)
    List<PaperFormatTemplateEntity> selectVisibleTemplates(@Param("ownerUserId") UUID ownerUserId);

    /**
     * 查询所有管理员模板列表
     *
     * @return 所有模板列表
     */
    @Select("""
            select *
            from public.paper_format_template
            order by updated_at desc, created_at desc
            """)
    List<PaperFormatTemplateEntity> selectAdminTemplates();
}
