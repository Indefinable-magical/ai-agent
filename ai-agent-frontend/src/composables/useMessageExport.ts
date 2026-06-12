import { ref, type Ref } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import { exportStockReportPdf } from '@/services/chat'

type ChatMessage = {
  content?: string
  isUser?: boolean
}

type UseMessageExportOptions = {
  messages: Ref<ChatMessage[]>
  getConversationTitle: () => string
  defaultFileName?: string
}

type ReportTemplate = 'brief' | 'professional' | 'presentation'

const templateNameMap: Record<ReportTemplate, string> = {
  brief: '简版',
  professional: '专业版',
  presentation: '汇报版',
}

const sanitizeFileName = (value: string, fallback: string) => {
  return String(value || fallback)
    .replace(/[\\/:*?"<>|]/g, '-')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 60) || fallback
}

const downloadBlob = (blob: Blob, fileName: string) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

const sanitizeReportContent = (content: string) => {
  return String(content || '')
    .split(/\r?\n/)
    .filter((line) => {
      const trimmed = line.trim()
      return !/^(#{1,6}\s*)?(引用来源|参考来源|知识来源|来源)\s*[:：]/.test(trimmed)
        && !/^(#{1,6}\s*)?(引用来源|参考来源|知识来源|来源)\s*$/.test(trimmed)
        && !/\b(stock_info|csv_stock_pool|pdf_announcement|web_research)\b/i.test(trimmed)
    })
    .join('\n')
    .replaceAll('股票池示例.csv', '股票池资料')
    .replaceAll('股票关注对象知识库.md', '知识库资料')
    .replaceAll('stock_info数据库条目', '结构化股票资料')
    .replaceAll('stock_info 数据库条目', '结构化股票资料')
    .trim()
}

export const useMessageExport = ({
  messages,
  getConversationTitle,
  defaultFileName = '股票分析报告',
}: UseMessageExportOptions) => {
  const pdfExportingIndex = ref(-1)

  const getMessageExportBaseName = (index: number) => {
    for (let i = index - 1; i >= 0; i -= 1) {
      const previousMessage = messages.value[i]
      if (previousMessage?.isUser && previousMessage.content) {
        return previousMessage.content.replace(/\s+/g, ' ').trim().slice(0, 28)
      }
    }
    return getConversationTitle()
  }

  const getExportableAiMessage = (index: number) => {
    const targetMessage = messages.value[index]
    if (!targetMessage || targetMessage.isUser || !targetMessage.content?.trim()) {
      antMessage.warning('这条 AI 回复还没有可导出的内容')
      return null
    }
    return targetMessage
  }

  const buildReportMarkdown = (content: string, template: ReportTemplate) => {
    const title = getConversationTitle() || defaultFileName
    const generatedAt = new Date().toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
    const normalizedContent = sanitizeReportContent(content)

    if (template === 'brief') {
      return [
        `# ${title}`,
        '',
        `- 模板：${templateNameMap[template]}`,
        `- 生成时间：${generatedAt}`,
        '',
        '## 核心内容',
        '',
        normalizedContent,
        '',
        '> 以上内容仅供学习和研究参考，不构成投资建议、收益承诺或买卖指令。',
        '',
      ].join('\n')
    }

    if (template === 'presentation') {
      return [
        `# ${title}`,
        '',
        `- 模板：${templateNameMap[template]}`,
        `- 生成时间：${generatedAt}`,
        '',
        '## 一页汇报摘要',
        '',
        normalizedContent,
        '',
        '## 汇报提示',
        '',
        '- 建议先讲投资结论，再讲关注逻辑，最后讲风险和后续跟踪。',
        '- 如用于正式汇报，请补充最新行情、公告、财报和估值数据。',
        '',
        '> 以上内容仅供学习和研究参考，不构成投资建议、收益承诺或买卖指令。',
        '',
      ].join('\n')
    }

    return [
      `# ${title}`,
      '',
      `- 模板：${templateNameMap[template]}`,
      `- 生成时间：${generatedAt}`,
      '',
      '## 投研正文',
      '',
      normalizedContent,
      '',
      '## 后续验证清单',
      '',
      '- 补充最新公告、财报和行业数据。',
      '- 对照同业估值和业绩增速验证结论。',
      '- 持续跟踪风险标签是否发生变化。',
      '',
      '> 以上内容仅供学习和研究参考，不构成投资建议、收益承诺或买卖指令。',
      '',
    ].join('\n')
  }

  const normalizeTemplate = (template?: string): ReportTemplate => {
    return template === 'brief' || template === 'presentation' ? template : 'professional'
  }

  const exportMessageMarkdown = (index: number, template?: string) => {
    const targetMessage = getExportableAiMessage(index)
    if (!targetMessage) {
      return
    }
    const reportTemplate = normalizeTemplate(template)
    const markdown = buildReportMarkdown(targetMessage.content || '', reportTemplate)
    const fileBaseName = sanitizeFileName(getMessageExportBaseName(index), defaultFileName)
    downloadBlob(new Blob([markdown], { type: 'text/markdown;charset=utf-8' }), `${fileBaseName}.md`)
    antMessage.success(`${templateNameMap[reportTemplate]} Markdown 已导出`)
  }

  const exportMessagePdf = async (index: number, template?: string) => {
    const targetMessage = getExportableAiMessage(index)
    if (!targetMessage || pdfExportingIndex.value !== -1) {
      return
    }

    const reportTemplate = normalizeTemplate(template)
    const fileBaseName = sanitizeFileName(getMessageExportBaseName(index), defaultFileName)
    pdfExportingIndex.value = index
    try {
      const res = await exportStockReportPdf({
        fileName: `${fileBaseName}.pdf`,
        markdown: buildReportMarkdown(targetMessage.content || '', reportTemplate),
      })
      downloadBlob(new Blob([res.data], { type: 'application/pdf' }), `${fileBaseName}.pdf`)
      antMessage.success(`${templateNameMap[reportTemplate]} PDF 已导出`)
    } catch (error) {
      antMessage.error('PDF 导出失败，请稍后重试')
    } finally {
      pdfExportingIndex.value = -1
    }
  }

  return {
    pdfExportingIndex,
    exportMessageMarkdown,
    exportMessagePdf,
  }
}
