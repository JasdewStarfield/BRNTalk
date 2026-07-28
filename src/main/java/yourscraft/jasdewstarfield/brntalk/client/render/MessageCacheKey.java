package yourscraft.jasdewstarfield.brntalk.client.render;

import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;
import yourscraft.jasdewstarfield.brntalk.runtime.TalkThread;

/**
 * 渲染和时间轴缓存的稳定身份。消息 ID 只能在线程内部唯一，因此必须同时包含线程 ID。
 */
public record MessageCacheKey(String threadId, String messageId) {
    public static MessageCacheKey of(TalkThread thread, TalkMessage message) {
        return new MessageCacheKey(thread.getId(), message.getId());
    }
}
