package com.merlinkitsune.starenginelib.item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketInventory;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

/**
 * Trinkets 3.7.2 适配(Fabric 1.20.1 的饰品栏) —— 对齐 Forge 侧 {@code CuriosCompat} 的**调用面**,
 * 让消费方的饰品查询代码在换平台后不用改方法名。
 *
 * <h2>类型映射(Curios → Trinkets)</h2>
 * <table border="1">
 *   <caption>API 对照</caption>
 *   <tr><th>Curios 5.x</th><th>本适配层</th><th>Trinkets 3.7.2 底层</th></tr>
 *   <tr><td>{@code ICuriosItemHandler}</td><td>{@link InventoryView}</td><td>{@code TrinketComponent}</td></tr>
 *   <tr><td>{@code ICurioStacksHandler}</td><td>{@link SlotHandler}</td><td>{@code TrinketInventory}(实现 {@code Container})</td></tr>
 *   <tr><td>{@code getStacks()}</td><td>{@link SlotHandler#getStacks()}</td><td>同一 {@code TrinketInventory}</td></tr>
 *   <tr><td>{@code getStacks().getSlots()/getStackInSlot/setStackInSlot}</td>
 *       <td>{@link SlotInventory} 的同名方法</td>
 *       <td>{@code getContainerSize}/{@code getItem}/{@code setItem}</td></tr>
 *   <tr><td>{@code findFirstCurio(Predicate)}</td><td>{@link InventoryView#findFirstCurio(Predicate)}</td>
 *       <td>遍历 {@code getInventory()}</td></tr>
 * </table>
 *
 * <h2>槽位命名</h2>
 * <p>Curios 的槽位标识({@code dice}/{@code stand}/{@code chip})在 Fabric 侧映射为
 * <b>同名的 Trinkets 槽位组</b>,每组一个同名槽位 ⇒ {@code data/trinkets/slots/<group>/<group>.json}
 * 与 {@code data/trinkets/slots/<group>/group.json}。因此本适配层把
 * {@code getInventory()} 的两层 Map(组 → 槽名 → 库存)**按槽名拍平**,
 * 于是 Curios 的「按 identifier 取槽位组」语义得以保持。
 *
 * <p>本类**不声明 Trinkets 为前置**:照 Forge 侧 {@code CuriosCompat} 的口径,库自身零调用,
 * 调用方(消费方 mod)自己声明 trinkets 必需。
 */
public final class TrinketsCompat {

    /** Curios 的 {@code IItemHandler} 子集(槽位内容读写)。 */
    public interface SlotInventory {
        int getSlots();

        ItemStack getStackInSlot(int slot);

        void setStackInSlot(int slot, ItemStack stack);
    }

    /** Curios 的 {@code ICurioStacksHandler} 子集。 */
    public interface SlotHandler {
        SlotInventory getStacks();

        int getSlots();

        /**
         * 该槽位组当前的属性修饰符(Curios {@code ICurioStacksHandler#getModifiers})。
         *
         * <p>⚠️ **两平台 key 类型一致、可直接复用**:Curios 侧是 {@code Map<String, AttributeModifier>}
         * (key = modifier 的 id 字符串),Trinkets 侧是 {@code Map<UUID, AttributeModifier>}
         * (key = modifier 的 UUID)。本模组的两个槽位修饰符常量本身就是 {@link java.util.UUID}
         * (见 {@code DiceCurioItem#CHIP_SLOT_MODIFIER} / {@code #CURIO_LEGACY_MODIFIER}),
         * 故这里**直接采用 UUID**,与 Trinkets 原生签名一致、零转换。
         *
         * <p>Trinkets 的 {@code TrinketInventory} 以「baseSize + 修饰符运算结果」决定槽位数
         * (javap 实证字段:{@code baseSize} / {@code modifiersByOperation} /
         * {@code getModifiersByOperation}),与 Curios 用修饰符增量控制槽位数的机制**同源**
         * ⇒ 动态筹码槽位(随骰子星级增长)可原样移植。
         */
        Map<java.util.UUID, AttributeModifier> getModifiers();

        /** 按 UUID 移除修饰符(Curios {@code removeModifier(String)} 的 UUID 版)。 */
        void removeModifier(java.util.UUID id);

        /** 登记一个**持久**修饰符(Curios {@code addPermanentModifier};Trinkets 侧名为
         *  {@code addPersistentModifier})。随存档保留。 */
        void addPermanentModifier(AttributeModifier modifier);

        /** 提交变更并触发同步(Curios {@code update()})。 */
        void update();
    }

    /** Curios {@code SlotResult} 的等价物:命中的槽位标识 / 序号 / 物品栈。 */
    public record SlotResult(String slotId, int index, ItemStack stack) {
    }

    /** Curios {@code ICuriosItemHandler} 的等价物。 */
    public static final class InventoryView {
        private final Map<String, SlotHandler> handlers;

