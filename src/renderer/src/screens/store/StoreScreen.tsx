import { AnimatePresence, motion } from 'framer-motion'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { StoreProduct } from '@shared/types'
import { PageLayout } from '../../components/layout/PageLayout'
import { CartIcon, ExternalIcon } from '../../components/ui/icons'
import { hasLink, openLink } from '../../lib/links'
import { effectivePrice, formatMoney, htmlToText, stockOf } from '../../lib/site-format'
import { useNotifyStore } from '../../store/notify-store'
import { useSiteStore } from '../../store/site-store'

const ALL = '__all'

/** Launcher içi mağaza: ürünler siteden (LeaderOS) gelir, satın alma site kredisiyle yapılır. */
export function StoreScreen(): React.JSX.Element {
  const { t, i18n } = useTranslation()
  const { listing, listingState, profile, profileState, loadListing, loadProfile } = useSiteStore()
  const [category, setCategory] = useState(ALL)
  const [selected, setSelected] = useState<StoreProduct | null>(null)

  useEffect(() => {
    void loadListing(true)
    void loadProfile(true)
  }, [loadListing, loadProfile])

  const categories = useMemo(() => (listing?.categories ?? []).filter((c) => c.products.length > 0), [listing])
  const products = useMemo(() => {
    const list = category === ALL ? categories.flatMap((c) => c.products) : (categories.find((c) => c.id === category)?.products ?? [])
    // Aynı ürün birden çok kategoride görünmesin; öne çıkanlar ve öncelik önce.
    const seen = new Set<string>()
    return list
      .filter((p) => (seen.has(p.id) ? false : (seen.add(p.id), true)))
      .sort((a, b) => Number(b.featured) - Number(a.featured) || (Number(b.priority) || 0) - (Number(a.priority) || 0))
  }, [categories, category])

  const currency = profile?.currency
  const money = (n: number): string => formatMoney(n, currency, i18n.language)

  return (
    <PageLayout title={t('nav.store')}>
      {/* Bakiye çubuğu */}
      <div className="panel flex flex-wrap items-center gap-3 p-4">
        <div className="flex items-center gap-3">
          <span className="grid size-10 place-items-center rounded-xl border border-primary/50 bg-primary/12 text-primary">
            <CartIcon size={19} />
          </span>
          <div>
            <div className="text-[12px] font-semibold text-muted">{t('store.balance')}</div>
            <div className="text-[18px] font-extrabold tabular-nums">
              {profileState === 'loading' && !profile
                ? '…'
                : profile?.linked
                  ? (profile.credits ?? (profile.rawCredits !== undefined ? money(profile.rawCredits) : '—'))
                  : '—'}
            </div>
          </div>
        </div>
        <div className="ml-auto flex gap-2">
          {hasLink('credits') && (
            <button type="button" className="btn btn-green" onClick={() => openLink('credits')}>
              {t('store.topUp')} <ExternalIcon size={15} />
            </button>
          )}
        </div>
      </div>

      {profile && !profile.linked && <div className="panel border-danger/50 p-4 text-[13.5px] text-[#e9b4b4]">{t('store.notLinked')}</div>}

      {/* Kategoriler */}
      {categories.length > 1 && (
        <div className="flex flex-wrap gap-2">
          {[{ id: ALL, name: t('store.all') }, ...categories].map((c) => (
            <button
              key={c.id}
              type="button"
              onClick={() => setCategory(c.id)}
              className={`cursor-pointer rounded-full border px-4 py-1.5 text-[13px] font-bold transition-colors ${
                category === c.id ? 'border-primary/70 bg-primary/15 text-primary' : 'border-border bg-panel text-[#c9ccd1] hover:border-[#3a3b41] hover:text-white'
              }`}
            >
              {c.name}
            </button>
          ))}
        </div>
      )}

      {/* Ürünler */}
      {listingState === 'loading' && !listing ? (
        <div className="panel p-10 text-center text-sm text-muted">{t('common.loading')}</div>
      ) : listingState === 'error' && !listing ? (
        <div className="panel flex flex-col items-center gap-3 p-10 text-center text-sm text-muted">
          {t('store.loadFailed')}
          <button type="button" className="btn btn-ghost" onClick={() => void loadListing(true)}>
            {t('common.retry')}
          </button>
        </div>
      ) : products.length === 0 ? (
        <div className="panel p-10 text-center text-sm text-muted">{t('store.empty')}</div>
      ) : (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(200px,1fr))] gap-4">
          {products.map((p, i) => (
            <ProductCard key={p.id} product={p} index={i} money={money} onBuy={() => setSelected(p)} />
          ))}
        </div>
      )}

      <AnimatePresence>{selected && <ConfirmDialog product={selected} money={money} onClose={() => setSelected(null)} />}</AnimatePresence>
    </PageLayout>
  )
}

