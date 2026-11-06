import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import de from './locales/de.json'
import en from './locales/en.json'
import fr from './locales/fr.json'
import it from './locales/it.json'
import ja from './locales/ja.json'
import tr from './locales/tr.json'

/**
 * Arayüzdeki tüm metinler bu kaynaklardan gelir. Dil, ayarlar yüklendiğinde
 * (useApplyPreferences) settings.language'a göre değiştirilir; varsayılan TR.
 * Bir dilde eksik anahtar olursa İngilizceye, İngilizcede de yoksa Türkçeye düşülür.
 */
void i18n.use(initReactI18next).init({
  resources: {
    tr: { translation: tr },
    en: { translation: en },
    de: { translation: de },
    fr: { translation: fr },
    it: { translation: it },
    ja: { translation: ja }
  },
  lng: 'tr',
  fallbackLng: { tr: ['en'], default: ['en', 'tr'] },
  interpolation: { escapeValue: false },
  returnNull: false
})

export default i18n
