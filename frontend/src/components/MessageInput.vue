<template>
  <div class="border-t border-slate-200 bg-white p-4 sm:p-5">
    <div class="mx-auto flex max-w-3xl items-end gap-3 rounded-2xl border border-slate-300 bg-white p-2 shadow-sm">
      <textarea
        v-model="input"
        rows="1"
        placeholder="输入消息，按 Enter 发送"
        class="max-h-40 min-h-11 flex-1 resize-none bg-transparent px-3 py-2 text-[15px] text-slate-700 outline-none placeholder:text-slate-400"
        @keydown.enter.exact.prevent="onSubmit"
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
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

const emit = defineEmits<{
  send: [value: string]
}>()

const input = ref('')

const canSend = computed(() => input.value.trim().length > 0)

const onSubmit = () => {
  const value = input.value.trim()
  if (!value) return

  emit('send', value)
  input.value = ''
}
</script>
