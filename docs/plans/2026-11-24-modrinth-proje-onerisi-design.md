# Onaylı hilecilerden Modrinth proje yasağı önerisi (CevherAuth 1.10.0)

Tarih: 2026-11-24 · Durum: onay bekliyor

## Amaç ve neden

Mod denetimi (ModDogrulama) bilinmeyen jar'ı reddediyor ama Modrinth'te yayımlanmış her projeyi kabul ediyor;
`mod-dogrulama.yasakli-projeler` boş. Hileciler Modrinth'te "masum" duran modlarla (ör. hile yardımcıları,
freecam/xray türevleri) giriyor. Arkadaşın önerdiği "tespit edilen hilelerden kendini geliştiren koruma"nın güvenli
hali: onaylı yasaklardaki mod listelerinden **yasaklanacak Modrinth projesi önerisi** çıkarmak; yetkili onaylamadan
hiçbir şey uygulanmaz. Kod ya da dışarıdan veri çekilip çekirdeğe işlenmez; sistem yalnızca bir proje kimliği listesini
günceller.

## Hedefler

- Lobi her oyuncunun son Modrinth proje listesini saklar (normal oyuncu taban çizgisi).
- Kalıcı yasakta yasaklanan kişinin o anki Modrinth projeleri yasak kaydına eklenir (CevherKoruma "Hileydi → Onayla"
  ve elle `/cevherauth yasakla` dahil).
- Eşikleri geçen projeler için yetkililere tıklanabilir öneri: [Yasakla] [Yoksay]; etkilenecek masum oyuncu sayısı
  gösterilir.
- Onaylanan proje lobide yasaklı projelere eklenir; o modla gelen oyuncu sonraki girişte reddedilir. Geri alınabilir.

## Hedef olmayanlar

- Geçmiş yasaklara geriye dönük mod listesi (veri yok; bugünden itibaren toplanır).
- Hash kara listesi (bilinmeyen jar zaten reddediliyor; gereksiz).
- J ekranına (cevher-client modu) yeni sekme — öneriler sohbet + komutla yönetilir.
- Launcher/oturum modunu sağlamlaştırma (ayrı konu).

## Kullanıcı kararları

- Yol B: her şey CevherAuth'ta (veri ve uygulama aynı yerde, CevherKoruma'ya dokunulmaz).
- **Kullanıcının istemcisine gömülü modlar hariç tutulur:** hash'i `izinli-hashler`, `jar-klasorleri` (launcher'ın
  kendi modları) veya `modpack-dosyalari` (.mrpack) içinde olan jar ne taban çizgisine ne yasak kaydına yazılır, hiçbir
  zaman aday olmaz. Yalnızca hash'i Modrinth önbelleğinden çözülen, izinli listede olmayan jar'lar sayılır.

## Mimari (lobi = ana kayıt)

Mod denetimi yalnızca lobide yapılıyor (kapı rolünde `ModDogrulama` klasör taramaz, denetim yapmaz); yasakların ana
kaydı da lobide. Bu yüzden:

1. **Taban çizgisi (lobi):** `ModDogrulama.kontrol()` liste kabul edildiğinde (reddedilmeyen girişte) oyuncunun
   Modrinth projelerini çıkarır: her girdi için hash izinli değilse ve `modrinthCache`'te varsa → proje kimliği (+ mod
   kimliği gösterim için). Yeni sınıf `ModIstatistik` bunu `mod-listeleri.json`'a yazar:
   `ad(küçük) → {zaman, projeler: {projeId: modId}}`. Sınırlar: oyuncu başına en fazla 200 proje, toplam 20 000
   oyuncu (en eski düşer), 90 günden eski girdiler taban çizgisinden sayılmaz. Yazma: kirli bayrağı + 60 sn'de bir
   eşzamansız, geçici dosya + atomik taşıma.
2. **Yasak kaydı (lobi):** `KaliciYasak.yasakla()` lobide çalışırken (yerel yasak ya da kapıdan gelen
   `/api/internal/ban`) yasaklanan adın `ModIstatistik`'teki son projelerini `Kayit.modProjeleri`
   (`Map<String,String>` projeId→modId) alanına ekler; kayıt yeni bir hesapla genişlerse o hesabın projeleri de
   birleştirilir. Alan, mevcut JSON eşitlemesiyle kapılara da gider (yetkili görür).
3. **Öneri hesabı (lobi):** her yeni yasak kaydında, kayıttaki her proje için:
   - `hileci` = bu projeyi içeren **farklı yasak kaydı** sayısı (bir kayıt = bir kişi, yan hesaplar dahil),
   - `taban` = yasaklı olmayan ve son 90 günde görülen oyuncu sayısı, `normal` = bunlardan projeyi kullananlar.
   - Aday koşulu: `hileci ≥ min-hileci (2)` ve `taban ≥ min-taban (20)` ve `normal / taban ≤ max-oran (0.05)` ve proje
     zaten yasaklı/yoksayılmış değil.
   - Aday `proje-onerileri.json`'a `{proje, modId, hileci, normal, taban, kayitlar[≤10 id], durum: BEKLIYOR, zaman}`
     olarak yazılır (zaten BEKLIYOR ise sayılar güncellenir).
