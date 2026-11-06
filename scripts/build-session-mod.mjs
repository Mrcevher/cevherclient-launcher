// Cevherclient oturum modunu (game-mods/cevher-session) derler ve launcher'ın
// paketleyeceği resources/mods/cevher-session-<mc>.jar konumuna kopyalar.
//
//   npm run build:mod                        → gradle.properties'teki sürüm (1.21.11)
//   npm run build:mod -- --mc 1.21.11 --loader 0.19.5
//   npm run build:client-mod                 → game-mods/cevher-client (mod menüsü)
//
// Farklı bir Minecraft sürümü için derlerken o sürümün fabric-example-mod dalındaki
// loom_version / Gradle sürümünün uyumlu olduğundan emin olun.
import { spawnSync } from 'node:child_process'
import { copyFileSync, mkdirSync, readdirSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')

const argv = process.argv.slice(2)
const opt = (name) => {
  const i = argv.indexOf(`--${name}`)
  return i >= 0 ? argv[i + 1] : undefined
}

// --mod cevher-client → game-mods/cevher-client (npm run build:client-mod); varsayılan oturum modu.
const modName = opt('mod') ?? 'cevher-session'
const modDir = join(root, 'game-mods', modName)

const props = readFileSync(join(modDir, 'gradle.properties'), 'utf8')
const mc = opt('mc') ?? props.match(/^minecraft_version=(.+)$/m)[1].trim()
const gradleArgs = ['build', '--console=plain', `-Pminecraft_version=${mc}`]
if (opt('loader')) gradleArgs.push(`-Ploader_version=${opt('loader')}`)

const isWin = process.platform === 'win32'
const result = spawnSync(join(modDir, isWin ? 'gradlew.bat' : 'gradlew'), gradleArgs, {
  cwd: modDir,
  stdio: 'inherit',
  shell: isWin
})
if (result.status !== 0) process.exit(result.status ?? 1)

const libs = join(modDir, 'build', 'libs')
const jar = readdirSync(libs).find((f) => f.startsWith(`${modName}-${mc}-`) && f.endsWith('.jar') && !f.includes('-sources'))
if (!jar) {
  console.error(`Derlenen jar bulunamadı: ${libs}`)
  process.exit(1)
}

const outDir = join(root, 'resources', 'mods')
mkdirSync(outDir, { recursive: true })
const out = join(outDir, `${modName}-${mc}.jar`)
copyFileSync(join(libs, jar), out)
console.log(`✓ ${out}`)
