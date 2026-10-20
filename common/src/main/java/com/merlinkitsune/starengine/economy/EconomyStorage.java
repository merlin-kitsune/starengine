package com.merlinkitsune.starengine.economy;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

/**
 * 钱包余额的**平台存储 seam**（StarEngine）。
 *
 * <p>库把「余额存在哪、怎么落盘」这一步留给平台:NeoForge / Forge 的玩家持久化与读档 API 不同
 * （且离线读 {@code .dat} 时两侧的数据根键也不同),因此本接口只在共享源码里声明契约,
 * 具体实现由各平台子项目提供（{@code NeoForgeEconomyStorage} / {@code ForgeEconomyStorage}）,
 * 在库入口构造时经 {@link StarEngineEconomy#installStorage(EconomyStorage)} 注入。
 *
 * <p><b>设计约束（沿用库既有不变量）</b>:实现**不得注册任何注册表条目** —— 本库把
 * 「余额」存在玩家自带的持久化数据里（{@code Entity#getPersistentData()}），
 * 因而新增本功能不会改变任何 {@code ResourceLocation} 归属、不影响既有存档与数据包。
 *
 * <p><b>死亡语义</b>:余额随玩家数据走;玩家死亡重生会换一个实体,故各平台实现**必须**在
 * 克隆/重生事件里把余额显式复制过去（默认不复制 ⇒ 不写就是「死亡掉钱」）。
 */
public interface EconomyStorage {

    /** 平台存储是否已就绪（未注入实现时为 false,API 一律退化为安全空操作）。 */
    boolean isAvailable();

    /** 读取在线玩家余额（无记录返回 0,不得返回负数）。 */
    long getBalance(Player player);

    /** 写入在线玩家余额（实现内部钳到 ≥ 0,并顺带记录玩家名以便离线榜单显示）。 */
    void writeBalance(Player player, long balance);

    /**
     * 读取**离线玩家**余额（用于排行榜）。
     *
     * <p>实现直接解析玩家数据文件;读不到（文件不存在 / 无本模组数据 / 解析失败）一律返回 0,
     * 不得抛出异常。
     */
    long readOfflineBalance(MinecraftServer server, UUID playerId);

    /** 读取**离线玩家**名称（同 {@link #readOfflineBalance} 的数据来源;读不到返回 {@code null}）。 */
    String readOfflineName(MinecraftServer server, UUID playerId);
}
