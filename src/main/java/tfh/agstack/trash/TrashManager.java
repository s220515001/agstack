package tfh.agstack.trash;

import tfh.agstack.config.ModConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TrashManager {
    private static final Map<UUID, TrashRecord> records = new ConcurrentHashMap<>();

    public static void addRecord(UUID playerId, TrashRecord record) {
        records.put(playerId, record);
    }

    public static TrashRecord getRecord(UUID playerId) {
        return records.get(playerId);
    }

    public static void clearRecord(UUID playerId) {
        records.remove(playerId);
    }

    public static void tickRecords() {
        ModConfig config = ModConfig.get();
        if (!config.trashEnabled) return;
        long now = System.currentTimeMillis();
        int retention = config.trashRetentionSeconds;
        records.entrySet().removeIf(entry -> entry.getValue().isExpired(now, retention));
    }

    public static void removePlayer(UUID playerId) {
        records.remove(playerId);
    }
}