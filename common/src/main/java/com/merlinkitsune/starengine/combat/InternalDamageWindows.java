package com.merlinkitsune.starengine.combat;

/**
 * 「本模组内部伤害窗口」的**平台注入 seam**。
 *
 * <p>为什么需要它:{@link PlayerHostilityTracker} 的口径要求「只记**主动攻击**」——
 * 模组内部的范围/波及伤害(溅射、AOE)与反击注入都不是主动攻击,否则会自造敌对立场并互相升级
 * (A 溅射 C ⇒ C 敌视 A ⇒ C 溅射 A ⇒ …)。而这两个窗口的开关状态由**主模组**的
 * {@code combat.DiceCombatEvents} 持有(该类的静态字段与生命期都属玩法实现,不宜下沉),
 * 故库内只留判定接口,由消费方在启动时把实现注入进来。
 *
 * <p>未注入时的默认语义:**一律返回 false**(即"不在任何内部窗口内")。
 * 这是安全方向 —— 漏判只会多记一条敌对立场,而误判会让真实攻击不计入。消费方应尽早在
 * 模组构造/公共初始化中调用 {@link #install}。
 */
public final class InternalDamageWindows {

    /** 判定器:入参无用,实现方直接读自己的窗口状态。 */
    @FunctionalInterface
    public interface Probe {
        boolean isInternal();
    }

    private static volatile Probe aoeProbe = () -> false;
    private static volatile Probe counterProbe = () -> false;

    private InternalDamageWindows() {
    }

    /**
     * 注入两条窗口判定(消费方启动时调用一次)。
     *
     * @param aoeProbe     是否处于「本模组内部范围/波及伤害」窗口
     * @param counterProbe 是否处于「反击注入」窗口
     */
    public static void install(Probe aoeProbe, Probe counterProbe) {
        InternalDamageWindows.aoeProbe = (aoeProbe != null) ? aoeProbe : () -> false;
        InternalDamageWindows.counterProbe = (counterProbe != null) ? counterProbe : () -> false;
    }

    /** 当前是否处于内部范围/波及伤害窗口。 */
    public static boolean isInternalAoe() {
        return aoeProbe.isInternal();
    }

    /** 当前是否处于反击注入窗口。 */
    public static boolean isInCounterChain() {
        return counterProbe.isInternal();
    }
}
