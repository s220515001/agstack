package tfh.agstack.trash;

import tfh.agstack.config.ModConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TrashManager {
    private static final Map<UUID, Deque<Batch>> batches = new ConcurrentHashMap<>();
    private static final long BATCH_TIMEOUT_MS = 500; // 500ms 内视为同一批次

    public static void addRecord(UUID playerId, TrashRecord record) {
        Deque<Batch> deque = batches.computeIfAbsent(playerId, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        Batch latest = deque.peekLast();
        if (latest == null || (now - latest.timestamp) > BATCH_TIMEOUT_MS) {
            // 新批次
            Batch newBatch = new Batch(now, new ArrayList<>());
            newBatch.records.add(record);
            deque.addLast(newBatch);
        } else {
            // 加入现有批次
            latest.records.add(record);
            latest.timestamp = now; // 更新时间戳
        }
    }

    public static List<TrashRecord> popLastBatch(UUID playerId) {
        Deque<Batch> deque = batches.get(playerId);
        if (deque == null || deque.isEmpty()) return Collections.emptyList();
        Batch last = deque.pollLast();
        return last != null ? last.records : Collections.emptyList();
    }

    public static void clearPlayer(UUID playerId) {
        batches.remove(playerId);
    }

    public static void tickRecords() {
        ModConfig config = ModConfig.get();
        if (!config.trashEnabled) return;
        long now = System.currentTimeMillis();
        int retention = config.trashRetentionSeconds;
        batches.values().forEach(deque -> {
            deque.removeIf(batch -> {
                // 批次的记录有效期以最后一个记录的时间为准，但简化：批次内所有记录共享同一个时间戳
                return (now - batch.timestamp) > retention * 1000L;
            });
        });
        // 清理空队列
        batches.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    public static void removePlayer(UUID playerId) {
        batches.remove(playerId);
    }

    private static class Batch {
        long timestamp;
        List<TrashRecord> records;

        Batch(long timestamp, List<TrashRecord> records) {
            this.timestamp = timestamp;
            this.records = records;
        }
    }
}