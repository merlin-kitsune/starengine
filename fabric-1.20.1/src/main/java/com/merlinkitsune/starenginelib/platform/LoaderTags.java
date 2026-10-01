package com.merlinkitsune.starenginelib.platform;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * 加载器通用标签 shim(Fabric 1.20.1)。
 *
 * <p>{@code c:bosses} 等 Common Tags 在 NeoForge 位于
 * {@code net.neoforged.neoforge.common.Tags},在 Forge 位于
 * {@code net.minecraftforge.common.Tags};Fabric 侧**没有**平台提供的常量,
 * 故这里直接按约定命名空间 {@code c} 构造 TagKey(Common Tags 是跨加载器的数据约定,
 * 由各模组/整合包的 datapack 提供 {@code data/c/tags/entity_types/bosses.json})。
 */
public final class LoaderTags {
    /** 通用 tag 命名空间(Common Tags,以 {@code c} 为命名空间) */
    public static final String COMMON_NAMESPACE = "c";

    /** {@code c:bosses} —— 覆盖末影龙/凋灵/监守者及灾变等模组的 boss */
    public static final TagKey<EntityType<?>> BOSSES =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(COMMON_NAMESPACE, "bosses"));

    /** 备用别名:在 Fabric 侧 BOSSES 本身就是直接构造的,这里保留同一引用以维持共享源码的引用面 */
    public static final TagKey<EntityType<?>> BOSSES_RAW = BOSSES;

    /**
     * 实体是否为 {@code c:bosses}。
     *
     * <p>把「按 tag 判定实体类型」的版本差异收进本 shim:1.20.1 / 1.21.1 直接
     * {@code EntityType#is(TagKey)},26.1 起该方法已移除、须经 {@code builtInRegistryHolder()},
     * 而共享源码 {@code BossEntityUtil} 只调用本方法。
     */
    public static boolean isBoss(Entity entity) {
        return entity != null && entity.getType().is(BOSSES);
    }

    private LoaderTags() {
    }
}