        InventoryView(TrinketComponent component) {
            Map<String, SlotHandler> flat = new LinkedHashMap<>();
            component.getInventory().forEach((group, slots) ->
                    slots.forEach((slotName, inventory) -> flat.put(slotName, new Handler(inventory))));
            this.handlers = Collections.unmodifiableMap(flat);
        }

        /** 全部槽位组(按槽名索引)。 */
        public Map<String, SlotHandler> getCurios() {
            return handlers;
        }

        /** 按标识取槽位组。 */
        public Optional<SlotHandler> getStacksHandler(String identifier) {
            return Optional.ofNullable(handlers.get(identifier));
        }

        /** 首个命中谓词的已装备物品(Curios {@code findFirstCurio} 的等价物)。 */
        public Optional<SlotResult> findFirstCurio(Predicate<ItemStack> predicate) {
            for (Map.Entry<String, SlotHandler> entry : handlers.entrySet()) {
                SlotInventory stacks = entry.getValue().getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (!stack.isEmpty() && predicate.test(stack)) {
                        return Optional.of(new SlotResult(entry.getKey(), i, stack));
                    }
                }
            }
            return Optional.empty();
        }

        /** 是否装备了命中谓词的物品(等价于 {@code findFirstCurio(...).isPresent()})。 */
        public boolean isEquipped(Predicate<ItemStack> predicate) {
            return findFirstCurio(predicate).isPresent();
        }

        /** 全部命中谓词的已装备物品(Curios {@code findCurios} 的等价物,按槽位组顺序展开)。 */
        public java.util.List<SlotResult> findCurios(Predicate<ItemStack> predicate) {
            java.util.List<SlotResult> out = new java.util.ArrayList<>();
            for (Map.Entry<String, SlotHandler> entry : handlers.entrySet()) {
                SlotInventory stacks = entry.getValue().getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (!stack.isEmpty() && predicate.test(stack)) {
                        out.add(new SlotResult(entry.getKey(), i, stack));
                    }
                }
            }
            return out;
        }

        /** 某一槽位组的全部已装备物品(Curios {@code getStacksHandler} + 遍历)。 */
        public java.util.List<ItemStack> getAllIn(String identifier) {
            SlotHandler handler = handlers.get(identifier);
            if (handler == null) {
                return java.util.List.of();
            }
            SlotInventory stacks = handler.getStacks();
            java.util.List<ItemStack> out = new java.util.ArrayList<>();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    out.add(stack);
                }
            }
            return out;
        }
    }

    private static final class Handler implements SlotHandler {
        private final TrinketInventory inventory;

        Handler(TrinketInventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public SlotInventory getStacks() {
            return new Slots(inventory);
        }

        @Override
        public int getSlots() {
            return inventory.getContainerSize();
        }

        @Override
        public Map<java.util.UUID, AttributeModifier> getModifiers() {
            return inventory.getModifiers();
        }

        @Override
        public void removeModifier(java.util.UUID id) {
            inventory.removeModifier(id);
        }

        @Override
        public void addPermanentModifier(AttributeModifier modifier) {
            // Curios 名 addPermanentModifier ↔ Trinkets 名 addPersistentModifier(语义一致:随存档保留)
            inventory.addPersistentModifier(modifier);
        }

        @Override
        public void update() {
            inventory.update();
        }
    }

    private static final class Slots implements SlotInventory {
        private final TrinketInventory inventory;

        Slots(TrinketInventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public int getSlots() {
            return inventory.getContainerSize();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getItem(slot);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            inventory.setItem(slot, stack);
        }
    }

    /** 全部槽位组(对应 Curios 侧同名方法)。 */
    public static Map<String, SlotHandler> getCuriosMap(LivingEntity entity) {
        return getCuriosInventory(entity).map(InventoryView::getCurios).orElseGet(Collections::emptyMap);
    }

    /** 饰品库存视图(对应 Curios 侧 {@code getCuriosInventory})。 */
    public static Optional<InventoryView> getCuriosInventory(LivingEntity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        return TrinketsApi.getTrinketComponent(entity).map(InventoryView::new);
    }

    /** 按标识取槽位组(对应 Curios 侧同名方法)。 */
    public static Optional<SlotHandler> getStacksHandler(LivingEntity entity, String identifier) {
        return getCuriosInventory(entity).flatMap(view -> view.getStacksHandler(identifier));
    }

    /** 该实体是否装备了命中谓词的物品。 */
    public static boolean isEquipped(LivingEntity entity, Predicate<ItemStack> predicate) {
        return getCuriosInventory(entity).map(view -> view.isEquipped(predicate)).orElse(false);
    }

    /**
     * 仅用于消解「{@code SlotReference} 未被直接使用」的静态检查:本适配层对外不透出 Trinkets 类型,
     * 但保留该签名引用以标明底层来源。
     */
    public static int indexOf(SlotReference reference) {
        return reference.index();
    }

    private TrinketsCompat() {
    }
}
