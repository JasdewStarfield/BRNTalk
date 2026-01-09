package yourscraft.jasdewstarfield.brntalk.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TalkMessage {

    public enum Type {
        TEXT,
        WAIT,
        CHOICE;

        public static Type fromString(String s) {
            if (s == null) return TEXT;
            return switch (s.toLowerCase()) {
                case "choice" -> CHOICE;
                case "wait" -> WAIT;
                default -> TEXT;
            };
        }
    }

    public enum SpeakerType {
        NPC,
        PLAYER;

        public static SpeakerType fromString(String s) {
            if (s == null) return NPC;
            return switch (s.toLowerCase()) {
                case "player" -> PLAYER;
                default -> NPC;
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
    private final SpeakerType speakerType;
    private final String speaker;
    private final String text;
    private final String action;
    private final long timestamp;
    private final List<Choice> choices = new ArrayList<>();

    public TalkMessage(String id, Type type, SpeakerType speakerType, String speaker, String text, String action,
                       long timestamp, String nextId) {
        this.id = id;
        this.type = type;
        this.speakerType = speakerType;
        this.speaker = speaker;
        this.text = text;
        this.action = action;
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

    public SpeakerType getSpeakerType() {
        return speakerType;
    }

    public String getSpeaker() {
        return speaker;
    }

    public String getText() {
        return text;
    }

    public String getAction() {
        return action;
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

    // 一个带有新时间戳的副本（用于运行时记录）
    public TalkMessage withTimestamp(long newTimestamp) {
        TalkMessage newMsg = new TalkMessage(
                this.id,
                this.type,
                this.speakerType,
                this.speaker,
                this.text,
                this.action,
                newTimestamp,
                this.nextId
        );
        for (Choice c : this.choices) {
            newMsg.addChoice(c);
        }
        return newMsg;
    }
}
