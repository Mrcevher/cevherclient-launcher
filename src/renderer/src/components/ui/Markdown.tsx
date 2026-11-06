import ReactMarkdown, { type Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { call } from '../../lib/api'

/**
 * Uzaktan gelen markdown'ı güvenli biçimde çizer: ham HTML devre dışıdır (react-markdown
 * varsayılanı), görseller gösterilmez, bağlantılar sistem tarayıcısında açılır.
 */
const components: Components = {
  a: ({ href, children }) => (
    <a
      href={href}
      className="text-accent underline underline-offset-2 hover:brightness-125"
      onClick={(e) => {
        e.preventDefault()
        if (href) void call('shell:openExternal', href).catch(() => undefined)
      }}
    >
      {children}
    </a>
  ),
  img: ({ alt }) => <span className="text-muted italic">[{alt}]</span>,
  h1: ({ children }) => <h4 className="tech mt-3 text-xl font-bold">{children}</h4>,
  h2: ({ children }) => <h4 className="tech mt-3 text-lg font-bold">{children}</h4>,
  h3: ({ children }) => <h5 className="tech mt-2 font-bold">{children}</h5>,
  p: ({ children }) => <p className="mt-2 leading-relaxed">{children}</p>,
  ul: ({ children }) => <ul className="mt-2 list-disc space-y-1 pl-5 marker:text-accent">{children}</ul>,
  ol: ({ children }) => <ol className="mt-2 list-decimal space-y-1 pl-5 marker:text-accent">{children}</ol>,
  strong: ({ children }) => <strong className="font-semibold text-text">{children}</strong>,
  code: ({ children }) => <code className="rounded bg-black/30 px-1.5 py-0.5 font-mono text-[0.85em] text-accent">{children}</code>,
  blockquote: ({ children }) => (
    <blockquote className="mt-3 border-l-2 border-accent bg-accent/8 py-1 pr-3 pl-3 italic">{children}</blockquote>
  ),
  table: ({ children }) => (
    <div className="mt-3 overflow-x-auto rounded-lg border border-border">
      <table className="w-full text-left text-sm">{children}</table>
    </div>
  ),
  th: ({ children }) => <th className="tech border-b border-border bg-black/20 px-3 py-1.5 font-bold tracking-wide uppercase">{children}</th>,
  td: ({ children }) => <td className="border-b border-border/50 px-3 py-1.5">{children}</td>
}

export function Markdown({ children }: { children: string }): React.JSX.Element {
  return (
    <div className="text-muted text-sm select-text">
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
        {children}
      </ReactMarkdown>
    </div>
  )
}
