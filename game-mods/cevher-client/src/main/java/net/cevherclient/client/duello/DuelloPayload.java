package net.cevherclient.client.duello;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.cevherclient.client.CevherClientMod;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * {@code cevherclient:duello} kanalının içeriği, her iki yönde de aynı: uzunluk öneki olmadan düz UTF-8 JSON,
 * paketin tüm gövdesi metindir. Sunucu tarafı CevherDuello eklentisinin {@code ModKanali} sınıfıdır.
 */
public record DuelloPayload(String text) implements CustomPacketPayload {
	public static final Type<DuelloPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("cevherclient", "duello"));
	/** Sunucudan gelen bir mesajın üst sınırı; fazlası okunup atılır ve boş metin sayılır. */
	private static final int MAX_BYTES = 8 * 1024;

	public static final StreamCodec<ByteBuf, DuelloPayload> CODEC = StreamCodec.of(
		(buf, payload) -> buf.writeBytes(payload.text().getBytes(StandardCharsets.UTF_8)),
		buf -> {
			int length = buf.readableBytes();
			if (length > MAX_BYTES) {
				buf.skipBytes(length);
				CevherClientMod.LOGGER.warn("Çok büyük düello mesajı yok sayıldı ({} bayt)", length);
				return new DuelloPayload("");
			}
			byte[] bytes = new byte[length];
			buf.readBytes(bytes);
			return new DuelloPayload(new String(bytes, StandardCharsets.UTF_8));
		});

	@Override
	public Type<DuelloPayload> type() {
		return TYPE;
	}
}
