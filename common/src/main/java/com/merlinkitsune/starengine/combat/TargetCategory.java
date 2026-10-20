package com.merlinkitsune.starengine.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;

/**
 * 「生物立场」四分类的**唯一入口**（2026-09-26 用户裁决，必须遵守）。
 *
 * <p>分类口径：
 * <ul>
 *   <li><b>{@link #HOSTILE}（敌对）</b>：{@link Enemy} 实例 —— 含 {@code Monster} 全部子类，
 *       以及 {@code Ghast}/{@code Phantom}/{@code EnderDragon}/{@code Slime}（含岩浆怪）/
 *       {@code Shulker}/{@code Zoglin}/{@code Hoglin}。平静的敌对类中立生物
 *       （末影人/僵尸猪灵/猪灵/猪灵蛮兵/疣猪兽）**同样计入** —— 它们本身就是 {@link Enemy}。</li>
 *   <li><b>{@link #NEUTRAL}（中立）</b>：{@link NeutralMob} 实例且**未被驯服**。
 *       全原版直接实现者仅 6 个：{@code EnderMan}/{@code ZombifiedPiglin}（二者即 {@link Enemy}，
 *       由上一条覆盖）与<b>狼 / 铁傀儡 / 北极熊 / 蜜蜂</b>。</li>
 *   <li><b>{@link #FRIENDLY}（友好）</b>：**已被驯服**的 {@link TamableAnimal}（狼 / 猫 / 鹦鹉）。</li>
 *   <li><b>{@link #PASSIVE}（被动）</b>：其余全部实体（牛/羊/猪/鸡等）。</li>
 * </ul>
 *
 * <p><b>与本类所在的 {@link HostileTargets} 的一致性（硬约束）</b>：
 * {@code HostileTargets.isHostile(entity)} 为真的条件，与本类返回
 * {@link #HOSTILE} 或 {@link #NEUTRAL} 的条件**必须逐条等价**（两者都再叠加消费方注入的
 * {@code ExtraHostileProbe} seam）。判定顺序因此固定为：
 * <pre>
 *   Enemy            → HOSTILE      （优先：覆盖「既 Enemy 又 NeutralMob」的末影人/僵尸猪灵）
 *   已驯服 TamableAnimal → FRIENDLY   （对应 HostileTargets 的 isTamedPet 排除）
 *   NeutralMob       → NEUTRAL
 *   其余             → PASSIVE
 * </pre>
 * 改动判定顺序前必须先复核 {@code HostileTargets}，否则两处口径会静默分叉。
 *
 * <p><b>为什么顺序不是「Enemy → NeutralMob → TamableAnimal」</b>：狼同时实现
 * {@code NeutralMob} 与 {@code TamableAnimal}，若先判 {@code NeutralMob} 则已驯服的狼会被
 * 误判为中立。故「已驯服」必须在 {@code NeutralMob} 之前拦截 —— 这与
 * {@code HostileTargets.isHostile} 内部「{@code NeutralMob && !isTamedPet}」的短路顺序等价。
 *
 * <p><b>熊猫/骆驼/山羊/羊驼/行商羊驼/海豚/狐狸等不是 {@code NeutralMob}</b>（原版未把它们
 * 标记为中立生物），故落入 {@link #PASSIVE}。若将来需要改判，须另行裁决并同步
 * {@code HostileTargets}。
 */
public enum TargetCategory {

    /** 敌对生物（{@link Enemy}）。初始防御力 2、初始攻击力 5。 */
    HOSTILE,

    /** 中立生物（{@link NeutralMob} 且未驯服）。初始防御力 0、初始攻击力 4。 */
    NEUTRAL,

    /** 被动生物（其余生物，如牛/羊/猪/鸡）。初始防御力 0、初始攻击力 0。 */
    PASSIVE,

    /** 友好生物（已驯服的宠物）。初始防御力 0、初始攻击力 0。 */
    FRIENDLY;

    /**
     * 对实体做四分类。
     *
     * @param entity 待判定实体（允许为 {@code null}，返回 {@link #PASSIVE}）
     * @return 该实体的立场分类，永不为 {@code null}
     */
    public static TargetCategory classify(Entity entity) {
        if (entity == null) return PASSIVE;
        // ① 敌对优先：Enemy 已涵盖 Monster 全部子类与 Slime/Ghast/Phantom 等
        if (entity instanceof Enemy) return HOSTILE;
        // ② 已驯服的宠物先于 NeutralMob 拦截（狼同时实现两者，顺序不可颠倒）
        if (entity instanceof TamableAnimal tamable && tamable.isTame()) return FRIENDLY;
        // ③ 中立生物
        if (entity instanceof NeutralMob) return NEUTRAL;
        // ④ 其余
        return PASSIVE;
    }

    /**
     * 该分类是否属于「敌对侧」（{@link #HOSTILE} 或 {@link #NEUTRAL}）。
     *
     * <p>与 {@code HostileTargets.isHostile} 的原版口径部分**等价**（不含消费方 seam）。
     */
    public boolean isHostileSide() {
        return this == HOSTILE || this == NEUTRAL;
    }
}
