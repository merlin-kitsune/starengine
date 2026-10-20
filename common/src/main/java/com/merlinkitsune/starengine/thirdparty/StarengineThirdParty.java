package com.merlinkitsune.starengine.thirdparty;

import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 第三方模组联动的**唯一注册 / 调用门面**（2026-10-19 用户裁决，必须遵守）。
 *
 * <p><b>为什么要这个门面</b>：本库不硬依赖任何第三方模组，但部分玩法口径（如「精英 / Boss」判定）
 * 需要识别第三方写了什么标记。这类判定的键名、读取方式都属「第三方 API」——若散落在玩法代码里，
 * 一旦第三方改约定就要同时改四平台消费方。故库内只放**探针注册表**，默认注册内置探针，
 * 消费方按需注册/注销自己的探针；玩法代码只调用语义方法 {@link #isEliteOrBoss}。
 *
 * <p><b>已内置的探针</b>：
 * <ul>
 *   <li>{@link #APOTHEOSIS}（神化 / Apotheosis）：识别 {@link #APOTHEOSIS_BOSS_KEY}（Apothic Invader）
 *       与 {@link #APOTHEOSIS_MINIBOSS_KEY}（Apothic Elite）两个实体持久化 NBT 布尔键。
 *       读取走反射而非直接引用第三方类，故四平台均**可编译**；未安装该模组时恒为 {@code false}。</li>
 * </ul>
 *
 * <p><b>探针约定</b>：实现必须容忍 {@code null}、不得抛异常（抛出的 {@link RuntimeException} 由本门面吞掉，
 * 等价于该探针本次返回 {@code false}，不会中断玩法）。探针按注册 id 去重，最后一次注册生效。
 */
public final class StarengineThirdParty {

    /** Apotheosis（神化）的模组 id —— 内置探针的注册 id。 */
    public static final String APOTHEOSIS = "apotheosis";

    /** 神化 Boss（Apothic Invader）标记键 —— 与 Apotheosis {@code Invader.BOSS_KEY} 一致。 */
    public static final String APOTHEOSIS_BOSS_KEY = "apoth.boss";

    /** 神化精英（Apothic Elite / miniboss）标记键 —— 与 Apotheosis {@code Elite.MINIBOSS_KEY} 一致。 */
    public static final String APOTHEOSIS_MINIBOSS_KEY = "apoth.miniboss";

    /** 第三方「精英 / Boss」探针：返回该实体是否被某个第三方模组标记为精英或 Boss。 */
    @FunctionalInterface
    public interface EliteProbe {
        boolean isEliteOrBoss(LivingEntity entity);
    }

    private static final ConcurrentMap<String, EliteProbe> ELITE_PROBES = new ConcurrentHashMap<>();

    static {
        ELITE_PROBES.put(APOTHEOSIS, ApotheosisMarkers::isEliteOrBoss);
    }

    private StarengineThirdParty() {
    }

    /**
     * 注册（或替换）一个「精英 / Boss」探针。
     *
     * @param id    探针 id（约定用第三方 modid；内置 {@link #APOTHEOSIS} 可被覆盖或注销）
     * @param probe 探针实现；传 {@code null} 等价于注销该 id
     */
    public static void registerEliteProbe(String id, EliteProbe probe) {
        Objects.requireNonNull(id, "id");
        if (probe == null) {
            ELITE_PROBES.remove(id);
        } else {
            ELITE_PROBES.put(id, probe);
        }
    }

    /**
     * 注销一个探针。
     *
     * @param id 探针 id
     * @return {@code true} = 确有该 id 的探针被移除
     */
    public static boolean unregisterEliteProbe(String id) {
        return id != null && ELITE_PROBES.remove(id) != null;
    }

    /**
     * 任一已注册探针认定该实体为精英 / Boss 时返回 {@code true}。
     *
     * @param entity 待判定实体（可为 {@code null}）
     * @return {@code true} = 被某个第三方模组标记为精英 / Boss
     */
    public static boolean isEliteOrBoss(LivingEntity entity) {
        if (entity == null) return false;
        for (EliteProbe probe : ELITE_PROBES.values()) {
            try {
                if (probe.isEliteOrBoss(entity)) return true;
            } catch (RuntimeException ignored) {
                // 探针异常不中断玩法：按「本次未命中」处理。
            }
        }
        return false;
    }

    /**
     * 内置 Apotheosis 探针的直调入口（不受注册表覆盖影响）。
     *
     * @param entity 待判定实体（可为 {@code null}）
     * @return {@code true} = 被 Apotheosis 标记为 Boss（Invader）或精英（miniboss）
     */
    public static boolean isApotheosisEliteOrBoss(LivingEntity entity) {
        return ApotheosisMarkers.isEliteOrBoss(entity);
    }
}
