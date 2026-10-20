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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 钱包余额的 NeoForge 26.1.2 存储实现。
 *
 * <p><b>与 1.21.1 侧功能完全一致</b>（2026-09-22 补齐）:三线的功能实现必须一致,本类与
 * {@code neoforge-1.21.1} 的同名类只差**两处 26.1.2 的平台 API 变更**（都在下方就地注明）:
 * <ol>
 *   <li>{@link CompoundTag} 的取值/子标签访问改成了 {@code Optional} / {@code *Or} 系列
 *       （不再有 {@code contains(String,int)}、{@code getCompound(String)}）;</li>
 *   <li>命令权限由「整数等级」改为**命名的权限集**（{@code CommandSourceStack#hasPermission(int)}
 *       已删除，改用 {@code permissions().hasPermission(Permission)}）。</li>
 * </ol>
 * 其余（NBT 布局、数据根段名、Clone 复制、离线解析）与另一条 NeoForge 线逐字同构。
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
        return walletTag(player, false).getLongOr(BALANCE_KEY, 0L);
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
        return wallet == null ? 0L : wallet.getLongOr(BALANCE_KEY, 0L);
    }

    @Override
    public String readOfflineName(MinecraftServer server, UUID playerId) {
        CompoundTag wallet = readOfflineWallet(server, playerId);
        if (wallet == null) return null;
        String name = wallet.getStringOr(NAME_KEY, "");
        return name.isEmpty() ? null : name;
    }

    /** 取（必要时创建并挂回）钱包 NBT;只有挂在持久化数据上的标签才会被保存。 */
    private static CompoundTag walletTag(Player player, boolean create) {
        CompoundTag persistent = player.getPersistentData();
        migrateLegacyRoot(persistent);
        if (!create && !persistent.contains(ROOT_KEY)) {
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
        if (persistent.contains(ROOT_KEY) && !persistent.getCompoundOrEmpty(ROOT_KEY).isEmpty()) {
            return;
        }
        if (!persistent.contains(LEGACY_ROOT_KEY)) {
            return;
        }
        CompoundTag legacy = persistent.getCompoundOrEmpty(LEGACY_ROOT_KEY);
        if (legacy.isEmpty()) {
            return;
        }
        persistent.put(ROOT_KEY, legacy.copy());
        LOGGER.info("AP_WALLET_NBT_MIGRATED:key=starengine_lib->starengine");
    }

    /**
     * {@code parent[key]} 的「取或建并挂回」。
     *
     * <p>⚠️ 26.1.2 的 {@link CompoundTag} 去掉了 {@code contains(String,int)} 与返回裸 CompoundTag 的
     * {@code getCompound(String)}：这里的 {@code contains(key)} 不带类型参数（本库是该键的唯一写入者，
     * 类型必然正确），缺键时新建并 {@code put} 回去 —— 直接拿一个游离标签写入是会丢的。
     */
    private static CompoundTag nested(CompoundTag parent, String key, boolean create) {
        if (parent.contains(key)) {
            return parent.getCompoundOrEmpty(key);
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
            if (!root.contains(PERSISTENT_ROOT)) return null;
            CompoundTag mod = root.getCompoundOrEmpty(PERSISTENT_ROOT);
            CompoundTag wallet = mod.contains(ROOT_KEY) && !mod.getCompoundOrEmpty(ROOT_KEY).isEmpty()
                    ? mod.getCompoundOrEmpty(ROOT_KEY)
                    : mod.contains(LEGACY_ROOT_KEY) ? mod.getCompoundOrEmpty(LEGACY_ROOT_KEY) : null;
            if (wallet == null) return null;
            return wallet.contains(WALLET_KEY) ? wallet.getCompoundOrEmpty(WALLET_KEY) : null;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // ⚠️ 26.1.2 的权限已由「整数等级」改为**命名的权限集**：CommandSourceStack 不再提供
        //    hasPermission(int)，改为 permissions().hasPermission(Permission)。此处把公共 seam
        //    的数字等级映射到对应命名权限，保证与 1.20.1 / 1.21.1 的「level ≥ 2 才能用 /starcoin」
        //    完全等价（ADMIN_PERMISSION_LEVEL = 2 ⇒ COMMANDS_GAMEMASTER）。
        StarCoinCommand.register(event.getDispatcher(),
                (source, level) -> source.permissions().hasPermission(permissionFor(level)));
    }

    /** 整数权限等级 → 26.1.2 的命名权限（0 = 能发命令即可，1 = Moderator，2 = Gamemaster，3 = Admin，4 = Owner）。 */
    private static Permission permissionFor(int level) {
        if (level >= 4) return Permissions.COMMANDS_OWNER;
        if (level == 3) return Permissions.COMMANDS_ADMIN;
        if (level == 2) return Permissions.COMMANDS_GAMEMASTER;
        if (level == 1) return Permissions.COMMANDS_MODERATOR;
        return Permissions.CHAT_SEND_COMMANDS;
    }

    /** 死亡重生/切换实体时把余额复制到新实体（本库的口径:余额永不因死亡丢失）。 */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag source = walletTag(event.getOriginal(), false);
        if (!source.contains(BALANCE_KEY)) return;
        CompoundTag target = walletTag(event.getEntity(), true);
        target.putLong(BALANCE_KEY, source.getLongOr(BALANCE_KEY, 0L));
        target.putString(NAME_KEY, event.getEntity().getName().getString());
    }
}
