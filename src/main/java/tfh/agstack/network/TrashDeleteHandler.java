package tfh.agstack.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import tfh.agstack.config.ModConfig;
import tfh.agstack.trash.TrashManager;
import tfh.agstack.trash.TrashRecord;

public class TrashDeleteHandler implements ServerPlayNetworking.PlayPayloadHandler<TrashDeletePayload> {
    @Override
    public void receive(TrashDeletePayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        ModConfig config = ModConfig.get();
        if (!config.trashEnabled) return;

        ScreenHandler handler = player.currentScreenHandler;
        if (handler.syncId != payload.syncId()) return;

        // 检查光标是否为空
        if (!handler.getCursorStack().isEmpty()) return;

        // 验证槽位索引
        if (payload.slotIndex() < 0 || payload.slotIndex() >= handler.slots.size()) return;

        Slot slot = handler.getSlot(payload.slotIndex());
        if (slot == null) return;

        ItemStack stack = slot.getStack();
        if (stack.isEmpty()) return;

        // 创建记录，覆盖旧记录
        TrashRecord record = new TrashRecord(stack, handler.syncId, payload.slotIndex(), System.currentTimeMillis());
        TrashManager.addRecord(player.getUuid(), record);

        // 清空槽位
        slot.setStack(ItemStack.EMPTY);
        handler.sendContentUpdates();
    }
}