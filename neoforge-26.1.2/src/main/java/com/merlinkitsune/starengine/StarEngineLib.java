package com.merlinkitsune.starengine;

import net.neoforged.fml.common.Mod;

/**
 * StarEngine / NeoForge 26.1.2 入口(第三条线)。
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
        // 注入 NeoForge 钱包存储实现（玩家持久化 NBT）——与 1.21.1 / 1.20.1 同构
        // 2026-09-22 补齐：本线此前漏移植该类 ⇒ StarEngineEconomy.isAvailable() 恒 false，
        // /starcoin 命令不注册、拾取星币不吸收、余额条恒 0（消费方代码本身是对的）。
        com.merlinkitsune.starengine.economy.NeoForgeEconomyStorage.install();
    }
}
