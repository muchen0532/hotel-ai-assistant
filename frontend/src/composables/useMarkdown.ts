import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

// Configure marked for chat output
marked.setOptions({
  breaks: true,   // \n → <br>
  gfm: true,
})

/**
 * Returns sanitized HTML from a markdown string.
 * Safe to bind with v-html.
 */
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

/**
 * Reactive version for use in template.
 */
export function useMarkdownComputed(rawRef: () => string) {
  return computed(() => useMarkdown(rawRef()))
}
