package tfh.agstack.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.*;
import tfh.agstack.config.ModConfig;

public class AggregationHelper {

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

        // 盔甲不同材质（含鞘翅，必须同槽位）
        if (config.allowArmorDifferentMaterial && isArmor(a) && isArmor(b)) {
            EquipmentSlot slotA = getArmorSlot(a);
            EquipmentSlot slotB = getArmorSlot(b);
            return slotA == slotB;
        }

        return false;
    }

    public static boolean isWeapon(ItemStack stack) {
        return stack.getItem() instanceof ToolItem;
    }

    public static boolean isArmor(ItemStack stack) {
        // 盔甲物品（ArmorItem）或鞘翅（ElytraItem）
        return stack.getItem() instanceof ArmorItem || stack.getItem() instanceof ElytraItem;
    }

    /**
     * 获取盔甲或鞘翅的装备槽位。
     * 对于 ArmorItem，使用 getSlotType()；对于 ElytraItem，固定为 CHEST。
     */
    public static EquipmentSlot getArmorSlot(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ArmorItem armor) {
            return armor.getSlotType();
        } else if (item instanceof ElytraItem) {
            return EquipmentSlot.CHEST;
        }
        return null;
    }
}