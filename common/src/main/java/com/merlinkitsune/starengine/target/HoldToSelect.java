package com.merlinkitsune.starengine.target;

import net.minecraft.world.entity.player.Player;

/**
 * 目标选择「手持即选择」（hold-to-select）扩展点。
 *
 * <p>实现了 {@link TargetSelectionAction} 的动作类**按需**同时实现本接口，表示该动作由
 * **主手手持对应物品**触发，而不是按键触发：
 * <ul>
 *   <li>开局由消费方每 tick 驱动：主手持有该物品且当前无会话 ⇒ 自动开局；</li>
 *   <li>收官由消费方的会话管理每 tick 校验 {@link #stillHeld(Player)}：物品一旦离开主手
 *       （切槽 / 丢弃 / 被消耗 / 换成别的物品）即**立即取消会话**；</li>
 *   <li>此类会话**没有倒计时**：会话的过期字段不参与判定，客户端提示也不追加「剩余 N 秒」。</li>
 * </ul>
 *
 * <p>本接口只约束「是否仍手持」这一判定，不提供任何具体物品 / 会话实现：会话生命周期、
 * 取消时机与提示文案全部由消费方负责（前置库不注册事件、不持有玩家数据）。
 *
 * <p>本接口无默认方法 —— 实现者必须给出 {@link #stillHeld(Player)} 的判定。
 *
 * @see TargetSelectionAction
 * @see SelfTargetable
 */
public interface HoldToSelect {
    /** 该动作对应的物品此刻是否仍被玩家**主手**持有（每 tick 由服务端会话校验调用）。 */
    boolean stillHeld(Player player);
}
