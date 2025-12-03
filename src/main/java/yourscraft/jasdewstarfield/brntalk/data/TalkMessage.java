package yourscraft.jasdewstarfield.brntalk.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TalkMessage {

    public enum Type {
        TEXT,
        CHOICE;

        public static Type fromString(String s) {
            if (s == null) return TEXT;
            return switch (s.toLowerCase()) {
                case "choice" -> CHOICE;
                default -> TEXT;
            };
        }
    }

    public static class Choice {
        private final String id;                // 选项 id（可选）
        private final String text;              // 显示在按钮上的文本
        private final String nextConversationId; // 点击后跳去哪个对话（可为空）

        public Choice(String id, String text, String nextConversationId) {
            this.id = id;
            this.text = text;
            this.nextConversationId = nextConversationId;
        }

        public String getId() {
            return id;
        }

        public String getText() {
            return text;
        }

        public String getNextConversationId() {
            return nextConversationId;
        }
    }

    private final Type type;
    private final String speaker;
    private final String text;
    private final long timestamp;
    private final List<Choice> choices = new ArrayList<>();

    public TalkMessage(Type type, String speaker, String text, long timestamp) {
        this.type = type;
        this.speaker = speaker;
        this.text = text;
        this.timestamp = timestamp;
    }

    public Type getType() {
        return type;
    }

    public String getSpeaker() {
        return speaker;
    }

    public String getText() {
        return text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void addChoice(Choice choice) {
        choices.add(choice);
    }

    public List<Choice> getChoices() {
        return Collections.unmodifiableList(choices);
    }
}
