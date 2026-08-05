package tfh.agstack.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import tfh.agstack.component.AggregatedStackComponent;
import tfh.agstack.component.ModDataComponents;

public class CycleArmorPrimaryHandler implements ServerPlayNetworking.PlayPayloadHandler<CycleArmorPrimaryPayload> {
    @Override
    public void receive(CycleArmorPrimaryPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        int direction = payload.direction();

        if (payload.armorSlot() == -1) {
            // 全部切换：切换四个盔甲槽位
            cycleArmorSlot(player, EquipmentSlot.HEAD, direction);
            cycleArmorSlot(player, EquipmentSlot.CHEST, direction);
            cycleArmorSlot(player, EquipmentSlot.LEGS, direction);
            cycleArmorSlot(player, EquipmentSlot.FEET, direction);
        } else {
            EquipmentSlot slot = switch (payload.armorSlot()) {
                case 0 -> EquipmentSlot.FEET;
                case 1 -> EquipmentSlot.LEGS;
                case 2 -> EquipmentSlot.CHEST;
                case 3 -> EquipmentSlot.HEAD;
                default -> null;
            };
            if (slot != null) {
                cycleArmorSlot(player, slot, direction);
            }
        }
        player.currentScreenHandler.sendContentUpdates();
    }

    private void cycleArmorSlot(ServerPlayerEntity player, EquipmentSlot slot, int direction) {
        ItemStack stack = player.getEquippedStack(slot);
        AggregatedStackComponent comp = stack.get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null || comp.isEmpty()) return;

        int size = comp.subItems().size();
        int newIndex = comp.primaryIndex() + direction;
        if (newIndex < 0) newIndex += size;
        if (newIndex >= size) newIndex %= size;

        if (newIndex != comp.primaryIndex()) {
            AggregatedStackComponent newComp = comp.withNewPrimary(newIndex);
            // 使用静态工厂方法重建外层 ItemStack，确保 Item 类型与主物品一致
            ItemStack newStack = AggregatedStackComponent.createAggregatedStack(newComp);
            player.equipStack(slot, newStack);
        }
    }
}