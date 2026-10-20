package com.merlinkitsune.starengine.combat;

import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 敌对玩家记录表(全局规则,原属主模组 {@code combat.PlayerHostilityTracker},2026-09-22 下沉)。
 *
 * <p>口径:「非同队伍玩家,且死亡前主动攻击过你的玩家,一律视为敌对目标;若玩家死亡则清除敌对立场」。
 *
 * <h2>职责切分(下沉要点)</h2>
 * <ul>
 *   <li><b>库内(本类)</b>:纯粹的数据表 + 查询 + 清理,<b>不注册任何事件</b>
 *       (库的红线:不注册注册表条目、不依赖平台事件 API)。</li>
 *   <li><b>消费方</b>:负责把四个平台事件接到本类的 {@link #recordAttack} / {@link #forget}
 *       上 —— 记进攻({@code LivingDamageEvent})、死亡清({@code LivingDeathEvent})、
 *       死亡重生克隆清({@code PlayerEvent.Clone})、登出清({@code PlayerLoggedOutEvent})。</li>
 * </ul>
 *
 * <h2>记录口径(消费方在调用前须自行把守)</h2>
 * <ol>
 *   <li>仅玩家对玩家,且非本人 —— 本类的 {@link #recordAttack} 会再兜一层。</li>
 *   <li>仅服务端 —— 本类不做维度判定,由调用方在服务端事件里调用。</li>
 *   <li>不被取消的伤害 —— 由调用方按平台语义判定(1.20.1 的 {@code LivingDamageEvent} 可取消)。</li>
 *   <li><b>排除本模组内部窗口</b>({@link InternalDamageWindows})—— 由调用方调用
 *       {@link #recordAttack} 之前判定,或直接使用 {@link #recordAttackIfExternal}。</li>
 * </ol>
 *
 * <p><b>存储</b>:服务端内存静态表,不做持久化(服务器重启即清空,与"死亡清除"同一口径);
 * 仅在服务端主线程读写。
 */
public final class PlayerHostilityTracker {

    /** 受害者 UUID → 「主动攻击过该受害者」的玩家 UUID 集合 */
    private static final Map<UUID, Set<UUID>> HOSTILE_ATTACKERS = new HashMap<>();

    private PlayerHostilityTracker() {
    }

    /**
     * 记录「攻击者主动攻击过受害者」。仅玩家对玩家、非本人时生效。
     *
     * <p><b>不做</b>内部窗口判定 —— 该方法假定调用方已确认这是一次"主动攻击"。
     * 若无法自行判定,请改用 {@link #recordAttackIfExternal}。
     */
    public static void recordAttack(Player victim, Player attacker) {
        if (victim == null || attacker == null) return;
        if (victim == attacker) return;
        HOSTILE_ATTACKERS.computeIfAbsent(victim.getUUID(), key -> new HashSet<>())
                .add(attacker.getUUID());
    }

    /**
     * 便利方法:内部窗口(范围/波及伤害、反击注入)之外才记录。
     * 等价于先查 {@link InternalDamageWindows} 再调 {@link #recordAttack}。
     */
    public static void recordAttackIfExternal(Player victim, Player attacker) {
        if (InternalDamageWindows.isInternalAoe() || InternalDamageWindows.isInCounterChain()) return;
        recordAttack(victim, attacker);
    }

    /**
     * {@code attacker} 是否曾主动攻击过 {@code victim}。
     *
     * <p>注意入参顺序:<b>第一个是受害者、第二个是攻击者</b>(与原主模组一致,
     * 调用点见 {@link HostileTargets#isHostile(net.minecraft.world.entity.Entity, net.minecraft.world.entity.Entity)})。
     */
    public static boolean hasAttacked(Player victim, Player attacker) {
        if (victim == null || attacker == null) return false;
        Set<UUID> attackers = HOSTILE_ATTACKERS.get(victim.getUUID());
        return attackers != null && attackers.contains(attacker.getUUID());
    }

    /** 清除该玩家的全部敌对立场(它作为攻击者的记录 + 它作为目标的记录)。 */
    public static void forget(Player player) {
        if (player != null) forget(player.getUUID());
    }

    /** 清除该 UUID 的全部敌对立场(它作为攻击者的记录 + 它作为目标的记录)。 */
    public static void forget(UUID uuid) {
        if (uuid == null) return;
        HOSTILE_ATTACKERS.remove(uuid);
        for (Set<UUID> attackers : HOSTILE_ATTACKERS.values()) {
            attackers.remove(uuid);
        }
    }

    /** 清空全表(测试/诊断用)。 */
    public static void clearAll() {
        HOSTILE_ATTACKERS.clear();
    }
}
