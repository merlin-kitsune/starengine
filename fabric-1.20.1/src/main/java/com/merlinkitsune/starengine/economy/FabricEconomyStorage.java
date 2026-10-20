package com.merlinkitsune.starengine.economy;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.merlinkitsune.starengine.StarEngineLib;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 钱包余额的 Fabric 1.20.1 存储实现（语义与另三线的 {@code *EconomyStorage} 等价）。
 *
 * <h2>平台差异（必须登记）</h2>
 * <ul>
 *   <li><b>没有 {@code Entity#getPersistentData()}</b> —— 那是 Forge/NeoForge 的补丁
 *       （javap 实证：1.20.1 原版 {@code Entity} / {@code BlockEntity} 均无该方法）。
 *       本线改用 <b>Fabric API 自带的 Data Attachment API</b>
 *       （{@code fabric-data-attachment-api-v1}，随 fabric-api 分发、无需任何第三方 mod）
 *       承担持久化：附件随实体 NBT 自动存读，{@code copyOnDeath} 承担死亡重生复制 ——
 *       与 NeoForge 的 {@code AttachmentType} 结构 1:1。</li>
 *   <li><b>玩家 .dat 里的根段是 {@code fabric:attachments}</b>
 *       （Forge 侧为 {@code ForgeData}、NeoForge 侧为 {@code NeoForgeData}）
 *       ⇒ 离线读档的路径与另三线不同（见 {@link #readOfflineWallet}）。
 *       ⚠️ 该键名取自 {@code AttachmentTarget#NBT_ATTACHMENT_KEY} 的字节码字符串常量
 *       （javap 实证 = {@code "fabric:attachments"}），不是猜的。</li>
 *   <li><b>死亡重生复制</b>由附件的 {@code copyOnDeath()} 承担（Forge/NeoForge 侧是显式监听
 *       {@code PlayerEvent.Clone}）；其注入点由 fabric-api 的 {@code fabric-entity-events-v1}
 *       模块驱动（{@code AttachmentTargetImpl.transfer}）—— 这正是
 *       {@code fabric-data-attachment-api-v1} 声明依赖 {@code fabric-entity-events-v1} 的原因。</li>
 *   <li>命令注册走 FAPI {@code CommandRegistrationCallback}（Forge 侧为 {@code RegisterCommandsEvent}）。</li>
 * </ul>
 *
 * <h2>NBT 布局</h2>
 * <pre>
 *   玩家 .dat
 *     └─ fabric:attachments
 *          └─ starengine:star_coin_wallet      ← 附件的 identifier()
 *               ├─ balance : Long
 *               └─ name    : String
 * </pre>
 * <b>不注册任何注册表条目</b>：附件挂在实体上，除 {@code ResourceLocation} 归属之外
 * 不占用任何注册表位置，不影响既有存档与数据包。
 */
public final class FabricEconomyStorage implements EconomyStorage {

    private static final String BALANCE_KEY = "balance";
    private static final String NAME_KEY = "name";

    /** Fabric API 把实体的全部持久附件写进实体 NBT 的这个根段。 */
    private static final String ATTACHMENT_ROOT = "fabric:attachments";

    /** 附件 id ⇒ 玩家 .dat 里的完整路径 = {@code fabric:attachments/<id>}。 */
    public static final ResourceLocation COMPONENT_ID =
            new ResourceLocation(StarEngineLib.MODID, "star_coin_wallet");

    // TODO(2.0.1): 删除 starengine_lib 旧键迁移
    private static final ResourceLocation LEGACY_COMPONENT_ID =
            new ResourceLocation("starengine_lib", "star_coin_wallet"); // TODO(2.0.1): 旧附件 id，2.0.1 删除

    private static final Logger LOGGER = LoggerFactory.getLogger(StarEngineLib.MODID + ".wallet");

    /**
     * 钱包载荷 = 一个只含 {@code balance} / {@code name} 的 NBT 复合标签。
     *
     * <p>用 {@code CompoundTag} 而非自定义 record，是为了让序列化形如「原样两键」——
     * 与另三线写进 {@code ForgeData} / {@code NeoForgeData} 的子结构**逐键一致**，
     * 跨线迁移存档时字段名不需要翻译。
     *
     * <p>{@code persistent(...)} 让它随实体 NBT 存读；{@code copyOnDeath()} 让它在
     * 玩家死亡重生时复制（默认**不**复制 ⇒ 不写就是「死亡掉钱」）。
     */
    public static final AttachmentType<CompoundTag> WALLET = AttachmentRegistry
            .<CompoundTag>builder()
            .persistent(CompoundTag.CODEC)
            .copyOnDeath()
            .initializer(CompoundTag::new)
            .buildAndRegister(COMPONENT_ID);

    /**
     * 2.0.0 专属：旧附件 id 的读取入口（只读回退 + 迁移；随 2.0.1 的 TODO 一并删除）。
     *
     * <p>注册它只是为了让 Fabric 能把旧 id 的既有持久化数据反序列化出来；
     * 迁移后旧键保留一档，便于降级回滚。
     */
    // TODO(2.0.1): 删除 starengine_lib 旧键迁移
    public static final AttachmentType<CompoundTag> LEGACY_WALLET = AttachmentRegistry
            .<CompoundTag>builder()
            .persistent(CompoundTag.CODEC)
            .copyOnDeath()
            .initializer(CompoundTag::new)
            .buildAndRegister(LEGACY_COMPONENT_ID);

    /** 在库入口构造时注入本实现。 */
    public static void install() {
        StarEngineEconomy.installStorage(new FabricEconomyStorage());
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public long getBalance(Player player) {
        if (player == null) {
            return 0L;
        }
        CompoundTag tag = walletTag(player);
        return tag == null ? 0L : Math.max(0L, tag.getLong(BALANCE_KEY));
    }

    @Override
    public void writeBalance(Player player, long balance) {
        if (player == null) {
            return;
        }
        AttachmentTarget target = (AttachmentTarget) player;
        walletTag(player); // 先做旧附件 id 的迁移
        CompoundTag tag = target.getAttachedOrCreate(WALLET);
        tag.putLong(BALANCE_KEY, Math.max(0L, balance));
        tag.putString(NAME_KEY, player.getName().getString());
        // 附件是引用类型：仅改内容不会触发「已变更」标记，必须重新 attach 一次。
        target.setAttached(WALLET, tag);
    }

    @Override
    public long readOfflineBalance(MinecraftServer server, UUID playerId) {
        CompoundTag wallet = readOfflineWallet(server, playerId);
        return wallet == null ? 0L : wallet.getLong(BALANCE_KEY);
    }

    @Override
    public String readOfflineName(MinecraftServer server, UUID playerId) {
        CompoundTag wallet = readOfflineWallet(server, playerId);
        if (wallet == null) {
            return null;
        }
        String name = wallet.getString(NAME_KEY);
        return name.isEmpty() ? null : name;
    }

    /**
     * 取钱包附件：新 id 无数据而旧 id 有数据时，复制到新 id 并记迁移日志。
     *
     * <p>旧附件保留一档（2.0.1 删除），便于降级回滚。
     */
    // TODO(2.0.1): 删除 starengine_lib 旧键迁移
    private static CompoundTag walletTag(Player player) {
        AttachmentTarget target = (AttachmentTarget) player;
        CompoundTag tag = target.getAttached(WALLET);
        if (tag != null && !tag.isEmpty()) {
            return tag;
        }
        CompoundTag legacy = target.getAttached(LEGACY_WALLET);
        if (legacy == null || legacy.isEmpty()) {
            return tag;
        }
        CompoundTag migrated = legacy.copy();
        target.setAttached(WALLET, migrated);
        LOGGER.info("AP_WALLET_NBT_MIGRATED:key=starengine_lib->starengine");
        return migrated;
    }

    /**
     * 离线读取钱包数据：直接解析玩家 {@code .dat}，走
     * {@code fabric:attachments → starengine:star_coin_wallet} 路径。
     * 任一环节缺失/解析失败一律返回 {@code null}（不抛异常，供 FTB 等第三方安全联动）。
     */
    private static CompoundTag readOfflineWallet(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) {
            return null;
        }
        File dataFile = server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                .resolve(playerId + ".dat").toFile();
        if (!dataFile.isFile()) {
            return null;
        }
        try {
            CompoundTag root = NbtIo.readCompressed(dataFile);
            if (!root.contains(ATTACHMENT_ROOT, Tag.TAG_COMPOUND)) {
                return null;
            }
            CompoundTag attachments = root.getCompound(ATTACHMENT_ROOT);
            String key = COMPONENT_ID.toString();
            if (!attachments.contains(key, Tag.TAG_COMPOUND) || attachments.getCompound(key).isEmpty()) {
                // TODO(2.0.1): 删除 starengine_lib 旧键迁移（离线只回退读取，不写玩家 .dat）
                key = LEGACY_COMPONENT_ID.toString();
                if (!attachments.contains(key, Tag.TAG_COMPOUND)) {
                    return null;
                }
            }
            return attachments.getCompound(key);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    /** 命令注册：{@code /starcoin add|set|remove|get|rank}。 */
    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                StarCoinCommand.register(dispatcher, (source, level) -> source.hasPermission(level)));
    }
}
