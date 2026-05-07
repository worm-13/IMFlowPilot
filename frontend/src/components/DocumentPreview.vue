<template>
  <Teleport to="body">
    <Transition name="preview">
      <div
        v-if="visible"
        class="fixed inset-0 z-50 flex flex-col bg-white"
        @keydown="handleKeydown"
        tabindex="0"
        ref="overlayRef"
      >
        <div class="flex items-center justify-between border-b border-slate-200 px-6 py-3">
          <span class="text-sm font-medium text-slate-600">文档预览</span>
          <div class="flex items-center gap-3">
            <button
              class="rounded-lg px-3 py-1.5 text-sm text-slate-500 transition hover:bg-slate-100"
              @click="toggleSidebar"
            >
              {{ showSidebar ? '隐藏目录' : '显示目录' }}
            </button>
            <button
              class="rounded-lg px-4 py-1.5 text-sm text-slate-500 transition hover:bg-slate-100 hover:text-slate-700"
              @click="close"
            >
              关闭预览
            </button>
          </div>
        </div>

        <div class="flex flex-1 overflow-hidden">
          <Transition name="sidebar">
            <aside
              v-if="showSidebar"
              class="w-64 flex-shrink-0 overflow-y-auto border-r border-slate-200 bg-slate-50 p-4"
            >
              <nav class="space-y-1">
                <a
                  v-for="heading in headings"
                  :key="heading.id"
                  :href="`#${heading.id}`"
                  class="block rounded-lg px-3 py-1.5 text-sm transition"
                  :class="heading.level === 1 ? 'font-semibold text-slate-800' : heading.level === 2 ? 'pl-6 text-slate-600' : 'pl-10 text-slate-500'"
                  :style="{ paddingLeft: `${(heading.level - 1) * 16 + 12}px` }"
                  @click.prevent="scrollToHeading(heading.id)"
                >
                  {{ heading.text }}
                </a>
              </nav>
            </aside>
          </Transition>

          <div class="flex-1 overflow-y-auto" ref="contentRef">
            <div
              class="mx-auto max-w-3xl px-8 py-10"
              v-html="renderedMarkdown"
            />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { marked } from 'marked'

const props = defineProps<{
  modelValue: boolean
  content: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const overlayRef = ref<HTMLElement | null>(null)
const contentRef = ref<HTMLElement | null>(null)
const showSidebar = ref(true)

interface Heading {
  id: string
  text: string
  level: number
}

const visible = computed(() => props.modelValue)

const close = () => {
  emit('update:modelValue', false)
}

const toggleSidebar = () => {
  showSidebar.value = !showSidebar.value
}

const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') {
    close()
  }
}

const headings = computed<Heading[]>(() => {
  const result: Heading[] = []
  const regex = /^(#{1,6})\s+(.+)$/gm
  let match: RegExpExecArray | null
  while ((match = regex.exec(props.content)) !== null) {
    const level = match[1].length
    const text = match[2].trim()
    const id = text
      .toLowerCase()
      .replace(/[^\w\u4e00-\u9fff]+/g, '-')
      .replace(/(^-|-$)/g, '')
    result.push({ id, text, level })
  }
  return result
})

const scrollToHeading = (id: string) => {
  if (!contentRef.value) return
  const el = contentRef.value.querySelector(`#${CSS.escape(id)}`)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

const renderedMarkdown = computed(() => {
  if (!props.content) return ''

  let html = marked.parse(props.content, { async: false }) as string

  html = html.replace(/<h([1-6])>/g, (_, level) => {
    return `<h${level} class="heading-${level}">`
  })

  return html
})

watch(() => visible.value, (val) => {
  if (val) {
    document.body.style.overflow = 'hidden'
    setTimeout(() => overlayRef.value?.focus(), 100)
  } else {
    document.body.style.overflow = ''
  }
})

onMounted(() => {
  document.body.style.overflow = ''
})

onUnmounted(() => {
  document.body.style.overflow = ''
})
</script>

<style>
.preview-enter-active,
.preview-leave-active {
  transition: opacity 0.3s ease;
}

.preview-enter-from,
.preview-leave-to {
  opacity: 0;
}

.sidebar-enter-active,
.sidebar-leave-active {
  transition: width 0.2s ease, opacity 0.2s ease;
}

.sidebar-enter-from,
.sidebar-leave-to {
  width: 0;
  opacity: 0;
}
</style>

<style scoped>
:deep(.heading-1) {
  font-size: 1.875rem;
  font-weight: 700;
  color: #1e293b;
  margin-top: 2rem;
  margin-bottom: 1rem;
  padding-bottom: 0.5rem;
  border-bottom: 2px solid #e2e8f0;
}

:deep(.heading-2) {
  font-size: 1.5rem;
  font-weight: 600;
  color: #334155;
  margin-top: 1.75rem;
  margin-bottom: 0.75rem;
}

:deep(.heading-3) {
  font-size: 1.25rem;
  font-weight: 600;
  color: #475569;
  margin-top: 1.5rem;
  margin-bottom: 0.5rem;
}

:deep(.heading-4) {
  font-size: 1.125rem;
  font-weight: 600;
  color: #64748b;
  margin-top: 1.25rem;
  margin-bottom: 0.5rem;
}

:deep(p) {
  color: #334155;
  line-height: 1.75;
  margin-bottom: 0.75rem;
}

:deep(ul),
:deep(ol) {
  color: #334155;
  line-height: 1.75;
  margin-bottom: 0.75rem;
  padding-left: 1.5rem;
}

:deep(li) {
  margin-bottom: 0.25rem;
}

:deep(blockquote) {
  border-left: 4px solid #6366f1;
  padding-left: 1rem;
  margin: 1rem 0;
  color: #64748b;
  font-style: italic;
}

:deep(code) {
  background-color: #f1f5f9;
  padding: 0.125rem 0.375rem;
  border-radius: 0.25rem;
  font-size: 0.875rem;
  color: #e11d48;
}

:deep(pre) {
  background-color: #1e293b;
  color: #e2e8f0;
  padding: 1rem;
  border-radius: 0.5rem;
  overflow-x: auto;
  margin-bottom: 1rem;
}

:deep(pre code) {
  background-color: transparent;
  color: inherit;
  padding: 0;
}

:deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 1rem;
}

:deep(th) {
  background-color: #f1f5f9;
  padding: 0.5rem 0.75rem;
  text-align: left;
  font-weight: 600;
  color: #334155;
  border: 1px solid #e2e8f0;
}

:deep(td) {
  padding: 0.5rem 0.75rem;
  border: 1px solid #e2e8f0;
  color: #475569;
}

:deep(hr) {
  border: none;
  border-top: 1px solid #e2e8f0;
  margin: 1.5rem 0;
}

:deep(a) {
  color: #6366f1;
  text-decoration: underline;
}

:deep(strong) {
  font-weight: 600;
  color: #1e293b;
}

:deep(img) {
  max-width: 100%;
  border-radius: 0.5rem;
  margin: 1rem 0;
}
</style>
