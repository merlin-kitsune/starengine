package com.merlinkitsune.starengine.target;

/**
 * 目标选择「允许对自身使用」扩展点。
 *
 * <p>实现了 {@link TargetSelectionAction} 的动作类**按需**同时实现本接口，并覆写
 * {@link #allowSelf()} 返回 {@code true}；消费方在会话启动时把该标志快照进会话并随开局包下发，
 * 客户端据此追加「或按下 鼠标右键 对自身使用」的提示，并在右键时改为提交对自身的确认。
 *
 * <p>放行判定由 {@link SelectorTargets} 的四参 {@code matches(type, selector, target, allowSelf)}
 * 承担：仅当本次会话允许自身**且**目标就是选择者时才放行。{@link TargetType} 的 {@code matches}
 * 始终排除自身，故**不需要**放宽枚举语义。
 *
 * <p>未实现本接口（或实现但返回 {@code false}）的动作，行为与引入本接口前逐字一致。
 *
 * @see TargetSelectionAction
 * @see SelectorTargets
 */
public interface SelfTargetable {
    /** 本次选择会话是否允许把技能用在选择者自己身上（缺省 false = 不允许）。 */
    default boolean allowSelf() {
        return false;
    }
}
