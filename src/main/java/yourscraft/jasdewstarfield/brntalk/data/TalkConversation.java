package yourscraft.jasdewstarfield.brntalk.data;

import java.util.*;

public class TalkConversation {

    private final String id;
    private final List<TalkMessage> messages = new ArrayList<>();
    private final Map<String, TalkMessage> messageMap = new HashMap<>();

    public TalkConversation(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void addMessage(TalkMessage message) {
        messages.add(message);

        // 建立索引
        if (message.getId() != null && !message.getId().isEmpty()) {
            messageMap.put(message.getId(), message);
        }
    }

    public List<TalkMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public TalkMessage getMessage(String msgId) {
        return messageMap.get(msgId);
    }

    public TalkMessage getFirstMessage() {
        if (messages.isEmpty()) return null;
        return messages.getFirst();
    }
}
