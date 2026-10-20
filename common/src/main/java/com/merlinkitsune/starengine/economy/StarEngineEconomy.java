package com.merlinkitsune.starengine.economy;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

/**
 * StarEngine 的**玩家余额账本 API**（对外入口,供本库消费方与第三方模组调用）。
 *
 * <p>语义与既有经济类模组的通行做法一致（但代码为**独立实现**,不参考任何第三方源码）:
 * <ul>
 *   <li>余额是**玩家级**的单一数值（单位由消费方定义 —— 本模组里是「星币」,1 星币 = 1,1 星币袋 = 9）;</li>
 *   <li>所有写操作都在**服务端**执行并**钳到 ≥ 0**;**扣除前必须校验余额**,校验失败时状态零改动;</li>
 *   <li>余额**不受死亡掉落影响**（随玩家数据保留,见 {@link EconomyStorage} 的死亡语义说明）;</li>
 *   <li>本批**不做客户端同步**（需求里没有余额显示）⇒ 客户端不应调用写接口,读接口在客户端返回 0。</li>
 * </ul>
 *
 * <p><b>给第三方模组（如 FTB 任务/经济联动）</b>:直接使用下面的静态方法即可,
 * 不依赖任何注册表条目或数据组件;存储未安装时（例如本模组的 26.1.2 线尚未接入）
 * 全部读接口返回 0、写接口返回 false,**不会抛异常**,便于调用方安全降级。
 *
 * <p>⚠️ 本类只做「余额算术 + 委派存储」,不含任何物品概念(星币/星币袋的物品折算留在消费方),
 * 以保证库层与具体模组的货币物品解耦。
 */
public final class StarEngineEconomy {

    private static EconomyStorage storage;

    private StarEngineEconomy() {
    }

    /** 平台侧注入存储实现（各平台入口构造时调用一次;重复调用以最后一次为准）。 */
    public static void installStorage(EconomyStorage impl) {
        storage = impl;
    }

    /** 平台存储是否就绪。 */
    public static boolean isAvailable() {
        return storage != null && storage.isAvailable();
    }

    /** 读取余额（不可用时返回 0;实现保证不为负）。 */
    public static long getBalance(Player player) {
        if (!isAvailable() || player == null) return 0L;
        return Math.max(0L, storage.getBalance(player));
    }

    /** 余额是否足够。 */
    public static boolean hasBalance(Player player, long amount) {
        return amount >= 0L && getBalance(player) >= amount;
    }

    /** 直接设置余额（钳到 ≥ 0;不可用时返回 false）。 */
    public static boolean setBalance(Player player, long amount) {
        if (!isAvailable() || player == null) return false;
        storage.writeBalance(player, Math.max(0L, amount));
        return true;
    }

    /** 存入（负数一律拒绝;返回是否受理）。 */
    public static boolean deposit(Player player, long amount) {
        if (!isAvailable() || player == null || amount < 0L) return false;
        if (amount == 0L) return true;
        storage.writeBalance(player, Math.max(0L, storage.getBalance(player) + amount));
        return true;
    }

    /** 取出（负数拒绝、余额不足拒绝;成功才改动状态）。 */
    public static boolean withdraw(Player player, long amount) {
        if (!isAvailable() || player == null || amount < 0L) return false;
        if (amount == 0L) return true;
        long current = Math.max(0L, storage.getBalance(player));
        if (current < amount) return false;
        storage.writeBalance(player, current - amount);
        return true;
    }

    /** 玩家间转账（先校验付款方余额,再一扣一加;付款方与收款方为同一玩家时直接拒绝）。 */
    public static boolean transfer(Player from, Player to, long amount) {
        if (!isAvailable() || from == null || to == null || from == to || amount < 0L) return false;
        if (amount == 0L) return true;
        long current = Math.max(0L, storage.getBalance(from));
        if (current < amount) return false;
        storage.writeBalance(from, current - amount);
        storage.writeBalance(to, Math.max(0L, storage.getBalance(to) + amount));
        return true;
    }

    /** 离线玩家余额（排行榜用;不可用或读不到返回 0）。 */
    public static long getOfflineBalance(MinecraftServer server, UUID playerId) {
        if (!isAvailable() || server == null || playerId == null) return 0L;
        return Math.max(0L, storage.readOfflineBalance(server, playerId));
    }

    /** 离线玩家名称（排行榜用;读不到返回 {@code null},调用方自行退化为 UUID 文本）。 */
    public static String getOfflineName(MinecraftServer server, UUID playerId) {
        if (!isAvailable() || server == null || playerId == null) return null;
        return storage.readOfflineName(server, playerId);
    }
}
