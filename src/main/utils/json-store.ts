import { app } from 'electron'
import { mkdir, readFile, rename, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'

/**
 * userData altında küçük bir JSON dosyası. Okumalar önbelleklenir, yazmalar sıraya
 * alınır ve önce geçici dosyaya yazılıp yeniden adlandırılır (yarım yazılmış dosya
 * oluşmaz).
 */
export class JsonStore<T> {
  private readonly filePath: string
  private cache: T | null = null
  private writeChain: Promise<void> = Promise.resolve()

  constructor(
    fileName: string,
    private readonly defaults: T,
    /** Diskten okunan ham veriyi geçerli bir T'ye dönüştürür. */
    private readonly sanitize: (raw: unknown) => T = (raw) => (raw as T) ?? defaults
  ) {
    this.filePath = join(app.getPath('userData'), fileName)
  }

  async read(): Promise<T> {
    if (this.cache) return this.cache
    try {
      const text = await readFile(this.filePath, 'utf8')
      this.cache = this.sanitize(JSON.parse(text))
    } catch {
      // Dosya yok veya bozuk → varsayılanlar.
      this.cache = this.sanitize(structuredClone(this.defaults))
    }
    return this.cache
  }

  async write(value: T): Promise<void> {
    this.cache = value
    const snapshot = JSON.stringify(value, null, 2)
    this.writeChain = this.writeChain
      .catch(() => undefined)
      .then(async () => {
        await mkdir(dirname(this.filePath), { recursive: true })
        const tmp = `${this.filePath}.tmp`
        await writeFile(tmp, snapshot, 'utf8')
        await rename(tmp, this.filePath)
      })
    return this.writeChain
  }
}
