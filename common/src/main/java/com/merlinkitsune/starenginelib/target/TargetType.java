package com.merlinkitsune.starenginelib.target;

import com.merlinkitsune.starenginelib.combat.CreatureTargets;
import com.merlinkitsune.starenginelib.combat.HostileTargets;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * 目标选择器的可指定目标类型。
 * 每次选择会话由 {@link TargetSelectionAction#targetType()} 决定允许的目标种类，
 * 客户端用于过滤准星目标（避免错选），服务端用于确认时二次校验（权威判定）。
 */
public enum TargetType {
    /** 仅玩家（排除选择者自身） */
    PLAYER {
        @Override
        public boolean matches(Player selector, LivingEntity target) {
            return target instanceof Player && target != selector;
        }
    },
    /** 仅敌对生物（vanilla {@link Enemy} 标记接口：僵尸/骷髅/掠夺者等） */
    ENEMY {
        @Override
        public boolean matches(Player selector, LivingEntity target) {
            return target instanceof Enemy;
        }
    },
    /** 任意活体目标（玩家/敌对/中立/被动，排除选择者自身） */
    LIVING {
        @Override
        public boolean matches(Player selector, LivingEntity target) {
            return target != selector;
        }
    },
    /**
     * 敌对生物 或 非队友玩家（立牌主动技能专用，如占星师虚弱印记/秘密侦探隐匿调查）：
     * - 敌对生物（vanilla {@link Enemy}）→ 可选中（客户端显示红色高亮）；
     * - 玩家且不属于选择者队伍 → 可选中（黄色高亮）；选择者无队伍时对所有其他玩家生效；
     * - 队友玩家 / 被动生物 / 自己 → 不可选中。
     */
    ENEMY_OR_RIVAL {
        @Override
        public boolean matches(Player selector, LivingEntity target) {
            if (target instanceof Enemy) return true;
            if (target instanceof Player other && other != selector) {
                // 选择者无队伍 → 所有玩家可选;有队伍 → 仅非队友玩家可选
                return selector.getTeam() == null || selector.getTeam() != other.getTeam();
            }
            return false;
        }
    },
    /**
     * 敌对目标 ∪ 未驯服的可驯服生物（**效果牌专用**；2026-10-03 用户裁决）。
     *
     * <p>口径见 {@link CreatureTargets}：比 {@link #ENEMY} 多出「未驯服的狼 / 猫 / 鹦鹉」与
     * 「无主的马 / 驴 / 骡 / 骆驼 / 羊驼」；**不含玩家**；已驯服宠物与村民 / 流浪商人不在内。
     * ⚠️ 立牌选择器**不用**本类型（仍走 {@link #ENEMY} / {@link #ENEMY_OR_RIVAL}）。
     */
    CREATURE {
        @Override
        public boolean matches(Player selector, LivingEntity target) {
            return CreatureTargets.isCreatureTarget(target);
        }
    },
    /** {@link #CREATURE} ∪ 非队友玩家（**符卡-祸专用**）。 */
    CREATURE_OR_RIVAL {
        @Override
        public boolean matches(Player selector, LivingEntity target) {
            if (CreatureTargets.isCreatureTarget(target)) return true;
            if (target instanceof Player other && other != selector) {
                return selector.getTeam() == null || selector.getTeam() != other.getTeam();
            }
            return false;
        }
    },
    /**
     * 非敌对生物（**治疗 / 功能效果牌专用**；2026-10-03 用户裁决，同日二版定稿）。
     *
     * <p>口径 = 「**不属于原版敌对生物**（{@code Enemy} 标志）的活体」。判据委托本库的
     * {@link HostileTargets#isHostileMob}（**生物类别口径**，**不是** {@code isHostile} 那条战斗口径），
     * 经 {@link SelectorTargets} 路由后生效 ⇒ **未驯服的狼 / 铁傀儡 / 北极熊 / 蜜蜂等中立生物、
     * 已驯服宠物、村民、被动家畜、玩家全部可选**；只有僵尸 / 骷髅 / 掠夺者 / 末影人 / 猪灵这类
     * 原版敌对生物不可选。
     *
     * <p>⚠️ **本类型不得用于伤害效果牌**（那一路用 {@link #CREATURE} / {@link #CREATURE_OR_RIVAL}）——
     * 本类型是「可对**非敌对生物**施放的治疗 / 增益牌」专用（狂暴 / 奢华大餐 / 加急加快）。
     * 两者的方向**相反**：本类型**排除**敌对生物、村民**可选**；伤害牌那条**包含**未驯服的可驯服生物、
     * **排除**村民。混用会让「治疗牌打怪 / 伤害牌喂村民」。
     *
     * <p>⚠️ 基础 {@link #matches} 只排除选择者自身（= 任意活体）；**真正的过滤器在
     * {@link SelectorTargets#matches}**（客户端准星 / 客户端半径高亮 / 服务端确认三处共用）——
     * 单看本处实现**不能**得出「可选集合」。
     */
    NON_HOSTILE {
        @Override
        public boolean matches(Player selector, LivingEntity target) {
            return target != selector;
        }
    };

    public abstract boolean matches(Player selector, LivingEntity target);
}
