package net.cevherclient.client.vaka;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * {@code cevherclient:vaka}: sunucudan (CevherKoruma) yalnızca OP'lara gelen hile vakaları. Gövde uzunluk öneki
 * olmadan düz UTF-8 JSON'dur: {"t":"vaka","uyar":bool,"vaka":{...}} veya {"t":"liste","vakalar":[...]}.
 */
public record VakaPayload(String text) implements CustomPacketPayload {
	public static final Type<VakaPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("cevherclient", "vaka"));
	private static final int MAX_BYTES = 256 * 1024;

	public static final StreamCodec<ByteBuf, VakaPayload> CODEC = StreamCodec.of(
		(buf, payload) -> buf.writeBytes(payload.text().getBytes(StandardCharsets.UTF_8)),
		buf -> {
			int length = buf.readableBytes();
			if (length > MAX_BYTES) {
				buf.skipBytes(length);
				return new VakaPayload("");
			}
			byte[] bytes = new byte[length];
			buf.readBytes(bytes);
			return new VakaPayload(new String(bytes, StandardCharsets.UTF_8));
		});

	@Override
	public Type<VakaPayload> type() {
		return TYPE;
	}
}
