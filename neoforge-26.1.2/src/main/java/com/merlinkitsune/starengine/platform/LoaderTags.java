package com.merlinkitsune.starengine.platform;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * 加载器通用标签 shim(NeoForge 26.1.2)。
 *
 * <p>{@code c:bosses} 等 Common Tags 在 NeoForge 位于
 * {@code net.neoforged.neoforge.common.Tags},在 Forge 位于
 * {@code net.minecraftforge.common.Tags};共享源码统一引用本类。
 *
 * <p>26.1 的两处平台差异都在这里吸收:
 * <ol>
 *   <li>标识符类由 {@code net.minecraft.resources.ResourceLocation} 改名为
 *       {@code net.minecraft.resources.Identifier};</li>
 *   <li>{@code EntityType} 不再直接提供 {@code is(TagKey)},须经 {@code builtInRegistryHolder()}。</li>
 * </ol>
 * 因此 26.1.2 侧直接用 {@link TagKey#create} 构造 {@code c:bosses}(不引用平台 Tags 常量,
 * 避免常量位置随版本漂移),并由 {@link #isBoss} 承担 tag 判定。
 */
public final class LoaderTags {
    /** 通用 tag 命名空间(Common Tags,以 {@code c} 为命名空间) */
    public static final String COMMON_NAMESPACE = "c";

    /** {@code c:bosses} —— 覆盖末影龙/凋灵/监守者及灾变等模组的 boss */
    public static final TagKey<EntityType<?>> BOSSES =
            TagKey.create(Registries.ENTITY_TYPE,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(COMMON_NAMESPACE, "bosses"));

    /**
     * 实体是否为 {@code c:bosses}。
     *
     * <p>版本差异:26.1 起 {@code EntityType#is(TagKey)} 已移除,须经
     * {@code builtInRegistryHolder()}(见 26.1.2 侧 {@code BossEntityUtil} 的既有写法),
     * 而共享源码只调用本方法。
     */
    public static boolean isBoss(net.minecraft.world.entity.Entity entity) {
        return entity != null && entity.getType().builtInRegistryHolder().is(BOSSES);
    }

    private LoaderTags() {
    }
}
