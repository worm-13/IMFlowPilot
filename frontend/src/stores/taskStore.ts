import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface StepItem {
    step: string
    name: string
    status: string
    tool?: string
    args?: Record<string, unknown>
}

export interface SlideData {
    title: string
    content: string
}

export const useTaskStore = defineStore('task', () => {
    const taskName = ref('')
    const steps = ref<StepItem[]>([])
    const panelOpen = ref(false)

    const slidesData = ref<SlideData[]>([])
    const isPresenting = ref(false)
    const currentSlideIndex = ref(0)

    const currentSlide = computed(() => {
        if (slidesData.value.length === 0) return null
        return slidesData.value[currentSlideIndex.value] ?? null
    })

    const totalSlides = computed(() => slidesData.value.length)

    const updateTask = (name: string, newSteps: StepItem[]) => {
        taskName.value = name
        steps.value = newSteps
        panelOpen.value = true
    }

    const resetTask = () => {
        taskName.value = ''
        steps.value = []
        panelOpen.value = false
    }

    const closePanel = () => {
        panelOpen.value = false
    }

    const startPresentation = (slides: SlideData[]) => {
        slidesData.value = slides
        currentSlideIndex.value = 0
        isPresenting.value = true
    }

    const nextSlide = () => {
        if (currentSlideIndex.value < slidesData.value.length - 1) {
            currentSlideIndex.value++
        }
    }

    const prevSlide = () => {
        if (currentSlideIndex.value > 0) {
            currentSlideIndex.value--
        }
    }

    const goToSlide = (index: number) => {
        if (index >= 0 && index < slidesData.value.length) {
            currentSlideIndex.value = index
        }
    }

    const exitPresentation = () => {
        isPresenting.value = false
        slidesData.value = []
        currentSlideIndex.value = 0
    }

    return {
        taskName,
        steps,
        panelOpen,
        slidesData,
        isPresenting,
        currentSlideIndex,
        currentSlide,
        totalSlides,
        updateTask,
        resetTask,
        closePanel,
        startPresentation,
        nextSlide,
        prevSlide,
        goToSlide,
        exitPresentation,
    }
})
