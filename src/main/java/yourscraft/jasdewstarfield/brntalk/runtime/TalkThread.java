package yourscraft.jasdewstarfield.brntalk.runtime;

import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TalkThread {
    private final String id;
    private final String scriptId;
    private final long startTime;
    private long lastReadTime;
    private final List<TalkMessage> messages = new ArrayList<>();

    /**
     * 构造函数现在需要 scriptId (Conversation ID)
     * @param id 线程 ID
     * @param scriptId 剧本 ID
     * @param startTime 启动时间
     */
    public TalkThread(String id, String scriptId, long startTime, long lastReadTime) {
        this.id = id;
        this.scriptId = scriptId;
        this.startTime = startTime;
        this.lastReadTime = lastReadTime;
    }

    // ---------- Getter 方法 ----------

    public String getId() {
        return id;
    }

    public String getScriptId() {
        return scriptId;
    }

    public long getStartTime() {
        return startTime;
    }

    public String getFormattedTime() {
        LocalDateTime time = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(getLastActivityTime()),
                ZoneId.systemDefault()
        );
        return time.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    public List<TalkMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public TalkMessage getCurrentMessage() {
        if (messages.isEmpty()) return null;
        return messages.getLast();
    }

    public String getLastMessagePreview() {
        TalkMessage last = getCurrentMessage();
        if (last == null) return "";
        return last.getSpeaker() + ": " + last.getText();
    }

    public long getLastActivityTime() {
        if (messages.isEmpty()) {
            return this.startTime;
        }

        TalkMessage last = messages.getLast();

        // 核心逻辑：
        // 1. 如果是实时新消息，last.getTimestamp() 是当前时间，肯定比 startTime 大 -> 返回新时间（顶到最前）
        // 2. 如果是加载的历史消息，last.getTimestamp() 是 0，比 startTime 小 -> 返回 startTime（保持原位）
        return Math.max(this.startTime, last.getTimestamp());
    }

    public long getLastReadTime() {
        return lastReadTime;
    }

    // ---------- Setter 方法 ----------

    /**
     * 追加一条新的消息到历史记录中。
     * @param message 要添加的消息对象
     */
    public void appendMessage(TalkMessage message) {
        if (message == null) return;
        messages.add(message);
    }

    public void setLastReadTime(long lastReadTime) {
        this.lastReadTime = lastReadTime;
    }

}
