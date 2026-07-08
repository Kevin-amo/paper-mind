package com.lqr.papermind.paperformat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.papermind.paperformat.entity.PaperFormatCheckJobEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

/**
 * 论文格式检查任务数据访问接口，提供检查任务的查询操作
 */
public interface PaperFormatCheckJobMapper extends BaseMapper<PaperFormatCheckJobEntity> {

    /**
     * 根据用户ID、文档来源ID和检查范围查询最新的检查任务
     *
     * @param ownerUserId 用户ID
     * @param sourceId    文档来源ID
     * @param scope       检查范围
     * @return 最新的检查任务
     */
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

    /**
     * 根据评审任务ID查询最新的格式预检任务
     *
     * @param taskId 评审任务ID
     * @return 最新的预检任务
     */
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
