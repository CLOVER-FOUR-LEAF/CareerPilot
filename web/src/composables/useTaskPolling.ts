import { computed, onUnmounted, ref, shallowRef } from 'vue'
import { aiTaskApi, createSseStream } from '@/api'
import type { AiTask, AiTaskCreateParams, AiTaskStatus, TaskStreamEvent } from '@/types'

export interface UseTaskPollingOptions {
  /** 轮询间隔，默认 1500ms */
  interval?: number
  /** 最长等待时间，默认 60000ms */
  timeout?: number
  /** 走 §5.4 SSE 实时进度（失败自动降级为轮询） */
  useStream?: boolean
}

const TERMINAL: AiTaskStatus[] = ['SUCCESS', 'FAILED', 'CANCELLED']

function isTerminal(status: AiTaskStatus | ''): boolean {
  return !!status && TERMINAL.includes(status)
}

/**
 * AI 任务通用流程：创建 → 跟踪 → 产出。
 * Optimize / Interview / CoverLetter / CareerPlan 共用，避免重复逻辑。
 */
export function useTaskPolling<T>(options: UseTaskPollingOptions = {}) {
  const interval = options.interval ?? 1500
  const timeout = options.timeout ?? 60000
  const useStream = options.useStream ?? false

  const taskId = ref('')
  const status = ref<AiTaskStatus | ''>('')
  const progress = ref(0)
  const currentAgent = ref('')
  // 用 shallowRef 保留 Agent 输出的原始泛型结构（ref 会做 UnwrapRef 展开）
  const output = shallowRef<T | null>(null)
  const errorMessage = ref('')
  const task = shallowRef<AiTask<T> | null>(null)

  const polling = ref(false)
  const finished = ref(false)
  const timedOut = ref(false)
  const succeeded = computed(() => finished.value && status.value === 'SUCCESS')
  const running = computed(() => polling.value && !finished.value && !timedOut.value)

  let cancelled = false
  let stopStream: (() => void) | null = null
  let wakeUp: (() => void) | null = null

  function reset() {
    cancel()
    taskId.value = ''
    status.value = ''
    progress.value = 0
    currentAgent.value = ''
    output.value = null
    errorMessage.value = ''
    task.value = null
    polling.value = false
    finished.value = false
    timedOut.value = false
    cancelled = false
  }

  /** 主动中止等待（组件卸载 / 用户重置） */
  function cancel() {
    cancelled = true
    polling.value = false
    if (stopStream) {
      stopStream()
      stopStream = null
    }
    if (wakeUp) {
      const resolve = wakeUp
      wakeUp = null
      resolve()
    }
  }

  function applyTask(next: AiTask<T>) {
    task.value = next
    status.value = next.status
    if (typeof next.progress === 'number') progress.value = next.progress
    if (next.currentAgent) currentAgent.value = next.currentAgent
    if (next.output !== null && next.output !== undefined) output.value = next.output
    if (next.errorMessage) errorMessage.value = next.errorMessage
    if (isTerminal(next.status)) {
      if (next.status === 'SUCCESS') progress.value = 100
      polling.value = false
      finished.value = true
    }
  }

  async function fetchFinal(id: string): Promise<void> {
    try {
      const res = await aiTaskApi.get<T>(id)
      applyTask(res.data)
    } catch {
      // 拉不到最终快照就沿用流里的数据
    }
  }

  function watchStream(id: string): Promise<void> {
    return new Promise<void>((resolve) => {
      let settled = false
      const settle = () => {
        if (settled) return
        settled = true
        stopStream = null
        resolve()
      }
      stopStream = createSseStream<TaskStreamEvent<T>>(`/ai-tasks/${id}/stream`, {
        onEvent(event, data) {
          if (data.status) status.value = data.status
          if (typeof data.progress === 'number') progress.value = data.progress
          if (data.currentAgent) currentAgent.value = data.currentAgent
          if (event === 'done') {
            if (data.output !== undefined && data.output !== null) output.value = data.output
            progress.value = 100
            void fetchFinal(id).finally(settle)
          } else if (event === 'error') {
            errorMessage.value = data.errorMessage || '任务执行失败'
            stopStream?.()
            settle()
          }
        },
        // 流不可用（后端未开启 / 网络中断）→ 交给调用方降级轮询
        onError: settle,
        onClose: settle,
      })
    })
  }

  async function watchPoll(id: string): Promise<void> {
    const deadline = Date.now() + timeout
    for (;;) {
      if (cancelled) return
      if (Date.now() > deadline) {
        timedOut.value = true
        polling.value = false
        if (!errorMessage.value) errorMessage.value = '任务执行超时，请稍后重试'
        return
      }
      try {
        const res = await aiTaskApi.get<T>(id)
        applyTask(res.data)
      } catch {
        // 单次查询失败不终止跟踪，继续等待
      }
      if (cancelled || finished.value || timedOut.value) return
      await new Promise<void>((resolve) => {
        const timer = setTimeout(() => {
          wakeUp = null
          resolve()
        }, interval)
        wakeUp = () => {
          clearTimeout(timer)
          wakeUp = null
          resolve()
        }
      })
    }
  }

  /** 跟踪一个已在运行的任务（不重置已有状态） */
  async function startWatching(id: string): Promise<AiTask<T> | null> {
    taskId.value = id
    polling.value = true
    cancelled = false

    if (useStream) {
      await watchStream(id)
      if (cancelled || finished.value || timedOut.value) return task.value
      // 流提前结束且没有终态 → 轮询兜底
    }

    await watchPoll(id)
    return task.value
  }

  /** 跟踪一个已有的 taskId（会先清空上次状态） */
  async function watch(id: string): Promise<AiTask<T> | null> {
    reset()
    return startWatching(id)
  }

  /** 创建并跟踪（页面唯一入口） */
  async function submit(params: AiTaskCreateParams): Promise<AiTask<T> | null> {
    reset()
    const res = await aiTaskApi.create<T>(params)
    const snapshot = res.data
    taskId.value = snapshot.taskId
    applyTask(snapshot)
    if (finished.value) return snapshot
    return startWatching(snapshot.taskId)
  }

  onUnmounted(cancel)

  return {
    taskId,
    status,
    progress,
    currentAgent,
    output,
    errorMessage,
    task,
    polling,
    running,
    finished,
    timedOut,
    succeeded,
    submit,
    watch,
    reset,
    cancel,
  }
}
