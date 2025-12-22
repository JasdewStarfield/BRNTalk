package yourscraft.jasdewstarfield.brntalk.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brntalk.Brntalk;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

import java.util.List;
import java.util.Optional;

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
            return new NetChoice(c.getId(), c.getText(), c.getNextId());
        }

        public TalkMessage.Choice toChoice() {
            return new TalkMessage.Choice(id, text, nextConversationId);
        }
    }

    /* ------------- 单条消息的快照 ------------- */
    public record NetMessage(
            String id,
            TalkMessage.Type type,
            TalkMessage.SpeakerType speakerType,
            String speaker,
            String text,
            long timestamp,
            Optional<String> nextId, // 可能为空
            List<NetChoice> choices
    ) {
        // 把 enum Type 编成 int，再反解回来
        public static final StreamCodec<ByteBuf, TalkMessage.Type> TYPE_STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(
                        i -> TalkMessage.Type.values()[i],
                        TalkMessage.Type::ordinal
                );

        public static final StreamCodec<ByteBuf, TalkMessage.SpeakerType> SPEAKER_TYPE_STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(
                        i -> TalkMessage.SpeakerType.values()[i],
                        TalkMessage.SpeakerType::ordinal
                );

        public static final StreamCodec<ByteBuf, NetMessage> STREAM_CODEC = StreamCodec.of(
                // 1. 编码器 (Encoder): 把对象写入 Buffer
                (buf, val) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, val.id());
                    TYPE_STREAM_CODEC.encode(buf, val.type());
                    SPEAKER_TYPE_STREAM_CODEC.encode(buf, val.speakerType());
                    ByteBufCodecs.STRING_UTF8.encode(buf, val.speaker());
                    ByteBufCodecs.STRING_UTF8.encode(buf, val.text());
                    ByteBufCodecs.VAR_LONG.encode(buf, val.timestamp());
                    ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).encode(buf, val.nextId());
                    NetChoice.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, val.choices());
                },
                // 2. 解码器 (Decoder): 从 Buffer 读取并生成对象
                (buf) -> new NetMessage(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        TYPE_STREAM_CODEC.decode(buf),
                        SPEAKER_TYPE_STREAM_CODEC.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.VAR_LONG.decode(buf),
                        ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buf),
                        NetChoice.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf)
                )
        );

        public static NetMessage fromMessage(TalkMessage msg) {
            List<NetChoice> choices = msg.getChoices()
                    .stream()
                    .map(NetChoice::fromChoice)
                    .toList();
            return new NetMessage(
                    msg.getId(),
                    msg.getType(),
                    msg.getSpeakerType(),
                    msg.getSpeaker(),
                    msg.getText(),
                    msg.getTimestamp(),
                    Optional.ofNullable(msg.getNextId()),
                    choices
            );
        }

        /** 在客户端把快照还原成真正的 TalkMessage */
        public TalkMessage toMessage() {
            TalkMessage m = new TalkMessage(
                    id,
                    type,
                    speakerType,
                    speaker,
                    text,
                    timestamp,
                    nextId.orElse(null)
            );
            for (NetChoice c : choices) {
                m.addChoice(c.toChoice());
            }
            return m;
        }
    }

    /* ------------- 一个 Thread 的快照 ------------- */
    public record NetThread(
            String id,
            String scriptId,
            long startedAt,
            long lastReadTime,
            List<NetMessage> messages
    ) {
        public static final StreamCodec<ByteBuf, NetThread> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, NetThread::id,
                        ByteBufCodecs.STRING_UTF8, NetThread::scriptId,
                        ByteBufCodecs.VAR_LONG, NetThread::startedAt,
                        ByteBufCodecs.VAR_LONG, NetThread::lastReadTime,
                        NetMessage.STREAM_CODEC.apply(ByteBufCodecs.list()), NetThread::messages,
                        NetThread::new
                );

        public static NetThread fromThread(TalkThread thread) {
            List<NetMessage> msgs = thread.getMessages()
                    .stream()
                    .map(NetMessage::fromMessage)
                    .toList();
            return new NetThread(thread.getId(), thread.getScriptId(), thread.getStartTime(), thread.getLastReadTime(), msgs);
        }

        /** 在客户端把快照还原成 TalkThread（内部用一个临时 TalkConversation 来填充消息） */
        public TalkThread toThread() {
            TalkThread thread = new TalkThread(id, scriptId, startedAt, lastReadTime);

            for (NetMessage nm : messages) {
                thread.appendMessage(nm.toMessage());
            }

            return thread;
        }
    }

    /* ------------- 1. 全量同步包 ------------- */
    // 场景：玩家加入或服务器 /reload
    public record SyncThreadsPayload(List<NetThread> threads) implements CustomPacketPayload {

        public static final Type<SyncThreadsPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Brntalk.MODID, "sync_threads"));

        public static final StreamCodec<ByteBuf, SyncThreadsPayload> STREAM_CODEC =
                NetThread.STREAM_CODEC.apply(ByteBufCodecs.list())
                        .map(SyncThreadsPayload::new, SyncThreadsPayload::threads);

        @Override
        public CustomPacketPayload.@NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }


    /* ------------- 2. 追加新对话包 ------------- */
    // 场景：使用startConversation
    public record AddThreadPayload(PayloadSync.NetThread thread) implements CustomPacketPayload {
        public static final Type<AddThreadPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Brntalk.MODID, "add_thread"));

        public static final StreamCodec<ByteBuf, AddThreadPayload> STREAM_CODEC =
                StreamCodec.composite(
                        PayloadSync.NetThread.STREAM_CODEC, AddThreadPayload::thread,
                        AddThreadPayload::new
                );

        @Override
        public CustomPacketPayload.@NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /* ------------- 2. 追加消息包 ------------- */
    // 场景：对话进行中，发送新生成的文本
    public record AppendMessagesPayload(String threadId, List<NetMessage> newMessages) implements CustomPacketPayload {
        public static final Type<AppendMessagesPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Brntalk.MODID, "append_msgs"));

        public static final StreamCodec<ByteBuf, AppendMessagesPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, AppendMessagesPayload::threadId,
                        NetMessage.STREAM_CODEC.apply(ByteBufCodecs.list()), AppendMessagesPayload::newMessages,
                        AppendMessagesPayload::new
                );

        @Override
        public CustomPacketPayload.@NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /* ------------- 3. 状态更新包 ------------- */
    // 场景：更新已读时间 (消除红点)
    public record UpdateStatePayload(String threadId, long lastReadTime) implements CustomPacketPayload {
        public static final Type<UpdateStatePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Brntalk.MODID, "update_state"));

        public static final StreamCodec<ByteBuf, UpdateStatePayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, UpdateStatePayload::threadId,
                        ByteBufCodecs.VAR_LONG, UpdateStatePayload::lastReadTime,
                        UpdateStatePayload::new
                );

        @Override
        public CustomPacketPayload.@NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
