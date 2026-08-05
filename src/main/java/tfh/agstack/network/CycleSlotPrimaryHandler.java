package tfh.agstack.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import tfh.agstack.component.AggregatedStackComponent;
import tfh.agstack.component.ModDataComponents;

public class CycleSlotPrimaryHandler implements ServerPlayNetworking.PlayPayloadHandler<CycleSlotPrimaryPayload> {
    @Override
    public void receive(CycleSlotPrimaryPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        ScreenHandler handler = player.currentScreenHandler;

        if (handler.syncId != payload.syncId()) return;

        // 根据 inventoryClassName 和 slotIndex 查找槽位（保留之前的通用方案）
        Slot targetSlot = null;
        for (Slot slot : handler.slots) {
            if (slot.inventory.getClass().getName().equals(payload.inventoryClassName()) &&
                    slot.getIndex() == payload.slotIndex()) {
                targetSlot = slot;
                break;
            }
        }
        if (targetSlot == null) return;

        ItemStack stack = targetSlot.getStack();
        AggregatedStackComponent comp = stack.get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null || comp.isEmpty()) return;

        int size = comp.subItems().size();
        int newIndex = comp.primaryIndex() + payload.direction();
        if (newIndex < 0) newIndex += size;
        if (newIndex >= size) newIndex %= size;

        if (newIndex != comp.primaryIndex()) {
            AggregatedStackComponent newComp = comp.withNewPrimary(newIndex);
            // 重新创建外层 ItemStack
            ItemStack newStack = AggregatedStackComponent.createAggregatedStack(newComp);
            targetSlot.setStack(newStack);
            handler.sendContentUpdates();
        }
    }
}