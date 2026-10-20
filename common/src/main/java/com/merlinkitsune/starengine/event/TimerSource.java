package com.merlinkitsune.starengine.event;

import java.util.Map;

import net.minecraft.world.entity.player.Player;

/**
 * 计时表的**最小 seam**:{@link EffectTimerGuard} 只经本接口读写「某个玩家身上各效果的结束时刻」。
 *
 * <p>库侧不注册 AttachmentType、也不依赖任何消费方数据结构,因此「计时表存在哪」由消费方决定:
 * 主模组须在初始化阶段调用 {@link EffectTimerGuard#setTimerSource(TimerSource)} 注入实现
 * (例如把它存进自己的 AttachmentType;守卫每次写入都传新建 Map,从不原地修改来源 Map)。
 *
 * <p>未注入时守卫使用库内退化实现({@link #getTimers} 恒空、{@link #setTimers} 空操作、
 * {@link #now} 取 {@code player.level().getGameTime()}):守卫因此不生效,但**不会抛异常**。
 *
 * <p>实现约束:三个方法只在服务端 tick 路径被调用(守卫在客户端直接返回),返回的 Map 允许为
 * {@code null}(守卫按空表处理);键为效果的注册名(如 {@code minecraft:invisibility})。
 */
public interface TimerSource {

    /** 读取该玩家的计时表(无记录可返回空 Map 或 null)。 */
    Map<String, EffectTimerGuard.TimerEntry> getTimers(Player player);

    /** 写入该玩家的计时表(整体替换;守卫传入的总是新建的 Map)。 */
    void setTimers(Player player, Map<String, EffectTimerGuard.TimerEntry> timers);

    /** 当前游戏时间(tick),守卫据此计算「预期剩余」;默认语义为 {@code gameTime}。 */
    long now(Player player);
}
