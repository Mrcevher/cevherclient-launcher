<?php $this->layout('layouts/main'); ?>
<?php
	// Yasak itiraz sayfası (tema: pop). Kaynak: CevherClient/website/theme/pages/ban-itiraz/index.php
	// Form doğrudan CevherAuth'a (auth.cevhercraft.com) gider; hesap adı + şifre ile yalnızca hesabın sahibi itiraz edebilir.
	$itirazApi = 'https://auth.cevhercraft.com/api/yasak/itiraz';
?>

<style>
	.cvb { color: var(--text); padding: 120px 0 clamp(56px, 8vw, 104px); }
	.cvb-wrap { max-width: 720px; margin: 0 auto; padding: 0 5vw; }
	.cvb-eyebrow { display: inline-block; margin-bottom: 10px; font-size: .85rem; font-weight: 800; letter-spacing: 3px; text-transform: uppercase; color: #ff8a8e; }
	.cvb-h1 { margin: 0 0 14px; font-size: clamp(32px, 4.4vw, 52px); font-weight: 900; text-transform: uppercase; letter-spacing: 1px; line-height: 1; }
	.cvb-lead { margin: 0 0 28px; font-size: 1.02rem; line-height: 1.6; color: var(--text-dim); }
	.cvb-card { padding: clamp(20px, 3vw, 32px); border-radius: 16px; background: var(--card-bg); border: 1px solid var(--card-bd); box-shadow: 0 6px 0 var(--card-depth), 0 20px 40px rgba(0,0,0,.35); }
	.cvb-field { display: grid; gap: 7px; margin-bottom: 18px; }
	.cvb-field label { font-size: .82rem; font-weight: 800; letter-spacing: 1px; text-transform: uppercase; color: var(--text-dim); }
	.cvb-field input, .cvb-field textarea { width: 100%; box-sizing: border-box; padding: 12px 14px; border-radius: 10px; background: var(--surface-2); border: 1px solid var(--card-bd); color: var(--text); font: inherit; font-size: 1rem; }
	.cvb-field textarea { min-height: 150px; resize: vertical; line-height: 1.5; }
	.cvb-field input:focus, .cvb-field textarea:focus { outline: 2px solid #8fa8ff; outline-offset: 1px; }
	.cvb-hint { font-size: .8rem; color: var(--text-dim); }
	.cvb-row { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; }
	.cvb-btn { display: inline-flex; align-items: center; justify-content: center; height: 46px; padding: 0 22px; border-radius: 12px; border: 0; cursor: pointer; font: inherit; font-weight: 900; letter-spacing: .6px; text-transform: uppercase; color: #fff; background: #e5484d; box-shadow: 0 4px 0 #8f1d22; transition: transform .1s, filter .2s; }
	.cvb-btn:hover { filter: brightness(1.08); }
	.cvb-btn:active { transform: translateY(2px); box-shadow: 0 2px 0 #8f1d22; }
	.cvb-btn[disabled] { opacity: .6; cursor: wait; }
	.cvb-btn--ghost { background: var(--surface-2); color: var(--text); box-shadow: 0 4px 0 var(--card-depth); border: 1px solid var(--card-bd); }
	.cvb-msg { display: none; margin-top: 18px; padding: 14px 16px; border-radius: 12px; font-weight: 600; line-height: 1.5; }
	.cvb-msg.is-ok { display: block; background: rgba(74, 222, 128, .12); border: 1px solid rgba(74, 222, 128, .4); color: #a7f3c0; }
	.cvb-msg.is-err { display: block; background: rgba(229, 72, 77, .12); border: 1px solid rgba(229, 72, 77, .45); color: #ffb3b6; }
	.cvb-status { display: none; margin-top: 22px; }
	.cvb-status.is-on { display: block; }
	.cvb-status h3 { margin: 0 0 10px; font-size: 1rem; text-transform: uppercase; letter-spacing: 1px; }
	.cvb-status ul { margin: 0; padding: 0; list-style: none; display: grid; gap: 8px; }
	.cvb-status li { padding: 10px 12px; border-radius: 10px; background: var(--surface-2); border: 1px solid var(--card-bd); font-size: .9rem; color: var(--text-dim); }
	.cvb-tag { display: inline-block; margin-right: 8px; padding: 2px 8px; border-radius: 999px; font-size: .72rem; font-weight: 800; letter-spacing: .5px; }
	.cvb-tag--BEKLIYOR { background: rgba(255, 176, 32, .15); color: #ffcf7a; }
	.cvb-tag--REDDEDILDI { background: rgba(229, 72, 77, .15); color: #ffb3b6; }
	.cvb-tag--KABUL { background: rgba(74, 222, 128, .15); color: #a7f3c0; }
	.cvb-rules { margin: 26px 0 0; padding-left: 18px; color: var(--text-dim); font-size: .9rem; line-height: 1.6; }
</style>

<section class="cvb">
	<div class="cvb-wrap">
		<span class="cvb-eyebrow">Yasak itirazı</span>
		<h1 class="cvb-h1">Yasağına itiraz et</h1>
		<p class="cvb-lead">Hesabın hile nedeniyle kalıcı olarak yasaklandıysa ve bunun bir hata olduğunu düşünüyorsan buradan itiraz gönderebilirsin. Talebin yetkililerin ekranına düşer; yasak, bir kurucu ve bir üst yetkili onaylarsa kaldırılır.</p>

		<form class="cvb-card" id="cvbForm" autocomplete="on" novalidate>
			<div class="cvb-field">
				<label for="cvbUser">Oyun içi kullanıcı adın</label>
				<input id="cvbUser" name="username" type="text" minlength="3" maxlength="16" pattern="[A-Za-z0-9_]{3,16}" autocomplete="username" required>
			</div>
			<div class="cvb-field">
				<label for="cvbPass">Şifren</label>
				<input id="cvbPass" name="password" type="password" maxlength="128" autocomplete="current-password" required>
				<span class="cvb-hint">Launcher'da veya sitede kullandığın şifre. Yalnızca hesabın sahibi itiraz edebilsin diye sorulur.</span>
			</div>
			<div class="cvb-field">
				<label for="cvbMsg">Neden yasağın kaldırılmalı?</label>
				<textarea id="cvbMsg" name="mesaj" minlength="10" maxlength="1000" required placeholder="Ne olduğunu kendi cümlelerinle anlat. Ör. o saatte ne yapıyordun, hangi istemciyi kullanıyordun..."></textarea>
				<span class="cvb-hint"><span id="cvbCount">0</span>/1000</span>
			</div>
			<div class="cvb-row">
				<button class="cvb-btn" type="submit" id="cvbSend">İtirazı gönder</button>
				<button class="cvb-btn cvb-btn--ghost" type="button" id="cvbCheck">Durumumu göster</button>
			</div>
			<div class="cvb-msg" id="cvbMsgBox" role="status" aria-live="polite"></div>
			<div class="cvb-status" id="cvbStatus">
				<h3>Yasak bilgin</h3>
				<ul id="cvbStatusList"></ul>
			</div>
		</form>

		<ul class="cvb-rules">
			<li>Aynı anda yalnızca bir bekleyen itirazın olabilir; 24 saatte en fazla iki itiraz gönderebilirsin.</li>
			<li>Reddedilen itirazın nedenini "Durumumu göster" ile görebilirsin.</li>
			<li>Başka bir hesapla oyuna girmeye çalışmak yasağı uzatır: aynı bilgisayardan açılan hesaplar da yasaklanır.</li>
		</ul>
	</div>
</section>

<script>
(function () {
	var API = <?= json_encode($itirazApi) ?>;
	var form = document.getElementById('cvbForm');
	var user = document.getElementById('cvbUser');
	var pass = document.getElementById('cvbPass');
	var msg = document.getElementById('cvbMsg');
	var count = document.getElementById('cvbCount');
	var box = document.getElementById('cvbMsgBox');
	var send = document.getElementById('cvbSend');
	var check = document.getElementById('cvbCheck');
	var status = document.getElementById('cvbStatus');
	var list = document.getElementById('cvbStatusList');
	var ERR = {
		WRONG_CREDENTIALS: 'Kullanıcı adı veya şifre yanlış.',
		YASAK_YOK: 'Bu hesap için kalıcı bir yasak bulunamadı.',
		ZATEN_BEKLIYOR: 'Zaten bekleyen bir itirazın var. Yetkililerin yanıtını bekle.',
		COK_SIK: 'Son 24 saatte yeterince itiraz gönderdin. Daha sonra tekrar dene.',
		MESSAGE_TOO_SHORT: 'Lütfen en az 10 karakterlik bir açıklama yaz.',
		TOO_MANY_ATTEMPTS: 'Çok fazla deneme yapıldı. Birkaç dakika sonra tekrar dene.',
		BAD_REQUEST: 'Bilgiler eksik veya hatalı.'
	};
	var DURUM = { BEKLIYOR: 'Bekliyor', REDDEDILDI: 'Reddedildi', KABUL: 'Kabul edildi' };

	msg.addEventListener('input', function () { count.textContent = msg.value.length; });

	function goster(tur, metin) {
		box.className = 'cvb-msg ' + (tur === 'ok' ? 'is-ok' : 'is-err');
		box.textContent = metin;
	}

	function durumYaz(d) {
		list.innerHTML = '';
		if (!d || !d.yasakli) { status.className = 'cvb-status'; return; }
		var li = document.createElement('li');
		li.textContent = 'Sebep: ' + d.sebep + ' · Tarih: ' + d.tarih + ' · Kaldırma onayı: ' + d.onay + '/2';
		list.appendChild(li);
		(d.talepler || []).slice().reverse().forEach(function (t) {
			var x = document.createElement('li');
			var tag = document.createElement('span');
			tag.className = 'cvb-tag cvb-tag--' + t.durum;
			tag.textContent = DURUM[t.durum] || t.durum;
			x.appendChild(tag);
			x.appendChild(document.createTextNode(t.tarih + (t.neden ? ' — ' + t.neden : '')));
			list.appendChild(x);
		});
		status.className = 'cvb-status is-on';
	}

	function istek(govde) {
		return fetch(API, {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify(govde)
		}).then(function (r) {
			return r.json().catch(function () { return {}; }).then(function (j) { return { ok: r.ok, j: j }; });
		});
	}

	function hata(j) {
		return ERR[j && j.error] || 'Bir sorun oluştu. Daha sonra tekrar dene.';
	}

	form.addEventListener('submit', function (e) {
		e.preventDefault();
		if (!/^[A-Za-z0-9_]{3,16}$/.test(user.value)) { goster('err', 'Kullanıcı adı 3-16 karakter, harf/rakam/alt çizgi olmalı.'); return; }
		if (msg.value.trim().length < 10) { goster('err', ERR.MESSAGE_TOO_SHORT); return; }
		send.disabled = true;
		istek({ username: user.value, password: pass.value, mesaj: msg.value.trim() }).then(function (r) {
			if (r.ok) {
				goster('ok', 'İtirazın alındı. Yetkililer inceleyince durumunu buradan görebilirsin.');
				msg.value = ''; count.textContent = '0';
			} else {
				goster('err', hata(r.j));
			}
			durumYaz(r.j && r.j.durum);
		}).catch(function () {
			goster('err', 'Sunucuya ulaşılamadı. Daha sonra tekrar dene.');
		}).then(function () { send.disabled = false; });
	});

	check.addEventListener('click', function () {
		if (!user.value || !pass.value) { goster('err', 'Durumunu görmek için kullanıcı adı ve şifreni gir.'); return; }
		check.disabled = true;
		istek({ username: user.value, password: pass.value, islem: 'durum' }).then(function (r) {
			if (r.ok) {
				var d = r.j.durum;
				if (d && d.yasakli) { box.className = 'cvb-msg'; } else { goster('ok', 'Bu hesap yasaklı değil.'); }
				durumYaz(d);
			} else {
				goster('err', hata(r.j));
			}
		}).catch(function () {
			goster('err', 'Sunucuya ulaşılamadı. Daha sonra tekrar dene.');
		}).then(function () { check.disabled = false; });
	});
})();
</script>
