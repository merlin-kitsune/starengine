package com.merlinkitsune.starengine.combat;

/**
 * 防御力换算公式（2026-09-26 用户裁决，必须遵守）。
 *
 * <h2>玩家侧</h2>
 * <pre>
 *   playerDefense = 4 + 当前护甲值 × 0.30 + 0.85 × 盔甲韧性
 * </pre>
 * 原实现额外有 {@code Math.min(护甲, 20)} 与 {@code Math.min(有效护甲, 20)} 两道上限，
 * 自 2.0.0 起**两侧一并移除**（用户 2026-09-26 裁决），护甲按其真实值参与换算。
 *
 * <h2>生物侧</h2>
 * <pre>
 *   mobDefense = baseDefense(category) + 当前护甲值 × 0.40 + 1.0 × 盔甲韧性   ← 最终取整
 * </pre>
 * 其中 {@code baseDefense} 见 {@link TargetBattleStats}（敌对 0 / 中立 0 / 被动 0 / 友好 0）。
 *
 * <h2>系数取值的来历（2.0.0-SNAPSHOT.2，2026-09-26 裁决）</h2>
 * 独立数值仿真（消费方仓库 <code>tools/balance_sim/dice_combat_tuning.py</code>）显示：旧系数下
 * 「裸装被低阶怪打穿、满配又被 1 点下限压平」，曲线两头同时偏离原版。参数经蒙特卡洛标定改为
 * 「<b>抬基数 + 凹斜率</b>」：
 * <ul>
 *   <li><b>基数</b>：玩家起始 2 → 4（治前期太痛）、生物起始 2 → 0（治前期打不动怪）；</li>
 *   <li><b>斜率</b>：玩家护甲系数 0.5 → 0.30、韧性系数 1.4 → 0.85、生物护甲系数 0.5 → 0.40、
 *       生物韧性系数 1.125 → 1.0（把「裸装 → 满配」的跨度凹化，避免「穿上好甲就无敌」）。</li>
 * </ul>
 * ⚠️ <b>「护甲 → 防御力」不再是统一的「÷2」</b>：玩家与生物两侧系数自本版起分道，故原
 * {@code ARMOR_DIVISOR} 常量已删除，改由 {@link #PLAYER_ARMOR_COEF} / {@link #MOB_ARMOR_COEF}
 * 分别表达（SNAPSHOT.2 内的破坏性变更）。
 *
 * <h2>护甲取值口径（兼容性硬约束，未变）</h2>
 * 调用方传入的 {@code armor} **必须是属性终值**（{@code LivingEntity#getArmorValue()}）——
 * 即「所有状态效果与第三方模组（如七咒之戒）修饰完毕之后的真实护甲值」。
 * 本库**不做**任何二次修正、不识别任何具体模组 ⇒ 第三方效果恒先生效、永远不被本系统覆盖，
 * 这是「最大模组兼容性」的实现基础。
 *
 * <p>本类**纯函数、无状态**：不注册注册表条目、不注册事件、不反向依赖任何消费方。
 */
public final class CombatFormula {

    /** 玩家侧起始防御力。 */
    public static final double PLAYER_BASE_DEFENSE = 4.0;
    /** 玩家侧护甲值 → 防御力的系数。 */
    public static final double PLAYER_ARMOR_COEF = 0.30;
    /** 玩家侧盔甲韧性系数。 */
    public static final double PLAYER_TOUGHNESS_COEF = 0.85;
    /** 生物侧护甲值 → 防御力的系数。 */
    public static final double MOB_ARMOR_COEF = 0.40;
    /** 生物侧盔甲韧性系数。 */
    public static final double MOB_TOUGHNESS_COEF = 1.0;

    private CombatFormula() {
    }

    /**
     * 玩家侧防御力换算（不含骰点与战斗牌加成）。
     *
     * @param armor     当前护甲值（属性终值，建议 {@code LivingEntity#getArmorValue()}）
     * @param toughness 当前盔甲韧性（属性终值）
     */
    public static double playerDefense(double armor, double toughness) {
        return PLAYER_BASE_DEFENSE + armor * PLAYER_ARMOR_COEF + PLAYER_TOUGHNESS_COEF * toughness;
    }

    /**
     * 生物侧防御力换算（不含骰点与战斗牌加成）。
     *
     * @param category  生物立场分类（决定起始项，当前四分类均为 0）
     * @param armor     当前护甲值（属性终值）
     * @param toughness 当前盔甲韧性（属性终值）
     */
    public static double mobDefense(TargetCategory category, double armor, double toughness) {
        return TargetBattleStats.baseDefense(category) + armor * MOB_ARMOR_COEF + MOB_TOUGHNESS_COEF * toughness;
    }

    /**
     * 生物侧防御力换算并**最终取整**（向下取整，与既有显示口径 {@code Math.floor} 一致）。
     */
    public static int mobDefenseInt(TargetCategory category, double armor, double toughness) {
        return (int) Math.floor(mobDefense(category, armor, toughness));
    }
}
