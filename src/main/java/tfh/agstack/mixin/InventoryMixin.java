package tfh.agstack.mixin;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tfh.agstack.component.AggregatedStackComponent;
import tfh.agstack.component.ModDataComponents;
import tfh.agstack.config.ModConfig;
import tfh.agstack.util.AggregationHelper;

import java.util.List;

@Mixin(PlayerInventory.class)
public abstract class InventoryMixin {

    @Inject(method = "insertStack(Lnet/minecraft/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true)
    private void onInsertStack(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        ModConfig config = ModConfig.get();
        if (!config.autoPickupStack) return;
        if (stack.isEmpty()) return;

        if (config.isBlacklisted(stack)) {
            return;
        }

        if (stack.get(ModDataComponents.AGGREGATED_STACK) != null) {
            if (stack.getCount() != 1) stack.setCount(1);
            return;
        }

        PlayerInventory inv = (PlayerInventory)(Object)this;

        // 1. 尝试放入已有的聚合槽
        for (int i = 0; i < inv.size(); i++) {
            ItemStack existing = inv.getStack(i);
            AggregatedStackComponent comp = existing.get(ModDataComponents.AGGREGATED_STACK);
            if (comp != null && AggregationHelper.canAggregate(existing, stack) &&
                    comp.getSubItemCount() < config.maxSubItems) {
                if (!ItemStack.areItemsAndComponentsEqual(comp.getPrimary(), stack)) {
                    continue;
                }
                AggregatedStackComponent newComp = comp.addSubItem(stack.copy());
                // 更新聚合栈，外层 Item 类型可能不变（因为主物品未变），但为了安全，重新创建外层
                ItemStack newAggregated = AggregatedStackComponent.createAggregatedStack(newComp);
                inv.setStack(i, newAggregated);
                stack.setCount(0);
                cir.setReturnValue(true);
                return;
            }
        }

        // 2. 没有聚合槽，但存在同ID且组件不同的普通物品 -> 创建聚合槽
        for (int i = 0; i < inv.size(); i++) {
            ItemStack existing = inv.getStack(i);
            if (!existing.isEmpty() && AggregationHelper.canAggregate(existing, stack) &&
                    existing.get(ModDataComponents.AGGREGATED_STACK) == null &&
                    !ItemStack.areItemsAndComponentsEqual(existing, stack)) {
                AggregatedStackComponent newComp = new AggregatedStackComponent(
                        List.of(existing.copy(), stack.copy()), 0);
                ItemStack aggregated = AggregatedStackComponent.createAggregatedStack(newComp);
                inv.setStack(i, aggregated);
                stack.setCount(0);
                cir.setReturnValue(true);
                return;
            }
        }
    }
}