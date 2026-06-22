<script setup lang="ts">
import { computed } from 'vue';
import type { DocumentStatus, UserStatus } from '../../types';

const props = defineProps<{
  status: DocumentStatus | UserStatus | string;
}>();

const normalizedStatus = computed(() => props.status?.toUpperCase() ?? '');

const tagType = computed(() => {
  switch (normalizedStatus.value) {
    case 'ACTIVE':
    case 'INDEXED':
    case 'READY':
    case 'COMPLETED':
    case 'CONSENSUS_CONFIRMED':
      return 'success';
    case 'FAILED':
      return 'danger';
    case 'DISABLED':
      return 'info';
    case 'PROCESSING':
    case 'PENDING':
    case 'QUEUED':
    case 'PARSING':
    case 'CHUNKING':
    case 'EMBEDDING':
    case 'INDEXING':
    case 'PENDING_ASSIGNMENT':
    case 'ASSIGNED':
      return 'warning';
    case 'IN_REVIEW':
    case 'REVIEWING':
    case 'SUBMITTED':
      return 'primary';
    default:
      return 'info';
  }
});

const label = computed(() => {
  switch (normalizedStatus.value) {
    case 'ACTIVE':
      return '启用';
    case 'DISABLED':
      return '禁用';
    case 'INDEXED':
    case 'READY':
      return '已就绪';
    case 'FAILED':
      return '失败';
    case 'PROCESSING':
      return '处理中';
    case 'PENDING':
    case 'QUEUED':
      return '排队中';
    case 'PARSING':
      return '解析中';
    case 'CHUNKING':
      return '切分中';
    case 'EMBEDDING':
      return '嵌入中';
    case 'INDEXING':
      return '索引中';
    case 'PENDING_ASSIGNMENT':
      return '待分配';
    case 'ASSIGNED':
      return '已分配';
    case 'IN_REVIEW':
    case 'REVIEWING':
      return '评审中';
    case 'SUBMITTED':
      return '已提交';
    case 'COMPLETED':
      return '已完成';
    case 'CONSENSUS_CONFIRMED':
      return '共识已确认';
    default:
      return props.status || '-';
  }
});
</script>

<template>
  <el-tag :type="tagType" effect="light">{{ label }}</el-tag>
</template>
