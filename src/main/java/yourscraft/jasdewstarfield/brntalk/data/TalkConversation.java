package yourscraft.jasdewstarfield.brntalk.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TalkConversation {

    private final String id;
    private final List<TalkMessage> messages = new ArrayList<>();

    public TalkConversation(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void addMessage(TalkMessage message) {
        messages.add(message);
    }

    public List<TalkMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }
}
