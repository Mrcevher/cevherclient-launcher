package net.cevherclient.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Küçük JSON dosyaları için dayanıklı okuma/yazma: geçici dosyaya yazılır, diske zorlanır
 * (fsync), önceki sürüm {@code .bak} olarak saklanır ve atomik yer değiştirme yapılır.
 * Okurken ana dosya bozuksa {@code .bak} denenir; ikisi de okunamazsa hata fırlatılır
 * (boş/bozuk dosya sessizce "hesap yok" sayılmaz).
 */
final class JsonFile {
	static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private JsonFile() {
	}

	private static Path backup(Path file) {
		return file.resolveSibling(file.getFileName() + ".bak");
	}

	private static <T> T parse(Path file, Type type) throws IOException {
		String text = Files.readString(file, StandardCharsets.UTF_8);
		T value = GSON.fromJson(text, type);
		if (value == null) {
			throw new IOException(file.getFileName() + " boş veya geçersiz");
		}
		return value;
	}

	static <T> T read(Path file, Type type, T fallback) throws IOException {
		Path bak = backup(file);
		Exception primary = null;
		if (Files.exists(file)) {
			try {
				return parse(file, type);
			} catch (IOException | RuntimeException e) {
				primary = e;
			}
		} else if (!Files.exists(bak)) {
			return fallback;
		}
		if (Files.exists(bak)) {
			try {
				return parse(bak, type);
			} catch (IOException | RuntimeException e) {
				if (primary == null) {
					primary = e;
				}
			}
		}
		if (primary instanceof IOException io) {
			throw io;
		}
		if (primary instanceof RuntimeException re) {
			throw re;
		}
		return fallback;
	}

	static void write(Path file, Object value) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		ByteBuffer data = ByteBuffer.wrap(GSON.toJson(value).getBytes(StandardCharsets.UTF_8));
		try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
			while (data.hasRemaining()) {
				ch.write(data);
			}
			ch.force(true);
		}
		if (Files.exists(file)) {
			Files.copy(file, backup(file), StandardCopyOption.REPLACE_EXISTING);
		}
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}
}
