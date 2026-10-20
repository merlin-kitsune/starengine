package com.merlinkitsune.starengine.thirdparty;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Apotheosis（神化）精英 / Boss 标记探测 —— 库内**内置**的第三方探针实现（包内可见）。
 *
 * <p><b>判据</b>：Apotheosis 把标记写进实体的持久化数据（{@code Entity#getPersistentData()}）：
 * {@code apoth.boss}（Apothic Invader = 神化 Boss）与 {@code apoth.miniboss}（Apothic Elite = 神化精英），
 * 二者均为 {@code boolean}。未安装该模组时键恒不存在 ⇒ {@code false}。
 *
 * <p><b>为什么全程反射</b>：本类落在四平台共用的 {@code common} 源码里，而
 * <ol>
 *   <li>{@code Entity#getPersistentData()} 是 Forge / NeoForge 的补丁（Fabric 1.20.1 的原版无此方法）
 *       ⇒ 直接调用会让 {@code fabric-1.20.1} 编译失败；</li>
 *   <li>{@code CompoundTag#getBoolean(String)} 的返回类型跨版本不一致
 *       （1.20.1 / 1.21.1 返回 {@code boolean}，26.1.2 返回 {@code Optional<Boolean>}，
 *       另提供 {@code getBooleanOr(String, boolean)}）⇒ 直接调用必然在某条线上编译不过。</li>
 * </ol>
 * 故这里只保留「符号名 + 参数类型」的反射查找：找得到就调用，找不到（如 Fabric 侧的
 * {@code getPersistentData}）即视为「该平台无此联动」并返回 {@code false}。
 */
final class ApotheosisMarkers {

    private ApotheosisMarkers() {
    }

    static boolean isEliteOrBoss(LivingEntity entity) {
        if (entity == null) return false;
        return readPersistentBoolean(entity, StarengineThirdParty.APOTHEOSIS_BOSS_KEY)
                || readPersistentBoolean(entity, StarengineThirdParty.APOTHEOSIS_MINIBOSS_KEY);
    }

    private static boolean readPersistentBoolean(Entity entity, String key) {
        Method getter = Reflection.PERSISTENT_DATA;
        if (getter == null) return false;
        Object persistent;
        try {
            persistent = getter.invoke(entity);
        } catch (Exception ignored) {
            return false;
        }
        if (!(persistent instanceof CompoundTag)) return false;
        return Reflection.readBoolean((CompoundTag) persistent, key);
    }

    /** 反射句柄缓存：解析一次，四平台各自在自己的版本上命中不同分支。 */
    private static final class Reflection {

        /** {@code Entity#getPersistentData()}（仅 Forge / NeoForge 存在；Fabric 为 {@code null}）。 */
        static final Method PERSISTENT_DATA = lookup(Entity.class, "getPersistentData");

        /** {@code CompoundTag#getBooleanOr(String, boolean)}（26.1.2 起存在）。 */
        static final Method GET_BOOLEAN_OR =
                lookup(CompoundTag.class, "getBooleanOr", String.class, boolean.class);

        /** {@code CompoundTag#getBoolean(String)}（1.20.1 / 1.21.1；26.1.2 返回 Optional）。 */
        static final Method GET_BOOLEAN = lookup(CompoundTag.class, "getBoolean", String.class);

        private Reflection() {
        }

        static Method lookup(Class<?> owner, String name, Class<?>... parameterTypes) {
            try {
                return owner.getMethod(name, parameterTypes);
            } catch (NoSuchMethodException | SecurityException ignored) {
                return null;
            }
        }

        static boolean readBoolean(CompoundTag tag, String key) {
            if (GET_BOOLEAN_OR != null) {
                try {
                    return Boolean.TRUE.equals(GET_BOOLEAN_OR.invoke(tag, key, Boolean.FALSE));
                } catch (Exception ignored) {
                    // 落到下一分支
                }
            }
            if (GET_BOOLEAN != null) {
                try {
                    Object value = GET_BOOLEAN.invoke(tag, key);
                    if (value instanceof Boolean) return (Boolean) value;
                    if (value instanceof Optional) {
                        Object unwrapped = ((Optional<?>) value).orElse(null);
                        return Boolean.TRUE.equals(unwrapped);
                    }
                } catch (Exception ignored) {
                    // 读不到即视为未标记
                }
            }
            return false;
        }
    }
}
