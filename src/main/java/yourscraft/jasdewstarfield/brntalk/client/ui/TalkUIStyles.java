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
    public static final ResourceLocation TEX_FRAME = loc("textures/gui/ui_frame_connected.png");
    public static final ResourceLocation TEX_BG_LEFT = loc("textures/gui/ui_bg_left_tile.png");
    public static final ResourceLocation TEX_BG_RIGHT = loc("textures/gui/ui_bg_right_tile.png");
    public static final ResourceLocation TEX_DIVIDER = loc("textures/gui/ui_bg_middle_tile.png");
    public static final ResourceLocation TEX_PARTS = loc("textures/gui/ui_deco_parts.png");

    private static ResourceLocation loc(String path) {
        return ResourceLocation.fromNamespaceAndPath(Brntalk.MODID, path);
    }

    // ==========================================================
    // 2. 布局尺寸 (Layout Dimensions)
    // ==========================================================

    // 窗口外边距 (屏幕边缘到 UI 边缘的距离)
    public static final int WIN_MARGIN_X = 20;
    public static final int WIN_MARGIN_Y = 20;

    // 9-Slice 外框的长宽和边框厚度
    public static final int FRAME_W = 52;
    public static final int FRAME_H = 50;
    public static final int FRAME_BORDER_W = 10;
    public static final int FRAME_BORDER_H = 9;
    public static final int FRAME_INNER_PADDING = 2;

    // Deco 部件图片大小
    public static final int DECO_W = 256;
    public static final int DECO_H = 128;

    // 竖向锁链
    public static final int CHAIN_V_W = 3;
    public static final int CHAIN_V_H = 6;
    public static final int CHAIN_V_U = 42;
    public static final int CHAIN_V_V = 0;

    // 横向锁链
    public static final int CHAIN_H_W = 6;
    public static final int CHAIN_H_H = 3;
    public static final int CHAIN_H_U = 46;
    public static final int CHAIN_H_V = 0;

    // 左下角电子管
    public static final int DECO_BL_W = 41;
    public static final int DECO_BL_H = 13;
    public static final int DECO_BL_U = 0;
    public static final int DECO_BL_V = 0;

    // 自定义滚动条
    public static final int DECO_SCROLL_BAR_W = 5;
    public static final int DECO_SCROLL_BAR_H = 14;
    public static final int DECO_SCROLL_BAR_U = 53;
    public static final int DECO_SCROLL_BAR_V = 0;

    // 列表区域占内部总宽度的比例
    public static final float LIST_WIDTH_RATIO = 0.32f;

    // 列表区域的最小像素宽度
    public static final int LIST_MIN_WIDTH = 80;

    // 列表区域的最大像素宽度
    public static final int LIST_MAX_WIDTH = 200;

    // 分割线的宽度
    public static final int DIVIDER_WIDTH = 9;

    // 聊天内容的 y-offset
    public static final int CHAT_CONTENTS_Y_OFFSET = 8;

    // 气泡内边距
    public static final int BUBBLE_PADDING_X = 8;
    public static final int BUBBLE_PADDING_Y = 6;

    // 气泡之间的垂直间距
    public static final int MSG_SPACING = 10;

    // 气泡最大宽度占右侧区域的比例
    public static final float MAX_BUBBLE_WIDTH_RATIO = 0.85f;

    // 列表项高度
    public static final int THREAD_LIST_ENTRY_HEIGHT = 28;

    // HUD
    public static final int HUD_WIDTH = 160;
    public static final int HUD_PADDING = 4;
    public static final long HUD_FADE_OUT_START = 5000;
    public static final long HUD_FADE_OUT_DURATION = 1000;


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

    // --- Vanilla-Style UI ---
    public static final int COLOR_VANILLA_BG          = 0xC0101010; // 原版风格背景 (半透明深黑)

    // --- 杂项 ---
    public static final int COLOR_NO_MSG_TEXT         = 0xFFFFFFFF;
    public static final int COLOR_DIVISION            = 0xFFFFFFFF; // 原版风格分割线 (白)

    // --- HUD ---
    public static final int HUD_BG_COLOR              = 0xA0000000; // 背景：半透黑
    // 侧边条颜色
    public static final int HUD_BAR_PLAYER            = 0xFF00AA00; // 绿色
    public static final int HUD_BAR_NPC               = 0xFFFFAA00; // 金色
    public static final int HUD_TEXT_NAME_PLAYER      = 0xFFA0FFA0;
    public static final int HUD_TEXT_NAME_NPC         = 0xFFFFD700;
    public static final int HUD_TEXT_CONTENT          = 0xFFFFFFFF;
    public static final int HUD_TEXT_WAITING          = 0xFFFFFF55; // "等待回应"的黄色
}
