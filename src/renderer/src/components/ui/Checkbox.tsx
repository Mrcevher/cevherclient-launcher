import { CheckIcon } from './icons'

/** Yeşil çerçeveli onay kutusu (tasarımdaki "Fabric mod yükleyicisini kullan" gibi). */
export function Checkbox({
  checked,
  onChange,
  label,
  disabled = false
}: {
  checked: boolean
  onChange(value: boolean): void
  label: string
  disabled?: boolean
}): React.JSX.Element {
  return (
    <label className={`inline-flex items-center gap-2.5 text-[15px] ${disabled ? 'cursor-not-allowed opacity-50' : 'cursor-pointer'}`}>
      <button
        type="button"
        role="checkbox"
        aria-checked={checked}
        disabled={disabled}
        onClick={() => onChange(!checked)}
        className={`grid size-6 shrink-0 place-items-center rounded-md border-2 transition-colors ${
          checked ? 'border-primary bg-primary/15 text-primary' : 'border-[#3a3b41] bg-transparent text-transparent'
        } ${disabled ? 'cursor-not-allowed' : 'cursor-pointer'}`}
      >
        <CheckIcon size={16} strokeWidth={3} />
      </button>
      {label}
    </label>
  )
}
