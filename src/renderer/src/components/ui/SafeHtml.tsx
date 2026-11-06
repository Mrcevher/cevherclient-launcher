import { Fragment, type ReactNode } from 'react'
import { call } from '../../lib/api'
import { reportError } from '../../lib/errors'

/**
 * Siteden gelen (DOMParser ile ayrıştırılmış) içeriği React öğelerine çevirir. Yalnızca beyaz
 * listedeki etiketler çizilir; öznitelikler (style, on*, class …) hiç taşınmaz. Bağlantılar sistem
 * tarayıcısında açılır, görseller yalnızca https'ten yüklenir. Ham HTML hiçbir zaman DOM'a
 * enjekte edilmez.
 */

const BLOCK: Record<string, string> = {
  p: 'my-2',
  h1: 'mt-4 mb-2 text-[18px] font-bold',
  h2: 'mt-4 mb-2 text-[17px] font-bold',
  h3: 'mt-3 mb-1.5 text-[15.5px] font-bold',
  h4: 'mt-3 mb-1.5 text-[14.5px] font-bold',
  h5: 'mt-2 mb-1 font-bold',
  h6: 'mt-2 mb-1 font-bold',
  ul: 'my-2 list-disc pl-6',
  ol: 'my-2 list-decimal pl-6',
  li: 'my-0.5',
  blockquote: 'my-3 border-l-2 border-primary/60 pl-3 text-muted',
  pre: 'my-3 overflow-x-auto rounded-lg bg-panel-2 p-3 text-[12.5px]',
  table: 'my-3 w-full border-collapse text-[13px]',
  thead: '',
  tbody: '',
  tr: 'border-b border-border/70',
  th: 'px-2 py-1.5 text-left font-bold',
  td: 'px-2 py-1.5',
  div: '',
  section: '',
  figure: 'my-3',
  figcaption: 'mt-1 text-[12px] text-muted',
  hr: 'my-4 border-border'
}
const INLINE: Record<string, string> = {
  b: 'font-bold',
  strong: 'font-bold',
  i: 'italic',
  em: 'italic',
  u: 'underline',
  s: 'line-through',
  del: 'line-through',
  code: 'rounded bg-panel-2 px-1 py-0.5 text-[12.5px]',
  span: '',
  small: 'text-[12px]',
  sub: '',
  sup: '',
  mark: 'bg-primary/25 text-text'
}

function resolve(href: string | null, base: string): string | undefined {
  if (!href) return undefined
  try {
    return new URL(href, base).href
  } catch {
    return undefined
  }
}

function open(url: string): void {
  call('shell:openExternal', url).catch((err) => reportError(err))
}

function render(node: Node, base: string, key: number): ReactNode {
  if (node.nodeType === Node.TEXT_NODE) return node.textContent
  if (node.nodeType !== Node.ELEMENT_NODE) return null
  const el = node as Element
  const tag = el.tagName.toLowerCase()
  const children = (): ReactNode[] => [...el.childNodes].map((c, i) => render(c, base, i))

  if (tag === 'br') return <br key={key} />
  if (tag === 'hr') return <hr key={key} className={BLOCK.hr} />
  if (tag === 'img') {
    const src = resolve(el.getAttribute('data-src') || el.getAttribute('src'), base)
    if (!src || !/^https:/i.test(src)) return null
    return (
      <img
        key={key}
        src={src}
        alt={el.getAttribute('alt') ?? ''}
        draggable={false}
        referrerPolicy="no-referrer"
        loading="lazy"
        className="my-2 inline-block max-h-[360px] max-w-full rounded-lg"
      />
    )
  }
  if (tag === 'a') {
    const href = resolve(el.getAttribute('href'), base)
    if (!href || !/^https?:/i.test(href)) return <Fragment key={key}>{children()}</Fragment>
    return (
      <a
        key={key}
        href={href}
        onClick={(e) => {
          e.preventDefault()
          open(href)
        }}
        className="text-primary underline underline-offset-2 hover:brightness-125"
      >
        {children()}
      </a>
    )
  }
  if (tag in BLOCK) {
    const Tag = tag as keyof React.JSX.IntrinsicElements
    return (
      <Tag key={key} className={BLOCK[tag] || undefined}>
        {children()}
      </Tag>
    )
  }
  if (tag in INLINE) {
    const Tag = tag as keyof React.JSX.IntrinsicElements
    return (
      <Tag key={key} className={INLINE[tag] || undefined}>
        {children()}
      </Tag>
    )
  }
  // Bilinmeyen ama zararsız sarmalayıcılar (article, font, center …): yalnızca içerik.
  if (['script', 'style', 'iframe', 'object', 'embed', 'form', 'input', 'button', 'select', 'textarea', 'svg', 'video', 'audio'].includes(tag)) return null
  return <Fragment key={key}>{children()}</Fragment>
}

export function SafeHtml({ root, base }: { root: Element; base: string }): React.JSX.Element {
  return <div className="text-[14px] leading-relaxed text-[#d5d8dc]">{[...root.childNodes].map((n, i) => render(n, base, i))}</div>
}
