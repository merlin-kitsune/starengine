package com.merlinkitsune.starengine.economy;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * {@code /starcoin} 调试与管理命令（库侧构建器,平台侧只需把它注册进 dispatcher）。
 *
 * <h2>子命令</h2>
 * <ul>
 *   <li>{@code add|set|remove <players> <amount>} —— 权限 2;批量加/设/减（{@code remove} 余额不足者**跳过且不改状态**）;</li>
 *   <li>{@code get [player]} —— 权限 0;显示余额（缺省为自己）;</li>
 *   <li>{@code rank [page]} —— 权限 0;余额排行榜（每页 10 条）;**含离线玩家**
 *       —— 离线数据由 {@link EconomyStorage#readOfflineBalance} 直接解析玩家数据文件得到。</li>
 * </ul>
 *
 * <p><b>实现说明（与第三方模组的同名命令无关,本类为独立实现）</b>:
 * 命令构建放在共享源码里,平台侧只负责把它挂到 {@code RegisterCommandsEvent};
 * 消息文本使用**字面英文**（本库不携带任何 lang 资源文件,而本命令定位是调试工具）,
 * 消费方若需要本地化可自行包一层自己的命令。
 *
 * <p>余额单位由消费方定义;本库存的是整数,故金额参数用 {@code long}。
 */
public final class StarCoinCommand {

    /** 管理子命令所需权限等级（与既有模组的管理命令一致:2 = 管理员）。 */
    public static final int ADMIN_PERMISSION_LEVEL = 2;

    /** 排行榜每页条数。 */
    private static final int PAGE_SIZE = 10;

    private StarCoinCommand() {
    }

    /**
     * 把 {@code /starcoin} 注册进调度器（平台侧在 RegisterCommandsEvent 里调用）。
     *
     * @param gate 平台侧提供的权限判定（{@link CommandPermissionGate} —— 三个 MC 版本的权限方法签名不一致,
     *             故不能写进共享源码）
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandPermissionGate gate) {
        dispatcher.register(Commands.literal("starcoin")
                .then(Commands.literal("add").requires(source -> gate.test(source, ADMIN_PERMISSION_LEVEL))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("amount", LongArgumentType.longArg(0L))
                                        .executes(context -> bulk(context, LongArgumentType.getLong(context, "amount"), Mode.ADD)))))
                .then(Commands.literal("set").requires(source -> gate.test(source, ADMIN_PERMISSION_LEVEL))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("amount", LongArgumentType.longArg(0L))
                                        .executes(context -> bulk(context, LongArgumentType.getLong(context, "amount"), Mode.SET)))))
                .then(Commands.literal("remove").requires(source -> gate.test(source, ADMIN_PERMISSION_LEVEL))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("amount", LongArgumentType.longArg(0L))
                                        .executes(context -> bulk(context, LongArgumentType.getLong(context, "amount"), Mode.REMOVE)))))
                .then(Commands.literal("get")
                        .executes(context -> show(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> show(context, EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("rank")
                        .executes(context -> rank(context, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> rank(context, IntegerArgumentType.getInteger(context, "page"))))));
    }

    /** 管理动作类型。 */
    private enum Mode {
        ADD,
        SET,
        REMOVE
    }

    private static int bulk(CommandContext<CommandSourceStack> context, long amount, Mode mode) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!StarEngineEconomy.isAvailable()) {
            context.getSource().sendSystemMessage(Component.literal("§cStar coin wallet storage is not available on this side."));
            return 0;
        }
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        int success = 0;
        for (ServerPlayer player : players) {
            boolean ok;
            if (mode == Mode.SET) {
                ok = StarEngineEconomy.setBalance(player, amount);
            } else if (mode == Mode.ADD) {
                ok = StarEngineEconomy.deposit(player, amount);
            } else {
                ok = StarEngineEconomy.withdraw(player, amount);
            }
            if (!ok) {
                context.getSource().sendSystemMessage(Component.literal(
                        "§cInsufficient balance for " + player.getName().getString() + " (skipped)."));
                continue;
            }
            success++;
            context.getSource().sendSystemMessage(Component.literal("§a" + describe(mode) + " " + amount
                    + " star coins for " + player.getName().getString()
                    + " -> now " + StarEngineEconomy.getBalance(player)));
        }
        return success;
    }

    private static String describe(Mode mode) {
        if (mode == Mode.SET) return "Set";
        return mode == Mode.ADD ? "Added" : "Removed";
    }

    private static int show(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        if (!StarEngineEconomy.isAvailable()) {
            context.getSource().sendSystemMessage(Component.literal("§cStar coin wallet storage is not available on this side."));
            return 0;
        }
        context.getSource().sendSystemMessage(Component.literal("§6" + target.getName().getString()
                + "§r: §e" + StarEngineEconomy.getBalance(target) + "§r star coins"));
        return 1;
    }

    /** 排行榜:在线玩家读实时余额,离线玩家读数据文件;按余额降序分页。 */
    private static int rank(CommandContext<CommandSourceStack> context, int page) {
        MinecraftServer server = context.getSource().getServer();
        if (server == null || !StarEngineEconomy.isAvailable()) {
            context.getSource().sendSystemMessage(Component.literal("§cStar coin wallet storage is not available on this side."));
            return 0;
        }

        List<Entry> entries = new ArrayList<>();
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            entries.add(new Entry(player.getName().getString(), StarEngineEconomy.getBalance(player)));
        }

        File[] dataFiles = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile()
                .listFiles(file -> file.isFile() && file.getName().endsWith(".dat"));
        if (dataFiles != null) {
            for (File dataFile : dataFiles) {
                UUID id;
                try {
                    id = UUID.fromString(dataFile.getName().substring(0, dataFile.getName().length() - 4));
                } catch (IllegalArgumentException ignored) {
                    continue; // 非「UUID.dat」形态的文件直接跳过
                }
                if (online.contains(id)) continue;
                long balance = StarEngineEconomy.getOfflineBalance(server, id);
                String name = StarEngineEconomy.getOfflineName(server, id);
                entries.add(new Entry(name != null ? name : id.toString(), balance));
            }
        }

        entries.sort(Comparator.comparingLong(Entry::balance).reversed());
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) PAGE_SIZE));
        int safePage = Math.min(Math.max(1, page), totalPages);
        int from = (safePage - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, entries.size());

        context.getSource().sendSystemMessage(Component.literal("§6-> Star Coin Ranking (page "
                + safePage + "/" + totalPages + ") <-"));
        for (int i = from; i < to; i++) {
            Entry entry = entries.get(i);
            context.getSource().sendSystemMessage(Component.literal(
                    "§6" + (i + 1) + ". §f" + entry.name() + ": §e" + entry.balance()));
        }
        if (entries.isEmpty()) {
            context.getSource().sendSystemMessage(Component.literal("§7(no data)"));
        }
        return 1;
    }

    /** 榜单条目。 */
    private record Entry(String name, long balance) {
    }
}
