import type { TaskStreamEvent } from '@/types'
import { accessToken } from '@/utils/storage'

/**
 * SSE 客户端（契约 §5.4 `GET /api/v1/ai-tasks/{taskId}/stream`）
 *
 * 原生 EventSource 无法携带 Authorization 头，因此这里用 fetch + ReadableStream
 * 自行解析 `event:` / `data:` 分帧，前端无需后端改动。
 */

export interface SseHandlers<T> {
  /** 每收到一个完整事件触发一次 */
  onEvent: (event: string, data: T) => void
  /** 连接失败 / 读取中断（调用方通常降级为轮询） */
  onError?: (err: Error) => void
  /** 服务端关闭流 */
  onClose?: () => void
}

interface ParsedBlock {
  event: string
  data: string
}

const BLOCK_SEPARATOR = /\r\n\r\n|\n\n|\r\r/

/** 解析单个 SSE 块：注释行忽略，event 默认 message，data 多行用 \n 拼接 */
function parseBlock(block: string): ParsedBlock | null {
  const lines = block.split(/\r\n|\n|\r/)
  let event = 'message'
  const dataLines: string[] = []

  for (const line of lines) {
    if (!line || line.startsWith(':')) continue
    const idx = line.indexOf(':')
    const field = idx === -1 ? line : line.slice(0, idx)
    let value = idx === -1 ? '' : line.slice(idx + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') event = value
    else if (field === 'data') dataLines.push(value)
  }

  if (dataLines.length === 0) return null
  return { event, data: dataLines.join('\n') }
}

function safeParse<T>(raw: string): T | null {
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

/**
 * 订阅一个 SSE 流，返回取消函数。
 * `path` 为相对路径（如 `/ai-tasks/task_x/stream`），会自动补上 /api/v1 前缀。
 */
export function createSseStream<T = TaskStreamEvent>(path: string, handlers: SseHandlers<T>): () => void {
  const controller = new AbortController()
  let stopped = false

  const stop = () => {
    if (stopped) return
    stopped = true
    controller.abort()
  }

  const run = async () => {
    try {
      const res = await fetch(`/api/v1${path}`, {
        method: 'GET',
        headers: {
          Accept: 'text/event-stream',
          ...(accessToken.value ? { Authorization: `Bearer ${accessToken.value}` } : {}),
        },
        signal: controller.signal,
      })

      if (!res.ok) {
        throw new Error(`SSE 连接失败：${res.status}`)
      }
      if (!res.body) {
        throw new Error('SSE 响应无流式内容')
      }

      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        let match = BLOCK_SEPARATOR.exec(buffer)
        while (match) {
          const block = buffer.slice(0, match.index)
          buffer = buffer.slice(match.index + match[0].length)
          const parsed = parseBlock(block)
          if (parsed) {
            const payload = safeParse<T>(parsed.data)
            if (payload !== null) handlers.onEvent(parsed.event, payload)
          }
          match = BLOCK_SEPARATOR.exec(buffer)
        }
      }

      // 服务端主动关闭
      if (!stopped) {
        stopped = true
        handlers.onClose?.()
      }
    } catch (err) {
      if (!stopped) {
        stopped = true
        handlers.onError?.(err instanceof Error ? err : new Error(String(err)))
      }
    }
  }

  void run()
  return stop
}
