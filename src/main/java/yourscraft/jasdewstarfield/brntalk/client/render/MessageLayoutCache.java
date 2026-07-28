package yourscraft.jasdewstarfield.brntalk.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import yourscraft.jasdewstarfield.brntalk.client.text.ClientTextFormatter;
import yourscraft.jasdewstarfield.brntalk.data.TalkMessage;

import java.util.List;

/**
 * 缓存单条消息在指定宽度下的处理文本和折行结果。
 */
public final class MessageLayoutCache {
    public String processedText;
    public List<FormattedCharSequence> lines;
    private int cachedWidth = -1;

    public List<FormattedCharSequence> getLines(Font font, TalkMessage message, int maxWidth) {
        if (processedText == null) {
            processedText = ClientTextFormatter.process(message.getText());
        }
        if (lines == null || cachedWidth != maxWidth) {
            cachedWidth = maxWidth;
            lines = font.split(Component.literal(processedText), maxWidth);
        }
        return lines;
    }
}
