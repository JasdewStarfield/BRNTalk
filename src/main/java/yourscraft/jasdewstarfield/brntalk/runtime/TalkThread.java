package yourscraft.jasdewstarfield.brntalk.runtime;

import yourscraft.jasdewstarfield.brntalk.data.TalkConversation;
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
    private final List<TalkMessage> history = new ArrayList<>();
    private final long startedAt;

    public TalkThread(String id, long startedAt) {
        this.id = id;
        this.startedAt = startedAt;
    }

    public String getId() {
        return id;
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

    public void appendConversation(TalkConversation conversation) {
        history.addAll(conversation.getMessages());
    }

    public List<TalkMessage> getMessages() {
        return Collections.unmodifiableList(history);
    }

    // 用脚本中的最后一句当作列表里显示的“最后一条消息”
    public TalkMessage getLastMessage() {
        if (history.isEmpty()) return null;
        return history.getLast();
    }

    public String getLastMessagePreview() {
        TalkMessage last = getLastMessage();
        if (last == null) return "";
        return last.getSpeaker() + ": " + last.getText();
    }

}
