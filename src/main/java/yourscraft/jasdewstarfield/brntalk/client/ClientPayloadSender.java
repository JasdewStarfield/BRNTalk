package yourscraft.jasdewstarfield.brntalk.client;

import yourscraft.jasdewstarfield.brntalk.platform.TalkNetworking;

public class ClientPayloadSender {
    public static void requestOpenTalk() {
        // 兼容旧入口；新的共享代码应直接调用 TalkNetworking。
        TalkNetworking.requestOpenTalk();
    }

    public static void sendSelectChoice(String threadId, String choiceId) {
        // 兼容旧入口；具体发包逻辑集中在平台网络层。
        TalkNetworking.sendSelectChoice(threadId, choiceId);
    }

    public static void sendMarkRead(String threadId) {
        // 兼容旧入口；Forge 分支只需要替换 TalkNetworking 的内部实现。
        TalkNetworking.sendMarkRead(threadId);
    }
}
