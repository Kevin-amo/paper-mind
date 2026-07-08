package com.lqr.papermind.paperformat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.papermind.paperformat.entity.PaperFormatCheckJobEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

public interface PaperFormatCheckJobMapper extends BaseMapper<PaperFormatCheckJobEntity> {

    @Select("""
            select *
            from public.paper_format_check_job
            where owner_user_id = #{ownerUserId}
              and source_id = #{sourceId}
              and scope = #{scope}
            order by created_at desc
            limit 1
            """)
    PaperFormatCheckJobEntity selectLatestByOwnerSourceAndScope(@Param("ownerUserId") UUID ownerUserId,
                                                                @Param("sourceId") String sourceId,
                                                                @Param("scope") String scope);

    @Select("""
            select *
            from public.paper_format_check_job
            where review_task_id = #{taskId}
              and scope = 'REVIEW_PRECHECK'
            order by created_at desc
            limit 1
            """)
    PaperFormatCheckJobEntity selectLatestByReviewTaskId(@Param("taskId") UUID taskId);
}
