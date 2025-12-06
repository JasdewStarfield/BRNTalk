package yourscraft.jasdewstarfield.brntalk.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brntalk.Brntalk;
import yourscraft.jasdewstarfield.brntalk.data.TalkConversation;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

import java.util.ArrayList;
import java.util.List;

public class PayloadSync {

    /* ------------- 单个选项的快照 ------------- */
    public record NetChoice(String id, String text, String nextConversationId) {
        public static final StreamCodec<ByteBuf, NetChoice> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, NetChoice::id,
                        ByteBufCodecs.STRING_UTF8, NetChoice::text,
                        ByteBufCodecs.STRING_UTF8, NetChoice::nextConversationId,
                        NetChoice::new
                );

        public static NetChoice fromChoice(TalkMessage.Choice c) {
            return new NetChoice(c.getId(), c.getText(), c.getNextConversationId());
        }

        public TalkMessage.Choice toChoice() {
            return new TalkMessage.Choice(id, text, nextConversationId);
        }
    }

    /* ------------- 单条消息的快照 ------------- */
    public record NetMessage(
            TalkMessage.Type type,
            String speaker,
            String text,
            long timestamp,
            List<NetChoice> choices
    ) {
        // 把 enum Type 编成 int，再反解回来
        public static final StreamCodec<ByteBuf, TalkMessage.Type> TYPE_STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(
                        i -> TalkMessage.Type.values()[i],
                        TalkMessage.Type::ordinal
                );

        public static final StreamCodec<ByteBuf, NetMessage> STREAM_CODEC =
                StreamCodec.composite(
                        TYPE_STREAM_CODEC, NetMessage::type,
                        ByteBufCodecs.STRING_UTF8, NetMessage::speaker,
                        ByteBufCodecs.STRING_UTF8, NetMessage::text,
                        ByteBufCodecs.VAR_LONG, NetMessage::timestamp,
                        NetChoice.STREAM_CODEC.apply(ByteBufCodecs.list()), NetMessage::choices,
                        NetMessage::new
                );

        public static NetMessage fromMessage(TalkMessage msg) {
            List<NetChoice> choices = msg.getChoices()
                    .stream()
                    .map(NetChoice::fromChoice)
                    .toList();
            return new NetMessage(
                    msg.getType(),
                    msg.getSpeaker(),
                    msg.getText(),
                    msg.getTimestamp(),
                    choices
            );
        }

        /** 在客户端把快照还原成真正的 TalkMessage */
        public TalkMessage toMessage() {
            TalkMessage m = new TalkMessage(type, speaker, text, timestamp);
            for (NetChoice c : choices) {
                m.addChoice(c.toChoice());
            }
            return m;
        }
    }

    /* ------------- 一个 Thread 的快照 ------------- */
    public record NetThread(
            String id,
            long startedAt,
            List<NetMessage> messages
    ) {
        public static final StreamCodec<ByteBuf, NetThread> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, NetThread::id,
                        ByteBufCodecs.VAR_LONG, NetThread::startedAt,
                        NetMessage.STREAM_CODEC.apply(ByteBufCodecs.list()), NetThread::messages,
                        NetThread::new
                );

        public static NetThread fromThread(TalkThread thread) {
            List<NetMessage> msgs = thread.getMessages()
                    .stream()
                    .map(NetMessage::fromMessage)
                    .toList();
            return new NetThread(thread.getId(), thread.getStartedAt(), msgs);
        }

        /** 在客户端把快照还原成 TalkThread（内部用一个临时 TalkConversation 来填充消息） */
        public TalkThread toThread() {
            TalkThread thread = new TalkThread(id, startedAt);

            TalkConversation conv = new TalkConversation(id);
            for (NetMessage nm : messages) {
                conv.addMessage(nm.toMessage());
            }
            thread.appendConversation(conv);

            return thread;
        }
    }

    /* ------------- 真正发送的 S2C 包：同步所有 thread ------------- */
    public record SyncThreadsPayload(List<NetThread> threads) implements CustomPacketPayload {

        public static final Type<SyncThreadsPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Brntalk.MODID, "sync_threads"));

        public static final StreamCodec<ByteBuf, SyncThreadsPayload> STREAM_CODEC =
                NetThread.STREAM_CODEC.apply(ByteBufCodecs.list())
                        .map(SyncThreadsPayload::new, SyncThreadsPayload::threads);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
