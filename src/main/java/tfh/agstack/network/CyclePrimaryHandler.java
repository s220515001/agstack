package tfh.agstack.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import tfh.agstack.component.AggregatedStackComponent;
import tfh.agstack.component.ModDataComponents;

public class CyclePrimaryHandler implements ServerPlayNetworking.PlayPayloadHandler<CyclePrimaryPayload> {
    @Override
    public void receive(CyclePrimaryPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        ItemStack mainHand = player.getMainHandStack();
        AggregatedStackComponent comp = mainHand.get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null || comp.subItems().isEmpty()) return;

        int newIndex = comp.primaryIndex() + payload.direction();
        if (newIndex < 0) newIndex = comp.subItems().size() - 1;
        if (newIndex >= comp.subItems().size()) newIndex = 0;

        if (newIndex != comp.primaryIndex()) {
            AggregatedStackComponent newComp = comp.withNewPrimary(newIndex);
            // 重新创建外层 ItemStack，Item 类型与主物品一致
            ItemStack newMainHand = AggregatedStackComponent.createAggregatedStack(newComp);
            player.getInventory().main.set(player.getInventory().selectedSlot, newMainHand);
            player.currentScreenHandler.sendContentUpdates();
        }
    }
}