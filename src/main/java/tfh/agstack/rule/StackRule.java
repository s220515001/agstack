package tfh.agstack.rule;

import net.minecraft.nbt.NbtCompound;

public record StackRule(String match, int max, boolean deny, NbtCompound nbt, int priority) {
    public StackRule {
        if (max < 1 || max > 64) throw new IllegalArgumentException("max must be between 1 and 64");
        if (match == null || match.isBlank()) throw new IllegalArgumentException("match must not be empty");
    }
}