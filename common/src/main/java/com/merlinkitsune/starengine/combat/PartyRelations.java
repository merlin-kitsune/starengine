package com.merlinkitsune.starengine.combat;

import com.merlinkitsune.starengine.component.GameplayConstants;
import com.merlinkitsune.starengine.event.EventTargetCollector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 「同队 / 盟友」判定与**事件目标收集**的统一入口(自消费方下沉,2026-10-19)。
 *
 * <h2>为什么需要本类</h2>
 * 原版队友判定只有计分板({@code player.getTeam() != null && player.getTeam() == other.getTeam()}),
 * 而整合包里的队伍<b>不一定</b>走计分板:{@code FTB Teams} 用自己的一套 team/party 数据(这类玩家的
 * 计分板 {@code getTeam()} 恒为 {@code null}),{@code Open Parties and Claims}(OPAC)同理
 * ⇒ 队友判定失效:同一 FTB 队伍内仍能互相造成伤害、AoE 仍把队友算进敌对目标。
 *
 * <h2>判定顺序(并集,任一命中即为真)</h2>
 * <ol>
 *   <li><b>原版计分板队伍</b> —— {@code Entity#getTeam()} 为同一实例;</li>
 *   <li><b>FTB Teams</b> —— 服务端 {@code TeamManager#arePlayersInSameTeam(UUID,UUID)};
 *       客户端 {@code ClientTeamManager#getKnownPlayer(UUID)} → {@code KnownClientPlayer#teamId()};</li>
 *   <li><b>OPAC</b> —— 两个 UUID 的 party 相同,或两个 party 互为盟友({@code IServerPartyAPI#isAlly(UUID)})。</li>
 * </ol>
 *
 * <h2>⚠️ 单一事实源(重要)</h2>
 * 本类<b>不</b>持有任何第三方模组的反射契约。FTB Teams / OPAC 的类名、方法签名、惰性解析与
 * 「解析或调用失败一次即永久关闭该后端」的状态机**只有一份**,在
 * {@link EventTargetCollector} 内({@code Ftb} / {@code Opac});本类一律委托它的
 * {@link EventTargetCollector#isSameTeam(Player, Player)} /
 * {@link EventTargetCollector#hasAnyTeam(Player)} /
 * {@link EventTargetCollector#isSameTeamAmongEnabledSystems(Player, Player)}。
 * {@link #reportBackends()} 也只读 {@link EventTargetCollector#isFtbBackendAvailable()} 等访问器。
 * ⇒ <b>禁止</b>在本类或其消费方再复制一套 {@code dev.ftb.mods.ftbteams.*} / {@code xaero.pac.*} 反射。
 *
 * <p>收敛理由:两处反射此前各自演化,曾出现「同一上游 API 两侧契约不一致、其中一侧恒为未启用」的
 * 静默失效(消费方模组侧 2026-10-01 修复)。语义上两侧判定口径本来就相同(计分板实例相等 ∪ FTB 生效队伍
 * ∪ OPAC party/盟友),合并无口径分歧;差异只在配置开关的**取用时机**(见
 * {@link #isSameTeamAmongEnabledSystems})。
 *
 * <h2>失败方向(安全)</h2>
 * 任一后端不可用 ⇒ 该后端视为「不提供队伍信息」⇒ 判定退回其余后端,最终退回原版计分板口径
 * ⇒ 最坏情况与改动前一致。后端的实际接入状态由 {@link #reportBackends()} 打出可断言的机器行
 * ({@code AP_PARTY:},格式与下游消费方既有断言保持一致)。
 *
 * <h2>平台约束</h2>
 * 本类位于 {@code common} 源根,被四个平台子项目各编译一次:
 * 只使用 {@code Entity#getTeam()} / {@code Player#level()} / {@code ServerLevel#players()} 等
 * 三版本同签名 API 与 JDK 类型,<b>不引用</b>任何加载器专有类型,也不引用任何客户端专属类
 * (本类是双端类;FTB 客户端路径同样在 {@link EventTargetCollector} 内经反射调用)。
 * OPAC 后端**仅服务端**:客户端 API 只暴露本地玩家自己的 party,无法查询任意两名玩家的归属。
 */
public final class PartyRelations {

    private static final Logger LOGGER = LoggerFactory.getLogger("starengine.PartyRelations");

    private PartyRelations() {
    }

    // ══════════════════════════════ 公开判定 ══════════════════════════════

    /**
     * 两名实体是否属于**同一队伍**(原版计分板 ∪ FTB Teams ∪ OPAC,含 OPAC 的盟友队伍)。
     *
     * <p>目标不是玩家时只可能命中原版计分板(第三方队伍系统只覆盖玩家)。
     */
    public static boolean isSameTeam(Entity a, Entity b) {
        if (a == null || b == null) return false;
        if (a == b) return true;
        var teamA = a.getTeam();
        if (teamA != null && teamA == b.getTeam()) return true;
        if (!(a instanceof Player pa) || !(b instanceof Player pb)) return false;
        return EventTargetCollector.isSameTeam(pa, pb);
    }

    /**
     * 该实体是否**已在任一队伍系统中加入队伍**(原版计分板 ∪ FTB ∪ OPAC)。
     *
     * <p>用于「任一方无队伍即视为友方」这类宽松口径:若只看计分板,FTB 队伍成员会被误判为
     * 「无队伍」,从而对任何玩家都算友方 —— 那正是队伍系统失效的另一面。
     *
     * <p>⚠️ FTB 的判据是 **party / server team**,<b>不是</b>「存在 Team 对象」:
     * FTB 给每个玩家都建了个人队伍,按后者判定会恒真、把兜底堵死(判据在
     * {@link EventTargetCollector#hasAnyTeam(Player)} 内,本类不重复实现)。
     */
    public static boolean hasTeam(Entity entity) {
        if (entity == null) return false;
        if (entity.getTeam() != null) return true;
        if (!(entity instanceof Player player)) return false;
        return EventTargetCollector.hasAnyTeam(player);
    }

    /**
     * 「盟友豁免 + 敌对判定」的统一入口:先做 {@link #isSameTeam} 豁免,再委托库的全局唯一入口
     * {@link HostileTargets#isHostile(Entity, Entity)}。
     *
     * <p>{@code viewer} 为 {@code null}(如无归属的落雷)时不做豁免,直接委托库的既有语义。
     */
    public static boolean isHostileTo(Entity viewer, Entity target) {
        if (target == null) return false;
        if (isSameTeam(viewer, target)) return false;
        return HostileTargets.isHostile(viewer, target);
    }

    /**
     * 收集「与 {@code triggerer} 同队」的在线玩家(排除自己);语义与
     * {@link EventTargetCollector#collectTeamPlayers(Player)} 一致:
     * <b>未加入任何队伍 ⇒ 返回全服在线玩家</b>。
     *
     * <p>三个队伍系统的启用开关沿用 {@code GameplayConstants.EVENT_APPLY_MC_TEAM / _FTB_TEAM / _OPAC}
     * (三者默认均 {@code true}),口径与 {@link EventTargetCollector} 完全一致。
     */
    public static List<Player> collectTeamPlayers(Player triggerer) {
        List<Player> members = new ArrayList<>();
        if (triggerer == null || triggerer.level().isClientSide()) return members;
        if (!(triggerer.level() instanceof ServerLevel serverLevel)) return members;
        boolean anyEnabled = GameplayConstants.EVENT_APPLY_MC_TEAM
                || GameplayConstants.EVENT_APPLY_FTB_TEAM
                || GameplayConstants.EVENT_APPLY_OPAC;
        for (ServerPlayer sp : serverLevel.players()) {
            if (sp == triggerer) continue;
            if (sameTeamAmongEnabledSystems(triggerer, sp)) members.add(sp);
        }
        if (members.isEmpty() && anyEnabled && !hasTeam(triggerer)) {
            for (ServerPlayer sp : serverLevel.players()) {
                if (sp != triggerer) members.add(sp);
            }
        }
        return members;
    }

    /**
     * 「**事件效果目标**」的统一收集入口(2026-10-03 口径)。
     *
     * <p>与 {@link #collectTeamPlayers(Player)} 的区别:后者是「队友 + 无队伍时全服」的**旧**口径,
     * 本方法按新规则给出**事件广播**的目标集合:
     *
     * <ul>
     *   <li><b>触发者已加入队伍</b> ⇒ 全体队友 ∪ 半径内**非队友**玩家
     *       (队友不再按距离判定,故不存在「同队重复判定」);</li>
     *   <li><b>触发者未加入任何队伍</b> ⇒ 全部**无队伍**在线玩家 ∪ 半径内**所有**玩家
     *       (范围内**不做**无队伍筛选 —— 有队伍的玩家同样收到)。</li>
     * </ul>
     *
     * <p>触发者本人**恒**在集合内;队伍判定沿用三条后端的三开关口径
     * ({@link #sameTeamAmongEnabledSystems}),与 {@link #collectTeamPlayers(Player)} 同源。
     *
     * @param radius 半径(格),按**三维距离**判定
     */
    public static List<Player> collectEventTargets(Player triggerer, double radius) {
        List<Player> targets = new ArrayList<>();
        if (triggerer == null || triggerer.level().isClientSide()) return targets;
        if (!(triggerer.level() instanceof ServerLevel serverLevel)) return targets;
        targets.add(triggerer);
        double r2 = radius * radius;
        boolean selfHasTeam = hasTeam(triggerer);
        for (ServerPlayer sp : serverLevel.players()) {
            if (sp == triggerer) continue;
            boolean inRange = sp.distanceToSqr(triggerer) <= r2;
            if (selfHasTeam) {
                // 有队伍:全队 ∪ 半径内非队友
                if (sameTeamAmongEnabledSystems(triggerer, sp) || inRange) targets.add(sp);
            } else {
                // 无队伍:无队伍者 ∪ 半径内所有玩家
                if (inRange || !hasTeam(sp)) targets.add(sp);
            }
        }
        return targets;
    }

    /**
     * 按「已启用的队伍系统」判定同队 —— 单一实现在
     * {@link EventTargetCollector#isSameTeamAmongEnabledSystems(Player, Player)}(本类只做转发)。
     */
    private static boolean sameTeamAmongEnabledSystems(Player a, Player b) {
        return EventTargetCollector.isSameTeamAmongEnabledSystems(a, b);
    }

    // ══════════════════════════════ 诊断机器行 ══════════════════════════════

    /**
     * 打印一条**可断言的机器行**,报告三条后端的接入状态(供测试台 / 冒烟读日志断言)。
     *
     * <p>格式(全 ASCII,便于 grep):
     * {@code AP_PARTY: sw_mc=true sw_ftb=true sw_opac=true back_ftb=on back_opac=off why_ftb=OK why_opac=ClassNotFoundException:xaero...}
     *
     * <ul>
     *   <li>{@code sw_*} —— 库侧三条启用开关(公共配置项);</li>
     *   <li>{@code back_ftb} / {@code back_opac} —— 该后端的**反射契约是否解析成功**;</li>
     *   <li>{@code why_*} —— 解析失败的原因({@code OK} 表示未失败)。</li>
     * </ul>
     *
     * <p>⚠️ 后端状态**不**在本类计算,而是读 {@link EventTargetCollector} 的同一份状态机
     * ({@link EventTargetCollector#isFtbBackendAvailable()} 等);本方法只负责拼装
     * {@code AP_PARTY:} 这一既有机器行格式(下游断言依赖该前缀)。
     *
     * <p>本方法**只读**:不触碰任何玩家状态,可在 common setup 阶段调用。
     */
    public static String reportBackends() {
        String line = "AP_PARTY: sw_mc=" + GameplayConstants.EVENT_APPLY_MC_TEAM
                + " sw_ftb=" + GameplayConstants.EVENT_APPLY_FTB_TEAM
                + " sw_opac=" + GameplayConstants.EVENT_APPLY_OPAC
                + " back_ftb=" + onOff(EventTargetCollector.isFtbBackendAvailable())
                + " back_opac=" + onOff(EventTargetCollector.isOpacBackendAvailable())
                + " why_ftb=" + EventTargetCollector.ftbBackendFailureReason()
                + " why_opac=" + EventTargetCollector.opacBackendFailureReason();
        LOGGER.info("{}", line);
        return line;
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }
}
