package net.cevherclient.session;

/**
 * Oturum durumu. Mod yalnızca Cevherclient oyunu bir sunucuya bağlanmak için
 * başlattığında (-Dcevherclient.exitOnLeave=true) etkinleşir; başka bir launcher'da
 * ya da serbest oyunda hiçbir şey yapmaz.
 */
public final class CevherSession {
	public static final boolean EXIT_ON_LEAVE = Boolean.getBoolean("cevherclient.exitOnLeave");

	/** Sunucuya bağlanma denendi mi? (Açılıştaki ana menü kapanmaya yol açmasın diye.) */
	public static boolean connectionAttempted = false;

	private CevherSession() {
	}
}