function ProductCard({
  product,
  index,
  money,
  onBuy
}: {
  product: StoreProduct
  index: number
  money: (n: number) => string
  onBuy(): void
}): React.JSX.Element {
  const { t } = useTranslation()
  const { price, old } = effectivePrice(product)
  const stock = stockOf(product)
  const soldOut = stock === 0
  const details = htmlToText(product.details)

  return (
    <motion.div
      className="panel flex flex-col overflow-hidden"
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: Math.min(index, 12) * 0.03, duration: 0.22 }}
    >
      <div className="relative grid aspect-[4/3] place-items-center bg-[#0f1012]">
        {product.image ? (
          <img src={product.image} alt="" draggable={false} referrerPolicy="no-referrer" className="max-h-[80%] max-w-[80%] object-contain" />
        ) : (
          <CartIcon size={40} className="text-[#3a3b41]" />
        )}
        {product.featured && (
          <span className="absolute top-2 left-2 rounded-full bg-primary px-2 py-0.5 text-[10.5px] font-extrabold tracking-wide text-black uppercase">
            {t('store.featured')}
          </span>
        )}
        {old !== undefined && (
          <span className="absolute top-2 right-2 rounded-full bg-danger px-2 py-0.5 text-[10.5px] font-extrabold text-white">
            −%{Math.round((1 - price / old) * 100)}
          </span>
        )}
      </div>
      <div className="flex flex-1 flex-col gap-2 p-4">
        <h3 className="text-[15px] leading-snug font-bold">{product.name}</h3>
        {details && <p className="line-clamp-2 text-[12.5px] text-muted">{details}</p>}
        <div className="mt-auto flex items-baseline gap-2 pt-1">
          <span className="text-[17px] font-extrabold tabular-nums">{money(price)}</span>
          {old !== undefined && <span className="text-[12.5px] text-muted tabular-nums line-through">{money(old)}</span>}
        </div>
        {stock !== null && (
          <div className={`text-[12px] font-semibold ${soldOut ? 'text-danger' : 'text-muted'}`}>
            {soldOut ? t('store.soldOut') : t('store.stock', { count: stock })}
          </div>
        )}
        <button type="button" className="btn btn-green mt-1 w-full" disabled={soldOut} onClick={onBuy}>
          {t('store.buy')}
        </button>
      </div>
    </motion.div>
  )
}

function ConfirmDialog({
  product,
  money,
  onClose
}: {
  product: StoreProduct
  money: (n: number) => string
  onClose(): void
}): React.JSX.Element {
  const { t } = useTranslation()
  const { profile, buying, buy } = useSiteStore()
  const { price } = effectivePrice(product)
  const balance = profile?.linked ? profile.rawCredits : undefined
  const after = balance !== undefined ? balance - price : undefined
  const insufficient = after !== undefined && after < -0.0001
  const busy = buying === product.id

  useEffect(() => {
    const onKey = (e: KeyboardEvent): void => {
      if (e.key === 'Escape' && !busy) onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [busy, onClose])

  const confirm = async (): Promise<void> => {
    const result = await buy(product.id)
    if (!result) return
    useNotifyStore.getState().push({
      kind: 'success',
      title: t('store.boughtTitle'),
      message: t('store.boughtMessage', { name: product.name })
    })
    onClose()
  }

  return (
    <motion.div
      className="fixed inset-0 z-[60] grid place-items-center bg-black/60 p-6 backdrop-blur-sm"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      onMouseDown={(e) => e.target === e.currentTarget && !busy && onClose()}
    >
      <motion.div
        role="dialog"
        aria-modal="true"
        aria-label={t('store.confirmTitle')}
        className="w-full max-w-[400px] rounded-2xl border border-border bg-[#141518] p-6 shadow-[0_20px_50px_rgb(0_0_0/0.6)]"
        initial={{ scale: 0.96, y: 8 }}
        animate={{ scale: 1, y: 0 }}
        exit={{ scale: 0.96, y: 8 }}
      >
        <h2 className="text-[18px] font-extrabold">{t('store.confirmTitle')}</h2>
        <p className="mt-1 text-[13.5px] text-muted">{t('store.confirmHint')}</p>

        <dl className="mt-4 divide-y divide-border/70 rounded-xl bg-panel-2 px-4 text-[14px]">
          <Row label={t('store.product')}>{product.name}</Row>
          <Row label={t('store.price')}>{money(price)}</Row>
          {balance !== undefined && <Row label={t('store.balance')}>{money(balance)}</Row>}
          {after !== undefined && (
            <Row label={t('store.after')}>
              <span className={insufficient ? 'text-danger' : ''}>{money(after)}</span>
            </Row>
          )}
        </dl>

        {insufficient && <p className="mt-3 text-[13px] text-danger">{t('store.insufficient')}</p>}

        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className="btn btn-ghost" disabled={busy} onClick={onClose}>
            {t('quality.cancel')}
          </button>
          {insufficient && hasLink('credits') ? (
            <button type="button" className="btn btn-green" onClick={() => openLink('credits')}>
              {t('store.topUp')} <ExternalIcon size={15} />
            </button>
          ) : (
            <button type="button" className="btn btn-green" disabled={busy || profile?.linked === false} onClick={() => void confirm()}>
              {busy ? t('store.buying') : t('store.confirm')}
            </button>
          )}
        </div>
      </motion.div>
    </motion.div>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }): React.JSX.Element {
  return (
    <div className="flex items-center justify-between gap-4 py-2.5">
      <dt className="text-[#c3c6cb]">{label}</dt>
      <dd className="min-w-0 truncate text-right font-semibold tabular-nums">{children}</dd>
    </div>
  )
}
