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
    private final List<TalkMessage> messages = new ArrayList<>();

    /**
     * 构造函数现在需要 scriptId (Conversation ID)
     * @param id 线程 ID
     * @param scriptId 剧本 ID
     * @param startTime 启动时间
     */
    public TalkThread(String id, String scriptId, long startTime) {
        this.id = id;
        this.scriptId = scriptId;
        this.startTime = startTime;
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
                Instant.ofEpochMilli(startTime),
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

    // ---------- Setter 方法 ----------

    /**
     * 追加一条新的消息到历史记录中。
     * @param message 要添加的消息对象
     */
    public void appendMessage(TalkMessage message) {
        if (message == null) return;

        // 存入时更新时间戳为当前时间
        messages.add(message.withTimestamp(System.currentTimeMillis()));
    }

}
