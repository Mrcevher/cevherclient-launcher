<?php $this->layout('layouts/main'); ?>
<?php
	// CevherClient tanıtım sayfası (tema: pop). Kaynak: CevherClient/website/theme/pages/download/index.php
	$cvcVersion = '4.8.10';
	$cvcSize = '136 MB';
	$cvcUrl = 'https://github.com/Mrcevher/cevherclient-launcher/releases/download/v4.8.10/Cevherclient-Setup-4.8.10.exe';
	$cvcAssets = '/client/';
?>

<style>
	.cvc { --cvc-gap: clamp(56px, 8vw, 104px); color: var(--text); }
	.cvc a { text-decoration: none; }
	.cvc-wrap { max-width: var(--maxw, 1440px); margin: 0 auto; padding: 0 5vw; }
	.cvc-eyebrow { display: inline-block; margin-bottom: 10px; font-size: .85rem; font-weight: 800; letter-spacing: 3px; text-transform: uppercase; color: #8fa8ff; }
	.cvc-h1, .cvc-h2 { margin: 0 0 16px; font-weight: 900; text-transform: uppercase; letter-spacing: 1px; line-height: 1; color: var(--text); text-shadow: 0 3px 12px rgba(0,0,0,.35); }
	.cvc-h1 { font-size: clamp(36px, 5vw, 64px); }
	.cvc-h2 { font-size: clamp(28px, 3.6vw, 44px); }
	.cvc-lead { margin: 0 0 28px; max-width: 34em; font-size: 1.05rem; line-height: 1.6; color: var(--text-dim); }
	.cvc-head { max-width: 46em; margin-bottom: 36px; }
	.cvc-head p { margin: 0; font-size: 1.02rem; line-height: 1.6; color: var(--text-dim); }

	/* ---------- Giriş ---------- */
	.cvc-hero { padding: 120px 0 var(--cvc-gap); }
	.cvc-hero__grid { display: grid; grid-template-columns: minmax(0, .95fr) minmax(0, 1.15fr); gap: clamp(32px, 5vw, 72px); align-items: center; }
	.cvc-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 14px 18px; }
	.cvc-link { display: inline-flex; align-items: center; gap: 8px; font-weight: 800; font-size: .95rem; letter-spacing: .4px; text-transform: uppercase; color: var(--text-dim); transition: color .2s; }
	.cvc-link:hover { color: var(--text); }
	.cvc-link svg { transition: transform .2s; }
	.cvc-link:hover svg { transform: translateY(2px); }
	.cvc-chips { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 26px; padding: 0; list-style: none; }
	.cvc-chips li { display: inline-flex; align-items: center; gap: 7px; height: 32px; padding: 0 12px; border-radius: 999px; background: var(--surface-2); border: 1px solid var(--card-bd); font-size: .82rem; font-weight: 700; color: var(--text-dim); }
	.cvc-chips svg { color: #8fa8ff; }

	/* Launcher penceresi */
	.cvc-stage { position: relative; perspective: 1600px; }
	.cvc-stage::before { content: ""; position: absolute; inset: 8% 6% -6%; border-radius: 40px; background: radial-gradient(60% 60% at 50% 50%, rgba(79, 115, 255, .45), transparent 70%); filter: blur(40px); z-index: -1; }
	.cvc-window {
		position: relative; aspect-ratio: 16 / 10; overflow: hidden; border-radius: 16px;
		background: #0b1022 center / cover no-repeat;
		border: 1px solid rgba(255,255,255,.1);
		box-shadow: 0 8px 0 var(--card-depth), 0 40px 80px -20px rgba(0,0,0,.75), inset 0 1px 0 rgba(255,255,255,.08);
		transform: rotateY(-7deg) rotateX(3deg);
		transform-style: preserve-3d;
		transition: transform .6s cubic-bezier(.2,.8,.2,1);
	}
	.cvc-stage:hover .cvc-window { transform: rotateY(-2deg) rotateX(1deg); }
	.cvc-window video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; opacity: 0; transition: opacity .8s; }
	.cvc-window video.is-on { opacity: 1; }
	.cvc-window::after { content: ""; position: absolute; inset: 0; background: linear-gradient(180deg, transparent 55%, rgba(0,0,0,.5)); pointer-events: none; }
	.cvc-ui { position: absolute; inset: 0; z-index: 2; font-family: Inter, 'Segoe UI', system-ui, sans-serif; }
	.cvc-ui__burger { position: absolute; top: 3.2%; left: 2%; width: 6.6%; aspect-ratio: 1; display: grid; place-items: center; border-radius: 10px; color: #c9ccd1; }
	.cvc-ui__burger svg { width: 55%; height: 55%; }
	.cvc-ui__right { position: absolute; top: 3.2%; right: 1.6%; display: flex; align-items: center; gap: 6px; }
	.cvc-pill { display: inline-flex; align-items: center; gap: 6px; height: 26px; padding: 0 10px; border-radius: 999px; background: rgba(14,15,17,.82); border: 1px solid rgba(255,255,255,.07); backdrop-filter: blur(8px); font-size: 11px; font-weight: 600; color: #d5d8dc; white-space: nowrap; }
	.cvc-pill img { width: 16px; height: 16px; border-radius: 3px; image-rendering: pixelated; }
	.cvc-dot { width: 7px; height: 7px; border-radius: 50%; background: #3ccf6e; box-shadow: 0 0 8px #3ccf6e; }
	.cvc-win-btn { width: 24px; height: 24px; display: grid; place-items: center; color: #c9ccd1; }
	.cvc-ui__info { position: absolute; left: 2%; bottom: 3%; font-size: 10px; height: 22px; color: #c3c6cb; font-weight: 500; }
	.cvc-ui__hint { position: absolute; left: 0; right: 0; bottom: 1.4%; text-align: center; font-size: 8px; font-weight: 700; letter-spacing: .12em; color: rgba(255,255,255,.45); text-transform: uppercase; }
	/* İstemcideki OYNA butonu (styles/cevher-buttons.css) */
	.cvc-play {
		position: absolute; left: 50%; bottom: 8.5%; transform: translateX(-50%);
		width: 33%; height: 11.5%; min-height: 34px;
		display: flex; align-items: center; justify-content: center; overflow: hidden; isolation: isolate;
		border-radius: 10px; border: 1px solid rgba(255,238,198,.42);
		font-family: Inter, 'Segoe UI', system-ui, sans-serif; font-weight: 800; font-size: clamp(12px, 1.35vw, 19px); letter-spacing: .14em; color: #fff6e0;
		text-shadow: 0 1px 2px rgba(60,34,4,.55), 0 0 18px rgba(255,200,110,.35);
		background: linear-gradient(180deg, rgba(14,14,18,.06), rgba(14,14,18,.18)), linear-gradient(180deg, rgba(255,226,154,.40) 0%, rgba(247,196,96,.30) 48%, rgba(206,136,32,.38) 100%);
		backdrop-filter: blur(10px) saturate(130%);
		box-shadow: inset 0 1px 0 rgba(255,255,255,.45), inset 0 0 0 1px rgba(255,232,178,.16), 0 0 0 1px rgba(90,66,22,.28), 0 8px 24px -8px rgba(4,10,30,.55), 0 0 22px rgba(255,196,92,.18);
	}
	.cvc-play::before { content: ""; position: absolute; z-index: -1; top: -20%; bottom: -20%; left: -40%; width: 34%; transform: translateX(-120%) skewX(-22deg); background: linear-gradient(90deg, rgba(255,255,255,0), rgba(255,255,255,.55), rgba(255,255,255,0)); animation: cvc-sweep 5.5s ease-in-out 1.2s infinite; }
	.cvc-play::after { content: ""; position: absolute; z-index: -1; inset: 0 0 50% 0; background: linear-gradient(180deg, rgba(255,255,255,.28), rgba(255,255,255,0)); }
	@keyframes cvc-sweep { 0%, 72% { transform: translateX(-120%) skewX(-22deg); } 100% { transform: translateX(520%) skewX(-22deg); } }
	.cvc-badge { position: absolute; right: -14px; bottom: -22px; z-index: 3; display: flex; align-items: center; gap: 10px; padding: 10px 14px; border-radius: 14px; background: var(--card-bg); border: 1px solid var(--card-bd); box-shadow: 0 5px 0 var(--card-depth), 0 14px 26px rgba(0,0,0,.45); font-size: .85rem; font-weight: 700; color: var(--text-dim); }
	.cvc-badge b { display: block; font-size: 1rem; font-weight: 900; color: var(--text); }
	.cvc-badge__icon { width: 36px; height: 36px; display: grid; place-items: center; border-radius: 10px; background: var(--acc-face); box-shadow: inset 0 3px 0 var(--acc-rim); color: #fff; }

	/* ---------- Rakamlar ---------- */
	.cvc-stats { background: var(--panel-bg); border-top: 1px solid var(--card-bd); border-bottom: 1px solid var(--card-bd); }
	.cvc-stats__grid { display: grid; grid-template-columns: repeat(4, 1fr); }
	.cvc-stat { padding: 34px 16px; text-align: center; }
	.cvc-stat + .cvc-stat { border-left: 1px solid var(--card-bd); }
	.cvc-stat__num { font-size: clamp(30px, 3.4vw, 44px); font-weight: 900; line-height: 1; color: #8fa8ff; text-shadow: 0 3px 12px rgba(0,0,0,.35); }
	.cvc-stat__label { margin-top: 8px; font-size: .82rem; font-weight: 800; letter-spacing: 1px; text-transform: uppercase; color: var(--text-dim); }

	/* ---------- Özellikler ---------- */
	.cvc-section { padding: var(--cvc-gap) 0; }
	.cvc-features { display: grid; grid-template-columns: repeat(3, 1fr); gap: 22px; }
	.cvc-feature { position: relative; padding: 24px; transition: transform .25s, box-shadow .25s; }
	.cvc-feature:hover { transform: translateY(-4px); box-shadow: 0 9px 0 var(--card-depth), 0 22px 30px rgba(0,0,0,.45); }
	.cvc-feature__icon { width: 46px; height: 46px; display: grid; place-items: center; margin-bottom: 16px; border-radius: 12px; background: var(--acc-face); border: 2px solid var(--acc-border); box-shadow: inset 0 3px 0 var(--acc-rim), 0 4px 0 var(--acc-depth); color: #fff; }
	.cvc-feature h3 { margin: 0 0 8px; font-size: 1.1rem; font-weight: 900; text-transform: uppercase; letter-spacing: .5px; color: var(--text); }
	.cvc-feature p { margin: 0; font-size: .95rem; line-height: 1.6; color: var(--text-dim); }
	.cvc-feature kbd { display: inline-block; padding: 1px 7px; border-radius: 6px; background: var(--surface-2); border: 1px solid var(--card-bd); border-bottom-width: 3px; font-family: inherit; font-size: .8em; font-weight: 800; color: var(--text); }
	.cvc-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 14px; }
	.cvc-tags span { padding: 3px 9px; border-radius: 999px; background: var(--surface-2); border: 1px solid var(--card-bd); font-size: .75rem; font-weight: 700; color: var(--text-dim); }

	/* ---------- Kurulum adımları ---------- */
	.cvc-steps-band { background: var(--panel-bg); }
	.cvc-steps { display: grid; grid-template-columns: repeat(3, 1fr); gap: 22px; counter-reset: cvc; }
	.cvc-step { position: relative; padding: 26px 24px 24px; }
	.cvc-step__num { display: inline-grid; place-items: center; width: 44px; height: 44px; margin-bottom: 16px; border-radius: 12px; background: var(--surface-2); border: 2px solid var(--card-bd); box-shadow: 0 4px 0 var(--card-depth); font-size: 1.3rem; font-weight: 900; color: #8fa8ff; }
	.cvc-step h3 { margin: 0 0 8px; font-size: 1.1rem; font-weight: 900; text-transform: uppercase; letter-spacing: .5px; }
	.cvc-step p { margin: 0; font-size: .95rem; line-height: 1.6; color: var(--text-dim); }
	.cvc-note { display: flex; gap: 12px; align-items: flex-start; margin-top: 22px; padding: 16px 18px; border-radius: 14px; background: rgba(79,115,255,.08); border: 1px solid rgba(123,151,255,.28); font-size: .92rem; line-height: 1.55; color: var(--text-dim); }
	.cvc-note svg { flex: none; margin-top: 2px; color: #8fa8ff; }
	.cvc-note b { color: var(--text); }

	/* ---------- Sistem gereksinimleri ---------- */
	.cvc-req { padding: 0; overflow: hidden; }
	.cvc-req table { width: 100%; border-collapse: collapse; }
	.cvc-req th, .cvc-req td { padding: 15px 20px; text-align: left; vertical-align: top; border-top: 1px solid var(--card-bd); }
	.cvc-req thead th { border-top: 0; font-size: .8rem; font-weight: 800; letter-spacing: 1px; text-transform: uppercase; color: var(--text-dim); background: var(--surface-2); }
	.cvc-req tbody th { width: 30%; font-weight: 700; font-size: .92rem; color: var(--text-dim); }
	.cvc-req td { font-weight: 700; font-size: .95rem; line-height: 1.55; color: var(--text); }
	.cvc-req .is-rec { background: rgba(79,115,255,.06); }
	.cvc-fps { display: inline-block; margin-left: 8px; padding: 2px 8px; border-radius: 999px; background: var(--card-bg); border: 1px solid var(--card-bd); font-size: .72rem; color: #8fa8ff; letter-spacing: .5px; }

	/* ---------- Son çağrı ---------- */
	.cvc-cta { position: relative; overflow: hidden; padding: var(--cvc-gap) 0; background: var(--panel-bg); }
	.cvc-cta__bg { position: absolute; inset: 0; background: center / cover no-repeat; opacity: .22; filter: saturate(.9); }
	.cvc-cta::after { content: ""; position: absolute; inset: 0; background: linear-gradient(90deg, var(--panel-bg) 25%, rgba(13,22,52,.55)); }
	.cvc-cta .cvc-wrap { position: relative; z-index: 1; }
	.cvc-cta p { margin: 0 0 26px; max-width: 34em; font-size: 1.05rem; line-height: 1.6; color: var(--text-dim); }

	.cvc-files { padding-bottom: var(--cvc-gap); }

	/* ---------- Görünme animasyonu ---------- */
	.cvc-reveal { opacity: 0; transform: translateY(24px); transition: opacity .7s ease, transform .7s cubic-bezier(.2,.8,.2,1); }
	.cvc-reveal.is-in { opacity: 1; transform: none; }

	@media (max-width: 1100px) {
		.cvc-features { grid-template-columns: repeat(2, 1fr); }
	}
	@media (max-width: 900px) {
		.cvc-hero { padding-top: 100px; }
		.cvc-hero__grid { grid-template-columns: 1fr; }
		.cvc-window { transform: none; }
		.cvc-badge { right: 10px; bottom: -18px; }
		.cvc-steps { grid-template-columns: 1fr; }
		.cvc-stats__grid { grid-template-columns: repeat(2, 1fr); }
		.cvc-stat:nth-child(3) { border-left: 0; }
		.cvc-stat:nth-child(n+3) { border-top: 1px solid var(--card-bd); }
	}
	@media (max-width: 640px) {
		.cvc-wrap { padding: 0 16px; }
		.cvc-features { grid-template-columns: 1fr; }
		.cvc-ui__right .cvc-pill:first-child, .cvc-ui__info, .cvc-ui__hint { display: none; }
		.cvc-req th, .cvc-req td { padding: 12px; }
		.cvc-req tbody th { width: 34%; }
	}
	@media (prefers-reduced-motion: reduce) {
		.cvc-window, .cvc-reveal, .cvc-feature { transition: none; transform: none; }
		.cvc-reveal { opacity: 1; }
		.cvc-play::before { animation: none; }
	}
</style>

<div class="cvc">

	<!-- ===== Giriş ===== -->
	<section class="cvc-hero">
		<div class="cvc-wrap cvc-hero__grid">
			<div class="cvc-reveal">
				<span class="cvc-eyebrow">CevherClient</span>
				<h1 class="cvc-h1">CevherCraft'a tek tıkla bağlan</h1>
				<p class="cvc-lead">Sunucumuz için yaptığımız Minecraft launcher'ı. Performans modları, shader ve mod menüsü hazır gelir; <b>OYNA</b>'ya basarsın, ana menüyü görmeden doğrudan sunucudasın.</p>
				<div class="cvc-actions">
					<a href="<?php echo $cvcUrl; ?>" class="btn3d t-accent lg" data-cvc-dl>
						<span class="btn3d__face">
							<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v12"/><path d="m7 10 5 5 5-5"/><path d="M5 21h14"/></svg>
							İndir
						</span>
					</a>
					<a href="#cvc-features" class="cvc-link">
						Neler var?
						<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg>
					</a>
				</div>
				<ul class="cvc-chips">
					<li>
						<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M3 5.1 10.4 4v7.1H3V5.1Zm0 13.8 7.4 1.1v-7H3v5.9ZM11.3 3.9 21 2.5v8.6h-9.7V3.9Zm0 16.2L21 21.5v-8.5h-9.7v7.1Z"/></svg>
						Windows 10 / 11
					</li>
					<li data-cvc-ver>v<?php echo $cvcVersion; ?></li>
					<li data-cvc-size><?php echo $cvcSize; ?></li>
					<li>Ücretsiz</li>
				</ul>
			</div>

			<div class="cvc-stage cvc-reveal" aria-hidden="true">
				<div class="cvc-window" style="background-image: url('<?php echo $cvcAssets; ?>bg-poster.webp')">
					<video muted loop playsinline disablepictureinpicture preload="none" data-parts="bg-1.mp4,bg-2.mp4,bg-3.mp4"></video>
					<div class="cvc-ui">
						<span class="cvc-ui__burger"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 7h16M4 12h16M4 17h16"/></svg></span>
						<div class="cvc-ui__right">
							<span class="cvc-pill"><img src="https://minotar.net/helm/<?php echo auth()->check() ? rawurlencode(auth()->user()->displayName()) : 'Steve'; ?>/32.png" alt="" loading="lazy"><?php echo auth()->check() ? htmlspecialchars(auth()->user()->displayName(), ENT_QUOTES) : 'Oyuncu'; ?></span>
							<span class="cvc-pill"><span class="cvc-dot"></span>CevherCraft</span>
							<span class="cvc-win-btn"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M5 12h14"/></svg></span>
							<span class="cvc-win-btn"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M6 6l12 12M18 6 6 18"/></svg></span>
						</div>
						<span class="cvc-play">OYNA</span>
						<span class="cvc-pill cvc-ui__info">Minecraft 1.21.11 · CevherClient v<span data-cvc-ver-raw><?php echo $cvcVersion; ?></span></span>
						<span class="cvc-ui__hint">Sistem gereksinimleri</span>
					</div>
				</div>
				<div class="cvc-badge">
					<span class="cvc-badge__icon">
						<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a9 9 0 1 1-3-6.7"/><path d="M21 4v5h-5"/></svg>
					</span>
					<span><b>Hep güncel</b>Kendini otomatik günceller</span>
				</div>
			</div>
		</div>
	</section>

	<!-- ===== Rakamlar ===== -->
	<section class="cvc-stats">
		<div class="cvc-wrap cvc-stats__grid">
			<div class="cvc-stat cvc-reveal"><div class="cvc-stat__num">1</div><div class="cvc-stat__label">Tıkla bağlan</div></div>
			<div class="cvc-stat cvc-reveal"><div class="cvc-stat__num">17</div><div class="cvc-stat__label">Hazır mod</div></div>
			<div class="cvc-stat cvc-reveal"><div class="cvc-stat__num">3</div><div class="cvc-stat__label">Shader seviyesi</div></div>
			<div class="cvc-stat cvc-reveal"><div class="cvc-stat__num">6</div><div class="cvc-stat__label">Dil desteği</div></div>
		</div>
	</section>

	<!-- ===== Özellikler ===== -->
	<section class="cvc-section" id="cvc-features">
		<div class="cvc-wrap">
			<div class="cvc-head cvc-reveal">
				<span class="cvc-eyebrow">Özellikler</span>
				<h2 class="cvc-h2">Kur, aç, oyna</h2>
				<p>Mod kurmakla, ayar yapmakla uğraşma. CevherClient sunucuda iyi bir oyun için gereken her şeyi kendi halleder.</p>
			</div>
			<div class="cvc-features">
				<article class="card3d cvc-feature cvc-reveal">
					<div class="cvc-feature__icon"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M13 2 3 14h9l-1 8 10-12h-9l1-8z"/></svg></div>
					<h3>Doğrudan sunucuya</h3>
					<p>OYNA'ya bastığında oyun ana menüyü göstermeden CevherCraft'a bağlanır. Sunucudan çıkınca oyun kendiliğinden kapanır.</p>
				</article>
				<article class="card3d cvc-feature cvc-reveal">
					<div class="cvc-feature__icon"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="m12 14 4-4"/><path d="M3.34 19a10 10 0 1 1 17.32 0"/></svg></div>
					<h3>Performans paketi</h3>
					<p>FPS'i artıran modlar ilk açılışta otomatik kurulur ve sunucunun sürümüyle uyumlu tutulur.</p>
					<div class="cvc-tags"><span>Sodium</span><span>Lithium</span><span>FerriteCore</span><span>EntityCulling</span><span>Dynamic FPS</span></div>
				</article>
				<article class="card3d cvc-feature cvc-reveal">
					<div class="cvc-feature__icon"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg></div>
					<h3>Shader ve kalite</h3>
					<p>Düşük, orta ya da yüksek: bilgisayarına uygun shader seçilir. RAM ve Java ayarları da senin yerine yapılır.</p>
					<div class="cvc-tags"><span>Super Duper Vanilla</span><span>Photon</span><span>Complementary</span></div>
				</article>
				<article class="card3d cvc-feature cvc-reveal">
					<div class="cvc-feature__icon"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="14" rx="2"/><path d="M7 9h4M7 13h7"/><circle cx="17" cy="9" r="1"/></svg></div>
					<h3>Mod menüsü</h3>
					<p>Oyunda <kbd>Sağ Shift</kbd> ile açılır. FPS, ping, CPS, koordinat ve tuş vuruşları gibi HUD modüllerini aç, ekranda sürükleyip yerleştir, <kbd>C</kbd> ile yakınlaştır.</p>
				</article>
				<article class="card3d cvc-feature cvc-reveal">
					<div class="cvc-feature__icon"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 18h18"/><path d="M6 18 12 3l6 15"/><path d="M8.5 12h7"/></svg></div>
					<h3>Kozmetikler</h3>
					<p>Karakterine Cadı Şapkası tak. CevherClient kullanan diğer oyuncular da seni kozmetiğinle görür.</p>
				</article>
				<article class="card3d cvc-feature cvc-reveal">
					<div class="cvc-feature__icon"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg></div>
					<h3>Mod ara, tek tıkla kur</h3>
					<p>Sunucunun sürümüne uygun modları launcher'dan çıkmadan Modrinth'te ara; gereken ek modlarla birlikte kurulur.</p>
				</article>
			</div>
		</div>
	</section>

	<!-- ===== Kurulum ===== -->
	<section class="cvc-section cvc-steps-band">
		<div class="cvc-wrap">
			<div class="cvc-head cvc-reveal">
				<span class="cvc-eyebrow">Kurulum</span>
				<h2 class="cvc-h2">Üç adımda oyundasın</h2>
			</div>
			<div class="cvc-steps">
				<div class="card3d cvc-step cvc-reveal">
					<span class="cvc-step__num">1</span>
					<h3>İndir</h3>
					<p>Yukarıdaki <b>İndir</b> butonuyla kurulum dosyasını (<span data-cvc-file>Cevherclient-Setup-<?php echo $cvcVersion; ?>.exe</span>) bilgisayarına indir.</p>
				</div>
				<div class="card3d cvc-step cvc-reveal">
					<span class="cvc-step__num">2</span>
					<h3>Kur</h3>
					<p>Dosyayı çalıştır ve kurulumu bitir. Masaüstüne ve Başlat menüsüne CevherClient kısayolu eklenir.</p>
				</div>
				<div class="card3d cvc-step cvc-reveal">
					<span class="cvc-step__num">3</span>
					<h3>Giriş yap, OYNA</h3>
					<p>Kullanıcı adınla giriş yap, kalite seviyeni seç ve <b>OYNA</b>'ya bas. İlk açılışta gerekli dosyalar iner.</p>
				</div>
			</div>
			<div class="cvc-note cvc-reveal">
				<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="M12 8v4M12 16h.01"/></svg>
				<span>Windows <b>"Bilgisayarınız korundu"</b> uyarısı verirse <b>Ek bilgi</b>'ye, ardından <b>Yine de çalıştır</b>'a tıkla. Bu uyarı, kurulum dosyası henüz bir yayıncı sertifikasıyla imzalanmadığı için çıkar.</span>
			</div>
		</div>
	</section>

	<!-- ===== Sistem gereksinimleri ===== -->
	<section class="cvc-section" id="cvc-requirements">
		<div class="cvc-wrap">
			<div class="cvc-head cvc-reveal">
				<span class="cvc-eyebrow">Sistem</span>
				<h2 class="cvc-h2">Sistem gereksinimleri</h2>
			</div>
			<div class="card3d cvc-req cvc-reveal">
				<table>
					<thead>
						<tr><th></th><th>Minimum <span class="cvc-fps">30 FPS</span></th><th class="is-rec">Önerilen <span class="cvc-fps">60 FPS</span></th></tr>
					</thead>
					<tbody>
						<tr><th>İşletim sistemi</th><td>Windows 10 (x64)</td><td class="is-rec">Windows 11 (x64)</td></tr>
						<tr><th>İşlemci</th><td>Intel Core i5-4460<br>AMD Ryzen 3 1200</td><td class="is-rec">Intel Core i5-10400<br>AMD Ryzen 5 3600</td></tr>
						<tr><th>Ekran kartı</th><td>GTX 750 Ti · RX 460<br>Intel UHD 630</td><td class="is-rec">GTX 1660 Super<br>RX 5600 XT</td></tr>
						<tr><th>VRAM</th><td>2 GB</td><td class="is-rec">6 GB</td></tr>
						<tr><th>RAM</th><td>8 GB</td><td class="is-rec">16 GB</td></tr>
						<tr><th>Boş alan</th><td>8 GB HDD</td><td class="is-rec">12 GB SSD</td></tr>
						<tr><th>Grafik ayarı</th><td>Düşük · 1280×720</td><td class="is-rec">Yüksek · 1920×1080</td></tr>
					</tbody>
				</table>
			</div>
		</div>
	</section>

	<!-- ===== Son çağrı ===== -->
	<section class="cvc-cta">
		<div class="cvc-cta__bg" style="background-image: url('<?php echo $cvcAssets; ?>bg-poster.webp')"></div>
		<div class="cvc-wrap cvc-reveal">
			<span class="cvc-eyebrow">Hazır mısın?</span>
			<h2 class="cvc-h2">Sunucuda görüşelim</h2>
			<p>Bir kez kur, gerisini CevherClient halleder. Yeni sürüm çıktığında kendini günceller.</p>
			<a href="<?php echo $cvcUrl; ?>" class="btn3d t-accent lg" data-cvc-dl>
				<span class="btn3d__face">
					<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v12"/><path d="m7 10 5 5 5-5"/><path d="M5 21h14"/></svg>
					CevherClient'ı indir
				</span>
			</a>
		</div>
	</section>

	<?php if (isset($files) && count($files) > 0): ?>
		<!-- ===== Panelden eklenen diğer dosyalar ===== -->
		<section class="cvc-section cvc-files">
			<div class="cvc-wrap">
				<div class="card3d">
					<h2 class="card3d__title"><?php e__('Download'); ?></h2>
					<?php foreach ($files as $file): ?>
						<p><a href="<?php echo url('download.show', ['id' => $file["id"], 'slug' => $file["slug"]]) ?>"><?php echo $file["name"]; ?></a></p>
					<?php endforeach; ?>
				</div>
			</div>
		</section>
	<?php endif; ?>
</div>

<script>
	(function () {
		'use strict';
		var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

		// Görünürken beliren bölümler
		var items = document.querySelectorAll('.cvc-reveal');
		if ('IntersectionObserver' in window && !reduce) {
			var io = new IntersectionObserver(function (entries) {
				entries.forEach(function (e) {
					if (e.isIntersecting) { e.target.classList.add('is-in'); io.unobserve(e.target); }
				});
			}, { threshold: 0.12 });
			items.forEach(function (el, i) { el.style.transitionDelay = (i % 3) * 80 + 'ms'; io.observe(el); });
		} else {
			items.forEach(function (el) { el.classList.add('is-in'); });
		}

		// En güncel sürüm: GitHub Releases
		fetch('https://api.github.com/repos/Mrcevher/cevherclient-launcher/releases/latest', { headers: { Accept: 'application/vnd.github+json' } })
			.then(function (r) { return r.ok ? r.json() : null; })
			.then(function (rel) {
				if (!rel || !rel.assets) return;
				var exe = rel.assets.filter(function (a) { return /\.exe$/i.test(a.name); })[0];
				if (!exe) return;
				var ver = String(rel.tag_name || '').replace(/^v/, '');
				document.querySelectorAll('[data-cvc-dl]').forEach(function (a) { a.href = exe.browser_download_url; });
				document.querySelectorAll('[data-cvc-ver]').forEach(function (el) { el.textContent = 'v' + ver; });
				document.querySelectorAll('[data-cvc-ver-raw]').forEach(function (el) { el.textContent = ver; });
				document.querySelectorAll('[data-cvc-size]').forEach(function (el) { el.textContent = Math.round(exe.size / 1048576) + ' MB'; });
				document.querySelectorAll('[data-cvc-file]').forEach(function (el) { el.textContent = exe.name; });
			})
			.catch(function () {});

		// Pencere içindeki istemci videosu: görünür olunca parçaları indir, birleştir, oynat
		var video = document.querySelector('.cvc-window video');
		var conn = navigator.connection || {};
		if (video && !reduce && !conn.saveData && window.matchMedia('(min-width: 641px)').matches && 'IntersectionObserver' in window) {
			var base = '<?php echo $cvcAssets; ?>';
			var vio = new IntersectionObserver(function (entries) {
				if (!entries[0].isIntersecting) return;
				vio.disconnect();
				Promise.all(video.getAttribute('data-parts').split(',').map(function (p) {
					return fetch(base + p).then(function (r) { if (!r.ok) throw new Error(p); return r.arrayBuffer(); });
				})).then(function (bufs) {
					video.src = URL.createObjectURL(new Blob(bufs, { type: 'video/mp4' }));
					video.addEventListener('playing', function () { video.classList.add('is-on'); }, { once: true });
					var p = video.play(); if (p && p.catch) p.catch(function () {});
				}).catch(function () {});
			}, { rootMargin: '200px' });
			vio.observe(video);
		}
	})();
</script>
