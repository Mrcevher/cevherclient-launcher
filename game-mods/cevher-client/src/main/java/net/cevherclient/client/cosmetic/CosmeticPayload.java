package net.cevherclient.client.cosmetic;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.cevherclient.client.CevherClientMod;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * {@code cevherclient:cosmetic} kanalının içeriği, her iki yönde de aynı: uzunluk öneki olmadan düz UTF-8
 * metin, paketin tüm gövdesi metindir (Paper tarafı {@code getBytes(UTF_8)} ile okur/yazar).
 * Ayrıntılı protokol sunucu eklentisinde: server-plugins/cevher-ui-bridge, {@code CosmeticSync}.
 */
public record CosmeticPayload(String text) implements CustomPacketPayload {
	public static final Type<CosmeticPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("cevherclient", "cosmetic"));
	/** Sunucudan gelen bir mesajın üst sınırı; fazlası okunup atılır ve boş metin sayılır. */
	private static final int MAX_BYTES = 32 * 1024;

	public static final StreamCodec<ByteBuf, CosmeticPayload> CODEC = StreamCodec.of(
		(buf, payload) -> buf.writeBytes(payload.text().getBytes(StandardCharsets.UTF_8)),
		buf -> {
			int length = buf.readableBytes();
			if (length > MAX_BYTES) {
				buf.skipBytes(length);
				CevherClientMod.LOGGER.warn("Çok büyük kozmetik mesajı yok sayıldı ({} bayt)", length);
				return new CosmeticPayload("");
			}
			byte[] bytes = new byte[length];
			buf.readBytes(bytes);
			return new CosmeticPayload(new String(bytes, StandardCharsets.UTF_8));
		});

	@Override
	public Type<CosmeticPayload> type() {
		return TYPE;
	}
}
