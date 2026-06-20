package com.lqr.papermind.review.audit;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lqr.papermind.review.entity.ReviewAuditLogEntity;
import com.lqr.papermind.review.mapper.ReviewAuditLogMapper;
import com.lqr.papermind.review.audit.impl.ReviewAuditServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewAuditServiceTest {
    private final ReviewAuditLogMapper mapper = mock(ReviewAuditLogMapper.class);
    private final ReviewAuditService service = new ReviewAuditServiceImpl(mapper);

    @Test
    void appendShouldStoreBeforeAfterAndDiff() {
        Map<String, Object> before = Map.of("score", 70);
        Map<String, Object> after = Map.of("score", 80);
        Map<String, Object> clientInfo = Map.of("ip", "local");

        service.append(UUID.randomUUID(), UUID.randomUUID(), "ADJUST_REPORT", "manual adjustment", before, after, clientInfo);

        ArgumentCaptor<ReviewAuditLogEntity> captor = ArgumentCaptor.forClass(ReviewAuditLogEntity.class);
        verify(mapper).insert(captor.capture());
        ReviewAuditLogEntity log = captor.getValue();
        assertThat(log.getSnapshot()).isSameAs(after);
        assertThat(log.getBeforeSnapshot()).containsEntry("score", 70);
        assertThat(log.getAfterSnapshot()).containsEntry("score", 80);
        assertThat(log.getClientInfo()).containsEntry("ip", "local");
        assertThat(log.getDiff()).containsKey("score");
        assertThat(change(log.getDiff(), "score"))
                .containsEntry("before", 70)
                .containsEntry("after", 80);
    }

    @Test
    void appendShouldDiffAddedDeletedNullAndUnchangedKeys() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("same", "value");
        before.put("deleted", "old");
        before.put("nullChanged", null);
        before.put("staysNull", null);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("same", "value");
        after.put("added", "new");
        after.put("nullChanged", "now");
        after.put("staysNull", null);

        service.append(UUID.randomUUID(), UUID.randomUUID(), "ADJUST_REPORT", "manual adjustment", before, after, Map.of());

        ArgumentCaptor<ReviewAuditLogEntity> captor = ArgumentCaptor.forClass(ReviewAuditLogEntity.class);
        verify(mapper).insert(captor.capture());
        Map<String, Object> diff = captor.getValue().getDiff();
        assertThat(diff).containsOnlyKeys("deleted", "nullChanged", "added");
        assertThat(change(diff, "deleted"))
                .containsEntry("before", "old")
                .containsEntry("after", null);
        assertThat(change(diff, "added"))
                .containsEntry("before", null)
                .containsEntry("after", "new");
        assertThat(change(diff, "nullChanged"))
                .containsEntry("before", null)
                .containsEntry("after", "now");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> change(Map<String, Object> diff, String key) {
        return (Map<String, Object>) diff.get(key);
    }

    @Test
    void searchAuditLogsShouldDelegateToMapperWithPagedRequest() {
        UUID operatorUserId = UUID.randomUUID();
        OffsetDateTime startTime = OffsetDateTime.parse("2026-01-01T00:00:00+08:00");
        OffsetDateTime endTime = OffsetDateTime.parse("2026-06-30T23:59:59+08:00");
        Page<ReviewAuditLogEntity> emptyPage = new Page<>(1, 20, 0);
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(emptyPage);

        IPage<ReviewAuditLogEntity> result = service.searchAuditLogs(operatorUserId, "SUBMIT", startTime, endTime, 0, 20);

        assertThat(result).isSameAs(emptyPage);
        ArgumentCaptor<Page<ReviewAuditLogEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(mapper).selectPage(pageCaptor.capture(), any(Wrapper.class));
        Page<ReviewAuditLogEntity> pageReq = pageCaptor.getValue();
        // 外部0基页码应转换为 MyBatis-Plus 的1基页码
        assertThat(pageReq.getCurrent()).isEqualTo(1L);
        assertThat(pageReq.getSize()).isEqualTo(20L);
    }

    @Test
    void searchAuditLogsShouldClampInvalidPageAndSize() {
        Page<ReviewAuditLogEntity> emptyPage = new Page<>(1, 1, 0);
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(emptyPage);

        service.searchAuditLogs(null, null, null, null, -3, 0);

        ArgumentCaptor<Page<ReviewAuditLogEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(mapper).selectPage(pageCaptor.capture(), any(Wrapper.class));
        Page<ReviewAuditLogEntity> pageReq = pageCaptor.getValue();
        assertThat(pageReq.getCurrent()).isEqualTo(1L);
        assertThat(pageReq.getSize()).isEqualTo(1L);
    }
}
