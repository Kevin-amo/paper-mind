package com.lqr.papermind.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lqr.papermind.document.entity.DocumentChunkEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.UUID;

/**
 * 文档切片数据访问接口，提供向量记录回写能力。
 */
public interface DocumentChunkMapper extends BaseMapper<DocumentChunkEntity> {

    /**
     * 回写文档分块关联的向量库记录 ID。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param chunkId 分块 ID
     * @param vectorStoreId 向量库记录 ID
     * @return 更新行数
     */
    @Update("""
            update public.document_chunk
            set vector_store_id = #{vectorStoreId}, updated_at = now()
            where owner_user_id = #{ownerUserId}
              and chunk_id = #{chunkId}
            """)
    int updateVectorStoreId(@Param("ownerUserId") UUID ownerUserId,
                            @Param("chunkId") String chunkId,
                            @Param("vectorStoreId") UUID vectorStoreId);

    @Delete("""
            delete from public.document_chunk c
            using public.document d
            where c.owner_user_id = #{ownerUserId}
              and d.owner_user_id = c.owner_user_id
              and d.source_id = c.source_id
              and coalesce(d.metadata ->> 'sourceType', 'USER') = 'USER'
            """)
    int deleteUserDocumentChunks(@Param("ownerUserId") UUID ownerUserId);
}
