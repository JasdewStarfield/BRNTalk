package yourscraft.jasdewstarfield.brntalk.runtime;

import yourscraft.jasdewstarfield.brntalk.data.TalkConversation;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TalkThread {
    private final String id;
    private final TalkConversation conversation;
    private final long startedAt;

    public TalkThread(String id, TalkConversation conversation, long startedAt) {
        this.id = id;
        this.conversation = conversation;
        this.startedAt = startedAt;
    }

    public String getId() {
        return id;
    }

    public TalkConversation getConversation() {
        return conversation;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public String getFormattedTime() {
        LocalDateTime time = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(startedAt),
                ZoneId.systemDefault()
        );
        return time.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    // 用脚本中的最后一句当作列表里显示的“最后一条消息”
    public String getLastMessagePreview() {
        if (conversation.getMessages().isEmpty()) return "";
        TalkMessage last = conversation.getMessages().getLast();
        return last.getSpeaker() + ": " + last.getText();
    }
}
