package com.merlinkitsune.starengine.combat;

import com.merlinkitsune.starengine.item.BossEntityUtil;
import com.merlinkitsune.starengine.thirdparty.StarengineThirdParty;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「精英怪物或 Boss」判定的**唯一入口**（2026-09-21 用户裁决，必须遵守）。
 *
 * <p>口径（用户原话）：
 * <ul>
 *   <li><b>精英</b>：原版或模组怪物，<b>血量 &gt; 40 或 护甲 &gt; 20</b>；</li>
 *   <li><b>Boss</b>：{@code c:bosses} 通用标签 / 屏幕上方 boss 血条（{@link BossEntityUtil}）；</li>
 *   <li><b>第三方联动（含神化 Apotheosis）</b>：委托 {@link StarengineThirdParty#isEliteOrBoss}——
 *       库侧已内置并默认注册 Apotheosis 探针（{@code apoth.boss} = Apothic Invader，
 *       {@code apoth.miniboss} = Apothic Elite），玩法侧不再直接接触任何第三方 NBT 键或类。</li>
 * </ul>
 *
 * <p><b>为什么第三方判定下沉到库内门面</b>（2026-10-19 用户裁决）：本库**不硬依赖**任何第三方模组
 * （玩家可以不装），Apotheosis 的 {@code apoth.boss} / {@code apoth.miniboss} 两个实体持久化 NBT 键
 * （源码常量 {@code Invader.BOSS_KEY} / {@code Elite.MINIBOSS_KEY}）属于「第三方 API」⇒ 统一收进
 * {@link StarengineThirdParty} 的探针注册表，由库保证四平台可编译（Fabric 1.20.1 无
 * {@code Entity#getPersistentData()}，探针经反射自动降级为 {@code false}）。
 * 未安装 Apotheosis 时这两个键恒不存在，判定自然为 {@code false}。
 *
 * <p>与 {@link HostileTargets} 的分工：「敌对」判的是**立场**，「精英」判的是**强度档位**，
 * 二者正交 —— 精英判定必须先通过敌对判定才有意义（调用方自行保证）。
 */
public final class EliteTargets {

    /** 血量阈值（用户裁决）：最大生命值**严格大于**该值即视为精英。 */
    public static final float ELITE_HEALTH_THRESHOLD = 40.0F;

    /** 护甲阈值（用户裁决）：护甲值**严格大于**该值即视为精英。 */
    public static final int ELITE_ARMOR_THRESHOLD = 20;

    private EliteTargets() {
    }

    /**
     * 该实体是否属于「精英怪物或 Boss」。
     *
     * @param entity 待判定实体（通常已通过 {@link HostileTargets#isHostile} 的敌对判定）
     * @return {@code true} = 精英或 Boss
     */
    public static boolean isEliteOrBoss(LivingEntity entity) {
        if (entity == null) return false;
        // ① 强度阈值：血量 > 40 或 护甲 > 20（覆盖原版与模组怪物）
        if (entity.getMaxHealth() > ELITE_HEALTH_THRESHOLD) return true;
        if (entity.getArmorValue() > ELITE_ARMOR_THRESHOLD) return true;
        // ② Boss：c:bosses 通用标签 / 屏幕上方 boss 血条
        if (BossEntityUtil.isBossEntity(entity)) return true;
        // ③ 第三方标记（Apotheosis：apoth.boss / apoth.miniboss 等已注册探针）
        return StarengineThirdParty.isEliteOrBoss(entity);
    }
}
