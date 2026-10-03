package com.merlinkitsune.starenginelib.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;

/**
 * 「效果牌可选中生物」的判定入口（2026-10-03 用户裁决,必须遵守）。
 *
 * <p>与 {@link HostileTargets} **并列**：本类**只**服务「伤害效果牌（活体书页 / 符卡-祸等）的目标选择」，
 * **不改变** {@link HostileTargets#isHostile} 的既有语义 —— 立牌选择器（枪匠 / 大侦探 / 占星师…）、
 * 法伤闸门、飞星、派对关系等仍走原口径。
 *
 * <h2>口径</h2>
 * {@code 效果牌可选中生物 = HostileTargets.isHostile(e) ∪ 未驯服的可驯服生物}
 * <ul>
 *   <li><b>敌对 ∪ 中立（宠物除外）</b>：直接沿用 {@link HostileTargets}，本类不重写。</li>
 *   <li><b>未驯服的可驯服生物</b>：{@link #isUntamedTamable}。</li>
 *   <li><b>显式排除村民 / 流浪商人</b>：{@link Npc}（见下方「为什么仍需这道护栏」）。</li>
 * </ul>
 *
 * <h2>证据（三平台源码 jar 全量扫描 + 第三方实物 jar 字节码）</h2>
 * <ol>
 *   <li>第三方「生灵颂词」对友善生物的判据 = {@code instanceof
 *       net.minecraft.world.entity.animal.Animal}（{@code LivingOde$Events} 的
 *       {@code onFindTarget} / {@code onDamageIncoming} 字节码；村民本就不是 {@code Animal}，
 *       故它天然不含村民）。⚠️ 但本模组**不照搬** {@code Animal}：那会把牛 / 猪 / 羊 / 鸡 / 兔
 *       等被动家畜一并纳入，与 2026-10-03 用户裁决（只扩到「中立 / 可驯服」两档）不符。</li>
 *   <li>{@link OwnableEntity} 的**全部**实现者，三平台实测**只有 2 个**：{@code AbstractHorse}
 *       （1.21.1 / 1.20.1 位于 {@code animal/horse/}、26.1.2 位于 {@code animal/equine/}）与
 *       {@link TamableAnimal}。</li>
 *   <li>{@code AbstractVillager}（村民 / 流浪商人）**不实现** {@code OwnableEntity}、也不是
 *       {@code NeutralMob} / {@code Enemy} ⇒ 本口径**天然**不含它们。
 *       ⚠️ 仍保留 {@link Npc} 护栏，理由三条：① 用户明确要求「额外排除村民」；
 *       ② 防 vanilla 未来把村民接进 {@code OwnableEntity}；③ {@link Npc} 三平台同 FQN
 *       （{@code world.entity.npc.Npc}）且实测**只有 {@code AbstractVillager} 实现它**
 *       ⇒ 排除它不会误伤任何可驯服生物。
 *       ⚠️ **不能**改用 {@code AbstractVillager} 本身当判据：它在 26.1.2 已被移到
 *       {@code npc/villager/}，三平台共用一份 common 源码会编译不过。</li>
 *   <li>{@link OwnableEntity#getOwner()} 三平台签名一致（{@code default @Nullable LivingEntity}）；
 *       ⚠️ **不得**改用 {@code getOwnerUUID()} —— 26.1.2 已删除该方法。
 *       ⚠️ 也不得用类名 / 包路径硬编码（26.1.2 把 {@code animal/horse/**} 整体改名为
 *       {@code animal/equine/**}）。</li>
 * </ol>
 */
public final class CreatureTargets {
    private CreatureTargets() {
    }

    /**
     * 未驯服的可驯服生物：
     * <ul>
     *   <li>未驯服的 {@link TamableAnimal} —— 原版 = <b>狼 / 猫 / 鹦鹉</b>
     *       （{@code isTame()} 三平台签名一致）；</li>
     *   <li>无主的 {@link OwnableEntity} —— 原版 = <b>马 / 驴 / 骡 / 骆驼 / 羊驼 / 行商羊驼</b>
     *       （{@code getOwner() == null}）。</li>
     * </ul>
     * ⚠️ 已驯服的宠物（{@code isTame() == true}）与已被认领的坐骑（{@code getOwner() != null}）
     * **不计入** —— 这正是用户裁决里的「**无主的**可驯服中立生物」。
     */
    public static boolean isUntamedTamable(Entity entity) {
        if (entity == null) return false;
        if (entity instanceof TamableAnimal tamable) return !tamable.isTame();
        if (entity instanceof OwnableEntity ownable) return ownable.getOwner() == null;
        return false;
    }

    /** 效果牌可选中生物（口径见类注释）。村民 / 流浪商人一律排除。 */
    public static boolean isCreatureTarget(Entity entity) {
        if (entity == null) return false;
        if (entity instanceof Npc) return false;
        return HostileTargets.isHostile(entity) || isUntamedTamable(entity);
    }
}
