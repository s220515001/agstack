package tfh.agstack.trash;

import net.minecraft.item.ItemStack;

public class TrashRecord {
    private final ItemStack item;
    private final int syncId;
    private final int slotIndex;
    private final long timestamp;

    public TrashRecord(ItemStack item, int syncId, int slotIndex, long timestamp) {
        this.item = item.copy();
        this.syncId = syncId;
        this.slotIndex = slotIndex;
        this.timestamp = timestamp;
    }

    public ItemStack getItem() {
        return item.copy();
    }

    public int getSyncId() {
        return syncId;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isExpired(long currentTime, int retentionSeconds) {
        return (currentTime - timestamp) > retentionSeconds * 1000L;
    }
}