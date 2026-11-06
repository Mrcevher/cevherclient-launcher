import { resolve } from 'node:path'
import { defineConfig } from 'electron-vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Üç ayrı derleme hedefi: main (Node), preload (izole köprü) ve renderer (React).
// @shared takma adı her üçünde de ortak tiplere işaret eder.
const sharedAlias = { '@shared': resolve(__dirname, 'src/shared') }

export default defineConfig({
  main: {
    resolve: { alias: sharedAlias }
  },
  preload: {
    resolve: { alias: sharedAlias }
  },
  renderer: {
    resolve: {
      alias: {
        ...sharedAlias,
        '@renderer': resolve(__dirname, 'src/renderer/src')
      }
    },
    // İki sayfa: uygulama (index) ve açılışta logo + çubuk gösteren açılış penceresi (splash).
    build: {
      rollupOptions: {
        input: {
          index: resolve(__dirname, 'src/renderer/index.html'),
          splash: resolve(__dirname, 'src/renderer/splash.html')
        }
      }
    },
    plugins: [react(), tailwindcss()]
  }
})
