package com.merlinkitsune.starengine.event;

import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * {@link EffectTimerGuard} 的**平台效果 API 适配**（NeoForge 1.21.1）。
 *
 * <p>共享源码只能使用四版本签名一致的 API，而效果读写恰是分歧点：
 * 1.20.1 的 {@code LivingEntity#getEffect/removeEffect} 与 {@code MobEffectInstance} 构造器取
 * {@code MobEffect}，1.21.1 / 26.1.2 取 {@code Holder<MobEffect>}；26.1.2 的标识符类还叫
 * {@code Identifier}（{@code ResourceLocation} 已改名）。故这层薄壳按平台各一份，签名完全一致：
 * 共享源码只按「效果注册名（String）」交互。
 *
 * <p>仅供 {@link EffectTimerGuard} 内部使用，不承诺对外 API。
 */
public final class EffectTimerAccess {
    private EffectTimerAccess() {
    }

    /** 效果的注册名（计时表键）；无法解析时返回 {@code null}。 */
    public static String keyOf(MobEffectInstance instance) {
        Holder<MobEffect> effect = instance.getEffect();
        return effect == null ? null : effect.getRegisteredName();
    }

    /** 注册表里是否存在该注册名的效果。 */
    public static boolean exists(Player player, String key) {
        return holder(player, key).isPresent();
    }

    /** 玩家身上该效果的实例（按注册名）；无效键或玩家没有该效果时返回 {@code null}。 */
    public static MobEffectInstance get(Player player, String key) {
        Optional<Holder.Reference<MobEffect>> holder = holder(player, key);
        return holder.isPresent() ? player.getEffect(holder.get()) : null;
    }

    /** 按给定时长/参数施加（不存在则新增）；键无效时返回 {@code false} 且不做任何事。 */
    public static boolean apply(Player player, String key, int duration, int amplifier,
                                boolean ambient, boolean visible, boolean showIcon) {
        Optional<Holder.Reference<MobEffect>> holder = holder(player, key);
        if (holder.isEmpty()) return false;
        player.addEffect(new MobEffectInstance(holder.get(), duration, amplifier, ambient, visible, showIcon));
        return true;
    }

    /** 按注册名移除；键无效时不做任何事。 */
    public static void remove(Player player, String key) {
        holder(player, key).ifPresent(h -> player.removeEffect(h));
    }

    private static Optional<Holder.Reference<MobEffect>> holder(Player player, String key) {
        ResourceLocation id = ResourceLocation.tryParse(key);
        if (id == null) return Optional.empty();
        Registry<MobEffect> registry = player.level().registryAccess().registryOrThrow(Registries.MOB_EFFECT);
        return registry.getHolder(id);
    }
}
