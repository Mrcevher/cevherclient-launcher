# Cevherclient

Kendi Minecraft sunucumuz için özel bir **Minecraft Java Edition launcher'ı**.
Koyu, tek temalı arayüz: tam ekran CevherCraft görselli ana sayfa, solda hamburger ile açılıp kapanan
(tercih hatırlanır) menü
(Mağaza, Modlar, Haberler, Kredi Al, Discord, Profil, Ayarlar), sağ üstte hesap menüsü ve çevrimiçi
oyuncu sayısı, altta büyük **OYNA** butonu. Oyna'ya basınca "LINK START" geçişi oynar.
Arayüz dilleri: Türkçe (varsayılan), English, Deutsch, Français, Italiano, 日本語 (Ayarlar → Dil).
Uzaktan config metinleri (sunucu açıklaması, haberler) yalnızca TR/EN içeriyorsa diğer dillerde
İngilizcesi gösterilir. Minecraft/Mojang logosu kullanılmaz.

> **Durum: Faz 5 tamamlandı.** Sürüm listesi, SHA1 doğrulamalı ve devam edebilen indirme,
> Java seçimi/indirme, Server List Ping, Quick Play ile doğrudan sunucuya başlatma, `.mrpack`
> modpack senkronu, kullanıcı adıyla giriş ve otomatik güncelleme gerçek çalışır. Yayına almadan önce
> yapılması gerekenler için bkz. [Yayın öncesi kontrol listesi](#yayın-öncesi-kontrol-listesi).

---

## Gereksinimler

- **Node.js 22.12+** (Electron 44'ün gereksinimi; Node 24 ile test edildi)
- npm 10+

## Kurulum

```bash
npm install
```

`postinstall` adımı iki iş yapar:

- Electron binary'sini indirir (`install-electron`). Electron 44 binary'yi kendi postinstall'ında
  indirmez; npm 11 de bağımlılık script'lerini varsayılan olarak çalıştırmaz.
- [`scripts/patch-deps.mjs`](scripts/patch-deps.mjs) hatalı yayımlanmış @xmcl paketlerini düzeltir:
  `@xmcl/unzip@2.2.0`'ın `main` alanı derlenmiş dosya yerine `index.ts`'yi gösteriyor;
  `@xmcl/installer` ise `@xmcl/core`'un yayımlamadığı `@xmcl/core/utils` alt yolunu istiyor.
  `@xmcl/unzip`'in `workspace:` bağımlılığı da package.json'daki `overrides` ile çözülür.

Bu adım atlanırsa `npm run dev` "Electron uninstall" ya da "Cannot find module '@xmcl/unzip'"
hatası verir; `npm run postinstall` ile tamamlanır.

## Geliştirme

```bash
npm run dev         # Electron + Vite (HMR) ile uygulamayı açar
npm run typecheck   # main/preload ve renderer için ayrı ayrı tsc
npm run build       # typecheck + üretim derlemesi (out/)
```

### Mock servisler ve mock hataları

Arayüzü ağ ve indirme olmadan geliştirmek için oyun servislerinde
(sürüm, kurulum, Java, sunucu durumu, modpack, başlatma) Faz 1 mock'larına dönülebilir:

```bash
CEVHER_MOCK_SERVICES=1 npm run dev
```

Hata ekranlarını denemek için mock servislere yapay hata verdirilebilir:

```bash
CEVHER_MOCK_FAIL=LAUNCH_FAILED,AUTH_FAILED npm run dev
```

PowerShell'de: `$env:CEVHER_MOCK_FAIL="JAVA_NOT_FOUND"; npm run dev`

Desteklenenler (yalnızca `CEVHER_MOCK_SERVICES=1` ile): `JAVA_NOT_FOUND`, `AUTH_FAILED`,
`DOWNLOAD_FAILED` (indirme %40'ta düşer), `LAUNCH_FAILED`, `MODPACK_SYNC_FAILED`. Mock config'deki **Yapı Dünyası (Test)** sunucusunun adresi `.invalid` ile bittiği için gerçek
ping'de de her zaman çevrimdışıdır (`SERVER_OFFLINE` için). Ayarlar'da var olmayan bir Java yolu
seçmek `JAVA_NOT_FOUND` üretir.

---

## Mimari

```
src/
├─ shared/                 Main, preload ve renderer'ın ortak kodu
│  ├─ types/               Config, ayarlar ve alan tipleri
│  ├─ errors.ts            Hata kodları, önem dereceleri, AppError, IpcResult
│  ├─ ipc.ts               Tipli IPC sözleşmesi + izinli kanal listeleri
│  ├─ config-schema.ts     Uzaktan config doğrulayıcı
│  ├─ constants.ts / utils.ts
├─ main/                   Electron main process (dosya, ağ, process işleri YALNIZCA burada)
│  ├─ index.ts             Giriş: tek örnek kilidi, izinler, IPC kaydı
│  ├─ window.ts            Çerçevesiz pencere, güvenli webPreferences, gezinme engeli
│  ├─ updater.ts           electron-updater (GitHub Releases)
│  ├─ ipc/                 handle() sarmalayıcısı + kanal → servis bağlantıları
│  ├─ services/
│  │  ├─ types.ts          Servis arayüzleri (Auth, RemoteConfig, ServerStatus, Version,
│  │  │                    Install, Modpack, Launch, Java, Settings)
│  │  ├─ container.ts      Composition root: hangi implementasyonun kullanılacağı
│  │  ├─ minecraft/        Ortak: indirici (duraklat/devam, Range, SHA1), yollar, sürüm anahtarı
│  │  └─ <alan>/…          Gerçek implementasyonlar + Faz 1 mock'ları
│  └─ utils/               JsonStore (atomik JSON), EventSink (main → renderer olaylar)
├─ preload/                contextBridge ile window.cevher (beyaz listeli, tipli)
└─ renderer/               React arayüzü
   └─ src/
      ├─ screens/          Her ekran kendi klasöründe: login, home, mods, profile, news, settings
      ├─ components/       layout (başlık çubuğu, menü, oyuncu kartı, durum çubuğu),
      │                    ui (HudBar, Button, ikonlar…), effects (arka plan, LINK START),
      │                    feedback (sistem mesajları, altıgen kritik uyarı)
      ├─ store/            Zustand store'ları
      ├─ lib/              api (IPC istemcisi), errors (hata çeviri katmanı), theme, format
      ├─ i18n/             react-i18next + locales/tr.json, en.json
      ├─ assets/           Ana sayfa görseli (home-bg.webp) ve CevherCraft logosu
      └─ styles/           Tailwind v4 + renk paleti (CSS değişkenleri)
```

### Güvenlik

- `contextIsolation: true`, `nodeIntegration: false`, `sandbox: true`.
- Renderer yalnızca `window.cevher.invoke(kanal, …)` ve `window.cevher.on(olay, …)` kullanır.
  Kanallar `shared/ipc.ts`'de tiplidir; preload ayrıca çalışma zamanında beyaz listeyle denetler.
- Main, yalnızca uygulamanın kendi sayfasından gelen IPC isteklerini kabul eder; argümanları doğrular.
- CSP yalnızca yerel kaynaklara izin verir. Harici bağlantılar sistem tarayıcısında açılır
  (yalnızca http/https), uygulama içinde başka sayfaya gezinme engellenir.
- Haberlerdeki markdown'da ham HTML ve görseller devre dışıdır.

### Mock → gerçek implementasyon

IPC handler'ları ve UI yalnızca `services/types.ts`'deki arayüzleri bilir. Gerçek
implementasyona geçmek için yeni sınıfı yazıp `services/container.ts`'de değiştirmek yeterlidir:

```ts
// container.ts
const status = new SlpServerStatusService()   // Faz 2: gerçek Server List Ping
```

Faz 1 mock'ları silinmedi; `CEVHER_MOCK_SERVICES=1` ile seçilirler (bkz. [Mock servisler](#mock-servisler-ve-mock-hataları)).

| Servis | Implementasyon | Not |
|---|---|---|
| SettingsService | `FileSettingsService` | **Gerçek** — `settings.json`, doğrulama + sınırlama |
| RemoteConfigService | `JsonRemoteConfigService` | **Gerçek** — dosya / file:// / http(s) |
| AuthService | `UsernameAuthService` | **Gerçek** — yalnızca kullanıcı adı (lisanssız sunucu); UUID = `OfflinePlayer:<isim>` türetmesi |
| ServerStatusService | `SlpServerStatusService` | **Gerçek** — main process'te Server List Ping (SRV kaydı, oyuncu sayısı, Ping/Pong süresi) |
| VersionService | `RemoteVersionService` | **Gerçek** — Mojang manifest v2 + Fabric/Quilt meta, Forge promotions, NeoForge maven; `releaseTime`'a göre sıralı, 26.x destekli; ağ yoksa diskteki son liste |
| InstallService | `MinecraftInstallService` | **Gerçek** — @xmcl/installer manifest'leri; SHA1 doğrulamalı, dosya bazlı + HTTP Range ile devam; duraklat/devam/iptal |
| ModpackService | `MrpackModpackService` | **Gerçek** — Modrinth `.mrpack`: SHA1 doğrulamalı indirme, override'lar, eski dosyaları kaldırma, `options.txt`/`config/` koruma |
| JavaService | `MinecraftJavaService` | **Gerçek** — kurulum taraması + `java -version`; `javaVersion.majorVersion`'a göre seçim, yoksa Mojang runtime'ı indirir |
| PerformancePackService | `ModrinthPerformancePackService` | **Gerçek** — Modrinth'ten sürüme uygun Fabric modları, SHA1 doğrulamalı; kendi kaydıyla güncelle/kaldır |
| LaunchService | `MinecraftLaunchService` | **Gerçek** — @xmcl/core ile process; lisanssız (`legacy`) hesap, `--quickPlayMultiplayer host:port` |

#### Oyun dosyaları ve başlatma

- **Klasörler** (`userData` altında): `minecraft/` (versions, libraries, assets — tüm sürümler ortak),
  `instances/<sunucu-id>/` (sunucu profilinin oyun klasörü), `runtimes/<bileşen>/` (indirilen Java),
  `logs/game-latest.log` (son oyun çıktısı). Serbest sürüm başlatmada oyun klasörü `minecraft/`'tır.
- **Kurulum hattı:** vanilla version JSON (manifest v2 SHA1'i) → loader profili (Fabric/Quilt JSON;
  Forge/NeoForge installer + işlemciler, sürümün Java'sıyla) → asset index → istemci jar'ı,
  kütüphaneler, asset nesneleri. Her dosya SHA1 (yoksa boyut) ile doğrulanır; yalnızca eksik/bozuk
  olanlar indirilir. Yarım dosyalar `.part` olarak kalır ve HTTP Range ile sürdürülür. Başlatmadan
  önce aynı hat doğrulama için yeniden çalışır; son doğrulamadan sonra değişmemiş dosyalar hızlı geçer.
- **Java:** sürüm JSON'undaki `javaVersion` (ör. 26.x → `java-runtime-epsilon`, Java 25). Önce aynı
  majör sürüm, yoksa Mojang runtime'ı indirilir; indirilemezse uyumlu üst sürüm denenir. Ayarlardaki
  özel yol `java -version` ile doğrulanır.
- **Loader:** config'de `loader.version` boşsa o Minecraft sürümü için önerilen/en yeni kararlı loader
  otomatik seçilip kurulur.
- **Modpack:** `modpackManifestUrl` her başlatmada indirilir (ağ yoksa son kopya), dosyaları sunucunun
  instance klasörüne SHA1 doğrulamasıyla yerleşir. `overrides/` ve `client-overrides/` çıkarılır;
  `options.txt` ve `config/` yalnızca yoksa yazılır. Launcher'ın koyduğu ama paketten çıkarılan
  dosyalar silinir; oyuncunun kendi eklediklerine dokunulmaz (kayıt: `instances/<id>/.cevher/modpack.json`).
  Paketin `dependencies.minecraft` alanı profildeki sürümle uyuşmazsa `MODPACK_SYNC_FAILED`.
- **Başlatma:** Quick Play destekleyen sürümlerde (1.20+) `--quickPlayMultiplayer host:port`, eskilerde
  `--server/--port`. Kullanıcı adı ve `OfflinePlayer:<isim>` UUID'si kullanılır.
  Oyun penceresi hazır olmadan çıkarsa `LAUNCH_FAILED` (çıkış kodu + log sonu "Teknik ayrıntı"da),
  sonradan çıkarsa `launch:exit` olayı gönderilir.

#### Giriş: launcher hesabı, oyunda şifre yok

Sunucu lisanssız oyunculara açık (`online-mode=false`) olduğu için Microsoft hesabı istenmez.
Oyuncu hesabını **launcher'da** açar (Kayıt ol) ve launcher'da giriş yapar; oyunun içinde şifre sorulmaz.
Bunu sunucudaki küçük **CevherAuth** eklentisi sağlar ([`server-plugins/cevher-auth`](server-plugins/cevher-auth)):

1. Launcher, config'deki `authServerUrl` adresine (eklentinin HTTP API'si, varsayılan port **25580**)
   kullanıcı adı + şifre gönderir. Eklenti şifreyi PBKDF2-SHA256 ile saklar ve launcher'a uzun ömürlü
   bir oturum belirteci verir. Launcher belirteci `safeStorage` ile şifreleyip saklar; şifre hiçbir yere yazılmaz.
2. **Oyna**'ya basınca launcher, oyunu başlatmadan hemen önce bu oturumla tek kullanımlık, 5 dakikalık bir
   **giriş bileti** alır (`/api/join`). Sunucu bilete özel rastgele bir **sır** üretir ve launcher'a verir.
   Launcher sırrı oyun klasöründeki `.cevher/join-ticket.json` dosyasına yazar; oturum modu
   (`cevher-session`) oyun açılırken okuyup hemen siler. Sır komut satırında taşınmaz.
3. Oyuncu bağlanırken eklenti login aşamasında oyundan `cevherauth:proof/<nonce>` cookie'sini ister; mod
   HMAC-SHA256(sır, nonce + ad) ile yanıt verir. Kanıt doğruysa bilet tüketilir ve oyuncu hiçbir şey
   yazmadan girer. Bileti olmayan, kanıt göndermeyen ya da yanlış kanıt gönderen bağlantı reddedilir.
   Sır Minecraft bağlantısından hiç geçmez ve nonce her bağlanışta yenidir; bu yüzden adı bilen ya da
   oyuncuyla aynı IP'yi paylaşan biri de bileti kullanamaz. (`require-proof: true`, varsayılan.)

Ayrıntılar:
- Adlar büyük/küçük harf duyarsız tekildir (`arda` = `Arda`); oyunda kayıtlı yazım kullanılır.
  UUID Minecraft'ın offline türetmesidir (`OfflinePlayer:<isim>`).
- Yönetici (op) adlarıyla yalnızca sunucunun çalıştığı bilgisayardan kayıt olunabilir (`protect-op-names`).
- IP başına en çok 3 hesap, 10 dakikada 10 hatalı şifre denemesi; oturum 30 gün (her Oyna'da yenilenir).
- Yönetim (konsol/op): `/cevherauth liste`, `/cevherauth sil <ad>`, `/cevherauth sifre <ad> <yeni>`, `/cevherauth reload`.
- Ayarlar: `plugins/CevherAuth/config.yml`; hesaplar `accounts.json`, oturumlar `sessions.json` (yalnızca SHA-256 özetleri).
- `authServerUrl` config'de yoksa launcher yalnızca kullanıcı adı ister (korumasız mod).

**Yayına almadan önce:** API düz HTTP'dir. Oyuncular internetten bağlanacaksa 25580 portunu da
yönlendirin ve şifrelerin açık ağda gitmemesi için API'yi HTTPS arkasına alın (ör. Caddy/nginx reverse
proxy + `http.trust-proxy-headers: true`, ya da Cloudflare Tunnel); `authServerUrl`'i `https://…` yapın.
Tünel/proxy oyuncunun IP'sini değiştiriyorsa `check-ip: false` gerekir; `require-proof` açık kaldıkça
bilet yine yalnızca onu alan oyunda geçerlidir.

**Bilet kanıtına geçiş sırası:** `require-proof` açık eklenti, kanıtı gönderen yeni oturum modunu
(`resources/mods/cevher-session-<mc>.jar`, `npm run build:mod`) içeren launcher sürümüyle birlikte
çalışır. Önce yeni launcher'ı yayınlayıp oyuncuların güncellemesini bekleyin, sonra eklentiyi güncelleyin;
eski launcher'la girmeye çalışan oyuncu "launcher'ı güncelle" mesajı alır. Geçiş döneminde gerekirse
`require-proof: false` yapılabilir (o süre boyunca eski, yalnızca IP'ye dayalı koruma geçerlidir).
Oturum modu yalnızca Fabric/Quilt profillerinde çalışır; vanilla profilli sunucuya `require-proof` ile girilemez.

Eklentiyi derlemek (JDK 21; jar: `server-plugins/cevher-auth/build/libs/CevherAuth-1.0.0.jar`):

```bash
cd server-plugins/cevher-auth && ./gradlew build
```

### Hata altyapısı

Servisler `AppError(code, detail?, meta?)` fırlatır. IPC sınırında hata `IpcResult`
zarfıyla taşınır; renderer'da **tek katman** (`renderer/src/lib/errors.ts`) kodu i18n'den
kullanıcı dostu başlık/mesaja çevirir ve önem derecesine göre gösterir:

| Kod | Gösterim |
|---|---|
| `JAVA_NOT_FOUND` | Altıgen tam ekran uyarı |
| `NO_INTERNET` | Sistem mesajı |
| `AUTH_FAILED` | Sistem mesajı |
| `DOWNLOAD_FAILED` | Sistem mesajı |
| `LAUNCH_FAILED` | Altıgen tam ekran uyarı |
| `SERVER_OFFLINE` | Sistem mesajı |
| `MODPACK_SYNC_FAILED` | Altıgen tam ekran uyarı |
| `CONFIG_INVALID` | Altıgen tam ekran uyarı (ek) |
| `LAUNCHER_OUTDATED` | Altıgen tam ekran uyarı (ek) — `minLauncherVersion` kontrolü |
| `UNKNOWN` | Sistem mesajı (ek, yedek) |

Mesajlar `errors.<KOD>.title/message` anahtarlarındadır; `meta` alanları enterpolasyona
girer (ör. `{{server}}`, `{{version}}`). `meta.reason` verilirse `errors.<KOD>.reasons.<reason>`
kullanılır.

---

## Uzaktan config formatı

Launcher açılışta **tek bir JSON dosyası** okur. Yeni bir sunucu ya da mod paketi eklemek
için yalnızca bu dosya güncellenir.

**Kaynak önceliği:**

1. `settings.json` içindeki `remoteConfigUrl`
2. `CEVHER_CONFIG_URL` ortam değişkeni
3. Uygulamayla gelen mock dosya: [`resources/mock/remote-config.json`](resources/mock/remote-config.json)

Kaynak `https://…` URL'si, `file://…` URL'si veya düz dosya yolu olabilir.

```jsonc
{
  "minLauncherVersion": "0.1.0",          // altındaki launcher'lar uyarı gösterir
  "authServerUrl": "http://127.0.0.1:25580", // CevherAuth API'si; varsa launcher'da kayıt/giriş, oyunda şifre yok
  "avatarUrl": "https://mc-heads.net/avatar/{username}/{size}", // opsiyonel, oyuncu kafası görseli
  "links": {                               // menüdeki dış bağlantılar (https); tanımsız olan gizlenir
    "website": "https://cevhercraft.com",   // haberler (/blog) ve mağaza bu siteden okunur
    "store": "https://cevhercraft.com/store", // MAĞAZA artık launcher içinde; bu bağlantı yedek
    "credits": "https://cevhercraft.com/shop",// KREDİ AL
    "orders": "https://cevhercraft.com/account", // Siparişlerim
    "discord": "https://discord.gg/…"        // DISCORD
  },
  "servers": [
    {
      "id": "survival",                    // benzersiz, kalıcı (instance klasör adı olacak)
      "name": "Cevher Survival",           // düz metin VEYA { "tr": "…", "en": "…" }
      "description": { "tr": "…", "en": "…" },
      "address": "play.ornek.net",
      "port": 25565,                       // opsiyonel, varsayılan 25565
      "minecraftVersion": "1.21.10",
      "loader": { "type": "fabric", "version": "0.17.2" },  // vanilla|fabric|forge|neoforge|quilt; version boşsa en yenisi
      "modpackManifestUrl": "https://…/survival.mrpack",    // opsiyonel, Modrinth .mrpack
      "themeColor": "#2fd3c6"              // opsiyonel, #rrggbb
    }
  ],
  "news": [
    {
      "id": "launcher-0-1",
      "date": "2026-10-15T18:00:00Z",      // ISO 8601; akış yeniden eskiye sıralanır
      "title": { "tr": "…", "en": "…" },
      "content": { "tr": "**Markdown** içerik", "en": "…" }   // GFM: tablo, liste, alıntı
    }
  ]
}
```

Dosya açılışta doğrulanır; hatalı alan varsa `CONFIG_INVALID` hatası hangi alanın bozuk
olduğunu "Teknik ayrıntı" bölümünde gösterir (ör. `servers[1].port: geçersiz port`).

---

## Kullanıcı verisi

Electron `userData` klasöründe (Windows: `%APPDATA%\Cevherclient`, macOS:
`~/Library/Application Support/Cevherclient`, Linux: `~/.config/Cevherclient`):

| Dosya | İçerik |
|---|---|
| `settings.json` | Java yolu, RAM, çözünürlük, JVM argümanları, dil, seçili sunucu, Fabric açık/kapalı, Performans Paketi otomatik kurulum |
| `auth.json` | Giriş yapan hesap ve CevherAuth oturum belirteci (`safeStorage` ile şifreli; şifre saklanmaz) |
| `installed-versions.json` | Kurulu sürüm anahtarı → sürüm id'si ve son doğrulama zamanı |
| `minecraft/`, `instances/`, `runtimes/`, `logs/` | Oyun dosyaları (bkz. [Oyun dosyaları ve başlatma](#oyun-dosyaları-ve-başlatma)) |
| `modpacks/<sunucu-id>.mrpack` | Son indirilen modpack (çevrimdışı başlatma için) |
| `launch-history.json` | Sunucu/sürüm başına son oynanma zamanı |

---

## Modlar ve Performans Paketi

**Modlar** sayfasında:

- **Fabric mod yükleyicisini kullan** (varsayılan açık): kapatılırsa sunucu profili vanilla açılır;
  Performans Paketi ve sunucudan çıkınca otomatik kapanma o zaman çalışmaz.
- **Performans Paketi:** Fabric API, Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling,
  Krypton, ThreadTweak, Dynamic FPS; shader yükleyici Iris; oyun içi modlar Mod Menu, Shulker Box
  Tooltip, LambDynamicLights, SmoothPlayerAnimations, AppleSkin, Xaero's World Map ve Entity Texture
  Features. Modların zorunlu bağımlılıkları (Cloth Config, Player Animation Library, Text Placeholder
  API …) otomatik kurulur; bir mod başka birinin belirli sürümüne bağlıysa (Iris → Sodium) o sürüm
  alınır, aksi halde kararlı (release) sürüm beta'ya tercih edilir. Sunucu profilinin Minecraft sürümüne uygun en yeni Fabric
  sürümleri Modrinth'ten indirilir (SHA1 doğrulamalı) ve `instances/<sunucu>/mods`'a kurulur.
  Kayıt `instances/<sunucu>/.cevher/performance-pack.json`; yalnızca paketin koyduğu dosyalar
  güncellenir/silinir, modpack ve oyuncunun kendi modları etkilenmez. Bir mod o sürüm için
  yayınlanmamışsa "bu sürüm için yok" yazar, diğerleri kurulur.
- **İlk açılışta otomatik kur** (varsayılan açık): paket kurulu değilse ilk **OYNA**'da kurulur;
  Modrinth'e ulaşılamazsa oyun paketsiz açılır.
- **Mod Ara (Modrinth):** launcher'dan çıkmadan arama ve tek tıkla kurulum. Yalnızca sunucunun
  Minecraft sürümüne uygun, istemcide çalışan Fabric modları listelenir (boş aramada en popülerler).
  Kurulumda modun zorunlu bağımlılıkları da otomatik kurulur (Performans Paketinde olanlar atlanır);
  modu kaldırınca yalnızca onun için gelmiş bağımlılıklar da kaldırılır. Sunucu tarafında da gereken
  modlar "Sunucuda da kurulu olmalı" uyarısıyla gösterilir (Paper sunucusunda çalışmaz).
  Kayıt: `instances/<sunucu>/.cevher/user-mods.json`. Mod ikonları için CSP yalnızca
  `https://cdn.modrinth.com` görsellerine izin verir.

## Cihaz kalitesi (shader)

Girişten sonra, kalite henüz seçilmediyse **düşük / orta / yüksek** seçenekleri çıkar (sonradan
**Ayarlar → Cihaz Kalitesi**'nden değiştirilir). Üç seviyede de Performans Paketi, Iris, modlar,
JVM bayrakları (G1 ayarlı set), RAM (makine belleğinin ~%40'ı, 3–8 GB) ve oyun ayarları (çizim
mesafesi 10, simülasyon 8, VSync kapalı, az parçacık …; `options.txt` yalnızca ilk seçimde bir kez
yazılır) aynı ve en üst düzeyde optimize edilir. **Tek fark shader'dır:**

| Seviye | Shader (Modrinth) |
| --- | --- |
| Düşük | Super Duper Vanilla (`super-duper-vanilla`) |
| Orta | Photon Shaders (`photon-shader`) |
| Yüksek | Complementary Shaders – Unbound (`complementary-unbound`) |

Shader `instances/<sunucu>/shaderpacks`'e iner ve `config/iris.properties` ile otomatik seçilir
(gölge mesafesi 12). Seviye değişince eski seviyenin shader'ı silinir. Yazar shader'ı o Minecraft
sürümüne etiketlememişse (Super Duper Vanilla gibi) en yeni sürümü alınır. Kayıt:
`instances/<sunucu>/.cevher/quality.json`. JVM argümanları ve RAM yalnızca varsayılandayken
değiştirilir; elle ayarlanmışsa dokunulmaz.

## Mağaza, Profil ve Haberler (web sitesi)

Site LeaderOS'tur. **Site API anahtarı launcher'a hiç konmaz** (konsa herkes kendine kredi
yükleyebilirdi); launcher yalnızca kendi oturum belirteciyle CevherAuth eklentisine konuşur, eklenti
LeaderOS API'sine `X-Api-Key` ile gider (`leaderos.enabled/url/api-key`, `store.*`).

| Launcher → CevherAuth | CevherAuth → LeaderOS |
|---|---|
| `POST /api/site/profile {token}` | `GET /api/users/?username=`, `GET /api/credits?username=`, `GET /api/users/{id}/orders` |
| `POST /api/store/listing {token}` | `GET /api/store/listing?username=` |
| `POST /api/store/buy {token, productId}` | `GET /api/users/?username=`, `POST /api/store/buy` (`products[]`, `userID`) |

- **Mağaza** (menüde ve hesap menüsünde) launcher içinde açılır: kategoriler, ürün görselleri
  (`/assets/core/images/store/products/<imageID>.<imageType>`), indirim ve stok, bakiye. Satın alma
  onay penceresinden sonra **site kredisiyle** yapılır; ürün LeaderOS'un normal teslimatıyla gelir.
  Yalnızca site hesabıyla eşleşmiş (`siteSynced`: aynı ad + şifreyle sitede açılmış/girilmiş)
  launcher hesapları satın alabilir; böylece aynı adlı başkasının site kredisi harcanamaz. Hesap
  başına aynı anda tek satın alma, 10 dakikada en çok `store.max-purchases-per-10min`.
  **Kredi yükleme** (ödeme) sitede kalır: "Kredi Yükle" `links.credits`'i tarayıcıda açar.
- **Profil:** oyuncunun skin kafası, site bakiyesi, site kayıt tarihi, son 10 sipariş, oyuncu
  adı/UUID ve şifre değiştirme (CevherAuth `/api/password`).
- **Oyuncu kafası** config'teki `avatarUrl` şablonundan gelir (`{username}`, `{size}`; varsayılan
  `https://mc-heads.net/avatar/{username}/{size}`). Yüklenemezse addan türetilen piksel kafa çizilir.
  Sunucuda SkinsRestorer gibi bir skin eklentisi varsa şablonu onun kafa adresine çevirin.
- **Haberler** sitenin `/blog` sayfasından okunur (LeaderOS API'sinde blog uç noktası yok): main
  process yalnızca sitenin kendi kökünden HTML indirir, renderer `DOMParser` ile ayrıştırır ve
  yalnızca beyaz listedeki etiketleri çizer (betik/stil/iframe/form yok, bağlantılar tarayıcıda
  açılır). Site tanımsızsa, erişilemezse ya da blog boşsa config'teki `news` gösterilir. Sitenin
  kökü `links.website`, yoksa `links.store`'un kökü. 5 dakika önbellek.
- Görseller için CSP `img-src https:`'e açıldı (ürün, haber ve kafa görselleri); betik, stil ve ağ
  istekleri yine yalnızca uygulamanın kendisinden.

## Oyna → doğrudan sunucu, çıkınca oyun kapanır

Sunucu profilinde **Oyna**'ya basıldığında oyun ana menüyü göstermeden doğrudan sunucuya
bağlanır (1.20+ sürümlerde Quick Play `--quickPlayMultiplayer`, eskilerde `--server/--port`).

Oyuncu sunucudan ayrıldığında oyunun ana menüye düşmek yerine **kapanması** için launcher,
sunucunun instance'ına küçük bir istemci modu koyar: [`game-mods/cevher-session`](game-mods/cevher-session).

| Durum | Davranış |
|---|---|
| ESC → **Bağlantıyı Kes** | Oyun kapanır |
| Sunucudan atılma / bağlantı kopması / bağlanamama | Sebep ekranı gösterilir; oyuncu "Geri" deyince oyun kapanır |
| Oyunun kendi "Oyundan Çık"ı | Normal şekilde kapanır |

- Mod yalnızca launcher `-Dcevherclient.exitOnLeave=true` verdiğinde etkinleşir; aynı jar
  başka bir launcher'da ya da serbest oyunda hiçbir şey yapmaz.
- Kapanış `Minecraft#stop()` ile yapılır; ayarlar normal çıkıştaki gibi kaydedilir.
- Instance'a `mods/cevherclient-session.jar` adıyla kopyalanır; modpack senkronu bu dosyaya dokunmaz.
- **Gereksinim:** sunucu profili **Fabric** veya **Quilt** olmalı ve o Minecraft sürümü için
  `resources/mods/cevher-session-<mc>.jar` bulunmalı. Vanilla profillerde mod yüklenemez:
  oyun yine doğrudan sunucuya bağlanır ama çıkışta ana menüye döner.

Modu derlemek (JDK 21 gerekir; Gradle wrapper kendini indirir):

```bash
npm run build:mod
```

Sunucu Minecraft sürümünü değiştirirseniz modu o sürüm için yeniden derleyin
(`npm run build:mod -- --mc <sürüm>`; gerekirse `game-mods/cevher-session/gradle.properties`
içindeki `loom_version`'ı o sürümün [fabric-example-mod](https://github.com/FabricMC/fabric-example-mod)
dalıyla eşitleyin).

---

## Mod menüsü (istemci modu)

Oyun içinde **Sağ Shift** (Seçenekler → Kontroller'den değiştirilebilir) ya da ESC menüsündeki
**Mod Menüsü** düğmesiyle açılan istemci menüsü: [`game-mods/cevher-client`](game-mods/cevher-client).
Yalnızca istemci tarafında çalışır; sunucuya bir şey göndermez ve sunucudan bir şey gerektirmez.

Menü **Sword Art Online** temasındadır: açık gri yarı saydam paneller, turuncu seçim, yeşil "açık" ve ince vektör font
(renkler SAO arayüz modunun paletidir). Solda dikey bir daire düğme sütunu vardır; düğmeler tek tek belirir, üzerine
gelince turuncu yanıp dalga yayar. Seçilen düğmenin yanında gri bir pencere önce yatayda, sonra dikeyde açılır.
ESC önce pencereyi, sonra menüyü kapatır; açılış ve kapanışta hafif bir SAO çan sesi çalar.

| Düğme | Ne yapar |
|---|---|
| Modüller | Sekmeli (Tümü / HUD / Hareket / Görsel), aramalı ikon ızgarası. Hücreye tıklayınca sağda ayrıntı paneli açılır: açıklama, AÇIK/KAPALI düğmesi, boyut / arka plan / gölge / renk gibi ayarlar, HUD modülleri için "HUD'da düzenle" |
| Kozmetikler | Baş ızgarası (Cadı Şapkası), Sırt / Gövde (henüz kilitli), fareyi izleyen canlı önizleme, diğer oyuncuların kozmetiklerini açıp kapatan anahtar. Seçim `client.json`'a yazılır |
| HUD Düzenleyici | Açık HUD modüllerini sürükle, tekerlek veya +/- ile boyutlandır, oklarla it, sağ tıkla ayarlarını aç. Kenarlara ve ortaya yapışır |
| Kapat | Menüyü kapatır |

Modüller: Cevher İmzası, FPS, Ping, CPS, Koordinat, Saat, Yön, Bellek, Tuş Vuruşları, Zırh, İksir
(HUD); Otomatik Koşu (Hareket); Yakınlaştırma, varsayılan **C** tuşu (Görsel).
Varsayılan açık olanlar: İmza, FPS, Tuş Vuruşları, Yakınlaştırma.

- Ayarlar ve HUD konumları `instances/<sunucu-id>/config/cevherclient/client.json` içinde durur.
  Konumlar ekran boyutuna oranla saklanır, çözünürlük değişince modüller aynı yerde kalır.
- Instance'a `mods/cevherclient-client.jar` adıyla kopyalanır. **Gereksinim:** Fabric/Quilt profili,
  Fabric API (Performans Paketi ile gelir) ve o Minecraft sürümü için `resources/mods/cevher-client-<mc>.jar`.
- Yeni modül eklemek için `module/impl` altına `Module`, `HudModule` ya da `TextHudModule` türeten bir sınıf
  yazıp `ModuleManager.init()` içine eklemek yeterlidir; ayar paneli ve kayıt otomatik gelir. Menüdeki ikon ve
  açıklama `sao/SaoModuleInfo` içindedir; yeni ikon için `sao/SaoGlyph` enum'una vektör çizim eklenir (dış görsel yok).

**Kozmetikler:** şimdilik yalnızca baş yuvası var. Modeller Blockbench'te **Modlu Varlık** formatında çizilir; kutu UV'li küpler,
iç içe gruplar ve grup dönüşü desteklenir. Yeni kozmetik eklemek için:

1. Modeli Blockbench'te çiz; pivotu boyun (y=24) olacak şekilde yerleştir, baş kozmetiği için grubun origin'i başın tepesi (y=32) olsun.
2. `.bbmodel` dosyasını `assets/cevher_client/cosmetics/<id>/`, dokuyu `assets/cevher_client/textures/cosmetic/<id>.png` altına koy.
3. `cosmetics/cosmetics.json`'a `id`, `name`, `slot`, `model`, `texture` satırını ekle.

Dönüş açıları `(rx, -ry, rz)` olarak çevrilir; yalnızca X ekseni oyunda doğrulandı. Desteklenmeyen küp
(kutu UV'siz ya da dönük) log'a uyarı yazılır ve atlanır. Yuva `Cosmetic.Slot`'a yeni değer eklenerek genişler.

**Kozmetik eşitlemesi (diğer oyuncular seni kozmetiğinle görür).** Sunucuda
[`server-plugins/cevher-ui-bridge`](server-plugins/cevher-ui-bridge) eklentisi (1.2.0 ve üstü) kuruluysa
oyuncular seçtikleri kozmetiği `cevherclient:cosmetic` kanalıyla bildirir, sunucu doğrular ve aynı modlu
istemcilerdeki diğer oyunculara dağıtır. Eklenti yoksa ya da tek oyunculu dünyada kozmetik yalnızca kendi
karakterinde görünür. Cosmetic ekranının sağ altındaki anahtar, başkalarının kozmetiklerini görmeyi kapatır.

| Yön | Gövde (düz UTF-8 metin) |
|---|---|
| İstemci → sunucu | `head=<id>`; id boşsa kozmetik yok. Girişte ve seçim değişince gönderilir |
| Sunucu → istemci | Satır başına `<uuid> <id>`; id yerine `-` kozmetiğin kalktığını söyler. Kanalı kaydeden istemciye herkesin durumu tek mesajda, sonra her değişiklik tek satır |

- Seçimin kime ait olduğunu istemci söylemez, sunucu paketi gönderen oyuncuya yazar; başkası adına seçim yapılamaz.
- Sunucu `cosmetics.available` listesinde olmayan kimliği reddeder ve oyuncuya gerçek durumunu geri gönderir
  (istemci kendi seçimini ona göre düzeltir). `cosmetics.require-permission: true` ile her kozmetik için
  `cevherclient.cosmetic.<id>` yetkisi aranır. İki değişiklik arası en az `min-interval-ms` (varsayılan 500 ms).
- Seçim sunucuda saklanmaz; istemci her girişte yeniden gönderir.
- Yeni kozmetik eklerken kimliğini eklentinin `config.yml` dosyasındaki `cosmetics.available` listesine de yaz.

```bash
npm run build:client-mod
```

İki istemciyle denemek için (`game-mods/cevher-client`): `./gradlew runClient` ve ayrı bir terminalde
`./gradlew runClient2` (kendi `run2` klasörü, kullanıcı adı Bob). İkisini aynı sunucuya
`--args="--quickPlayMultiplayer localhost:<port>"` ile bağla; eklenti için Paper 1.21.11 test sunucusu gerekir.

Elle denemek için `game-mods/cevher-session` içinde `./gradlew runServer` ile yerel test sunucusunu
(çevrimdışı mod, 25599) açın, sonra `game-mods/cevher-client` içinde
`./gradlew runClient --args="--quickPlayMultiplayer localhost:25599"` çalıştırın.

---

## Build

```bash
npm run build:win     # Windows  → dist/Cevherclient-Setup-<sürüm>.exe (NSIS)
npm run build:mac     # macOS    → dist/Cevherclient-<sürüm>-<arch>.dmg (x64 + arm64)
npm run build:linux   # Linux    → dist/Cevherclient-<sürüm>.AppImage ve .deb
```

Notlar:

- Her platformun paketi en güvenilir şekilde **kendi işletim sisteminde** üretilir
  (macOS `.dmg` için macOS gerekir; Linux paketleri Linux/WSL'de, Windows kurulumu Windows'ta).
- Uygulama ikonu `build/icon.png` (512×512, saydam zeminde CevherCraft logosu — `src/renderer/src/assets/cevhercraft-logo.png`); `npm run build:icon` ile yeniden üretilir.
  electron-builder Windows `.ico` ve macOS `.icns` dosyalarını bundan kendisi oluşturur.
- Windows kurulumu masaüstü ve Başlat menüsü kısayolu oluşturur, "Uygulamalar" listesinden kaldırılabilir.
  Sessiz kurulum: `Cevherclient-Setup-<sürüm>.exe /S`.
- Yerleşik varsayılan config `extraResources` ile pakete `resources/mock/` altında eklenir.
- **Kod imzalama** ortam değişkenleriyle yapılır (`CSC_LINK`, `CSC_KEY_PASSWORD`; macOS noterliği için
  `APPLE_ID`, `APPLE_APP_SPECIFIC_PASSWORD`, `APPLE_TEAM_ID`). Değişkenler yoksa paketler imzasız
  üretilir (Windows SmartScreen / macOS Gatekeeper uyarısı normaldir).

Yapılandırma: [`electron-builder.yml`](electron-builder.yml).

### Sürüm yayınlama ve otomatik güncelleme

1. `package.json` sürümünü artırın (ör. `0.2.0`), etiketi gönderin: `git tag v0.2.0 && git push --tags`.
2. [`.github/workflows/release.yml`](.github/workflows/release.yml) her platformun paketini kendi işletim
   sisteminde üretir ve GitHub Releases'e yükler (imza sırları depo ayarlarından).
3. Kurulu launcher'lar açılışta ve 4 saatte bir `electron-updater` ile yeni sürümü arar, arka planda
   indirir; durum çubuğunda "kur ve yeniden başlat" çıkar (kapanırken de kurulur). Güncelleme
   kontrolü yalnızca paketlenmiş sürümde çalışır; `CEVHER_DISABLE_UPDATES=1` ile kapatılır.

### Yayın öncesi kontrol listesi

- [ ] `electron-builder.yml` → `publish.owner` / `publish.repo` kendi GitHub deponuz.
- [ ] Uzaktan config gerçek sunucu adresleriyle bir https adresinde; launcher'a varsayılan olarak
      gömmek için `resources/mock/remote-config.json`'ı güncelleyin veya `remoteConfigUrl` dağıtın.
- [x] Uygulama ikonu (`build/icon.png`).
- [ ] İmza sertifikaları (isteğe bağlı ama önerilir).

---

## Yol haritası

- **Faz 2 (tamamlandı):** Mojang version manifest'ten gerçek sürüm listesi (tarih bazlı sıralama, 26.x
  numaralandırması). Dosya bazlı devam eden, SHA1 doğrulamalı indirme. Sürüm metadata'sındaki
  `javaVersion.majorVersion`'a göre Java seçimi/indirme (26.x → Java 25). Gerçek Server List
  Ping. `--quickPlayMultiplayer` ile doğrudan sunucuya bağlanarak başlatma. Offline modda başlatma.
- **Faz 3 (tamamlandı):** Modrinth `.mrpack` modpack senkronizasyonu (SHA1, eski modları kaldırma,
  `options.txt` ve `config/` koruma), sunucu başına ayrı instance klasörü, loader otomatik kurulumu.
- **Faz 4 (değişti):** Microsoft girişi yazıldı, sonra kaldırıldı: sunucu lisanssız oyunculara açık
  olduğu için giriş yalnızca kullanıcı adıyla yapılır (bkz. [Giriş](#giriş-kullanıcı-adı)).
- **Faz 5 (tamamlandı):** `electron-updater` ile GitHub Releases üzerinden otomatik güncelleme,
  üç platform için sürüm iş akışı ve ortam değişkenleriyle kod imzalama.

## Teknoloji

Electron 44 · electron-vite 6 · Vite 8 · React 19 · TypeScript 5.9 · Tailwind CSS 4 ·
Framer Motion · Zustand · react-i18next · react-markdown + remark-gfm ·
Inter ve Rajdhani (OFL, `@fontsource` ile yerel olarak paketlenir).
