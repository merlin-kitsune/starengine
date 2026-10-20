package com.merlinkitsune.starengine;

import net.minecraftforge.fml.common.Mod;

/**
 * StarEngine / Forge 1.20.1 入口。
 *
 * <p>本库**不注册任何注册表条目**(物品/效果/附件/数据组件/能力全部留在消费方 mod),
 * 因此搬迁不会改变任何 {@code ResourceLocation} 归属、不破坏存档与数据包。
 * 入口存在只为:1) 让库成为独立可加载的 mod(FML 需要 mods.toml 对应的容器);
 * 2) 为将来库自身的配置/日志留锚点。
 */
@Mod(StarEngineLib.MODID)
public final class StarEngineLib {
    public static final String MODID = "starengine";

    public StarEngineLib() {
        // 注入 Forge 钱包存储实现（玩家持久化 NBT）
        com.merlinkitsune.starengine.economy.ForgeEconomyStorage.install();
    }
}
