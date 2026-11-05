package yourscraft.jasdewstarfield.brntalk.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class TalkTransferTest {
    private PayloadSync.NetMessage message(int id) {
        return new PayloadSync.NetMessage("m" + id, TalkMessage.Type.TEXT, TalkMessage.SpeakerType.NPC,
                "NPC", "冬".repeat(12000), 0, Optional.empty(), List.of());
    }
    private PayloadSync.NetThread thread() {
        var messages = new ArrayList<PayloadSync.NetMessage>();
        for (int i = 0; i < 40; i++) messages.add(message(i));
        return new PayloadSync.NetThread("thread", "script", 0, 0, messages, "session");
    }

    private CustomPacketPayload roundTrip(CustomPacketPayload original) {
        var frames = TalkTransfer.split(original);
        var assembler = new TalkTransfer.Assembler();
        CustomPacketPayload result = null;
        for (int i = 0; i < frames.size(); i++) {
            var wire = Unpooled.buffer();
            try {
                TalkTransfer.Frame.CODEC.encode(wire, frames.get(i));
                assertTrue(wire.readableBytes() <= TalkTransfer.FRAME_BYTES + 24);
                result = assembler.accept(TalkTransfer.Frame.CODEC.decode(wire), i);
                if (i != frames.size() - 1) assertNull(result, "Partial history must not be published");
            } finally { wire.release(); }
        }
        return result;
    }

    @Test void multiMegabyteUnicodeHistoryAndEmptySnapshotsRoundTrip() {
        var snapshot = new PayloadSync.SyncThreadsPayload(List.of(thread(), thread()));
        assertEquals(snapshot, roundTrip(snapshot));
        var empty = new PayloadSync.SyncThreadsPayload(List.of());
        assertEquals(empty, roundTrip(empty));
    }

    @Test void addAndAppendKeepTheirNotificationSemantics() {
        var add = new PayloadSync.AddThreadPayload(thread());
        var append = new PayloadSync.AppendMessagesPayload("thread", thread().messages());
        assertEquals(add, roundTrip(add));
        assertEquals(append, roundTrip(append));
    }

    @Test void missingFrameTimeoutAndDisconnectNeverCompleteOldData() {
        var frames = TalkTransfer.split(new PayloadSync.AddThreadPayload(thread()));
        var assembler = new TalkTransfer.Assembler();
        assertNull(assembler.accept(frames.getFirst(), 0));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(frames.get(2), 1));
        assertNull(assembler.accept(frames.getFirst(), 0));
        assembler.expire(31_000_000_000L);
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(frames.get(1), 31_000_000_001L));
        assertNull(assembler.accept(frames.getFirst(), 0));
        assembler.clear();
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(frames.get(1), 1));
        // A later complete replacement can recover after any abandoned attempt.
        CustomPacketPayload result = null;
        for (var frame : frames) result = assembler.accept(frame, 2);
        assertInstanceOf(PayloadSync.AddThreadPayload.class, result);
    }

    @Test void boundsAreRejectedBeforeLargeAllocation() {
        assertThrows(IllegalArgumentException.class, () -> new TalkTransfer.Frame(1, 0, TalkTransfer.MAX_BYTES + 1, 0, new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> new TalkTransfer.Frame(1, 0, 1, Integer.MAX_VALUE, new byte[1]));
        var wire = Unpooled.buffer();
        try {
            wire.writeLong(1).writeInt(0).writeInt(1).writeInt(0).writeInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () -> TalkTransfer.Frame.CODEC.decode(wire));
        } finally { wire.release(); }
    }
}
