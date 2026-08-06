package tfh.agstack.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.*;
import tfh.agstack.config.ModConfig;
import tfh.agstack.rule.RuleManager;
import tfh.agstack.rule.RuleManager.RuleMatch;

public class AggregationHelper {

    /**
     * 获取两个物品的聚合信息（是否允许聚合以及最大子物品数量）。
     * 优先级：JSON规则 → 游戏内配置 → 默认行为。
     */
    public static AggregationResult getAggregationInfo(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return AggregationResult.DENIED;

        // 1. JSON规则匹配
        RuleMatch match = RuleManager.getInstance().match(a, b);
        if (match.isMatch()) {
            if (match.isDenied()) return AggregationResult.DENIED;
            if (match.isAllowed()) return AggregationResult.allow(match.max());
        }

        // 2. 回退到游戏内规则
        ModConfig config = ModConfig.get();
        if (config.isBlacklisted(a) || config.isBlacklisted(b)) return AggregationResult.DENIED;

        // 3. 硬编码开关
        if (a.getItem() == b.getItem()) {
            // 完全相同物品，允许聚合，上限为 config.maxSubItems
            return AggregationResult.allow(config.maxSubItems);
        }

        if (config.allowWeaponDifferentMaterial && isWeapon(a) && isWeapon(b)) {
            return AggregationResult.allow(config.maxSubItems);
        }

        if (config.allowArmorDifferentMaterial && isArmor(a) && isArmor(b)) {
            EquipmentSlot slotA = getArmorSlot(a);
            EquipmentSlot slotB = getArmorSlot(b);
            if (slotA != null && slotA == slotB) {
                return AggregationResult.allow(config.maxSubItems);
            }
        }

        // 4. 默认不允许
        return AggregationResult.DENIED;
    }

    public static boolean canAggregate(ItemStack a, ItemStack b) {
        return getAggregationInfo(a, b).allowed();
    }

    public static boolean isWeapon(ItemStack stack) {
        return stack.getItem() instanceof ToolItem;
    }

    public static boolean isArmor(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem || stack.getItem() instanceof ElytraItem;
    }

    public static EquipmentSlot getArmorSlot(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ArmorItem armor) {
            return armor.getSlotType();
        } else if (item instanceof ElytraItem) {
            return EquipmentSlot.CHEST;
        }
        return null;
    }

    public record AggregationResult(boolean allowed, int maxLimit) {
        public static final AggregationResult DENIED = new AggregationResult(false, 0);
        public static AggregationResult allow(int max) {
            return new AggregationResult(true, Math.min(64, Math.max(1, max)));
        }
    }
}