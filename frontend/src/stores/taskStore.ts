import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface StepItem {
    step: string
    name: string
    status: string
    tool?: string
    args?: Record<string, unknown>
}

export const useTaskStore = defineStore('task', () => {
    const taskName = ref('')
    const steps = ref<StepItem[]>([])
    const panelOpen = ref(false)

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

    return {
        taskName,
        steps,
        panelOpen,
        updateTask,
        resetTask,
        closePanel,
    }
})
