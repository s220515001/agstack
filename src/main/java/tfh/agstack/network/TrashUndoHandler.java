package tfh.agstack.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import tfh.agstack.config.ModConfig;
import tfh.agstack.trash.TrashManager;
import tfh.agstack.trash.TrashRecord;

import java.util.List;

public class TrashUndoHandler implements ServerPlayNetworking.PlayPayloadHandler<TrashUndoPayload> {
    @Override
    public void receive(TrashUndoPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        ModConfig config = ModConfig.get();
        if (!config.trashEnabled) return;

        List<TrashRecord> batch = TrashManager.popLastBatch(player.getUuid());
        if (batch.isEmpty()) return;

        ScreenHandler handler = player.currentScreenHandler;

        for (TrashRecord record : batch) {
            // 检查是否超时（虽然批次整体超时会在tick中被清理，但以防万一）
            if (record.isExpired(System.currentTimeMillis(), config.trashRetentionSeconds)) continue;

            ItemStack itemToRestore = record.getItem();
            boolean restored = false;

            // 尝试恢复原槽位（如果屏幕未变且槽位为空）
            if (handler.syncId == record.getSyncId()) {
                int idx = record.getSlotIndex();
                if (idx >= 0 && idx < handler.slots.size()) {
                    Slot slot = handler.getSlot(idx);
                    if (slot != null && slot.getStack().isEmpty()) {
                        slot.setStack(itemToRestore);
                        restored = true;
                    }
                }
            }

            // 若原槽位不可用，寻找任意空位
            if (!restored) {
                for (Slot slot : handler.slots) {
                    if (slot.getStack().isEmpty()) {
                        slot.setStack(itemToRestore);
                        restored = true;
                        break;
                    }
                }
            }

            // 如果容器已满，丢到玩家脚下
            if (!restored) {
                player.dropItem(itemToRestore, false);
            }
        }

        handler.sendContentUpdates();
    }
}