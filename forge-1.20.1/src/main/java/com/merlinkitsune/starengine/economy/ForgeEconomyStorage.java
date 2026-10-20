package com.merlinkitsune.starengine.economy;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.merlinkitsune.starengine.StarEngineLib;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 钱包余额的 Forge 1.20.1 存储实现（语义与 1.21.1 侧 {@code NeoForgeEconomyStorage} 逐字等价）。
 *
 * <p>平台差异（必须登记）:
 * <ul>
 *   <li>注解是 {@code @Mod.EventBusSubscriber}（1.21.1 为 {@code @EventBusSubscriber}）;</li>
 *   <li>事件包名 {@code net.minecraftforge.event.*}（1.21.1 为 {@code net.neoforged.neoforge.event.*}）;</li>
 *   <li>玩家 {@code .dat} 里实体持久化数据的根段是 <b>{@code ForgeData}</b>（NeoForge 侧为 {@code NeoForgeData}）;</li>
 *   <li>本线 {@code NbtIo} 无 {@code NbtAccounter} 重载,直接读文件。</li>
 * </ul>
 *
 * <p>不注册任何注册表条目:余额存在玩家持久化数据里（见 1.21.1 侧同类注释的完整理由）,
 * 死亡重生经 {@link PlayerEvent.Clone} 显式复制 ⇒ 余额不因死亡丢失。
 */
@Mod.EventBusSubscriber(modid = StarEngineLib.MODID)
public final class ForgeEconomyStorage implements EconomyStorage {

    private static final String ROOT_KEY = "starengine";
    // TODO(2.0.1): 删除 starengine_lib 旧键迁移
    private static final String LEGACY_ROOT_KEY = "starengine_lib"; // TODO(2.0.1): 旧键，2.0.1 删除
    private static final String WALLET_KEY = "star_coin_wallet";
    private static final Logger LOGGER = LoggerFactory.getLogger(StarEngineLib.MODID + ".wallet");
    private static final String BALANCE_KEY = "balance";
    private static final String NAME_KEY = "name";
    /** 玩家 {@code .dat} 中「实体持久化数据」所在的根段（Forge 侧命名）。 */
    private static final String PERSISTENT_ROOT = "ForgeData";

    /** 在库入口构造时注入本实现。 */
    public static void install() {
        StarEngineEconomy.installStorage(new ForgeEconomyStorage());
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public long getBalance(Player player) {
        return walletTag(player, false).getLong(BALANCE_KEY);
    }

    @Override
    public void writeBalance(Player player, long balance) {
        CompoundTag wallet = walletTag(player, true);
        wallet.putLong(BALANCE_KEY, Math.max(0L, balance));
        wallet.putString(NAME_KEY, player.getName().getString());
    }

    @Override
    public long readOfflineBalance(MinecraftServer server, UUID playerId) {
        CompoundTag wallet = readOfflineWallet(server, playerId);
        return wallet == null ? 0L : wallet.getLong(BALANCE_KEY);
    }

    @Override
    public String readOfflineName(MinecraftServer server, UUID playerId) {
        CompoundTag wallet = readOfflineWallet(server, playerId);
        if (wallet == null) return null;
        String name = wallet.getString(NAME_KEY);
        return name.isEmpty() ? null : name;
    }

    private static CompoundTag walletTag(Player player, boolean create) {
        CompoundTag persistent = player.getPersistentData();
        migrateLegacyRoot(persistent);
        if (!create && !persistent.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
            return new CompoundTag();
        }
        CompoundTag mod = nested(persistent, ROOT_KEY, create);
        return nested(mod, WALLET_KEY, create);
    }

    /**
     * 2.0.0 专属：旧根键（{@link #LEGACY_ROOT_KEY}）→ 新根键的一次性迁移。
     *
     * <p>新键已有数据时不迁移；旧键保留一档（2.0.1 删除），便于降级回滚。
     */
    // TODO(2.0.1): 删除 starengine_lib 旧键迁移
    private static void migrateLegacyRoot(CompoundTag persistent) {
        if (persistent.contains(ROOT_KEY, Tag.TAG_COMPOUND) && !persistent.getCompound(ROOT_KEY).isEmpty()) {
            return;
        }
        if (!persistent.contains(LEGACY_ROOT_KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag legacy = persistent.getCompound(LEGACY_ROOT_KEY);
        if (legacy.isEmpty()) {
            return;
        }
        persistent.put(ROOT_KEY, legacy.copy());
        LOGGER.info("AP_WALLET_NBT_MIGRATED:key=starengine_lib->starengine");
    }

    private static CompoundTag nested(CompoundTag parent, String key, boolean create) {
        if (parent.contains(key, Tag.TAG_COMPOUND)) {
            return parent.getCompound(key);
        }
        CompoundTag child = new CompoundTag();
        if (create) {
            parent.put(key, child);
        }
        return child;
    }

    private static CompoundTag readOfflineWallet(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) return null;
        File dataFile = server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                .resolve(playerId.toString() + ".dat").toFile();
        if (!dataFile.isFile()) return null;
        try {
            CompoundTag root = NbtIo.readCompressed(dataFile);
            if (!root.contains(PERSISTENT_ROOT, Tag.TAG_COMPOUND)) return null;
            CompoundTag mod = root.getCompound(PERSISTENT_ROOT);
            CompoundTag wallet = mod.contains(ROOT_KEY, Tag.TAG_COMPOUND) && !mod.getCompound(ROOT_KEY).isEmpty()
                    ? mod.getCompound(ROOT_KEY)
                    : mod.contains(LEGACY_ROOT_KEY, Tag.TAG_COMPOUND) ? mod.getCompound(LEGACY_ROOT_KEY) : null;
            if (wallet == null) return null;
            return wallet.contains(WALLET_KEY, Tag.TAG_COMPOUND) ? wallet.getCompound(WALLET_KEY) : null;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        StarCoinCommand.register(event.getDispatcher(), (source, level) -> source.hasPermission(level));
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag source = walletTag(event.getOriginal(), false);
        if (!source.contains(BALANCE_KEY, Tag.TAG_LONG)) return;
        CompoundTag target = walletTag(event.getEntity(), true);
        target.putLong(BALANCE_KEY, source.getLong(BALANCE_KEY));
        target.putString(NAME_KEY, event.getEntity().getName().getString());
    }
}
