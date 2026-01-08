package yourscraft.jasdewstarfield.brntalk.client.ui;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brntalk.Brntalk;

/**
 * 集中管理 BRNTalk UI 的所有常量、颜色、尺寸和贴图资源。
 * 修改此处即可全局调整 UI 风格。
 */
public class TalkUIStyles {

    // ==========================================================
    // 1. 贴图资源 (Textures)
    // ==========================================================
    public static final ResourceLocation TEX_DECO = loc("textures/gui/ui_deco.png");
    public static final ResourceLocation TEX_FRAME = loc("textures/gui/ui_frame.png");
    public static final ResourceLocation TEX_BG_LEFT = loc("textures/gui/ui_bg_left_tile.png");
    public static final ResourceLocation TEX_BG_RIGHT = loc("textures/gui/ui_bg_right_tile.png");
    public static final ResourceLocation TEX_DIVIDER = loc("textures/gui/ui_bg_middle_tile.png");

    private static ResourceLocation loc(String path) {
        return ResourceLocation.fromNamespaceAndPath(Brntalk.MODID, path);
    }

    // ==========================================================
    // 2. 布局尺寸 (Layout Dimensions)
    // ==========================================================

    // 窗口外边距 (屏幕边缘到 UI 边缘的距离)
    public static final int WIN_MARGIN_X = 4;
    public static final int WIN_MARGIN_Y = 5;

    // 9-Slice 外框的长宽和边框厚度
    public static final int FRAME_W = 248;
    public static final int FRAME_H = 118;
    public static final int FRAME_BORDER_W = 8;
    public static final int FRAME_BORDER_H = 7;

    // 9-Slice 装饰的长宽和边框厚度
    public static final int DECO_W = 256;
    public static final int DECO_H = 128;
    public static final int DECO_BORDER_W = 56;
    public static final int DECO_BORDER_H = 28;

    // 左侧列表区域的宽度
    public static final int LEFT_AREA_WIDTH = 145;

    // 分割线的宽度
    public static final int DIVIDER_WIDTH = 9;

    // 气泡内边距
    public static final int BUBBLE_PADDING_X = 8;
    public static final int BUBBLE_PADDING_Y = 6;

    // 气泡之间的垂直间距
    public static final int MSG_SPACING = 10;

    // 气泡最大宽度占右侧区域的比例
    public static final float MAX_BUBBLE_WIDTH_RATIO = 0.85f;

    // 列表项高度
    public static final int THREAD_LIST_ENTRY_HEIGHT = 28;

    // ==========================================================
    // 3. 颜色定义 (Colors) - 格式: 0xAARRGGBB
    // ==========================================================

    // --- 聊天气泡 ---
    public static final int COLOR_PLAYER_BUBBLE_BG    = 0xD0004000; // 玩家气泡底色 (深绿半透)
    public static final int COLOR_PLAYER_BUBBLE_BORDER= 0xFF008000; // 玩家气泡边框 (绿)
    public static final int COLOR_PLAYER_NAME         = 0xFFFFAA00; // 玩家名字颜色 (金)

    public static final int COLOR_NPC_BUBBLE_BG       = 0xD0333333; // NPC 气泡底色 (深灰半透)
    public static final int COLOR_NPC_BUBBLE_BORDER   = 0xFF666666; // NPC 气泡边框 (灰)
    public static final int COLOR_NPC_NAME            = 0xFFFFAA00; // NPC 名字颜色 (金)

    public static final int COLOR_TEXT_NORMAL         = 0xFFFFFFFF; // 普通对话文字 (白)

    // --- 列表区域 ---
    public static final int COLOR_LIST_HOVER_BG       = 0x40FFFFFF; // 列表项悬停背景 (白半透)
    public static final int COLOR_LIST_TIME           = 0xFFFFFFFF; // 时间戳颜色
    public static final int COLOR_LIST_PREVIEW        = 0xFFAAAAAA; // 预览文本颜色 (灰)

    // --- 状态指示点 ---
    public static final int COLOR_DOT_TYPING          = 0xFF00FF00; // 正在输入 (绿)
    public static final int COLOR_DOT_UNREAD          = 0xFFFF0000; // 未读 (红)
    public static final int COLOR_DOT_WAITING         = 0xFFFFFF00; // 等待选项 (黄)

    // --- 杂项 ---
    public static final int COLOR_SCROLLBAR_TRACK     = 0x20FFFFFF;
    public static final int COLOR_SCROLLBAR_THUMB     = 0xFFCCCCCC;
    public static final int COLOR_NO_MSG_TEXT         = 0xFFFFFFFF;
}
