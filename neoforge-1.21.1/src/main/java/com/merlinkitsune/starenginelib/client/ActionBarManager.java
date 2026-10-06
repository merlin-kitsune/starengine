package com.merlinkitsune.starenginelib.client;

import com.merlinkitsune.starenginelib.component.GameplayConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 客户端 actionbar 管理器:以指定时长显示消息,并在最后 1 秒(默认 20 tick)淡出。
 * 总时长上限取 GameplayConstants.ACTIONBAR_DURATION_TICKS(默认 3 秒/60 tick),超出部分被截断,避免长时间显示。
 */
public final class ActionBarManager {
    private static Component message;
    private static long endTick;

    private ActionBarManager() {
    }

    // 显示消息:实际时长取 min(指定时长, 总时长上限),防止超时长时间显示
    public static void show(Component msg, int ticks) {
        message = msg;
        int capped = Math.max(1, Math.min(ticks, GameplayConstants.ACTIONBAR_DURATION_TICKS));
        endTick = currentTick() + capped;
    }

    private static long currentTick() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.getGameTime() : 0;
    }

    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (message == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.font == null) {
            message = null;
            return;
        }
        long remaining = endTick - mc.level.getGameTime();
        if (remaining <= 0) {
            message = null;
            return;
        }
        int fadeTicks = GameplayConstants.ACTIONBAR_FADE_TICKS;
        int alpha = remaining > fadeTicks ? 255 : (int) (remaining * 255 / (double) fadeTicks);
        int x = guiGraphics.guiWidth() / 2 - mc.font.width(message) / 2;
        // ⚠️ 位置必须与原版 actionbar **逐字一致** —— 复刻原版 Gui#renderOverlayMessage 的 yShift 规则：
        //      int yShift = Math.max(leftHeight, rightHeight) + (68 - 59);
        //      y = guiHeight - Math.max(yShift, 68);
        //    NeoForge 把 Gui 的 leftHeight / rightHeight 两字段 patch 成 public：前者累计**左侧**状态条
        //    （血量行数，含吸收心/黄心；护甲），后者累计**右侧**（饥饿、坐骑血、氧气）。
        //    ⇒ 任一侧被堆高时本行**跟着上抬**，始终与物品名提示（guiHeight - max(yShift,59)）保持 9px 间距。
        //    2026-10-05 用户实报「有黄心时 ActionBar 仍与物品名重叠」—— 旧实现硬编码 68，
        //    只覆盖 max(lh,rh) ≤ 59 的情形（max 一旦超过 59，物品名上抬而本行不动 ⇒ 压盖）。
        int yShift = Math.max(mc.gui.leftHeight, mc.gui.rightHeight) + (68 - 59);
        int y = guiGraphics.guiHeight() - Math.max(yShift, 68);
        int color = (alpha << 24) | 0xFFFFFF;
        guiGraphics.drawString(mc.font, message, x, y, color, true);
    }
}
