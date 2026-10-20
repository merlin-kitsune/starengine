package com.merlinkitsune.starengine.economy;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.merlinkitsune.starengine.StarEngineLib;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 钱包余额的 NeoForge 1.21.1 存储实现。
 *
 * <p><b>为什么不用 AttachmentType</b>:库的既有不变量是「**不注册任何注册表条目**」
 * （见 {@link StarEngineLib} 的类注释)——注册附件类型会引入新的 {@code ResourceLocation}。
 * 因此这里改用玩家自带的**实体持久化数据**（{@code Entity#getPersistentData()},
 * 落盘在玩家 {@code .dat} 的 {@code NeoForgeData} 段),对存档的侵入面为「多一个 NBT 子键」。
 *
 * <p><b>死亡保留</b>:玩家重生会换一个实体 ⇒ 在 {@link PlayerEvent.Clone} 里把余额显式复制到新实体
 * （持久化数据默认不跨克隆复制,不写这一步就等于「死亡掉钱」）。
 *
 * <p><b>离线读取</b>:排行榜直接解析玩家 {@code .dat} 里的同一段 NBT。
 */
@EventBusSubscriber(modid = StarEngineLib.MODID)
public final class NeoForgeEconomyStorage implements EconomyStorage {

    /** 本库在玩家持久化数据里的根键。 */
    private static final String ROOT_KEY = "starengine";
    // TODO(2.0.1): 删除 starengine_lib 旧键迁移
    private static final String LEGACY_ROOT_KEY = "starengine_lib"; // TODO(2.0.1): 旧键，2.0.1 删除
    private static final Logger LOGGER = LoggerFactory.getLogger(StarEngineLib.MODID + ".wallet");
    /** 钱包子键（余额与玩家名的实际容器）。 */
    private static final String WALLET_KEY = "star_coin_wallet";
    private static final String BALANCE_KEY = "balance";
    private static final String NAME_KEY = "name";
    /** 玩家 {@code .dat} 中「实体持久化数据」所在的根段（NeoForge 侧命名）。 */
    private static final String PERSISTENT_ROOT = "NeoForgeData";

    /** 在库入口构造时注入本实现。 */
    public static void install() {
        StarEngineEconomy.installStorage(new NeoForgeEconomyStorage());
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

    /** 取（必要时创建并挂回）钱包 NBT;只有挂在持久化数据上的标签才会被保存。 */
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

    /** {@code parent[key]} 的「取或建并挂回」——直接 {@code getCompound} 在缺键时会返回游离标签,写入会丢。 */
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

    /** 解析离线玩家数据文件（读不到一律返回 {@code null},不抛异常）。 */
    private static CompoundTag readOfflineWallet(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) return null;
        File dataFile = server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                .resolve(playerId.toString() + ".dat").toFile();
        if (!dataFile.isFile()) return null;
        try {
            CompoundTag root = NbtIo.readCompressed(dataFile.toPath(), NbtAccounter.unlimitedHeap());
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

    /** 死亡重生/切换实体时把余额复制到新实体（本库的口径:余额永不因死亡丢失）。 */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag source = walletTag(event.getOriginal(), false);
        if (!source.contains(BALANCE_KEY, Tag.TAG_LONG)) return;
        CompoundTag target = walletTag(event.getEntity(), true);
        target.putLong(BALANCE_KEY, source.getLong(BALANCE_KEY));
        target.putString(NAME_KEY, event.getEntity().getName().getString());
    }
}
