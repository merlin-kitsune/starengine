package com.merlinkitsune.starenginelib;

import net.fabricmc.api.ModInitializer;

/**
 * StarEngine Lib / Fabric 1.20.1 入口。
 *
 * <p>本库**不注册任何注册表条目**（物品/效果/附件/数据组件全部留在消费方 mod），
 * 因此搬迁不会改变任何 {@code ResourceLocation} 归属、不破坏存档与数据包。
 * 入口存在只为：1) 让库成为独立可加载的 mod（Fabric Loader 需要一个 {@code main} entrypoint）；
 * 2) 注入平台存储实现与库自带命令。
 *
 * <p><b>持久化后端</b>：钱包余额用 <b>Fabric API 自带的 Data Attachment API</b>
 * （{@code fabric-data-attachment-api-v1}，随 fabric-api 分发 ⇒ **零第三方前置**），
 * 由 {@link com.merlinkitsune.starenginelib.economy.FabricEconomyStorage} 在 {@link #onInitialize()}
 * 里注入。附件的静态初始化即完成注册，不需要 CCA 那样的独立 entrypoint。
 */
public final class StarEngineLib implements ModInitializer {
    public static final String MODID = "starengine_lib";

    @Override
    public void onInitialize() {
        // 注入 Fabric 钱包存储实现（Fabric API 附件 + copyOnDeath 死亡复制）
        com.merlinkitsune.starenginelib.economy.FabricEconomyStorage.install();
        // 注册 /starcoin add|set|remove|get|rank（FAPI CommandRegistrationCallback）
        com.merlinkitsune.starenginelib.economy.FabricEconomyStorage.registerCommands();
    }
}
