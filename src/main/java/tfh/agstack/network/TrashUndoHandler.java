package tfh.agstack.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import tfh.agstack.config.ModConfig;
import tfh.agstack.trash.TrashManager;
import tfh.agstack.trash.TrashRecord;

public class TrashUndoHandler implements ServerPlayNetworking.PlayPayloadHandler<TrashUndoPayload> {
    @Override
    public void receive(TrashUndoPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        ModConfig config = ModConfig.get();
        if (!config.trashEnabled) return;

        TrashRecord record = TrashManager.getRecord(player.getUuid());
        if (record == null) return;

        // 检查是否超时
        if (record.isExpired(System.currentTimeMillis(), config.trashRetentionSeconds)) {
            TrashManager.clearRecord(player.getUuid());
            return;
        }

        ScreenHandler handler = player.currentScreenHandler;
        ItemStack itemToRestore = record.getItem();

        // 尝试恢复原槽位
        boolean restored = false;
        if (handler.syncId == record.getSyncId()) {
            // 检查槽位是否有效
            int idx = record.getSlotIndex();
            if (idx >= 0 && idx < handler.slots.size()) {
                Slot slot = handler.getSlot(idx);
                if (slot != null && slot.getStack().isEmpty()) {
                    slot.setStack(itemToRestore);
                    restored = true;
                }
            }
        }

        // 若原槽位不可用（被占用或容器已关闭），寻找空位或丢出
        if (!restored) {
            // 先搜索当前容器（ScreenHandler）的所有槽位（包括玩家背包）
            for (Slot slot : handler.slots) {
                if (slot.getStack().isEmpty()) {
                    slot.setStack(itemToRestore);
                    restored = true;
                    break;
                }
            }
        }

        if (!restored) {
            // 容器全满，丢到玩家脚下
            player.dropItem(itemToRestore, false);
        }

        // 清除记录（无论是否成功恢复，记录已使用）
        TrashManager.clearRecord(player.getUuid());
        handler.sendContentUpdates();
    }
}