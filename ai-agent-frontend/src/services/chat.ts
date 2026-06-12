import request from '@/request'

const DEFAULT_API_BASE_URL = 'http://localhost:8123/api'

const getApiBaseUrl = () => {
  return request.defaults.baseURL || DEFAULT_API_BASE_URL
}

const buildSseUrl = (
  path: string,
  params: Record<string, string | number | boolean | undefined>
) => {
  const baseUrl = `${getApiBaseUrl().replace(/\/+$/, '')}/`
  const normalizedPath = path.replace(/^\/+/, '')
  const url = new URL(normalizedPath, baseUrl)

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null) {
      url.searchParams.set(key, String(value))
    }
  })

  return url.toString()
}

const createFetchStreamSource = (
  path: string,
  body: Record<string, string>,
) => {
  const listeners = new Map<string, Set<(event: MessageEvent) => void>>()
  const controller = new AbortController()
  const source = {
    onmessage: null as ((event: MessageEvent) => void) | null,
    onerror: null as ((event: Event) => void) | null,
    addEventListener(type: string, listener: (event: MessageEvent) => void) {
      if (!listeners.has(type)) {
        listeners.set(type, new Set())
      }
      listeners.get(type)?.add(listener)
    },
    removeEventListener(type: string, listener: (event: MessageEvent) => void) {
      listeners.get(type)?.delete(listener)
    },
    close() {
      controller.abort()
    },
  }

  const emit = (type: string, data = '') => {
    const event = new MessageEvent(type, { data })
    listeners.get(type)?.forEach((listener) => listener(event))
    if (type === 'message') {
      source.onmessage?.(event)
    }
  }

  const readStream = async () => {
    try {
      const res = await fetch(`${getApiBaseUrl().replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
        },
        body: JSON.stringify(body),
        signal: controller.signal,
      })

      if (!res.ok || !res.body) {
        throw new Error(`SSE request failed: ${res.status}`)
      }

      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      const flushEvent = (rawEvent: string) => {
        const lines = rawEvent.split(/\r?\n/)
        const eventType = lines
          .find((line) => line.startsWith('event:'))
          ?.replace(/^event:\s*/, '') || 'message'
        const data = lines
          .filter((line) => line.startsWith('data:'))
          .map((line) => line.replace(/^data:\s?/, ''))
          .join('\n')
        if (data || eventType !== 'message') {
          emit(eventType, data)
        }
      }

      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          break
        }
        buffer += decoder.decode(value, { stream: true })
        const events = buffer.split(/\r?\n\r?\n/)
        buffer = events.pop() || ''
        events.forEach(flushEvent)
      }

      if (buffer.trim()) {
        flushEvent(buffer)
      }
      emit('done')
      source.onmessage?.(new MessageEvent('message', { data: '[DONE]' }))
    } catch (error) {
      if (!controller.signal.aborted) {
        source.onerror?.(new Event('error'))
      }
    }
  }

  readStream()
  return source
}

export const chatWithStockApp = (message: string, chatId: string) => {
  if (message.length > 1200) {
    return createFetchStreamSource('/ai/stock_app/chat/sse', {
      message,
      chatId,
    })
  }

  const url = buildSseUrl('/ai/stock_app/chat/sse', {
    message,
    chatId,
  })

  return new EventSource(url, { withCredentials: true })
}

export const listStockConversations = (keyword = '') => {
  return request.get('/ai/stock_app/conversations', {
    params: keyword ? { keyword } : undefined,
  })
}

export const getStockConversationMessages = (conversationId: string) => {
  return request.get(`/ai/stock_app/conversations/${conversationId}/messages`)
}

export const deleteStockConversation = (conversationId: string) => {
  return request.delete(`/ai/stock_app/conversations/${conversationId}`)
}

export const renameStockConversation = (conversationId: string, title: string) => {
  return request.post(`/ai/stock_app/conversations/${conversationId}/rename`, {
    title,
  })
}

// 股票大师自选股与风险偏好接口。
export const listStockWatchlist = () => {
  return request.get<API.BaseResponseStockWatchlistVOArray>('/stock/user/watchlist')
}

export const addStockWatchlist = (body: API.StockWatchlistAddRequest) => {
  return request.post<API.BaseResponseLong>('/stock/user/watchlist/add', body)
}

export const updateStockWatchlist = (body: API.StockWatchlistUpdateRequest) => {
  return request.post<API.BaseResponseBoolean>('/stock/user/watchlist/update', body)
}

export const deleteStockWatchlist = (id: number) => {
  return request.post<API.BaseResponseBoolean>('/stock/user/watchlist/delete', {
    id,
  })
}

export const getStockUserPreference = () => {
  return request.get<API.BaseResponseStockUserPreferenceVO>('/stock/user/preference')
}

export const updateStockRiskPreference = (riskPreference: string) => {
  return request.post<API.BaseResponseStockUserPreferenceVO>('/stock/user/preference/risk', {
    riskPreference,
  })
}

export const exportStockReportPdf = (body: { fileName: string; markdown: string }) => {
  return request.post<Blob>('/stock/user/report/export/pdf', body, {
    responseType: 'blob',
  })
}

export const chatWithManus = (message: string, chatId: string) => {
  const url = buildSseUrl('/ai/manus/chat', {
    message,
    chatId,
  })

  return new EventSource(url, { withCredentials: true })
}

export const listManusConversations = (keyword = '') => {
  return request.get('/ai/manus/conversations', {
    params: keyword ? { keyword } : undefined,
  })
}

export const getManusConversationMessages = (conversationId: string) => {
  return request.get(`/ai/manus/conversations/${conversationId}/messages`)
}

export const deleteManusConversation = (conversationId: string) => {
  return request.delete(`/ai/manus/conversations/${conversationId}`)
}

export const renameManusConversation = (conversationId: string, title: string) => {
  return request.post(`/ai/manus/conversations/${conversationId}/rename`, {
    title,
  })
}
