package com.merlinkitsune.starengine.combat;

import net.minecraft.world.entity.Entity;

/**
 * 「生物初始攻防值」查询（2026-09-26 用户裁决，必须遵守）。
 *
 * <p>口径（2.0.0-SNAPSHOT.2，2026-09-26 裁决）：
 * <ul>
 *   <li>敌对 / 中立 / 被动 / 友好生物初始防御力一律 <b>0</b>；</li>
 *   <li>敌对生物初始攻击力 <b>4</b>；中立生物初始攻击力 <b>3</b>；被动 / 友好生物 <b>0</b>。</li>
 * </ul>
 *
 * <p><b>本版的两处下调及原因</b>（独立数值仿真标定，见消费方
 * <code>tools/balance_sim/dice_combat_tuning.py</code>）：
 * <ul>
 *   <li>初始防御 2 → 0：生物防御起始项把玩家前期输出压到 1 点下限附近（打不动怪）；
 *       归零后玩家输出下限抬回原版水平。</li>
 *   <li>初始攻击 5 → 4（中立同步 4 → 3）：生物攻击点在 2.0.0 起**不再包含事件原值**
 *       （<code>getNewDamage()</code> 已按裁决移除），若不补偿，前期承伤会高出约 1 点/击。</li>
 * </ul>
 *
 * <p>初始值只是**加成起点**：最终防御力/攻击力还要叠加「当前护甲值 / 盔甲韧性 / 生物自身伤害值」
 * 等项，换算见 {@link CombatFormula}。
 *
 * <p>本类**纯查表、无状态**：不注册注册表条目、不注册事件、不反向依赖任何消费方。
 */
public final class TargetBattleStats {

    /** 敌对生物初始防御力。 */
    public static final int HOSTILE_BASE_DEFENSE = 0;
    /** 中立生物初始防御力。 */
    public static final int NEUTRAL_BASE_DEFENSE = 0;
    /** 被动生物初始防御力。 */
    public static final int PASSIVE_BASE_DEFENSE = 0;
    /** 友好生物（已驯服宠物）初始防御力。 */
    public static final int FRIENDLY_BASE_DEFENSE = 0;

    /** 敌对生物初始攻击力。 */
    public static final int HOSTILE_BASE_ATTACK = 4;
    /** 中立生物初始攻击力。 */
    public static final int NEUTRAL_BASE_ATTACK = 3;
    /** 被动生物初始攻击力。 */
    public static final int PASSIVE_BASE_ATTACK = 0;
    /** 友好生物（已驯服宠物）初始攻击力。 */
    public static final int FRIENDLY_BASE_ATTACK = 0;

    private TargetBattleStats() {
    }

    /** 按分类取初始防御力。 */
    public static int baseDefense(TargetCategory category) {
        if (category == null) return PASSIVE_BASE_DEFENSE;
        return switch (category) {
            case HOSTILE -> HOSTILE_BASE_DEFENSE;
            case NEUTRAL -> NEUTRAL_BASE_DEFENSE;
            case PASSIVE -> PASSIVE_BASE_DEFENSE;
            case FRIENDLY -> FRIENDLY_BASE_DEFENSE;
        };
    }

    /** 按分类取初始攻击力。 */
    public static int baseAttack(TargetCategory category) {
        if (category == null) return PASSIVE_BASE_ATTACK;
        return switch (category) {
            case HOSTILE -> HOSTILE_BASE_ATTACK;
            case NEUTRAL -> NEUTRAL_BASE_ATTACK;
            case PASSIVE -> PASSIVE_BASE_ATTACK;
            case FRIENDLY -> FRIENDLY_BASE_ATTACK;
        };
    }

    /** 对实体取初始防御力（内部按 {@link TargetCategory#classify} 分类）。 */
    public static int baseDefense(Entity entity) {
        return baseDefense(TargetCategory.classify(entity));
    }

    /** 对实体取初始攻击力（内部按 {@link TargetCategory#classify} 分类）。 */
    public static int baseAttack(Entity entity) {
        return baseAttack(TargetCategory.classify(entity));
    }
}
