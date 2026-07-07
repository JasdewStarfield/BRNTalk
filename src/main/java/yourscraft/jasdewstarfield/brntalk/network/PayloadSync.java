package yourscraft.jasdewstarfield.brntalk.network;

import net.minecraft.network.FriendlyByteBuf;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class PayloadSync {

    private PayloadSync() {
    }

    private static <T> void writeList(FriendlyByteBuf buf, List<T> values, BiConsumer<T, FriendlyByteBuf> writer) {
        buf.writeVarInt(values.size());
        for (T value : values) {
            writer.accept(value, buf);
        }
    }

    private static <T> List<T> readList(FriendlyByteBuf buf, Function<FriendlyByteBuf, T> reader) {
        int size = buf.readVarInt();
        List<T> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(reader.apply(buf));
        }
        return values;
    }

    /* ------------- 单个选项的快照 ------------- */
    public record NetChoice(String id, String text, String nextConversationId) {
        public static void encode(NetChoice value, FriendlyByteBuf buf) {
            buf.writeUtf(value.id());
            buf.writeUtf(value.text());
            buf.writeUtf(value.nextConversationId() == null ? "" : value.nextConversationId());
        }

        public static NetChoice decode(FriendlyByteBuf buf) {
            return new NetChoice(buf.readUtf(), buf.readUtf(), buf.readUtf());
        }

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
        public static void encode(NetMessage value, FriendlyByteBuf buf) {
            buf.writeUtf(value.id());
            buf.writeEnum(value.type());
            buf.writeEnum(value.speakerType());
            buf.writeUtf(value.speaker());
            buf.writeUtf(value.text());
            buf.writeLong(value.timestamp());
            buf.writeOptional(value.nextId(), FriendlyByteBuf::writeUtf);
            writeList(buf, value.choices(), NetChoice::encode);
        }

        public static NetMessage decode(FriendlyByteBuf buf) {
            return new NetMessage(
                    buf.readUtf(),
                    buf.readEnum(TalkMessage.Type.class),
                    buf.readEnum(TalkMessage.SpeakerType.class),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readLong(),
                    buf.readOptional(FriendlyByteBuf::readUtf),
                    readList(buf, NetChoice::decode)
            );
        }

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
                    null,   // 客户端不需要知道执行了什么命令
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
        public static void encode(NetThread value, FriendlyByteBuf buf) {
            buf.writeUtf(value.id());
            buf.writeUtf(value.scriptId());
            buf.writeLong(value.startedAt());
            buf.writeLong(value.lastReadTime());
            writeList(buf, value.messages(), NetMessage::encode);
        }

        public static NetThread decode(FriendlyByteBuf buf) {
            return new NetThread(
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readLong(),
                    buf.readLong(),
                    readList(buf, NetMessage::decode)
            );
        }

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
    public record SyncThreadsPayload(List<NetThread> threads) {
        public static void encode(SyncThreadsPayload payload, FriendlyByteBuf buf) {
            writeList(buf, payload.threads(), NetThread::encode);
        }

        public static SyncThreadsPayload decode(FriendlyByteBuf buf) {
            return new SyncThreadsPayload(readList(buf, NetThread::decode));
        }
    }

    /* ------------- 2. 追加新对话包 ------------- */
    // 场景：使用 startConversation
    public record AddThreadPayload(PayloadSync.NetThread thread) {
        public static void encode(AddThreadPayload payload, FriendlyByteBuf buf) {
            NetThread.encode(payload.thread(), buf);
        }

        public static AddThreadPayload decode(FriendlyByteBuf buf) {
            return new AddThreadPayload(NetThread.decode(buf));
        }
    }

    /* ------------- 3. 追加消息包 ------------- */
    // 场景：对话进行中，发送新生成的文本
    public record AppendMessagesPayload(String threadId, List<NetMessage> newMessages) {
        public static void encode(AppendMessagesPayload payload, FriendlyByteBuf buf) {
            buf.writeUtf(payload.threadId());
            writeList(buf, payload.newMessages(), NetMessage::encode);
        }

        public static AppendMessagesPayload decode(FriendlyByteBuf buf) {
            return new AppendMessagesPayload(buf.readUtf(), readList(buf, NetMessage::decode));
        }
    }

    /* ------------- 4. 状态更新包 ------------- */
    // 场景：更新已读时间 (消除红点)
    public record UpdateStatePayload(String threadId, long lastReadTime) {
        public static void encode(UpdateStatePayload payload, FriendlyByteBuf buf) {
            buf.writeUtf(payload.threadId());
            buf.writeLong(payload.lastReadTime());
        }

        public static UpdateStatePayload decode(FriendlyByteBuf buf) {
            return new UpdateStatePayload(buf.readUtf(), buf.readLong());
        }
    }
}
