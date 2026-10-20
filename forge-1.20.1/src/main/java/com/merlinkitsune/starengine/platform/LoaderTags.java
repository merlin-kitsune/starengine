package com.merlinkitsune.starengine.platform;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * 加载器通用标签 shim(Forge 1.20.1)。
 *
 * <p>{@code c:bosses} 等 Common Tags 在 NeoForge 位于
 * {@code net.neoforged.neoforge.common.Tags},在 Forge 位于
 * {@code net.minecraftforge.common.Tags};共享源码统一引用本类。
 */
public final class LoaderTags {
    /** 通用 tag 命名空间(Common Tags,以 {@code c} 为命名空间) */
    public static final String COMMON_NAMESPACE = "c";

    /** {@code c:bosses} —— 覆盖末影龙/凋灵/监守者及灾变等模组的 boss */
    public static final TagKey<EntityType<?>> BOSSES =
            net.minecraftforge.common.Tags.EntityTypes.BOSSES;

    /** 备用:直接按 {@code c:bosses} 构造(当平台 Tags 未提供该常量时使用) */
    public static final TagKey<EntityType<?>> BOSSES_RAW =
            TagKey.create(Registries.ENTITY_TYPE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(COMMON_NAMESPACE, "bosses"));

    /**
     * 实体是否为 {@code c:bosses}。
     *
     * <p>把「按 tag 判定实体类型」的版本差异收进本 shim:1.20.1 / 1.21.1 直接
     * {@code EntityType#is(TagKey)},26.1 起该方法已移除、须经 {@code builtInRegistryHolder()},
     * 而共享源码 {@code BossEntityUtil} 只调用本方法。
     */
    public static boolean isBoss(net.minecraft.world.entity.Entity entity) {
        return entity != null && entity.getType().is(BOSSES);
    }

    private LoaderTags() {
    }
}
