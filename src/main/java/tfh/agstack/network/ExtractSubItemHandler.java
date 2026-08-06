package tfh.agstack.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import tfh.agstack.component.AggregatedStackComponent;
import tfh.agstack.component.ModDataComponents;

public class ExtractSubItemHandler implements ServerPlayNetworking.PlayPayloadHandler<ExtractSubItemPayload> {
    @Override
    public void receive(ExtractSubItemPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        ScreenHandler handler = player.currentScreenHandler;

        if (handler.syncId != payload.syncId()) return;
        if (payload.slotId() < 0 || payload.slotId() >= handler.slots.size()) return;

        Slot slot = handler.getSlot(payload.slotId());
        if (slot == null) return;

        ItemStack stack = slot.getStack();
        AggregatedStackComponent comp = stack.get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null) return;
        if (payload.subItemIndex() < 0 || payload.subItemIndex() >= comp.subItems().size()) return;

        if (payload.quickMove()) {
            ItemStack subItem = comp.getSubItem(payload.subItemIndex()).copy();
            if (subItem.isEmpty()) return;

            AggregatedStackComponent newComp = comp.removeSubItem(payload.subItemIndex());
            if (newComp == null || newComp.subItems().isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                ItemStack newAggregated = AggregatedStackComponent.createAggregatedStack(newComp);
                slot.setStack(newAggregated);
            }

            ItemStack remaining = handler.quickMove(player, slot.id);
            if (!remaining.isEmpty()) {
                player.getInventory().offerOrDrop(remaining);
            }
            handler.sendContentUpdates();
            return;
        }

        if (payload.button() == 0) {
            if (!handler.getCursorStack().isEmpty()) {
                return;
            }

            ItemStack subItem = comp.getSubItem(payload.subItemIndex()).copy();
            if (subItem.isEmpty()) return;

            AggregatedStackComponent newComp = comp.removeSubItem(payload.subItemIndex());
            if (newComp == null || newComp.subItems().isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                ItemStack newAggregated = AggregatedStackComponent.createAggregatedStack(newComp);
                slot.setStack(newAggregated);
            }

            handler.setCursorStack(subItem);
            // 发送光标更新包，使用 nextRevision()
            player.networkHandler.sendPacket(
                    new ScreenHandlerSlotUpdateS2CPacket(
                            handler.syncId,
                            handler.nextRevision(),
                            -1,
                            subItem
                    )
            );
            handler.sendContentUpdates();
            return;
        }

        if (payload.button() == 1) {
            AggregatedStackComponent newComp = comp.withNewPrimary(payload.subItemIndex());
            ItemStack newAggregated = AggregatedStackComponent.createAggregatedStack(newComp);
            slot.setStack(newAggregated);
            handler.sendContentUpdates();
        }
    }
}