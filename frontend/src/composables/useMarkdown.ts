import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.setOptions({
  breaks: true,
  gfm: true,
})


export function useMarkdown(raw: string): string {
  const html = marked.parse(raw) as string
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: [
      'p', 'br', 'strong', 'em', 'code', 'pre',
      'ul', 'ol', 'li', 'h1', 'h2', 'h3',
      'blockquote', 'a', 'hr',
    ],
    ALLOWED_ATTR: ['href', 'target', 'rel'],
  })
}


export function useMarkdownComputed(rawRef: () => string) {
  return computed(() => useMarkdown(rawRef()))
}
