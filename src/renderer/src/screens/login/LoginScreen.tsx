import { AnimatePresence, motion } from 'framer-motion'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { USERNAME_PATTERN } from '@shared/constants'
import { Button } from '../../components/ui/Button'
import { BackdropVideo } from '../../components/ui/BackdropVideo'
import logo from '../../assets/cevhercraft-logo.png'
import { PlayIcon } from '../../components/ui/icons'
import { Segmented } from '../../components/ui/Segmented'
import { useSessionStore } from '../../store/session-store'

/** Sunucudaki CevherAuth eklentisinin varsayılan alt sınırı; asıl kontrol sunucuda. */
const MIN_PASSWORD = 6

type Mode = 'login' | 'register'

/**
 * Giriş ekranı. Config'de hesap sunucusu varsa oyuncu burada kayıt olur ya da şifresiyle
 * giriş yapar; oyunun içinde bir daha şifre sorulmaz. Hesap sunucusu yoksa yalnızca
 * kullanıcı adı istenir. Arka plandaki parallax kristaller App seviyesinde çizilir.
 */
export function LoginScreen(): React.JSX.Element {
  const { t } = useTranslation()
  const busy = useSessionStore((s) => s.busy)
  const lastUsername = useSessionStore((s) => s.auth?.lastUsername ?? '')
  const passwordRequired = useSessionStore((s) => s.auth?.passwordRequired ?? false)
  const login = useSessionStore((s) => s.login)
  const register = useSessionStore((s) => s.register)

  const [mode, setMode] = useState<Mode>(lastUsername ? 'login' : 'register')
  const [username, setUsername] = useState(lastUsername)
  const [password, setPassword] = useState('')
  const [repeat, setRepeat] = useState('')
  const [touched, setTouched] = useState(false)

  const registering = passwordRequired && mode === 'register'
  const nameValid = USERNAME_PATTERN.test(username.trim())
  const passwordValid = !passwordRequired || password.length >= (registering ? MIN_PASSWORD : 1)
  const repeatValid = !registering || password === repeat
  const valid = nameValid && passwordValid && repeatValid

  const submit = (e: FormEvent): void => {
    e.preventDefault()
    setTouched(true)
    if (!valid || busy) return
    if (!passwordRequired) void login(username.trim())
    else if (registering) void register(username.trim(), password)
    else void login(username.trim(), password)
  }

  const switchMode = (next: Mode): void => {
    setMode(next)
    setTouched(false)
    setPassword('')
    setRepeat('')
  }

  return (
    <div className="absolute inset-0 grid place-items-center overflow-hidden p-6">
      {/* Ana sayfa arka plan videosu, koyulaştırılmış ve bulanık */}
      <BackdropVideo className="scale-105 blur-[6px]" />
      <div className="absolute inset-0 bg-black/55" />
      <motion.div
        className="relative w-[420px] rounded-2xl border border-border bg-[#141518]/95 px-8 pt-5 pb-7 shadow-[0_24px_70px_rgb(0_0_0/0.6)] backdrop-blur-md"
        initial={{ opacity: 0, y: 24, scale: 0.97 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ type: 'spring', stiffness: 220, damping: 24, delay: 0.05 }}
      >
        <div className="flex flex-col items-center text-center">
          <img src={logo} alt="CevherCraft" draggable={false} className="h-[110px] w-auto drop-shadow-[0_6px_18px_rgb(0_0_0/0.5)]" />
          <p className="text-muted -mt-1 text-sm">{t('app.tagline')}</p>
        </div>

        {passwordRequired && (
          <div className="mt-6 flex justify-center">
            <Segmented<Mode>
              value={mode}
              onChange={switchMode}
              size="sm"
              options={[
                { value: 'login', label: t('login.tabLogin') },
                { value: 'register', label: t('login.tabRegister') }
              ]}
            />
          </div>
        )}

        <form onSubmit={submit} noValidate className={passwordRequired ? 'mt-5' : 'mt-8'}>
          <p className="text-muted mb-4 text-xs">
            {!passwordRequired ? t('login.subtitle') : registering ? t('login.registerHint') : t('login.loginHint')}
          </p>

          <Field
            id="login-username"
            label={t('login.usernameLabel')}
            hint={t('login.usernameRule')}
            invalid={touched && !nameValid}
          >
            <input
              id="login-username"
              className="field text-base!"
              value={username}
              maxLength={16}
              autoFocus={!username}
              autoComplete="username"
              spellCheck={false}
              placeholder={t('login.usernamePlaceholder')}
              aria-invalid={touched && !nameValid}
              onChange={(e) => setUsername(e.target.value)}
            />
          </Field>

          {passwordRequired && (
            <Field
              id="login-password"
              label={t('login.passwordLabel')}
              hint={registering ? t('login.passwordRule', { min: MIN_PASSWORD }) : undefined}
              invalid={touched && !passwordValid}
            >
              <input
                id="login-password"
                type="password"
                className="field text-base!"
                value={password}
                maxLength={64}
                autoFocus={!!username}
                autoComplete={registering ? 'new-password' : 'current-password'}
                aria-invalid={touched && !passwordValid}
                onChange={(e) => setPassword(e.target.value)}
              />
            </Field>
          )}

          <AnimatePresence initial={false}>
            {registering && (
              <motion.div
                key="repeat"
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
                className="overflow-hidden"
              >
                <Field
                  id="login-repeat"
                  label={t('login.passwordRepeatLabel')}
                  hint={touched && !repeatValid ? t('login.passwordMismatch') : undefined}
                  invalid={touched && !repeatValid}
                >
                  <input
                    id="login-repeat"
                    type="password"
                    className="field text-base!"
                    value={repeat}
                    maxLength={64}
                    autoComplete="new-password"
                    aria-invalid={touched && !repeatValid}
                    onChange={(e) => setRepeat(e.target.value)}
                  />
                </Field>
              </motion.div>
            )}
          </AnimatePresence>

          <Button
            type="submit"
            variant="primary"
            size="lg"
            className="mt-2 w-full"
            icon={<PlayIcon size={18} />}
            loading={busy}
            disabled={busy}
          >
            {registering ? t('login.submitRegister') : t('login.submit')}
          </Button>
        </form>
      </motion.div>
    </div>
  )
}

function Field({
  id,
  label,
  hint,
  invalid,
  children
}: {
  id: string
  label: string
  hint?: string
  invalid: boolean
  children: React.ReactNode
}): React.JSX.Element {
  return (
    <div className="mb-3">
      <label htmlFor={id} className="mb-1.5 block text-[13px] font-semibold text-[#c3c6cb]">
        {label}
      </label>
      {children}
      {hint && <p className={`mt-1 text-xs ${invalid ? 'text-hp-low' : 'text-muted'}`}>{hint}</p>}
    </div>
  )
}
