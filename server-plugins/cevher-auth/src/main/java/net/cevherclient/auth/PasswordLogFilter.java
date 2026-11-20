package net.cevherclient.auth;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;

/**
 * Sunucu, oyuncu komutlarını konsola ve logs/latest.log'a yazar ("X issued server command: ...").
 * /login ve /register satırları şifre içerdiği için bu filtre onları gizler.
 */
final class PasswordLogFilter extends AbstractFilter {
	private PasswordLogFilter() {
	}

	static void install() {
		if (LogManager.getRootLogger() instanceof Logger root) {
			root.addFilter(new PasswordLogFilter());
		}
	}

	@Override
	public Result filter(LogEvent event) {
		if (event == null || event.getMessage() == null) {
			return Result.NEUTRAL;
		}
		String text = event.getMessage().getFormattedMessage();
		return text != null && LobbyLogin.SECRET_COMMAND.matcher(text).find() ? Filter.Result.DENY : Result.NEUTRAL;
	}
}
