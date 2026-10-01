package com.merlinkitsune.starenginelib.event;

import com.merlinkitsune.starenginelib.component.GameplayConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 事件目标收集器。
 * 收集事件应影响的玩家:玩家所在团队(Minecraft 原生 team / FTB Teams / OPAC)。
 *
 * 规则:如果触发者没有加入任何队伍,则原本按“友方/队友”发放的奖励或加成改为作用于全服在线玩家。
 *
 * <p>历史上的「触发者自身 + 范围内实体 + 已放出女仆」收集链({@code collectTargets} /
 * {@code collectTeamTargets} / {@code collectMaids} / {@code isMaidOwnedBy})与配套常量
 * {@code GameplayConstants.EVENT_RANGE} / {@code EVENT_APPLY_MAID} 已于 {@code 1.0.0-SNAPSHOT.5}
 * 按期删除 —— 合并后消费方三线对这些符号零引用(见 CHANGELOG 与 KNOWN-ISSUES KI-M3)。
 *
 * <h2>⚠️ 反射契约必须按<b>上游实物</b>核验(2026-10-01 全量重校)</h2>
 *
 * 本类此前的 FTB / OPAC 两处反射的目标<b>根本不存在</b>,而最外层
 * {@code catch (Exception ignored)} 把失败完全吞掉 ⇒ 两个后端<b>恒为未启用且毫无声响</b>
 * (等于没写)。实测后果:装了 FTB Teams 或 OPAC 的玩家被本类判为「没有任何队伍」
 * ⇒ 落进「全服皆友方」兜底 ⇒ <b>队友判定整体失效</b>。
 *
 * 逐条更正(每条都给上游源码 {@code 文件:行号},复跑基准见下):
 * <ol>
 *   <li><b>FTB 管理器访问器在<b>嵌套接口</b>上</b>:{@code isManagerLoaded / getManager /
 *       isClientManagerLoaded / getClientManager} 声明在 {@code FTBTeamsAPI$API} 上,
 *       <b>不在</b>外层类 {@code FTBTeamsAPI} 上;{@code Class#getMethod} <b>不会</b>跨到嵌套接口
 *       (外层类并未实现它)。⚠️ 但**本库旧代码并不是在外层类上取** —— 它走 {@code api.getClass()},
 *       那是 {@code api()} 返回的**实现类**,实现了该嵌套接口 ⇒ 那一步**能**解析成功;
 *       **本库的实际首个失败点是下一条的 {@code getTeamForPlayer}**(外层类取访问器那一步是
 *       下游消费方模组 {@code astral_dice} 的写法,两处写法不同、失败点也不同,勿混)。
 *       为稳妥,本库同时保留「方法挪回外层类」的 fallback。</li>
 *   <li><b>服务端取队伍</b>只有 {@code TeamManager#getTeamForPlayer(ServerPlayer)} 与
 *       {@code TeamManager#getTeamForPlayerID(UUID)};原代码查的 {@code getTeamForPlayer(Player)} /
 *       {@code getTeamForPlayer(UUID)} <b>一个都不存在</b>(参数类型错 / 方法名错)。</li>
 *   <li><b>客户端取队伍</b>是 {@code ClientTeamManager#getKnownPlayer(UUID) →
 *       Optional&lt;KnownClientPlayer&gt;} + {@code KnownClientPlayer#teamId()}
 *       (record 访问器,<b>无 get 前缀</b>),以及 {@code ClientTeamManager#getTeamByID(UUID)};
 *       原代码查的 {@code ClientTeamManager#getTeamForPlayer(Player)} 不存在。</li>
 *   <li><b>OPAC 包名整条错</b>:真实包路径为 {@code xaero.pac.*},原代码查的
 *       {@code dev.darkhax.opac.*} 该包<b>不存在</b>。正确入口链 = {@code OpenPACServerAPI.get(MinecraftServer)}
 *       → {@code OpenPACServerAPI#getPartyManager()} → {@code IPartyManagerAPI#getPartyByMember(UUID)};
 *       成员访问器是 {@code IServerPartyAPI#getOnlineMemberStream()} / {@code #getMemberInfoStream()},
 *       原代码查的 {@code getPartyMembers()} 不存在。</li>
 *   <li><b>{@code hasAnyTeam} 的 FTB 判据不能是「Team 对象非空」</b>:FTB 给<b>每个玩家</b>都建了个人队伍
 *       ⇒ 「非空」恒为真 ⇒ 把「未组队 ⇒ 友方作用于全服」的兜底彻底堵死。判据应为
 *       party / server team,即 {@code Team#isPartyTeam() || Team#isServerTeam()}。</li>
 * </ol>
 *
 * <h3>核验基准(可复跑)</h3>
 * <ul>
 *   <li>FTB Teams 分支 {@code 1.20.1} @ {@code f7dcaa9}({@code mod_version=2001.3.2});</li>
 *   <li>Open Parties and Claims 分支 {@code 1.20} @ {@code 3d73aae};</li>
 *   <li>两份上游源码克隆保留在消费方仓 {@code astral_dice_multiloader/ref/}
 *       ({@code ftb-teams} / {@code open-parties-and-claims}),逐条证据见下面两个后端内的行号注释。</li>
 * </ul>
 *
 * <p>⚠️ 本类只用<b>原版 + JDK</b> 类型与反射,<b>不得</b>编译期依赖任何一个第三方模组 ——
 * 四条线(+ 四个平台)的安装情况并不一致,编译期依赖会让其中一条直接编译不过。
 *
 * <h3>失败方向(与原设计一致:安全)</h3>
 * 任一后端不可解析 ⇒ 该后端视为「不提供队伍信息」⇒ 退回其余后端,最终退回原版计分板口径
 * ⇒ 最坏情况与本类此前的行为完全一致。区别只在于<b>解析失败不再静默</b>:
 * 「没装」(ClassNotFoundException)记 debug、「装了但签名不符」记 warn,
 * 并可由 {@link #describeBackends()} 打出可断言的机器行。
 */
public final class EventTargetCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventTargetCollector.class);

    /** 诊断机器行的前缀(全 ASCII,便于日志断言;与消费方模组侧的 {@code AP_*} 族同风格)。 */
    public static final String BACKEND_REPORT_PREFIX = "AP_LIB_PARTY:";

    private EventTargetCollector() {
    }

    // 收集触发者的团队在线玩家(Minecraft 原生 team / FTB Teams / OPAC,按配置开关)。
    // 若玩家没有加入任何队伍,则将全服在线玩家视为友方(排除自己)。
    public static List<Player> collectTeamPlayers(Player triggerer) {
        List<Player> members = new ArrayList<>();
        if (triggerer == null || triggerer.level().isClientSide()) return members;
        boolean anyTeamSystemEnabled = GameplayConstants.EVENT_APPLY_MC_TEAM
                || GameplayConstants.EVENT_APPLY_FTB_TEAM
                || GameplayConstants.EVENT_APPLY_OPAC;
        if (GameplayConstants.EVENT_APPLY_MC_TEAM) {
            collectMcTeamPlayers(triggerer, members);
        }
        if (GameplayConstants.EVENT_APPLY_FTB_TEAM) {
            Ftb.collectOnlineMembers(triggerer, members);
        }
        if (GameplayConstants.EVENT_APPLY_OPAC) {
            Opac.collectOnlineMembers(triggerer, members);
        }
        if (members.isEmpty() && anyTeamSystemEnabled && !hasAnyTeam(triggerer)) {
            if (triggerer.level() instanceof ServerLevel serverLevel) {
                for (ServerPlayer sp : serverLevel.players()) {
                    if (sp != triggerer) {
                        members.add(sp);
                    }
                }
            }
        }
        return members;
    }

    /**
     * 是否已加入任意队伍(Minecraft 原生 team / FTB Teams / OPAC;仅判断是否存在队伍,不读取配置开关)。
     *
     * <p>⚠️ FTB 的判据是 <b>party / server team</b>,不是「存在 Team 对象」——
     * FTB 给每个玩家都建了个人队伍,按后者判定会恒真、把「未组队 ⇒ 作用于全服」的兜底堵死。
     */
    public static boolean hasAnyTeam(Player triggerer) {
        if (triggerer == null) return false;
        if (triggerer.getTeam() != null) return true;
        return Ftb.hasTeam(triggerer) || Opac.partyId(triggerer) != null;
    }

    /**
     * 打印并返回一条<b>可断言的机器行</b>,报告两个第三方后端的接入状态
     * (供测试台 / 冒烟读日志断言;只读,可随时调用)。
     *
     * <p>格式(全 ASCII,便于 grep):
     * {@code AP_LIB_PARTY: back_ftb=on back_opac=off why_ftb=OK why_opac=ClassNotFoundException:xaero...}
     *
     * <ul>
     *   <li>{@code back_*} —— 该后端的<b>反射契约是否解析成功</b>
     *       ({@code on} = 第三方模组在场且签名全部命中;{@code off} = 未安装或签名不符);</li>
     *   <li>{@code why_*} —— 解析失败的原因({@code OK} 表示未失败)。
     *       ⚠️ 原版计分板后端无「未启用」概念,故不为它设字段。</li>
     * </ul>
     *
     * <p>「装了 FTB Teams 却 {@code back_ftb=off}」时,{@code why_ftb} 会直接指出是哪个类/方法没找到
     * —— 这正是本类在 2026-10-01 之前<b>静默失效</b>的那一类问题。
     */
    public static String describeBackends() {
        Ftb.resolve();
        Opac.resolve();
        String line = BACKEND_REPORT_PREFIX
                + " back_ftb=" + onOff(Ftb.enabled)
                + " back_opac=" + onOff(Opac.enabled)
                + " why_ftb=" + Ftb.failureReason
                + " why_opac=" + Opac.failureReason;
        LOGGER.info("{}", line);
        return line;
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }

    // Minecraft 原生 team:同队在线玩家
    private static void collectMcTeamPlayers(Player triggerer, List<Player> members) {
        if (!(triggerer.level() instanceof ServerLevel serverLevel)) return;
        var team = triggerer.getTeam();
        if (team == null) return;
        for (ServerPlayer sp : serverLevel.players()) {
            if (sp != triggerer && sp.getTeam() == team) {
                members.add(sp);
            }
        }
    }

    /**
     * 反射解析失败时的统一处置与记录。
     *
     * @param backend    后端名(仅用于日志)
     * @param failure    失败原因(同时写入该后端的 {@code failureReason})
     * @param signatures 失败前已解析成功的成员数(用于区分「没装」与「装了但签名不符」)
     */
    private static void logResolveFailure(String backend, Throwable failure, int signatures) {
        if (failure instanceof ClassNotFoundException) {
            // 绝大多数玩家的正常状态(没装该模组)⇒ 不进 WARN,避免制造日志噪音。
            LOGGER.debug("[StarEngine] 未安装 {}(队伍判定只用其余后端):{}", backend, failure.toString());
        } else {
            // 「装了但签名不符」才是需要开发者关注的真问题 —— 这正是本类曾静默失效的那一类。
            LOGGER.warn("[StarEngine] {} 在场但队伍判定接入失败(签名不符,失败前已接入 {} 项),退回其余后端:{}",
                    backend, signatures, failure.toString());
        }
    }

    // ══════════════════════════════ FTB Teams 后端 ══════════════════════════════

    /**
     * FTB Teams 后端(惰性反射,只在第一次用到时解析一次;解析或运行期出错即永久关闭本后端)。
     *
     * <p>契约逐条取自上游源码(见类头「核验基准」):
     * <ul>
     *   <li>{@code FTBTeamsAPI.api()} —— {@code common/src/main/java/dev/ftb/mods/ftbteams/api/FTBTeamsAPI.java:26}
     *       (外层类的 <b>static</b> 方法);</li>
     *   <li>{@code FTBTeamsAPI.API} 是 <b>嵌套接口</b> —— 同上 {@code :55};
     *       {@code isManagerLoaded()} {@code :62} / {@code getManager()} {@code :70} /
     *       {@code isClientManagerLoaded()} {@code :78} / {@code getClientManager()} {@code :86};</li>
     *   <li>{@code TeamManager#getTeamForPlayerID(UUID) → Optional<Team>} ——
     *       {@code api/TeamManager.java:50}(⚠️ 另有 {@code getTeamForPlayer(ServerPlayer)} 于 {@code :58};
     *       <b>没有</b> {@code getTeamForPlayer(UUID)});</li>
     *   <li>{@code TeamManager#arePlayersInSameTeam(UUID,UUID) → boolean} —— {@code :92};</li>
     *   <li>{@code ClientTeamManager#getKnownPlayer(UUID) → Optional<KnownClientPlayer>} ——
     *       {@code api/client/ClientTeamManager.java:73};{@code #getTeamByID(UUID)} —— {@code :51};</li>
     *   <li>{@code KnownClientPlayer#teamId()}(record 访问器,<b>无 get 前缀</b>) ——
     *       {@code api/client/KnownClientPlayer.java:18};</li>
     *   <li>{@code Team#getOnlineMembers() → Collection<ServerPlayer>} —— {@code api/Team.java:204};
     *       {@code #getMembers() → Set<UUID>} —— {@code :161};
     *       {@code #isPartyTeam()} —— {@code :93};{@code #isServerTeam()} —— {@code :102}。</li>
     * </ul>
     *
     * <p><b>必需 vs 可选</b>:服务端路径(取队伍、判同队、收成员)全部必需;
     * 客户端路径与「存在性判据」的成员缺失<b>只降低精度、不让整个后端失效</b>。
     */
    private static final class Ftb {

        private static final String API_CLASS = "dev.ftb.mods.ftbteams.api.FTBTeamsAPI";
        private static final String API_IFACE_CLASS = "dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API";
        private static final String TEAM_MANAGER_CLASS = "dev.ftb.mods.ftbteams.api.TeamManager";
        private static final String CLIENT_MANAGER_CLASS = "dev.ftb.mods.ftbteams.api.client.ClientTeamManager";
        private static final String KNOWN_PLAYER_CLASS = "dev.ftb.mods.ftbteams.api.client.KnownClientPlayer";
        private static final String TEAM_CLASS = "dev.ftb.mods.ftbteams.api.Team";

        private static volatile boolean resolved;
        private static volatile boolean enabled;
        private static volatile String failureReason = "NOT_RESOLVED";

        // ── 必需:API 入口(外层类静态方法)
        private static Method apiMethod;              // FTBTeamsAPI.api()
        // ── 必需:管理器访问器(声明在嵌套接口 FTBTeamsAPI$API 上)
        private static Method isManagerLoaded;        // API.isManagerLoaded()
        private static Method getManager;             // API.getManager() -> TeamManager
        // ── 必需:服务端取队伍 / 判同队
        private static Method getTeamForPlayerID;     // TeamManager.getTeamForPlayerID(UUID) -> Optional<Team>
        private static Method arePlayersInSameTeam;   // TeamManager.arePlayersInSameTeam(UUID, UUID)
        // ── 可选:收款成员(两者至少有其一即可;都没有只损失精度)
        private static Method teamGetOnlineMembers;   // Team#getOnlineMembers() -> Collection<ServerPlayer>
        private static Method teamGetMembers;         // Team#getMembers() -> Set<UUID>
        // ── 可选:客户端路径(只在 hasAnyTeam 的客户端分支用到)
        private static Method isClientManagerLoaded;  // API.isClientManagerLoaded()
        private static Method getClientManager;       // API.getClientManager() -> ClientTeamManager
        private static Method getKnownPlayer;         // ClientTeamManager#getKnownPlayer(UUID)
        private static Method getTeamByID;            // ClientTeamManager#getTeamByID(UUID)
        private static Method knownPlayerTeamId;      // KnownClientPlayer#teamId()
        // ── 可选:存在性判据(缺失只降低精度,见 hasTeam)
        private static Method teamIsPartyTeam;        // Team#isPartyTeam()
        private static Method teamIsServerTeam;       // Team#isServerTeam()

        private Ftb() {
        }

        /** 该玩家<b>生效</b>的 Team 对象(个人队伍 / party / server team 皆可能);任一步失败返回 null。 */
        private static Object team(Player player) {
            Object api = api();
            if (api == null) return null;
            try {
                if (player.level().isClientSide()) {
                    // 客户端路径三项**全部可选**(见 resolve 的可选段):任一缺失就当作「本类在客户端不提供
                    // 队伍信息」返回 null —— ⚠️ 绝不能让它抛出去,否则会被下面的 disable() 误判为
                    // 「该后端运行时不可用」而把**服务端**判定一起关掉。
                    if (isClientManagerLoaded == null || getClientManager == null
                            || getKnownPlayer == null || getTeamByID == null || knownPlayerTeamId == null) {
                        return null;
                    }
                    Object manager = clientManager(api);
                    if (manager == null) return null;
                    UUID teamId = clientTeamId(manager, player);
                    return teamId == null ? null : unwrap(getTeamByID.invoke(manager, teamId));
                }
                Object manager = serverManager(api);
                return manager == null ? null : unwrap(getTeamForPlayerID.invoke(manager, player.getUUID()));
            } catch (Throwable t) {
                disable(t);
                return null;
            }
        }

        /**
         * 是否<b>加入了队伍</b>(FTB 语义:party / server team)。
         *
         * <p>不用「Team 对象非空」—— FTB 给每个玩家都建了个人队伍,那样会恒为真,
         * 从而把「未组队 ⇒ 友方作用于全服」的兜底彻底堵死(原实现的缺陷之一)。
         */
        static boolean hasTeam(Player player) {
            if (player == null) return false;
            Object team = team(player);
            if (team == null) return false;
            if (teamIsPartyTeam == null || teamIsServerTeam == null) {
                // 判据方法在假设的版本差异下缺失:退回「有生效队伍对象即算有队伍」。
                // 刻意的偏向 —— 宁可少走「全服皆友方」兜底,也不要把它放大成对全服生效。
                return true;
            }
            try {
                return (boolean) teamIsPartyTeam.invoke(team) || (boolean) teamIsServerTeam.invoke(team);
            } catch (Throwable t) {
                disable(t);
                return false;
            }
        }

        /** 把该玩家所在队伍的<b>在线成员</b>并入 {@code members}(排除自己);失败静默退回、不抛。 */
        static void collectOnlineMembers(Player triggerer, List<Player> members) {
            Object team = team(triggerer);
            if (team == null) return;
            try {
                // 优先 getOnlineMembers()(Collection<ServerPlayer>);缺则退回 getMembers()(Set<UUID>)+世界查找。
                if (teamGetOnlineMembers != null) {
                    Object online = teamGetOnlineMembers.invoke(team);
                    if (online instanceof Iterable<?> iterable) {
                        for (Object member : iterable) {
                            if (member instanceof Player p && p != triggerer) {
                                members.add(p);
                            }
                        }
                    }
                    return;
                }
                if (teamGetMembers != null) {
                    Object ids = teamGetMembers.invoke(team);
                    if (ids instanceof Iterable<?> iterable) {
                        for (Object member : iterable) {
                            if (member instanceof UUID uuid) {
                                Player p = triggerer.level().getPlayerByUUID(uuid);
                                if (p != null && p != triggerer) {
                                    members.add(p);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                disable(t);
            }
        }

        /** 客户端算「同队」用的<b>生效队伍 id</b>({@code KnownClientPlayer#teamId()},record 访问器)。 */
        private static UUID clientTeamId(Object manager, Player player) throws Exception {
            Object known = unwrap(getKnownPlayer.invoke(manager, player.getUUID()));
            return known == null ? null : (UUID) knownPlayerTeamId.invoke(known);
        }

        private static Object serverManager(Object api) throws Exception {
            return (boolean) isManagerLoaded.invoke(api) ? getManager.invoke(api) : null;
        }

        private static Object clientManager(Object api) throws Exception {
            if (isClientManagerLoaded == null || getClientManager == null) return null;
            return (boolean) isClientManagerLoaded.invoke(api) ? getClientManager.invoke(api) : null;
        }

        private static Object api() {
            resolve();
            if (!enabled) return null;
            try {
                return apiMethod.invoke(null);
            } catch (Throwable t) {
                disable(t);
                return null;
            }
        }

        /** {@code Optional<?>} → 内部值;{@code empty} / 非 Optional / null 一律 null。 */
        private static Object unwrap(Object optional) {
            if (!(optional instanceof Optional<?> opt) || opt.isEmpty()) return null;
            return opt.get();
        }

        private static synchronized void resolve() {
            if (resolved) return;
            resolved = true;
            int signatures = 0;
            try {
                Class<?> apiCls = Class.forName(API_CLASS);
                Class<?> managerCls = Class.forName(TEAM_MANAGER_CLASS);
                Class<?> teamCls = Class.forName(TEAM_CLASS);
                apiMethod = apiCls.getMethod("api");
                signatures++;

                // 四个管理器访问器在**嵌套接口** FTBTeamsAPI$API 上(自 1.20.1 最早版起即如此);
                // 万一某版把方法挪回外层类,下面的 fallback 仍能命中。
                Class<?> accessorCls;
                try {
                    accessorCls = Class.forName(API_IFACE_CLASS);
                    accessorCls.getMethod("isManagerLoaded");
                } catch (Throwable nestedMissing) {
                    accessorCls = apiCls;
                }
                isManagerLoaded = accessorCls.getMethod("isManagerLoaded");
                getManager = accessorCls.getMethod("getManager");
                signatures += 2;

                getTeamForPlayerID = managerCls.getMethod("getTeamForPlayerID", UUID.class);
                arePlayersInSameTeam = managerCls.getMethod("arePlayersInSameTeam", UUID.class, UUID.class);
                signatures += 2;

                // ── 以下全部**可选**:缺任何一项都不让本后端失效
                try {
                    teamGetOnlineMembers = teamCls.getMethod("getOnlineMembers");
                    signatures++;
                } catch (Throwable missing) {
                    teamGetOnlineMembers = null;
                }
                try {
                    teamGetMembers = teamCls.getMethod("getMembers");
                    signatures++;
                } catch (Throwable missing) {
                    teamGetMembers = null;
                }
                try {
                    teamIsPartyTeam = teamCls.getMethod("isPartyTeam");
                    teamIsServerTeam = teamCls.getMethod("isServerTeam");
                    signatures += 2;
                } catch (Throwable missing) {
                    teamIsPartyTeam = null;
                    teamIsServerTeam = null;
                }
                try {
                    Class<?> clientCls = Class.forName(CLIENT_MANAGER_CLASS);
                    Class<?> knownCls = Class.forName(KNOWN_PLAYER_CLASS);
                    isClientManagerLoaded = accessorCls.getMethod("isClientManagerLoaded");
                    getClientManager = accessorCls.getMethod("getClientManager");
                    getKnownPlayer = clientCls.getMethod("getKnownPlayer", UUID.class);
                    getTeamByID = clientCls.getMethod("getTeamByID", UUID.class);
                    knownPlayerTeamId = knownCls.getMethod("teamId");
                    signatures += 5;
                } catch (Throwable clientMissing) {
                    // 客户端路径不可用:服务端判定不受影响(本类只在客户端用 hasTeam 时才需要它)。
                    isClientManagerLoaded = null;
                    getClientManager = null;
                    getKnownPlayer = null;
                    getTeamByID = null;
                    knownPlayerTeamId = null;
                }

                enabled = true;
                failureReason = "OK";
                LOGGER.debug("[StarEngine] FTB Teams 队伍判定已接入(命中 {} 项签名)", signatures);
            } catch (Throwable t) {
                enabled = false;
                failureReason = t.getClass().getSimpleName() + ":" + t.getMessage();
                logResolveFailure("FTB Teams", t, signatures);
            }
        }

        private static void disable(Throwable t) {
            if (enabled) {
                enabled = false;
                failureReason = "RUNTIME:" + t.getClass().getSimpleName();
                LOGGER.warn("[StarEngine] FTB Teams 队伍判定已停用(运行时兼容性问题),退回其余后端", t);
            }
        }
    }

    // ══════════════════════════ Open Parties and Claims 后端 ══════════════════════════

    /**
     * Open Parties and Claims(OPAC)后端(惰性反射,<b>仅服务端</b>)。
     *
     * <p>入口链契约取自上游源码(见类头「核验基准」):
     * <ul>
     *   <li>{@code xaero.pac.common.server.api.OpenPACServerAPI} ——
     *       {@code Common/src/main/java/xaero/pac/common/server/api/OpenPACServerAPI.java};
     *       {@code get(MinecraftServer)} 为 <b>static</b> —— {@code :126};
     *       {@code getPartyManager()} —— {@code :64};</li>
     *   <li>{@code IPartyManagerAPI#getPartyByMember(UUID) → IServerPartyAPI} ——
     *       {@code Common/.../server/parties/party/api/IPartyManagerAPI.java:59};</li>
     *   <li>{@code IServerPartyAPI#getId() → UUID} ——
     *       {@code Common/.../server/parties/party/api/IServerPartyAPI.java:83};
     *       {@code #getOnlineMemberStream() → Stream<ServerPlayer>} —— {@code :161};
     *       {@code #getMemberInfoStream() → Stream<IPartyMemberAPI>} —— {@code :59};</li>
     *   <li>{@code IPartyMemberAPI#getUUID() → UUID} ——
     *       {@code Common/.../parties/party/member/api/IPartyMemberAPI.java:34}。</li>
     * </ul>
     *
     * <p>⚠️ 原实现的包名 {@code dev.darkhax.opac.*} <b>整条不存在</b>(真实为 {@code xaero.pac.*}),
     * 成员访问器 {@code getPartyMembers()} 也不存在 —— 两者都已按上面实测的签名改正。
     *
     * <p>⚠️ <b>客户端不接入</b>:OPAC 的客户端侧只暴露「本地玩家自己的 party」,
     * 无法查询任意玩家的队伍归属,故客户端一律返回 {@code null}(与原实现同口径)。
     */
    private static final class Opac {

        private static final String API_CLASS = "xaero.pac.common.server.api.OpenPACServerAPI";
        private static final String MANAGER_CLASS = "xaero.pac.common.server.parties.party.api.IPartyManagerAPI";
        private static final String PARTY_CLASS = "xaero.pac.common.server.parties.party.api.IServerPartyAPI";
        private static final String MEMBER_CLASS = "xaero.pac.common.parties.party.member.api.IPartyMemberAPI";

        private static volatile boolean resolved;
        private static volatile boolean enabled;
        private static volatile String failureReason = "NOT_RESOLVED";

        // ── 必需:入口链
        private static Method getApi;                 // OpenPACServerAPI.get(MinecraftServer) [static]
        private static Method getPartyManager;        // OpenPACServerAPI#getPartyManager()
        private static Method getPartyByMember;       // IPartyManagerAPI#getPartyByMember(UUID)
        private static Method partyGetId;             // IServerPartyAPI#getId()
        // ── 可选:收款成员(两者至少有其一即可)
        private static Method getOnlineMemberStream;  // IServerPartyAPI#getOnlineMemberStream() -> Stream<ServerPlayer>
        private static Method getMemberInfoStream;    // IServerPartyAPI#getMemberInfoStream() -> Stream<IPartyMemberAPI>
        private static Method memberGetUuid;          // IPartyMemberAPI#getUUID()

        private Opac() {
        }

        /** 该玩家所属的 OPAC party;未接入 / 客户端 / 出错一律 {@code null}。 */
        static Object party(Player player) {
            resolve();
            if (!enabled) return null;
            if (player == null || player.level().isClientSide()) return null;
            MinecraftServer server = player.level().getServer();
            if (server == null) return null;
            try {
                Object api = getApi.invoke(null, server);
                Object manager = api == null ? null : getPartyManager.invoke(api);
                return manager == null ? null : getPartyByMember.invoke(manager, player.getUUID());
            } catch (Throwable t) {
                disable(t);
                return null;
            }
        }

        /** 该玩家所属 party 的 id(供「是否已加入队伍」判定);无 party / 未接入一律 {@code null}。 */
        static UUID partyId(Player player) {
            Object party = party(player);
            if (party == null) return null;
            try {
                return (UUID) partyGetId.invoke(party);
            } catch (Throwable t) {
                disable(t);
                return null;
            }
        }

        /** 把该玩家所在 party 的<b>在线成员</b>并入 {@code members}(排除自己);失败静默退回、不抛。 */
        static void collectOnlineMembers(Player triggerer, List<Player> members) {
            Object party = party(triggerer);
            if (party == null) return;
            try {
                // 优先 getOnlineMemberStream()(Stream<ServerPlayer>);缺则退回 getMemberInfoStream()+getUUID()+世界查找。
                if (getOnlineMemberStream != null) {
                    Object streamObj = getOnlineMemberStream.invoke(party);
                    if (streamObj instanceof Stream<?> stream) {
                        stream.forEach(member -> {
                            if (member instanceof Player p && p != triggerer) {
                                members.add(p);
                            }
                        });
                    }
                    return;
                }
                if (getMemberInfoStream != null && memberGetUuid != null) {
                    Object streamObj = getMemberInfoStream.invoke(party);
                    if (streamObj instanceof Stream<?> stream) {
                        stream.forEach(member -> {
                            try {
                                Object id = memberGetUuid.invoke(member);
                                if (id instanceof UUID uuid) {
                                    Player p = triggerer.level().getPlayerByUUID(uuid);
                                    if (p != null && p != triggerer) {
                                        members.add(p);
                                    }
                                }
                            } catch (Throwable t) {
                                disable(t);
                            }
                        });
                    }
                }
            } catch (Throwable t) {
                disable(t);
            }
        }

        private static synchronized void resolve() {
            if (resolved) return;
            resolved = true;
            int signatures = 0;
            try {
                Class<?> apiCls = Class.forName(API_CLASS);
                Class<?> managerCls = Class.forName(MANAGER_CLASS);
                Class<?> partyCls = Class.forName(PARTY_CLASS);
                getApi = apiCls.getMethod("get", MinecraftServer.class);
                getPartyManager = apiCls.getMethod("getPartyManager");
                getPartyByMember = managerCls.getMethod("getPartyByMember", UUID.class);
                partyGetId = partyCls.getMethod("getId");
                signatures += 4;

                // ── 以下**可选**:缺任何一项都不让本后端失效(只降低成员收集的精度)
                try {
                    getOnlineMemberStream = partyCls.getMethod("getOnlineMemberStream");
                    signatures++;
                } catch (Throwable missing) {
                    getOnlineMemberStream = null;
                }
                try {
                    Class<?> memberCls = Class.forName(MEMBER_CLASS);
                    getMemberInfoStream = partyCls.getMethod("getMemberInfoStream");
                    memberGetUuid = memberCls.getMethod("getUUID");
                    signatures += 2;
                } catch (Throwable missing) {
                    getMemberInfoStream = null;
                    memberGetUuid = null;
                }

                enabled = true;
                failureReason = "OK";
                LOGGER.debug("[StarEngine] OPAC 队伍判定已接入(命中 {} 项签名)", signatures);
            } catch (Throwable t) {
                enabled = false;
                failureReason = t.getClass().getSimpleName() + ":" + t.getMessage();
                logResolveFailure("Open Parties and Claims", t, signatures);
            }
        }

        private static void disable(Throwable t) {
            if (enabled) {
                enabled = false;
                failureReason = "RUNTIME:" + t.getClass().getSimpleName();
                LOGGER.warn("[StarEngine] OPAC 队伍判定已停用(运行时兼容性问题),退回其余后端", t);
            }
        }
    }
}
