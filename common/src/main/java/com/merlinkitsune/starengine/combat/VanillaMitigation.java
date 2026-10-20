package com.merlinkitsune.starengine.combat;

/**
 * 原版「抗性提升 + 保护附魔」两条**魔法减伤**通道的纯算术复刻（2026-09-26 用户裁决）。
 *
 * <h2>为什么需要它</h2>
 * 原版 {@code LivingEntity#actuallyHurt} 的结算次序是：
 * <pre>
 *   护甲减免 → 抗性 + 保护附魔减免 → [本模组在此覆盖式写入骰战终值] → 吸收(黄心) → setHealth
 * </pre>
 * 骰战在事件点用自算值**整段覆盖**，于是「抗性 / 保护」这两条已经算完的减免被一并丢弃
 * ⇒ 它们在骰战下完全失效。<b>护甲不在此列</b>：骰战把护甲折算进了「防御力」减法项，
 * 已经生效（见 {@link CombatFormula}）。
 *
 * <p>因此本类只复刻**剩下的一步**：把「抗性 × 保护」算成一个乘算因子，由消费方在
 * {@link DiceBattleResolver#resolve} **之后**乘上去，使两条通道在骰战下与原版等价。
 *
 * <h2>复刻依据（三版本逐行一致）</h2>
 * <pre>
 *   // 抗性提升：amplifier = 效果等级 − 1（I 级 = 0）
 *   i = (amplifier + 1) × 5
 *   damage = max(damage × (25 − i) / 25, 0)
 *   // 保护附魔（CombatRules#getDamageAfterMagicAbsorb）
 *   points = clamp(保护点数, 0, 20)
 *   damage = damage × (1 − points / 25)
 * </pre>
 * 三处版本差异只在**如何取到「保护点数」**，不在公式本身 ⇒ 取数由消费方负责、本类只做纯算术。
 *
 * <p>⚠️ 本类**不含** {@code BYPASSES_EFFECTS} / {@code BYPASSES_RESISTANCE} /
 * {@code BYPASSES_ENCHANTMENTS} 三个伤害标签门禁 —— 消费方若需完全等价，应在调用前自行判定
 * （骰战主伤害走原版 {@code player_attack}，不命中这三个标签）。
 *
 * <p>本类**纯函数、无状态**：不注册注册表条目、不注册事件、不反向依赖任何消费方。
 */
public final class VanillaMitigation {

    /** 抗性提升每级提供的减伤百分点（原版 {@code (amplifier + 1) * 5}）。 */
    public static final int RESISTANCE_PERCENT_PER_LEVEL = 5;
    /** 抗性通道的分母（原版 {@code 25}）。 */
    public static final float RESISTANCE_DENOMINATOR = 25.0F;
    /** 保护附魔点数的生效上限（原版 {@code CombatRules#getDamageAfterMagicAbsorb} 的上限）。 */
    public static final float MAX_PROTECTION_POINTS = 20.0F;

    private VanillaMitigation() {
    }

    /**
     * 抗性提升通道的减伤系数。
     *
     * @param resistanceAmplifier 抗性效果的 amplifier（I 级 = 0、II 级 = 1…）；
     *                            {@code null} 表示未持有该效果 ⇒ 返回 1.0（不减免）
     * @return 取值范围 {@code [0, 1]}；I 级 = 0.8、II 级 = 0.6、V 级及以上 = 0
     */
    public static float resistanceFactor(Integer resistanceAmplifier) {
        if (resistanceAmplifier == null) {
            return 1.0F;
        }
        int i = (resistanceAmplifier + 1) * RESISTANCE_PERCENT_PER_LEVEL;
        return Math.max((RESISTANCE_DENOMINATOR - i) / RESISTANCE_DENOMINATOR, 0.0F);
    }

    /**
     * 保护附魔通道的减伤系数。
     *
     * @param protectionPoints 保护点数（1.21.1 / 26.1.2 取 {@code float}，1.20.1 取 {@code int} 后转 float）；
     *                         负数按 0 计，超过 {@value #MAX_PROTECTION_POINTS} 按上限计
     * @return 取值范围 {@code [0.2, 1]}；全套保护 IV（16 点）= 0.36
     */
    public static float protectionFactor(float protectionPoints) {
        float points = Math.min(Math.max(protectionPoints, 0.0F), MAX_PROTECTION_POINTS);
        return 1.0F - points / RESISTANCE_DENOMINATOR;
    }

    /**
     * 两条通道的合成系数（≈ 原版 {@code LivingEntity#getDamageAfterMagicAbsorb} 的净效果）。
     *
     * @param resistanceAmplifier 抗性 amplifier，{@code null} = 无抗性
     * @param protectionPoints    保护点数
     * @return 直接乘到骰战终值上的因子
     */
    public static float magicAbsorbFactor(Integer resistanceAmplifier, float protectionPoints) {
        return resistanceFactor(resistanceAmplifier) * protectionFactor(protectionPoints);
    }

    /** {@link #magicAbsorbFactor(Integer, float)} 的 double 入参重载（便于与骰战链上的 double 值直连）。 */
    public static float magicAbsorbFactor(Integer resistanceAmplifier, double protectionPoints) {
        return magicAbsorbFactor(resistanceAmplifier, (float) protectionPoints);
    }
}
