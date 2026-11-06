/**
 * postinstall: hatalı yayımlanmış bağımlılıkları düzeltir. Paketlerin düzeltilmiş
 * sürümleri çıktığında bu betik hiçbir şey yapmaz.
 *
 * 1) @xmcl/unzip@2.2.0 npm'e `publishConfig` uygulanmadan yayımlandı: `main` derlenmiş
 *    `dist/index.js` yerine kaynak `index.ts`'yi gösteriyor ve main process'te
 *    "Cannot find module '@xmcl/unzip'" hatası oluşuyor. `dist/` pakette mevcut olduğu
 *    için `publishConfig.main/module` değerleri asıl alanlara kopyalanır. (`workspace:`
 *    bağımlılığı ise package.json'daki `overrides` ile çözülür.)
 * 2) @xmcl/installer@6.3.x `require('@xmcl/core/utils')` yapıyor ama @xmcl/core@2.16.2
 *    bu alt yolu yayımlamıyor. Installer oradan yalnızca `isNotNull` kullanıyor; core'daki
 *    tanımla aynı küçük bir köprü dosyası yazılır.
 */

import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const packages = ['@xmcl/unzip']

for (const name of packages) {
  const dir = join(root, 'node_modules', name)
  const file = join(dir, 'package.json')
  if (!existsSync(file)) continue

  const pkg = JSON.parse(readFileSync(file, 'utf8'))
  const publish = pkg.publishConfig ?? {}
  const mainMissing = !pkg.main || !existsSync(join(dir, pkg.main))
  if (!mainMissing || !publish.main || !existsSync(join(dir, publish.main))) continue

  pkg.main = publish.main
  if (publish.module) pkg.module = publish.module
  writeFileSync(file, `${JSON.stringify(pkg, null, 2)}\n`)
  console.log(`[patch-deps] ${name}@${pkg.version}: main → ${pkg.main}`)
}

const coreDir = join(root, 'node_modules', '@xmcl', 'core')
const coreUtils = join(coreDir, 'utils.js')
if (existsSync(join(coreDir, 'package.json')) && !existsSync(coreUtils)) {
  writeFileSync(
    coreUtils,
    [
      '// patch-deps.mjs tarafından eklendi: @xmcl/installer bu alt yolu bekliyor.',
      "'use strict'",
      'exports.isNotNull = function isNotNull(v) {',
      '  return v !== void 0',
      '}',
      ''
    ].join('\n')
  )
  console.log('[patch-deps] @xmcl/core: utils.js köprüsü eklendi')
}
