package com.merlinkitsune.starengine.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * {@link EffectTimerGuard} 的**平台效果 API 适配**（Forge 1.20.1）。
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
        ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(instance.getEffect());
        return id == null ? null : id.toString();
    }

    /** 注册表里是否存在该注册名的效果。 */
    public static boolean exists(Player player, String key) {
        return effect(key) != null;
    }

    /** 玩家身上该效果的实例（按注册名）；无效键或玩家没有该效果时返回 {@code null}。 */
    public static MobEffectInstance get(Player player, String key) {
        MobEffect effect = effect(key);
        return effect == null ? null : player.getEffect(effect);
    }

    /** 按给定时长/参数施加（不存在则新增）；键无效时返回 {@code false} 且不做任何事。 */
    public static boolean apply(Player player, String key, int duration, int amplifier,
                                boolean ambient, boolean visible, boolean showIcon) {
        MobEffect effect = effect(key);
        if (effect == null) return false;
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, ambient, visible, showIcon));
        return true;
    }

    /** 按注册名移除；键无效时不做任何事。 */
    public static void remove(Player player, String key) {
        MobEffect effect = effect(key);
        if (effect != null) player.removeEffect(effect);
    }

    private static MobEffect effect(String key) {
        ResourceLocation id = ResourceLocation.tryParse(key);
        return id == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(id);
    }
}
