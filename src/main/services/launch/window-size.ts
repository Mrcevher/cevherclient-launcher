import { screen } from 'electron'

/** Windows'ta oyun penceresinin başlık çubuğu + kenarlıkları için ayrılan pay (ölçeksiz piksel). */
const WINDOW_CHROME_DIP = { width: 16, height: 40 }

/**
 * Pencereli oyun boyutunu ana ekranın kullanılabilir alanına (görev çubuğu hariç) sığdırır.
 * Minecraft (GLFW) boyutu fiziksel piksel olarak alır; ekran ölçeklemesi (ör. %125) hesaba
 * katılır. İstenen boyut sığıyorsa aynen, sığmıyorsa en-boy oranı korunarak küçültülür.
 * Aksi halde pencere ekrandan taşar ve oyunun altı/sağı görünmez.
 */
export function fitToScreen(width: number, height: number): { width: number; height: number; scaled: boolean } {
  const display = screen.getPrimaryDisplay()
  const scale = display.scaleFactor || 1
  const maxWidth = Math.floor((display.workArea.width - WINDOW_CHROME_DIP.width) * scale)
  const maxHeight = Math.floor((display.workArea.height - WINDOW_CHROME_DIP.height) * scale)
  if (width <= maxWidth && height <= maxHeight) return { width, height, scaled: false }
  const ratio = Math.min(maxWidth / width, maxHeight / height)
  return { width: Math.floor(width * ratio), height: Math.floor(height * ratio), scaled: true }
}
