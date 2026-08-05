package tfh.agstack.util;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolItem;
import tfh.agstack.config.ModConfig;

public class AggregationHelper {

    /**
     * 判断两个物品是否可以聚合（堆叠）到一起。
     * 逻辑：
     * 1. 如果任一物品在黑名单中，返回 false。
     * 2. 如果物品 ID 完全相同，返回 true（原版行为）。
     * 3. 如果配置允许不同材质武器，且两者都是武器（ToolItem），返回 true。
     * 4. 如果配置允许不同材质盔甲，且两者都是盔甲（ArmorItem）且槽位相同，返回 true。
     * 5. 否则返回 false。
     */
    public static boolean canAggregate(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        ModConfig config = ModConfig.get();
        if (config.isBlacklisted(a) || config.isBlacklisted(b)) return false;

        // 完全相同的物品总是可以聚合
        if (a.getItem() == b.getItem()) return true;

        // 武器不同材质
        if (config.allowWeaponDifferentMaterial && isWeapon(a) && isWeapon(b)) {
            return true;
        }

        // 盔甲不同材质（必须同槽位）
        if (config.allowArmorDifferentMaterial && isArmor(a) && isArmor(b)) {
            ArmorItem armorA = (ArmorItem) a.getItem();
            ArmorItem armorB = (ArmorItem) b.getItem();
            return armorA.getSlotType() == armorB.getSlotType();
        }

        return false;
    }

    /**
     * 判断物品是否为武器（原版工具或剑）。
     * 注：SwordItem 是 ToolItem 的子类，所以只需检查 ToolItem。
     */
    public static boolean isWeapon(ItemStack stack) {
        return stack.getItem() instanceof ToolItem;
    }

    /**
     * 判断物品是否为盔甲。
     */
    public static boolean isArmor(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem;
    }
}