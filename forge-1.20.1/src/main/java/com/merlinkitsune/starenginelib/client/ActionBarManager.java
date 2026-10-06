package com.merlinkitsune.starenginelib.client;

import com.merlinkitsune.starenginelib.component.GameplayConstants;
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

    public static void show(Component msg) {
        show(msg, GameplayConstants.ACTIONBAR_DURATION_TICKS);
    }

    public static void clear() {
        message = null;
    }

    // 当前是否正在显示指定消息
    public static boolean isShowing(Component msg) {
        return message != null && message.equals(msg);
    }

    private static long currentTick() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.getGameTime() : 0;
    }

    public static void render(GuiGraphics guiGraphics, float partialTick) {
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
        // ⚠️ 本线位置**固定** guiHeight-68 —— 与 **1.20.1 原版** actionbar 一致
        //    （原版在 1.20.1 即 translate(screenWidth/2, screenHeight-68)；物品名恒为
        //     screenHeight-max(0,59) = screenHeight-59 ⇒ 两者恒差 9px、不会重叠）。
        //    ⚠️ **本线刻意不跟随 NeoForge 的动态 yShift**：leftHeight/rightHeight 是 NeoForge 1.21+ 才引入的
        //    patch，原版 1.20.1 **没有**这套机制（黄心 / 护甲不会让原版物品名或 actionbar 上抬）
        //    ⇒ 硬跟着算反而会错位。两条 NeoForge 线的同名实现走动态公式 —— 属**已登记的平台差异**。
        //    （历史：2026-10-03 曾把 58 改成 68 修「与物品名压盖」，那处修正对 1.20.1 仍然正确。）
        int y = guiGraphics.guiHeight() - 68;
        int color = (alpha << 24) | 0xFFFFFF;
        guiGraphics.drawString(mc.font, message, x, y, color, true);
    }
}
