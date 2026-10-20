package com.merlinkitsune.starengine.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 计时器守卫:保证经本类登记的所有有时长效果严格按 20 tick/秒 的速率流动。
 *
 * <p>外部 buff(如神秘遗物+ 的烈焰之核)可能加快或减慢目标身上效果的计时流动
 * (提前到期或逾期残留)。本守卫在每个游戏 tick 之前({@code PlayerTickEvent.Pre})
 * 把本模组记录的效果剩余时长强制校正回 gameTime 驱动的预期值:
 * <ul>
 *   <li>加速(剩余少于预期)→ 以预期剩余重新施加(延长);</li>
 *   <li>减速(剩余多于预期)→ 移除后按预期剩余重新施加(截断);</li>
 *   <li>到期后仍残留 → 移除并按 1 tick 重施加,使其本 tick 自然到期,
 *       确保 {@code MobEffectEvent.Expired} 及链式逻辑(赐福结束/标记递减等)正常触发。</li>
 * </ul>
 * 记录来源:
 * <ul>
 *   <li>消费方自定义效果(其命名空间下的效果)由 {@code MobEffectEvent.Added} 统一记录;</li>
 *   <li>消费方施加的原版效果(如完全隐身用的 INVISIBILITY)由 {@link #apply} 包装记录。</li>
 * </ul>
 *
 * <p><b>计时表解耦</b>:本类不再直接读写任何数据附件,而是经 {@link TimerSource} 读写计时表。
 * 主模组(消费方)**必须在初始化阶段调用** {@link #setTimerSource(TimerSource)} 注入实现
 * (典型做法:包住自己的 AttachmentType 的读/写);未注入时使用库内退化实现 —— 守卫不生效,
 * 但所有方法都安全返回,不会抛异常。平台差异(效果 API 的 {@code MobEffect} /
 * {@code Holder<MobEffect>} 分歧)由各平台子项目的 {@code EffectTimerAccess} 吸收。
 */
public final class EffectTimerGuard {
    /**
     * 视为“无限时长”的门槛:原版「真·无限时长」({@link MobEffectInstance#INFINITE_DURATION} = {@code -1})
     * 或等于/大于该阈值的历史常驻值,一律视为永续、不做计时守卫。
     */
    public static final int INFINITE_THRESHOLD = Integer.MAX_VALUE / 2;
    /** 减速截断容差(tick):剩余超出预期该值以上才移除重加,避免每 tick 抖动 */
    private static final int CLAMP_TOLERANCE = 20;

    // 守卫强制移除标志:效果移除拦截器(onModEffectRemovalPrevented)据此放行守卫自身的 removeEffect
    private static boolean forcedRemoval = false;

    /** 未注入时的退化实现:不存任何记录,时间取 gameTime ⇒ 守卫不生效但不会崩 */
    private static final TimerSource NOOP_TIMER_SOURCE = new TimerSource() {
        // NOOP TimerSource：主模组未注入时计时校正不生效 —— 只告警一次，便于定位漏注入。
        private boolean astral$warned;
        @Override
        public Map<String, TimerEntry> getTimers(Player player) {
            return Collections.emptyMap();
        }

        @Override
        public void setTimers(Player player, Map<String, TimerEntry> timers) {
        }

        @Override
        public long now(Player player) {
            return player.level().getGameTime();
        }
    };

    /** 计时表来源(可注入);默认为退化实现 */
    private static volatile TimerSource timerSource = NOOP_TIMER_SOURCE;

    /** 单个效果的计时记录:结束 tick + 重新施加所需的参数 */
    public record TimerEntry(long endTick, int amplifier, boolean ambient, boolean visible, boolean showIcon) {
        public static final Codec<TimerEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("end").forGetter(TimerEntry::endTick),
                Codec.INT.fieldOf("amp").forGetter(TimerEntry::amplifier),
                Codec.BOOL.fieldOf("ambient").forGetter(TimerEntry::ambient),
                Codec.BOOL.fieldOf("visible").forGetter(TimerEntry::visible),
                Codec.BOOL.fieldOf("icon").forGetter(TimerEntry::showIcon)
        ).apply(i, TimerEntry::new));
    }

    private EffectTimerGuard() {
    }

    /**
     * 注入计时表实现(主模组初始化时调用一次;重复调用以最后一次为准)。
     *
     * <p>传 {@code null} 表示回退到库内退化实现(守卫不生效、不抛异常)。
     */
    public static void setTimerSource(TimerSource source) {
        timerSource = source == null ? NOOP_TIMER_SOURCE : source;
    }

    /** 是否为守卫发起的强制移除(供效果移除拦截器放行) */
    public static boolean isForcedRemoval() {
        return forcedRemoval;
    }

    /**
     * 应用一个有时长的效果并记录其结束时刻(仅玩家目标记录;非玩家目标/无限时长直接透传)。
     * 供本模组施加“原版效果”的调用点使用;自定义效果由 Added 事件统一记录,无需此包装。
     */
    public static boolean apply(LivingEntity target, MobEffectInstance instance) {
        boolean applied = target.addEffect(instance);
        if (applied && target instanceof Player player) {
            record(player, instance);
        }
        return applied;
    }

    /** 记录/更新效果的结束时刻(仅玩家、有限时长;结束时刻取 max,避免刷新时被缩短的记录覆盖) */
    public static void record(Player player, MobEffectInstance instance) {
        if (player.level().isClientSide()) return;
        if (instance == null || instance.getEffect() == null) return;
        int duration = instance.getDuration();
        if (duration == MobEffectInstance.INFINITE_DURATION || duration >= INFINITE_THRESHOLD) return;
        String key = EffectTimerAccess.keyOf(instance);
        if (key == null) return;
        Map<String, TimerEntry> map = new HashMap<>(timersOf(player));
        TimerEntry prev = map.get(key);
        long end = timerSource.now(player) + duration;
        if (prev != null && prev.endTick() > end) {
            end = prev.endTick();
        }
        map.put(key, new TimerEntry(end, instance.getAmplifier(),
                instance.isAmbient(), instance.isVisible(), instance.showIcon()));
        timerSource.setTimers(player, map);
    }

    /** 每 tick({@code PlayerTickEvent.Pre})校正:把记录的效果剩余时长拉回 20t/s 预期值 */
    public static void tick(Player player) {
        if (player.level().isClientSide()) return;
        Map<String, TimerEntry> map = timersOf(player);
        if (map.isEmpty()) return;
        long now = timerSource.now(player);
        Map<String, TimerEntry> updated = new HashMap<>(map);
        boolean dirty = false;
        var it = updated.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            String key = e.getKey();
            TimerEntry timer = e.getValue();
            if (!EffectTimerAccess.exists(player, key)) {
                it.remove();
                dirty = true;
                continue;
            }
            MobEffectInstance inst = EffectTimerAccess.get(player, key);
            long expectedRemaining = timer.endTick() - now;
            if (expectedRemaining <= 0) {
                if (inst != null) {
                    // 外部减速导致到期后仍残留:移除并按 1 tick 重施加,使其本 tick 自然到期(正常触发 Expired 逻辑)
                    forceRemove(player, key);
                    EffectTimerAccess.apply(player, key, 1, timer.amplifier(),
                            timer.ambient(), timer.visible(), timer.showIcon());
                }
                it.remove();
                dirty = true;
            } else if (inst == null) {
                // 外部加速导致提前到期(或效果异常缺失):恢复预期剩余
                EffectTimerAccess.apply(player, key, (int) expectedRemaining, timer.amplifier(),
                        timer.ambient(), timer.visible(), timer.showIcon());
            } else {
                int actual = inst.getDuration();
                if (actual < expectedRemaining) {
                    // 加速:剩余少于预期 → 延长回预期
                    EffectTimerAccess.apply(player, key, (int) expectedRemaining, timer.amplifier(),
                            timer.ambient(), timer.visible(), timer.showIcon());
                } else if (actual > expectedRemaining + CLAMP_TOLERANCE) {
                    // 减速:剩余多于预期 → 移除后按预期截断
                    forceRemove(player, key);
                    EffectTimerAccess.apply(player, key, (int) expectedRemaining, timer.amplifier(),
                            timer.ambient(), timer.visible(), timer.showIcon());
                }
            }
        }
        if (dirty) {
            timerSource.setTimers(player, updated);
        }
    }

    /** 效果被成功移除(本模组主动移除)时遗忘记录,防止守卫把已结束的效果重新施加回来 */
    public static void forget(Player player, String effectKey) {
        if (player.level().isClientSide() || effectKey == null) return;
        Map<String, TimerEntry> map = timerSource.getTimers(player);
        if (map == null || !map.containsKey(effectKey)) return;
        Map<String, TimerEntry> updated = new HashMap<>(map);
        updated.remove(effectKey);
        timerSource.setTimers(player, updated);
    }

    /** 清空计时记录(死亡/重登等清场场景) */
    public static void clear(Player player) {
        if (player.level().isClientSide()) return;
        timerSource.setTimers(player, new HashMap<>());
    }

    /** 读取计时表(实现返回 null 时按空表处理) */
    private static Map<String, TimerEntry> timersOf(Player player) {
        Map<String, TimerEntry> map = timerSource.getTimers(player);
        return map == null ? Collections.emptyMap() : map;
    }

    private static void forceRemove(Player player, String key) {
        forcedRemoval = true;
        try {
            EffectTimerAccess.remove(player, key);
        } finally {
            forcedRemoval = false;
        }
    }
}
