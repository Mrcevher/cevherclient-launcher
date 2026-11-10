package net.cevherclient.client.sinif;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.cevherclient.client.CevherClientMod;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * {@code cevherclient:sinif} kanalının içeriği, her iki yönde de aynı: uzunluk öneki olmadan düz UTF-8 JSON,
 * paketin tüm gövdesi metindir. Sunucu tarafı SinifSistemi eklentisinin {@code SinifKanal} sınıfıdır.
 */
public record SinifPayload(String text) implements CustomPacketPayload {
	public static final Type<SinifPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath("cevherclient", "sinif"));
	/** Sunucudan gelen bir mesajın üst sınırı; fazlası okunup atılır ve boş metin sayılır. */
	private static final int MAX_BYTES = 512 * 1024;

	public static final StreamCodec<ByteBuf, SinifPayload> CODEC = StreamCodec.of(
		(buf, payload) -> buf.writeBytes(payload.text().getBytes(StandardCharsets.UTF_8)),
		buf -> {
			int length = buf.readableBytes();
			if (length > MAX_BYTES) {
				buf.skipBytes(length);
				CevherClientMod.LOGGER.warn("Çok büyük sınıf mesajı yok sayıldı ({} bayt)", length);
				return new SinifPayload("");
			}
			byte[] bytes = new byte[length];
			buf.readBytes(bytes);
			return new SinifPayload(new String(bytes, StandardCharsets.UTF_8));
		});

	@Override
	public Type<SinifPayload> type() {
		return TYPE;
	}
}
