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
        private final String nextId;   // 点击后跳转到的 Message ID

        public Choice(String id, String text, String nextId) {
            this.id = id;
            this.text = text;
            this.nextId = nextId;
        }

        public String getId() {
            return id;
        }

        public String getText() {
            return text;
        }

        public String getNextId() {
            return nextId;
        }
    }

    private final String id;
    private final String nextId;
    private final Type type;
    private final String speaker;
    private final String text;
    private final long timestamp;
    private final List<Choice> choices = new ArrayList<>();

    public TalkMessage(String id, Type type, String speaker, String text, long timestamp, String nextId) {
        this.id = id;
        this.type = type;
        this.speaker = speaker;
        this.text = text;
        this.timestamp = timestamp;
        this.nextId = nextId;
    }

    public String getId() {
        return id;
    }

    public String getNextId() {
        return nextId;
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