4. **Bildirim:** lobideki yetkililere doğrudan; kapılara `/api/internal/yasaklar` yanıtına `projeOnerileri`
   (BEKLIYOR olanlar) alanı eklenir. Kapı 30 sn'lik çekişte yeni (daha önce duyurulmamış proje+sayı) öneriyi
   `cevherauth.yasak.ustyetkili` veya `cevherauth.yasak.kurucu` iznine sahip çevrimiçi yetkililere sohbette gösterir:
   `[Yasak] Öneri: "<modId>" (Modrinth <proje>) 3 yasaklı hilecide var, normal oyuncuların %1'inde (2/180) →
   [Yasakla] [Yoksay]`. Metin `Component.text` ile kurulur (mod kimliği oyuncudan gelir; § ve kontrol karakteri atılır,
   en fazla 64 karakter).
5. **Karar komutları** (her iki sunucuda; kapıda mevcut `yasak-islem` iç ucuna yeni `islem` değerleriyle iletilir,
   rol kapıda izinden belirlenir — mevcut onay akışıyla aynı güven modeli):
   - `/cevherauth projeler` — bekleyen öneriler + yasaklı projeler.
   - `/cevherauth projeyasakla <proje>` — `yasakli-projeler.json`'a ekler (kim, ne zaman, öneri sayıları), öneri
     `YASAKLANDI`.
   - `/cevherauth projeyoksay <proje>` — öneri `YOKSAYILDI`; aynı proje bir daha önerilmez (yasaklı hileci sayısı
     yoksayma anındakinin 2 katına çıkarsa yeniden önerilir).
   - `/cevherauth projeizin <proje>` — yasağı kaldırır.
   - İzin: `cevherauth.yasak.ustyetkili` veya `cevherauth.yasak.kurucu`; tek onay yeterli (geri alınabilir).
6. **Uygulama (lobi):** `ModDogrulama` yasaklı proje kümesi = config `yasakli-projeler` ∪ `yasakli-projeler.json`.
   Mevcut kod zaten `yasakliProje` ile reddediyor ("İzin verilmeyen mod yüklü: <mod>").

## Yapılandırma (config.yml, mod-dogrulama altında)

```yaml
proje-onerisi:
  acik: true
  min-hileci: 2       # projeyi içeren en az bu kadar farklı yasak kaydı
  min-taban: 20       # oranın anlamlı olması için en az bu kadar yasaksız oyuncu listesi
  max-oran: 0.05      # yasaksız oyuncuların en fazla bu kadarında olmalı
  taban-gun: 90       # taban çizgisine sayılan en eski görülme
```

## Güvenlik (güven sınırları, saldırgan modeli, önlemler)

- **Güven sınırı 1 — istemci → lobi:** mod listesi oyuncunun bilgisayarından gelir, imzalı ama içeriği yalan olabilir.
  Saldırgan (hile geliştiricisi) masum bir projeyi "zehirlemek" için yan hesaplarla o modu taşıyıp yasaklanabilir.
  Önlemler: (a) nadirlik şartı — taban çizgisinin %5'inden yaygın proje asla aday olmaz, popüler modlar korunur;
  (b) bir yasak kaydı (yan hesaplar dahil, cihaz izi/IP ile birleşen) tek sayılır; (c) yetkili onayı zorunlu, öneri
  masum kullanıcı sayısını gösterir; (d) geri alınabilir. Saldırgan önerileri bastırmak için listesinden modu
  silebilir — bu sistemin sınırıdır (istemci verisi), öneri yalnızca yardımcıdır.
- **Güven sınırı 2 — kapı ↔ lobi iç API:** mevcut `network.secret` doğrulaması (`internalAuthorized`) kullanılır;
  yeni uç açılmaz, `yasak-islem` ve `yasaklar` genişletilir. Rol kapıda izinden belirlenir ve yalnızca `ROL_KURUCU` /
  `ROL_UST` kabul edilir (mevcut kodla aynı).
- **Oyuncu kaynaklı metin:** mod kimliği ve proje kimliği sohbete/JSON'a konmadan önce `[a-z0-9_.-]` ile sınırlanır,
  64 karakterde kesilir; proje kimliği Modrinth önbelleğinden gelir (biçim `[A-Za-z0-9]{8}` doğrulanır).
- **Kaynak tüketimi:** dosya ve bellek sınırları (yukarıda); öneri hesabı yalnızca yasak anında, en fazla kayıttaki 200
  proje × kayıt sayısı; ana iş parçacığını bekletmeyen yazma.
- **Gizlilik:** saklanan veri oyuncu adı + mod proje kimlikleri; IP saklanmaz.

## Yaklaşımlar (değerlendirilen)

- **B (seçilen):** tümü CevherAuth'ta, lobi ana kayıt. Veri, karar ve uygulama aynı yerde; elle yasaklar da sayılır.
- A: istatistik CevherKoruma'da — iki eklenti değişir, CevherKoruma lobide yok, taban çizgisi zayıf.
- C: istatistiksiz, J kanıtında elle seçme — kendi kendine öğrenmez.

## Benim kararlarım (işaretli)

- Eşikler (2 / 20 / %5 / 90 gün), tek onay, yoksayma yeniden öneri kuralı (2 kat).
- Yasaklı projeler config yerine `yasakli-projeler.json`'da (config'i programla yazmamak, YAML yorumlarını bozmamak
  için); config listesi de geçerli kalır.
- Sürüm: CevherAuth 1.10.0 (lobi + skyblock). Mod/launcher değişmez.

## Kurucuya kalanlar

- Lobide modu yeni yasaklanan çevrimiçi oyuncular atılmaz; sonraki girişte reddedilir.
- Test: lobide yan hesap + Modrinth modu ile iki yasak → öneri gelmeli; `/cevherauth projeyasakla` → aynı modla giriş
  reddedilmeli; `projeizin` → giriş geçmeli.
