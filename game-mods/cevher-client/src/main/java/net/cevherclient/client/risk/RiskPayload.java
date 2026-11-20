package net.cevherclient.client.risk;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * {@code cevherclient:risk}: sunucudan (CevherAuth RiskTakip) yalnızca OP'lara gelen risk listesi. Gövde uzunluk
 * öneki olmadan düz UTF-8 JSON'dur.
 */
public record RiskPayload(String text) implements CustomPacketPayload {
	public static final Type<RiskPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("cevherclient", "risk"));
	private static final int MAX_BYTES = 64 * 1024;

	public static final StreamCodec<ByteBuf, RiskPayload> CODEC = StreamCodec.of(
		(buf, payload) -> buf.writeBytes(payload.text().getBytes(StandardCharsets.UTF_8)),
		buf -> {
			int length = buf.readableBytes();
			if (length > MAX_BYTES) {
				buf.skipBytes(length);
				return new RiskPayload("");
			}
			byte[] bytes = new byte[length];
			buf.readBytes(bytes);
			return new RiskPayload(new String(bytes, StandardCharsets.UTF_8));
		});

	@Override
	public Type<RiskPayload> type() {
		return TYPE;
	}
}
