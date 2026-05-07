<template>
  <div class="border-t border-slate-200 bg-white p-4 sm:p-5">
    <div class="mx-auto flex max-w-3xl flex-col gap-2">
      <div v-if="showMentionPopup" class="relative">
        <div class="absolute bottom-0 left-3 z-10 w-48 rounded-xl border border-slate-200 bg-white py-1 shadow-lg">
          <button
            v-for="user in mentionCandidates"
            :key="user"
            class="flex w-full items-center gap-2 px-4 py-2 text-left text-sm text-slate-700 transition hover:bg-indigo-50"
            @click="selectMention(user)"
          >
            <span class="flex h-6 w-6 items-center justify-center rounded-full bg-indigo-100 text-xs font-medium text-indigo-600">@</span>
            {{ user }}
          </button>
        </div>
      </div>
      <div class="flex items-end gap-3 rounded-2xl border border-slate-300 bg-white p-2 shadow-sm">
        <textarea
          ref="textareaRef"
          v-model="input"
          rows="1"
          placeholder="输入消息，@agent 召唤助手，@其他人 分配任务"
          class="max-h-40 min-h-11 flex-1 resize-none bg-transparent px-3 py-2 text-[15px] text-slate-700 outline-none placeholder:text-slate-400"
          @keydown.enter.exact.prevent="onSubmit"
          @input="onInput"
          @keydown="onKeydown"
        ></textarea>
        <button
          type="button"
          class="rounded-xl bg-slate-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
          :disabled="!canSend"
          @click="onSubmit"
        >
          发送
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

const emit = defineEmits<{
  send: [value: string]
}>()

const MENTION_USERS = ['agent']

const input = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const showMentionPopup = ref(false)
const mentionQuery = ref('')
const mentionStartPos = ref(-1)

const canSend = computed(() => input.value.trim().length > 0)

const mentionCandidates = computed(() => {
  if (!mentionQuery.value) return MENTION_USERS
  const q = mentionQuery.value.toLowerCase()
  return MENTION_USERS.filter((u) => u.toLowerCase().includes(q))
})

const onInput = () => {
  const text = input.value
  const cursorPos = textareaRef.value?.selectionStart ?? text.length

  const lastAtIndex = text.lastIndexOf('@', cursorPos - 1)
  if (lastAtIndex >= 0) {
    const afterAt = text.slice(lastAtIndex + 1, cursorPos)
    if (afterAt.length <= 10 && !afterAt.includes(' ') && !afterAt.includes('@') && !afterAt.includes('\n')) {
      mentionStartPos.value = lastAtIndex
      mentionQuery.value = afterAt
      showMentionPopup.value = true
    } else {
      showMentionPopup.value = false
    }
  } else {
    showMentionPopup.value = false
  }
}

const onKeydown = (e: KeyboardEvent) => {
  if (showMentionPopup.value && (e.key === 'ArrowDown' || e.key === 'ArrowUp')) {
    e.preventDefault()
  }
  if (showMentionPopup.value && e.key === 'Escape') {
    showMentionPopup.value = false
  }
}

const selectMention = (user: string) => {
  const text = input.value
  const before = text.slice(0, mentionStartPos.value)
  const after = text.slice(mentionStartPos.value + 1 + mentionQuery.value.length)
  input.value = `${before}@${user} ${after}`
  showMentionPopup.value = false
  textareaRef.value?.focus()
}

const onSubmit = () => {
  const value = input.value.trim()
  if (!value) return

  emit('send', value)
  input.value = ''
  showMentionPopup.value = false
}
</script>
