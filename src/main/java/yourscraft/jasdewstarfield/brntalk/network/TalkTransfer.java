package yourscraft.jasdewstarfield.brntalk.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Byte-bounded transport shared by recovery snapshots and newly delivered dialogue messages. */
public final class TalkTransfer {
    public static final int FRAME_BYTES = 32 * 1024;
    public static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final AtomicLong IDS = new AtomicLong();
    private TalkTransfer() {}

    public record Frame(long transfer, int kind, int total, int offset, byte[] bytes) implements CustomPacketPayload {
        public static final Type<Frame> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("brntalk", "transfer"));
        public Frame {
            // Validate lengths before the assembler allocates any storage.
            if (kind < 0 || kind > 2 || total < 1 || total > MAX_BYTES || offset < 0
                    || bytes.length < 1 || bytes.length > FRAME_BYTES || offset > total - bytes.length)
                throw new IllegalArgumentException("Invalid dialogue transfer bounds");
        }
        public static final StreamCodec<ByteBuf, Frame> CODEC = StreamCodec.of((buf, frame) -> {
            buf.writeLong(frame.transfer()); buf.writeInt(frame.kind()); buf.writeInt(frame.total());
            buf.writeInt(frame.offset()); buf.writeInt(frame.bytes().length); buf.writeBytes(frame.bytes());
        }, buf -> {
            long id = buf.readLong(); int kind = buf.readInt(), total = buf.readInt(), offset = buf.readInt();
            int length = buf.readInt();
            if (length < 1 || length > FRAME_BYTES || length > buf.readableBytes())
                throw new IllegalArgumentException("Invalid dialogue frame length");
            byte[] bytes = new byte[length]; buf.readBytes(bytes);
            return new Frame(id, kind, total, offset, bytes);
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Encode completely before sending the first frame, so an invalid message cannot send half a snapshot. */
    public static List<Frame> split(CustomPacketPayload payload) {
        ByteBuf buffer = Unpooled.buffer(256, MAX_BYTES);
        try {
            int kind;
            if (payload instanceof PayloadSync.SyncThreadsPayload value) {
                kind = 0; PayloadSync.SyncThreadsPayload.STREAM_CODEC.encode(buffer, value);
            } else if (payload instanceof PayloadSync.AddThreadPayload value) {
                kind = 1; PayloadSync.AddThreadPayload.STREAM_CODEC.encode(buffer, value);
            } else if (payload instanceof PayloadSync.AppendMessagesPayload value) {
                kind = 2; PayloadSync.AppendMessagesPayload.STREAM_CODEC.encode(buffer, value);
            } else throw new IllegalArgumentException("Unsupported dialogue payload");
            int total = buffer.readableBytes();
            long transfer = IDS.incrementAndGet();
            List<Frame> frames = new ArrayList<>();
            for (int offset = 0; offset < total; offset += FRAME_BYTES) {
                byte[] bytes = new byte[Math.min(FRAME_BYTES, total - offset)];
                buffer.readBytes(bytes);
                frames.add(new Frame(transfer, kind, total, offset, bytes));
            }
            return frames;
        } finally { buffer.release(); }
    }

    /** One ordered connection has one pending transfer. Incomplete data never replaces visible history. */
    public static final class Assembler {
        private byte[] pending;
        private long transfer, lastActivity;
        private int kind, position;

        public CustomPacketPayload accept(Frame frame, long nowNanos) {
            expire(nowNanos);
            if (frame.offset() == 0) {
                clear();
                pending = new byte[frame.total()]; transfer = frame.transfer(); kind = frame.kind();
            }
            if (pending == null || transfer != frame.transfer() || kind != frame.kind()
                    || pending.length != frame.total() || position != frame.offset()) {
                clear();
                throw new IllegalArgumentException("Out-of-order dialogue transfer");
            }
            System.arraycopy(frame.bytes(), 0, pending, position, frame.bytes().length);
            position += frame.bytes().length; lastActivity = nowNanos;
            if (position != pending.length) return null;
            ByteBuf buffer = Unpooled.wrappedBuffer(pending);
            try {
                CustomPacketPayload result = switch (kind) {
                    case 0 -> PayloadSync.SyncThreadsPayload.STREAM_CODEC.decode(buffer);
                    case 1 -> PayloadSync.AddThreadPayload.STREAM_CODEC.decode(buffer);
                    case 2 -> PayloadSync.AppendMessagesPayload.STREAM_CODEC.decode(buffer);
                    default -> throw new IllegalStateException("Unknown transfer kind");
                };
                if (buffer.isReadable()) throw new IllegalArgumentException("Trailing dialogue data");
                return result;
            } finally { buffer.release(); clear(); }
        }

        /** Abandoned transfers release their buffer even if no later frame arrives. */
        public void expire(long nowNanos) {
            if (pending != null && nowNanos - lastActivity > 30_000_000_000L) clear();
        }
        public void clear() { pending = null; position = 0; }
    }
}
