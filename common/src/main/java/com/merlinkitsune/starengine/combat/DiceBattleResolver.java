package com.merlinkitsune.starengine.combat;

/**
 * 骰战（dice battle）对骰轮次与伤害合成的**纯算术**部分（2026-09-26 用户裁决）。
 *
 * <h2>对骰规则（统一口径）</h2>
 * <pre>
 *   攻击方掷骰 ⟺ 攻击方满足可掷骰条件
 *   防御方掷骰 ⟺ 防御方满足可掷骰条件
 *   对骰成立   ⟺ 双方都掷
 * </pre>
 * 即「一方能掷则另一方也必须掷，反之亦然」。任一方不满足条件 ⇒ 对骰不成立，退化为按基础
 * 攻防值直接结算（不掷任何骰）。
 *
 * <p>「可掷骰条件」的**具体判据由消费方决定**（如：玩家是否装备骰子、本次伤害是否为近战、
 * 是否属于本模组的骰战伤害类型……），本库只提供判定合取与伤害合成的纯函数，不介入玩法规则。
 *
 * <h2>伤害合成</h2>
 * <pre>
 *   finalDamage = max(1, 攻击方战斗点 × 0.15, 攻击方战斗点 − 防御方战斗点)
 * </pre>
 * 其中「战斗点」= 基础攻防值 + 骰战骰点 + 战斗牌加成（由消费方各自算好后传入）。
 *
 * <h2>下限：绝对 1 点 + 相对 15%（2026-09-26 裁决，2.0.0-SNAPSHOT.2 新增）</h2>
 * 早期语义只有**绝对下限 1 点**。数值仿真显示该语义在「高防目标 + 低攻来源」的档位上会把
 * 结果**恒定压到 1.0**，于是任何修饰器（保护 / 抗性 / 护甲变化）在该档位上的读数都退化为 0 ——
 * 即「机制其实生效了，但被下限吃掉，看起来像没生效」。
 * <p>自本版起在下限里加入**相对项** {@code 攻击方战斗点 × 0.15}：攻击点越高、下限越高，
 * 触底不再把差异抹平；对**未触底**的场景数学上零影响（{@code max} 取不到该项）。
 *
 * <p>⚠️ 该下限仍是**骰战层**的下限，不是最终伤害。消费方的减伤类效果（固定点数减法）必须在本
 * 方法**之后**应用，且**允许把伤害扣到 0**（顺序 = 骰战层 → 乘算因子 → 减算减免）。
 *
 * <p>本类**纯函数、无状态**：不注册注册表条目、不注册事件、不反向依赖任何消费方。
 */
public final class DiceBattleResolver {

    /** 骰战伤害的**绝对**下限（防御方战斗点 ≥ 攻击方战斗点时仍至少造成该点数）。 */
    public static final float MIN_DAMAGE = 1.0F;

    /**
     * 骰战伤害的**相对**下限比例（最终伤害 ≥ 攻击方战斗点 × 该值）。
     *
     * @see #resolve(float, float)
     */
    public static final float RELATIVE_FLOOR_RATIO = 0.15F;

    private DiceBattleResolver() {
    }

    /**
     * 对骰是否成立（双方都能掷骰）。
     *
     * @param attackerRolls 攻击方是否满足可掷骰条件
     * @param defenderRolls 防御方是否满足可掷骰条件
     */
    public static boolean opposedRollHolds(boolean attackerRolls, boolean defenderRolls) {
        return attackerRolls && defenderRolls;
    }

    /**
     * 骰战最终伤害合成（含默认下限：绝对 1 点 ∪ 攻击点 × {@value #RELATIVE_FLOOR_RATIO}）。
     *
     * @param attackPower   攻击方战斗点（基础攻击力 + 骰点 + 战斗牌攻击加成）
     * @param defensePower  防御方战斗点（基础防御力 + 骰点 + 战斗牌防御加成）
     * @return {@code max(1, attackPower × 0.15, attackPower − defensePower)}
     */
    public static float resolve(float attackPower, float defensePower) {
        return resolve(attackPower, defensePower, RELATIVE_FLOOR_RATIO);
    }

    /**
     * 指定相对下限比例的版本（供需要自定义口径的消费方使用）。
     *
     * @param relativeFloorRatio 相对下限比例；传 {@code 0} 即退化为纯「绝对 1 点下限」的旧语义
     */
    public static float resolve(float attackPower, float defensePower, float relativeFloorRatio) {
        float floor = Math.max(MIN_DAMAGE, attackPower * relativeFloorRatio);
        return Math.max(floor, attackPower - defensePower);
    }

    /** {@link #resolve(float, float)} 的 double 入参重载（返回 float，便于直接喂给伤害事件）。 */
    public static float resolve(double attackPower, double defensePower) {
        return resolve((float) attackPower, (float) defensePower);
    }
}
