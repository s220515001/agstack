package tfh.agstack.rule;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import tfh.agstack.Agstack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RuleManager {
    private static final RuleManager INSTANCE = new RuleManager();
    private List<StackRule> rules = new ArrayList<>();
    private MinecraftServer server;

    private RuleManager() {}

    public static RuleManager getInstance() {
        return INSTANCE;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public void load() throws IOException {
        rules = RuleLoader.load();
        Agstack.LOGGER.info("Loaded {} rules", rules.size());
    }

    public void setRules(List<StackRule> rules) {
        this.rules = new ArrayList<>(rules);
        this.rules.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        Agstack.LOGGER.info("Rules updated, total: {}", this.rules.size());
    }

    public void reload() {
        RuleLoader.reload();
    }

    public List<StackRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    /**
     * 匹配规则：
     * 1. 黑名单（任一物品匹配 deny:true）→ 拒绝
     * 2. 白名单（两个物品分别找到允许规则）→ 允许，max 取较小值
     * 3. 否则返回 NONE，回退到硬编码
     */
    public RuleMatch match(ItemStack a, ItemStack b) {
        if (server == null) {
            Agstack.LOGGER.warn("Server not set, cannot match rules");
            return RuleMatch.none();
        }
        if (a.isEmpty() || b.isEmpty()) {
            return RuleMatch.none();
        }

        // 1. 黑名单检查
        for (StackRule rule : rules) {
            if (rule.deny()) {
                if (matchesSingle(a, rule) || matchesSingle(b, rule)) {
                    Agstack.LOGGER.debug("Blacklist matched: {}", rule);
                    return RuleMatch.deny();
                }
            }
        }

        // 2. 白名单检查（分别找两个物品的最高优先级允许规则）
        StackRule ruleA = null;
        StackRule ruleB = null;
        for (StackRule rule : rules) {
            if (rule.deny()) continue; // 已处理
            if (ruleA == null && matchesSingle(a, rule)) {
                ruleA = rule;
            }
            if (ruleB == null && matchesSingle(b, rule)) {
                ruleB = rule;
            }
            if (ruleA != null && ruleB != null) break;
        }

        if (ruleA != null && ruleB != null) {
            int max = Math.min(ruleA.max(), ruleB.max());
            Agstack.LOGGER.debug("Allow rules: A={}, B={}, max={}", ruleA, ruleB, max);
            return RuleMatch.allow(max);
        }

        return RuleMatch.none();
    }

    /**
     * 检查单个物品是否匹配规则（ID/标签 + NBT）
     */
    private boolean matchesSingle(ItemStack stack, StackRule rule) {
        String match = rule.match();

        if (match.startsWith("#")) {
            // 标签匹配
            Identifier tagId = Identifier.tryParse(match.substring(1));
            if (tagId == null) {
                Agstack.LOGGER.warn("Invalid tag ID in rule: {}", match);
                return false;
            }
            TagKey<Item> tagKey = TagKey.of(RegistryKeys.ITEM, tagId);
            RegistryEntry<Item> entry = stack.getItem().getRegistryEntry();
            return entry.isIn(tagKey);
        } else {
            // 物品 ID 匹配（直接比较 Item 对象）
            Identifier itemId = Identifier.tryParse(match);
            if (itemId == null) {
                Agstack.LOGGER.warn("Invalid item ID in rule: {}", match);
                return false;
            }
            Item targetItem = Registries.ITEM.get(itemId);
            if (targetItem == null) return false;
            if (stack.getItem() != targetItem) return false;
        }

        // NBT 匹配（精确）
        if (rule.nbt() != null) {
            NbtComponent nbtComponent = stack.get(DataComponentTypes.CUSTOM_DATA);
            NbtCompound stackNbt = nbtComponent != null ? nbtComponent.copyNbt() : null;
            if (stackNbt == null) return false;
            return rule.nbt().equals(stackNbt);
        }

        return true;
    }

    // ==================== 匹配结果 ====================

    public record RuleMatch(Type type, int max) {
        public enum Type { ALLOW, DENY, NONE }
        public static RuleMatch allow(int max) {
            return new RuleMatch(Type.ALLOW, Math.min(64, Math.max(1, max)));
        }
        public static RuleMatch deny() {
            return new RuleMatch(Type.DENY, 0);
        }
        public static RuleMatch none() {
            return new RuleMatch(Type.NONE, 0);
        }
        public boolean isMatch() { return type != Type.NONE; }
        public boolean isAllowed() { return type == Type.ALLOW; }
        public boolean isDenied() { return type == Type.DENY; }
        @Override
        public String toString() {
            return switch (type) {
                case ALLOW -> "ALLOW(max=" + max + ")";
                case DENY -> "DENY";
                case NONE -> "NONE";
            };
        }
    }
}