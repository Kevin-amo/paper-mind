package com.lqr.papermind.paperformat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.papermind.paperformat.entity.PaperFormatTemplateEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

public interface PaperFormatTemplateMapper extends BaseMapper<PaperFormatTemplateEntity> {

    @Select("""
            select *
            from public.paper_format_template
            where owner_user_id = #{ownerUserId}
               or public_template = true
            order by updated_at desc, created_at desc
            """)
    List<PaperFormatTemplateEntity> selectVisibleTemplates(@Param("ownerUserId") UUID ownerUserId);
}
