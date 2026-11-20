package net.cevherclient.client.cihaz;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * {@code cevherclient:cihaz}: düz UTF-8 JSON, iki yönlü.
 * İstemci → sunucu: {"t":"iz","izler":["m:…","n:…"],"damgalar":["…"]} (bilgisayar kimliğinin tek yönlü özetleri).
 * Sunucu → istemci: {"t":"damga","k":"…"} (kalıcı yasakta bilgisayara yazılan belirteç).
 */
public record CihazPayload(String text) implements CustomPacketPayload {
	public static final Type<CihazPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("cevherclient", "cihaz"));
	private static final int MAX_BYTES = 8 * 1024;

	public static final StreamCodec<ByteBuf, CihazPayload> CODEC = StreamCodec.of(
		(buf, payload) -> buf.writeBytes(payload.text().getBytes(StandardCharsets.UTF_8)),
		buf -> {
			int length = buf.readableBytes();
			if (length > MAX_BYTES) {
				buf.skipBytes(length);
				return new CihazPayload("");
			}
			byte[] bytes = new byte[length];
			buf.readBytes(bytes);
			return new CihazPayload(new String(bytes, StandardCharsets.UTF_8));
		});

	@Override
	public Type<CihazPayload> type() {
		return TYPE;
	}
}
