package net.cevherclient.client.sao;

import java.util.Map;
import net.cevherclient.client.module.Module;

/** Her modülün menüde görünen ikonu ve açıklaması. Modül sınıfları arayüzü bilmesin diye burada tutulur. */
public final class SaoModuleInfo {
	private record Info(SaoGlyph glyph, String description) {
	}

	private static final Map<String, Info> INFO = Map.ofEntries(
		Map.entry("watermark", new Info(SaoGlyph.SWORD, "Ekranın köşesinde Cevherclient imzasını gösterir.")),
		Map.entry("fps", new Info(SaoGlyph.GAUGE, "Saniyedeki kare sayısını (FPS) gösterir.")),
		Map.entry("ping", new Info(SaoGlyph.SIGNAL, "Sunucuya olan gecikmeni milisaniye olarak gösterir.")),
		Map.entry("cps", new Info(SaoGlyph.CURSOR, "Saniyedeki sol ve sağ tık sayını gösterir.")),
		Map.entry("coordinates", new Info(SaoGlyph.PIN, "Bulunduğun konumun X, Y ve Z koordinatlarını gösterir.")),
		Map.entry("clock", new Info(SaoGlyph.CLOCK, "Bilgisayarının saatini gösterir.")),
		Map.entry("direction", new Info(SaoGlyph.COMPASS, "Baktığın yönü (Kuzey, Güney…) gösterir.")),
		Map.entry("memory", new Info(SaoGlyph.CHIP, "Oyunun kullandığı belleği gösterir.")),
		Map.entry("keystrokes", new Info(SaoGlyph.KEYS, "W A S D ve fare tuşlarının durumunu gösterir.")),
		Map.entry("armor", new Info(SaoGlyph.ARMOR, "Giydiğin zırhları ve dayanıklılıklarını gösterir.")),
		Map.entry("potions", new Info(SaoGlyph.FLASK, "Üzerindeki iksir etkilerini ve kalan sürelerini gösterir.")),
		Map.entry("tps", new Info(SaoGlyph.HOURGLASS, "Sunucunun saniyedeki tick sayısını (TPS) tahmin eder; sunucu kasınca düşer.")),
		Map.entry("speed", new Info(SaoGlyph.SPEED, "Yatay hızını blok/saniye olarak gösterir.")),
		Map.entry("biome", new Info(SaoGlyph.TREE, "Bulunduğun biyomun adını gösterir.")),
		Map.entry("day_counter", new Info(SaoGlyph.SUN, "Dünyada kaçıncı günde olduğunu gösterir.")),
		Map.entry("world_time", new Info(SaoGlyph.MOON, "Oyun içindeki saati (gündüz/gece) gösterir.")),
		Map.entry("saturation", new Info(SaoGlyph.FOOD, "Açlık çubuğunda görünmeyen doygunluk değerini ve açlığını gösterir.")),
		Map.entry("server_info", new Info(SaoGlyph.SERVER, "Bağlı olduğun sunucunun adresini ve oyuncu sayısını gösterir.")),
		Map.entry("combo", new Info(SaoGlyph.BOLT, "Hasar almadan art arda vurduğun darbe sayısını gösterir.")),
		Map.entry("reach", new Info(SaoGlyph.RULER, "Son vuruşunun mesafesini blok olarak gösterir.")),
		Map.entry("bow_charge", new Info(SaoGlyph.BOW, "Yay gererken atışın ne kadar dolduğunu gösterir.")),
		Map.entry("totem_counter", new Info(SaoGlyph.TOTEM,
			"Envanterindeki totem sayısını gösterir; oyuncuların patlattığı totem sayısını isimlerinin yanına yazar.")),
		Map.entry("cart_counter", new Info(SaoGlyph.CART, "Envanterindeki TNT'li vagon sayısını azaldıkça kırmızıya dönen renkle gösterir.")),
		Map.entry("array_list", new Info(SaoGlyph.LIST, "Açık modüllerin listesini ekranın kenarında gösterir.")),
		Map.entry("pack_display", new Info(SaoGlyph.PACK, "Kullandığın kaynak paketlerini gösterir.")),
		Map.entry("action_bar", new Info(SaoGlyph.MESSAGE_BAR, "Eylem çubuğu mesajlarını (hotbar üstündeki yazı) istediğin yere taşır.")),
		Map.entry("boss_bar", new Info(SaoGlyph.SKULL, "Boss can çubuklarını istediğin yere taşır.")),
		Map.entry("scoreboard", new Info(SaoGlyph.TABLE, "Yan skor tablosunu istediğin yere taşır, boyutlandırır, kırmızı numaraları gizler.")),
		Map.entry("player_model", new Info(SaoGlyph.PERSON, "Karakterinin küçük bir modelini ekranda gösterir.")),
		Map.entry("toggle_sprint", new Info(SaoGlyph.CHEVRONS, "İleri giderken koşma tuşunu basılı tutmaya gerek bırakmaz.")),
		Map.entry("zoom", new Info(SaoGlyph.MAGNIFIER,
			"Yakınlaştırma tuşuyla (varsayılan C) uzağı yakınlaştırır. Yakınken fare tekerleği yakınlığı değiştirir.")),
		Map.entry("freelook", new Info(SaoGlyph.EYE,
			"Serbest bakış tuşuyla (varsayılan Sol Alt) karakterin yönünü değiştirmeden etrafına bakarsın.")),
		Map.entry("fullbright", new Info(SaoGlyph.BULB, "Karanlıkta her yeri aydınlık gösterir.")),
		Map.entry("no_hurt_cam", new Info(SaoGlyph.CAMERA, "Hasar alınca kameranın sarsılmasını azaltır ya da kaldırır.")),
		Map.entry("crosshair", new Info(SaoGlyph.CROSSHAIR, "Nişangahı istediğin biçim, renk ve boyutta çizer.")),
		Map.entry("block_highlight", new Info(SaoGlyph.CUBE, "Baktığın bloğun çerçevesini renklendirir, kalınlaştırır ve içini boyar.")),
		Map.entry("hitboxes", new Info(SaoGlyph.HITBOX, "Varlıkların vuruş kutularını renkli çizer; hedef ve hasar alan ayrı renkte.")),
		Map.entry("hit_range", new Info(SaoGlyph.RING, "Oyuncuların çevresine vuruş menzili kadar halka çizer; menzile girince renk değişir.")),
		Map.entry("hit_color", new Info(SaoGlyph.HEART, "Hasar alan varlıkların kırmızı rengini değiştirir.")),
		Map.entry("time_changer", new Info(SaoGlyph.SUN, "Dünyanın saatini yalnızca kendi ekranında değiştirir.")),
		Map.entry("item_physic", new Info(SaoGlyph.DROP, "Yere düşen eşyalar dönmek yerine yere yatar.")),
		Map.entry("custom_crystal", new Info(SaoGlyph.CRYSTAL, "End kristallerinin boyutunu, dönüş hızını ve tabanını değiştirir.")),
		Map.entry("hide_armor", new Info(SaoGlyph.NO_ARMOR, "Zırhı gizler ki deri görünsün. Zırh yine korur; yalnızca görünüştür.")),
		Map.entry("held_item", new Info(SaoGlyph.RESIZE, "Elindeki eşyanın boyutunu türüne göre ayarlar, kalkanı aşağı indirir.")),
		Map.entry("vision", new Info(SaoGlyph.NO_EYE, "Balkabağı maskesini gizler, ekranı kaplayan ateşi aşağı indirir.")),
		Map.entry("particles", new Info(SaoGlyph.SPARKS, "Patlama, totem ve vuruş parçacıklarını azaltır.")),
		Map.entry("chat_timestamps", new Info(SaoGlyph.CHAT, "Sohbetteki her mesajın başına saati ekler.")),
		Map.entry("auto_gg", new Info(SaoGlyph.TROPHY, "Oyun bitince (isteğe bağlı öldürünce ya da ölünce) sohbete otomatik gg yazar.")));

	private SaoModuleInfo() {
	}

	public static SaoGlyph glyph(Module module) {
		Info info = INFO.get(module.id);
		return info == null ? SaoGlyph.MODULES : info.glyph();
	}

	public static String description(Module module) {
		Info info = INFO.get(module.id);
		return info == null ? "" : info.description();
	}
}
