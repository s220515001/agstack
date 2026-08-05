package tfh.agstack.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.dynamic.Codecs;

import java.util.ArrayList;
import java.util.List;

public record AggregatedStackComponent(List<ItemStack> subItems, int primaryIndex) {

    public static final Codec<AggregatedStackComponent> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.list(ItemStack.CODEC).fieldOf("subItems").forGetter(AggregatedStackComponent::subItems),
                    Codecs.NONNEGATIVE_INT.fieldOf("primaryIndex").forGetter(AggregatedStackComponent::primaryIndex)
            ).apply(instance, AggregatedStackComponent::new)
    );

    public static final PacketCodec<RegistryByteBuf, AggregatedStackComponent> PACKET_CODEC =
            PacketCodec.tuple(
                    PacketCodecs.collection(ArrayList::new, ItemStack.PACKET_CODEC),
                    AggregatedStackComponent::subItems,
                    PacketCodecs.INTEGER,
                    AggregatedStackComponent::primaryIndex,
                    AggregatedStackComponent::new
            );

    public ItemStack getPrimary() {
        if (primaryIndex >= 0 && primaryIndex < subItems.size()) {
            return subItems.get(primaryIndex);
        }
        return ItemStack.EMPTY;
    }

    public int totalCount() {
        return subItems.size();
    }

    public boolean isEmpty() {
        return subItems.isEmpty();
    }

    public AggregatedStackComponent withNewPrimary(int newIndex) {
        if (newIndex < 0 || newIndex >= subItems.size()) {
            return this;
        }
        return new AggregatedStackComponent(subItems, newIndex);
    }

    public ItemStack tryConvertToNormal() {
        if (subItems.size() == 1) {
            return subItems.get(0).copy();
        }
        return null;
    }

    public boolean shouldConvertToNormal() {
        return subItems.size() <= 1;
    }

    public AggregatedStackComponent addSubItem(ItemStack item) {
        List<ItemStack> newList = new ArrayList<>(subItems);
        newList.add(item.copy());
        int newPrimary = subItems.isEmpty() ? 0 : primaryIndex;
        return new AggregatedStackComponent(newList, newPrimary);
    }

    public AggregatedStackComponent removeSubItem(int index) {
        if (index < 0 || index >= subItems.size()) {
            return this;
        }
        List<ItemStack> newList = new ArrayList<>(subItems);
        newList.remove(index);
        if (newList.isEmpty()) {
            return null;
        }
        int newPrimary = primaryIndex;
        if (newPrimary >= newList.size()) {
            newPrimary = newList.size() - 1;
        }
        if (index < primaryIndex) {
            newPrimary--;
        }
        return new AggregatedStackComponent(newList, newPrimary);
    }

    public ItemStack getSubItem(int index) {
        if (index >= 0 && index < subItems.size()) {
            return subItems.get(index);
        }
        return ItemStack.EMPTY;
    }

    public AggregatedStackComponent withUpdatedPrimary(ItemStack newPrimary) {
        List<ItemStack> newList = new ArrayList<>(subItems);
        newList.set(primaryIndex, newPrimary.copy());
        return new AggregatedStackComponent(newList, primaryIndex);
    }

    public AggregatedStackComponent replaceSubItem(int index, ItemStack newItem) {
        if (index < 0 || index >= subItems.size()) return this;
        List<ItemStack> newList = new ArrayList<>(subItems);
        newList.set(index, newItem.copy());
        return new AggregatedStackComponent(newList, primaryIndex);
    }

    public int getSubItemCount() {
        return subItems.size();
    }

    public List<ItemStack> getSubItemsUnmodifiable() {
        return java.util.Collections.unmodifiableList(subItems);
    }

    /**
     * 将主物品的所有组件（药水效果、附魔等）复制到外层聚合栈，
     * 但保留聚合栈自己的 AGGREGATED_STACK 组件。
     * 注意：此方法不会改变外层 ItemStack 的 Item 类型。
     */
    public void applyToItemStack(ItemStack outer) {
        if (subItems.isEmpty()) return;
        ItemStack primary = getPrimary();
        // 保存自己的组件
        AggregatedStackComponent selfComp = outer.get(ModDataComponents.AGGREGATED_STACK);
        // 复制主物品的所有组件到外层
        outer.applyComponentsFrom(primary.getComponents());
        // 恢复自己的组件
        if (selfComp != null) {
            outer.set(ModDataComponents.AGGREGATED_STACK, selfComp);
        }
    }

    /**
     * 根据聚合组件创建一个新的外层 ItemStack，其 Item 类型与主物品一致。
     */
    public static ItemStack createAggregatedStack(AggregatedStackComponent comp) {
        if (comp == null || comp.isEmpty()) return ItemStack.EMPTY;
        ItemStack primary = comp.getPrimary();
        ItemStack stack = new ItemStack(primary.getItem());
        stack.set(ModDataComponents.AGGREGATED_STACK, comp);
        comp.applyToItemStack(stack);
        return stack;
    }
}