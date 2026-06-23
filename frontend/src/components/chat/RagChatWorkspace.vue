<script setup lang="ts">
import { ref } from 'vue';
import ChatMessageList from './ChatMessageList.vue';
import ChatComposer from './ChatComposer.vue';
import ChatDropZone from './ChatDropZone.vue';
import ChatUploadQueue from './ChatUploadQueue.vue';
import type { ConversationMessage } from '../../types';
import type { UploadQueueItem } from './ChatUploadQueue.vue';

const props = defineProps<{
  loading: boolean;
  messages: ConversationMessage[];
  activeConversation?: unknown;
  messagesLoading?: boolean;
  documentTotal: number;
  currentUserAvatarUrl?: string | null;
  uploadQueue: UploadQueueItem[];
}>();

const emit = defineEmits<{
  submit: [payload: { question: string; topK?: number }];
  openDocuments: [];
  dropFiles: [files: File[]];
  selectFiles: [];
  removeQueueItem: [id: string];
  clearQueue: [];
}>();

const composerRef = ref<{ fillQuestion: (question: string) => void } | null>(null);

function handleExample(question: string) {
  composerRef.value?.fillQuestion(question);
}
</script>

<template>
  <main class="rag-workspace claude-chat-main">
    <ChatDropZone @drop-files="emit('dropFiles', $event)">
      <ChatMessageList
        :messages="props.messages"
        :loading="props.messagesLoading"
        :current-user-avatar-url="props.currentUserAvatarUrl"
        @ask-example="handleExample"
      />
      <ChatUploadQueue
        :items="props.uploadQueue"
        @remove="emit('removeQueueItem', $event)"
        @clear="emit('clearQueue')"
      />
      <ChatComposer
        ref="composerRef"
        :loading="props.loading"
        @submit="emit('submit', $event)"
        @open-documents="emit('openDocuments')"
        @select-files="emit('selectFiles')"
      />
    </ChatDropZone>
  </main>
</template>

<style scoped>
.rag-workspace {
  position: relative;
  min-width: 0;
  height: calc(100vh - 36px);
  display: flex;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
  border: 0;
  border-radius: 0;
  background: var(--app-surface);
  box-shadow: none;
}

.rag-workspace::before {
  position: absolute;
  inset: 0;
  z-index: 0;
  background: var(--app-surface);
  content: '';
  pointer-events: none;
}

.rag-workspace > * {
  position: relative;
  z-index: 1;
}

@media (max-width: 900px) {
  .rag-workspace {
    height: auto;
    min-height: 70vh;
  }
}
</style>
