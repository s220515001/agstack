package tfh.agstack.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tfh.agstack.component.AggregatedStackComponent;
import tfh.agstack.component.ModDataComponents;
import tfh.agstack.config.ModConfig;
import tfh.agstack.util.AggregationHelper;
import tfh.agstack.util.DropFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {

    @Shadow
    public abstract ItemStack getCursorStack();

    @Shadow
    public abstract void setCursorStack(ItemStack stack);

    @Shadow
    public abstract ItemStack quickMove(PlayerEntity player, int slot);

    @Unique
    private boolean justSplitAggregated = false;

    @Unique
    private int lastSplitSlot = -1;

    private static final Set<Class<?>> DISALLOWED_HANDLERS = Set.of(
            CraftingScreenHandler.class,
            AbstractFurnaceScreenHandler.class,
            BrewingStandScreenHandler.class,
            MerchantScreenHandler.class,
            GrindstoneScreenHandler.class,
            AnvilScreenHandler.class,
            SmithingScreenHandler.class,
            StonecutterScreenHandler.class,
            LoomScreenHandler.class,
            CartographyTableScreenHandler.class
    );

    private boolean isAllowedContainer(ScreenHandler handler) {
        for (Class<?> disallowed : DISALLOWED_HANDLERS) {
            if (disallowed.isInstance(handler)) {
                return false;
            }
        }
        return true;
    }

    @Inject(method = "internalOnSlotClick", at = @At("HEAD"), cancellable = true)
    private void onSlotClick(int slotIndex, int button, SlotActionType actionType,
                             PlayerEntity player, CallbackInfo ci) {
        ScreenHandler handler = (ScreenHandler) (Object) this;
        ModConfig config = ModConfig.get();

        if (slotIndex == -1 && actionType == SlotActionType.PICKUP && button == 0) {
            ItemStack cursor = getCursorStack();
            if (!cursor.isEmpty()) {
                AggregatedStackComponent comp = cursor.get(ModDataComponents.AGGREGATED_STACK);
                if (comp != null && !comp.isEmpty()) {
                    DropFlag.setSkipDropProcessing(true);
                    try {
                        player.dropItem(cursor, true, false);
                    } finally {
                        DropFlag.clear();
                    }
                    setCursorStack(ItemStack.EMPTY);
                    ci.cancel();
                    return;
                }
            }
        }

        if (slotIndex < 0 || slotIndex >= handler.slots.size()) return;

        Slot slot = handler.slots.get(slotIndex);
        ItemStack slotStack = slot.getStack();
        AggregatedStackComponent slotComp = slotStack.get(ModDataComponents.AGGREGATED_STACK);
        ItemStack cursor = getCursorStack();
        AggregatedStackComponent cursorComp = cursor.get(ModDataComponents.AGGREGATED_STACK);
        boolean isPlayerInventory = slot.inventory instanceof PlayerInventory;
        boolean allowed = isAllowedContainer(handler);

        if (actionType == SlotActionType.THROW && slotComp != null) {
            DropFlag.setSkipDropProcessing(true);
            try {
                player.dropItem(slotStack, true, false);
            } finally {
                DropFlag.clear();
            }
            slot.setStack(ItemStack.EMPTY);
            ci.cancel();
            return;
        }

        if (actionType == SlotActionType.QUICK_MOVE) {
            if (justSplitAggregated && slotIndex == lastSplitSlot) {
                justSplitAggregated = false;
                lastSplitSlot = -1;
                ci.cancel();
                return;
            }
            if (slotComp != null) {
                splitOneItemFromAggregated(slot, slotComp, player);
                justSplitAggregated = true;
                lastSplitSlot = slotIndex;
                ci.cancel();
                return;
            }
            return;
        }

        if (actionType == SlotActionType.PICKUP_ALL && button == 0 && !slotStack.isEmpty()) {
            handlePickupAll(slotIndex, player, handler);
            ci.cancel();
            return;
        }

        if (slotComp == null && slotStack.isEmpty() && !cursor.isEmpty()) {
            if (cursorComp != null) {
                // 放置聚合槽到空格，直接复制，Item 类型正确（因为 cursor 本身就是正确的）
                slot.setStack(cursor.copy());
                setCursorStack(ItemStack.EMPTY);
                ci.cancel();
                return;
            }
        }

        if (!allowed && !isPlayerInventory) {
            if (cursorComp != null) {
                ci.cancel();
                return;
            }
            if (slotComp != null && !cursor.isEmpty() && cursorComp == null) {
                ci.cancel();
                return;
            }
        }

        // 拖拽创建聚合槽（两个普通物品）
        if (slotComp == null && !slotStack.isEmpty() && !cursor.isEmpty() &&
                AggregationHelper.canAggregate(cursor, slotStack) &&
                cursorComp == null &&
                actionType == SlotActionType.PICKUP && button == 0) {

            if (config.isBlacklisted(slotStack) || config.isBlacklisted(cursor)) {
                ci.cancel();
                return;
            }
            if (canMergeNormally(cursor, slotStack)) {
                return;
            }
            createAggregatedFromTwoStacks(slot, slotStack, cursor);
            ci.cancel();
            return;
        }

        if (slotComp != null) {

            if (actionType == SlotActionType.PICKUP && button == 0) {

                // 合并两个聚合槽
                if (cursorComp != null && AggregationHelper.canAggregate(cursor, slotStack)) {
                    if (containsBlacklisted(cursorComp)) {
                        ci.cancel();
                        return;
                    }
                    mergeAggregatedStacks(slot, slotStack, slotComp, cursor, cursorComp);
                    ci.cancel();
                    return;
                }

                if (cursor.isEmpty()) {
                    setCursorStack(slotStack.copy());
                    slot.setStack(ItemStack.EMPTY);
                    ci.cancel();
                    return;
                }

                // Shift+左键将光标上的普通物品添加到聚合槽
                if (player.isSneaking() && cursorComp == null && AggregationHelper.canAggregate(cursor, slotStack)) {
                    if (slotComp.getSubItemCount() < config.maxSubItems && !config.isBlacklisted(cursor)) {
                        AggregatedStackComponent newComp = slotComp.addSubItem(cursor.copy());
                        ItemStack newSlotStack = AggregatedStackComponent.createAggregatedStack(newComp);
                        slot.setStack(newSlotStack);
                        setCursorStack(ItemStack.EMPTY);
                    }
                    ci.cancel();
                    return;
                }

                // 普通交换：如果光标是普通物品，允许交换，但需保证外层 Item 正确
                ItemStack temp = slotStack.copy();
                if (cursorComp != null) {
                    // 光标是聚合槽，直接放入
                    slot.setStack(cursor.copy());
                } else {
                    // 光标是普通物品，直接放入（但普通物品不能放入聚合槽，原版行为？我们允许交换）
                    slot.setStack(cursor.copy());
                }
                setCursorStack(temp);
                ci.cancel();
                return;
            }

            if (actionType == SlotActionType.PICKUP && button == 1) {
                handleRightClick(slot, slotStack, slotComp, cursor);
                ci.cancel();
                return;
            }
        }
    }

    // ==================== 辅助方法 ====================

    @Unique
    private boolean containsBlacklisted(AggregatedStackComponent comp) {
        ModConfig config = ModConfig.get();
        for (ItemStack sub : comp.subItems()) {
            if (config.isBlacklisted(sub)) return true;
        }
        return false;
    }

    @Unique
    private void handlePickupAll(int slotIndex, PlayerEntity player, ScreenHandler handler) {
        ModConfig config = ModConfig.get();
        Slot clickedSlot = handler.slots.get(slotIndex);
        ItemStack clickedStack = clickedSlot.getStack();
        if (clickedStack.isEmpty()) return;

        List<Slot> matchingSlots = new ArrayList<>();
        for (Slot slot : handler.slots) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && AggregationHelper.canAggregate(stack, clickedStack) && !config.isBlacklisted(stack)) {
                matchingSlots.add(slot);
            }
        }
        if (matchingSlots.size() <= 1) return;

        List<ItemStack> allSubItems = new ArrayList<>();
        for (Slot slot : matchingSlots) {
            ItemStack stack = slot.getStack();
            AggregatedStackComponent otherComp = stack.get(ModDataComponents.AGGREGATED_STACK);
            if (otherComp != null) {
                allSubItems.addAll(otherComp.subItems());
            } else {
                allSubItems.add(stack.copy());
            }
            slot.setStack(ItemStack.EMPTY);
        }

        int maxSub = config.maxSubItems;
        List<AggregatedStackComponent> aggregates = new ArrayList<>();
        List<ItemStack> currentBatch = new ArrayList<>();
        for (ItemStack sub : allSubItems) {
            if (currentBatch.size() >= maxSub) {
                aggregates.add(new AggregatedStackComponent(new ArrayList<>(currentBatch), 0));
                currentBatch.clear();
            }
            currentBatch.add(sub.copy());
        }
        if (!currentBatch.isEmpty()) {
            aggregates.add(new AggregatedStackComponent(new ArrayList<>(currentBatch), 0));
        }

        List<Slot> slotsToFill = new ArrayList<>();
        slotsToFill.add(clickedSlot);
        for (Slot slot : matchingSlots) {
            if (slot != clickedSlot) slotsToFill.add(slot);
        }

        int fillIndex = 0;
        for (AggregatedStackComponent agg : aggregates) {
            ItemStack finalStack = AggregatedStackComponent.createAggregatedStack(agg);
            if (fillIndex < slotsToFill.size()) {
                slotsToFill.get(fillIndex).setStack(finalStack);
                fillIndex++;
            } else {
                player.getInventory().offerOrDrop(finalStack);
            }
        }
    }

    @Unique
    private void mergeAggregatedStacks(Slot slot, ItemStack slotStack,
                                       AggregatedStackComponent targetComp,
                                       ItemStack cursorStack,
                                       AggregatedStackComponent sourceComp) {
        ModConfig config = ModConfig.get();
        int maxItems = config.maxSubItems;
        int currentCount = targetComp.getSubItemCount();
        int available = maxItems - currentCount;

        if (available <= 0) {
            // 交换两个聚合槽
            slot.setStack(cursorStack.copy());
            setCursorStack(slotStack.copy());
            return;
        }

        List<ItemStack> sourceItems = new ArrayList<>(sourceComp.subItems());
        List<ItemStack> newTargetItems = new ArrayList<>(targetComp.subItems());
        int added = 0;

        for (ItemStack sub : sourceItems) {
            if (added >= available) break;
            if (config.isBlacklisted(sub)) continue;
            newTargetItems.add(sub.copy());
            added++;
        }

        // 目标聚合槽的主索引保持不变
        AggregatedStackComponent newTargetComp = new AggregatedStackComponent(
                newTargetItems,
                targetComp.primaryIndex()
        );
        ItemStack newSlotStack = AggregatedStackComponent.createAggregatedStack(newTargetComp);
        slot.setStack(newSlotStack);

        if (added >= sourceItems.size()) {
            setCursorStack(ItemStack.EMPTY);
        } else {
            List<ItemStack> remaining = sourceItems.subList(added, sourceItems.size());
            int newPrimary = sourceComp.primaryIndex() - added;
            if (newPrimary < 0) newPrimary = 0;
            AggregatedStackComponent newSourceComp = new AggregatedStackComponent(
                    new ArrayList<>(remaining),
                    newPrimary
            );
            ItemStack newCursor = AggregatedStackComponent.createAggregatedStack(newSourceComp);
            setCursorStack(newCursor);
        }
    }

    @Unique
    private void splitOneItemFromAggregated(Slot slot, AggregatedStackComponent comp, PlayerEntity player) {
        ItemStack primary = comp.getPrimary().copy();
        ItemStack splitItem;
        if (primary.getCount() > 1) {
            splitItem = primary.copy();
            splitItem.setCount(1);
            splitItem.remove(ModDataComponents.AGGREGATED_STACK);
            primary.setCount(primary.getCount() - 1);
            AggregatedStackComponent newComp = comp.withUpdatedPrimary(primary);
            ItemStack newSlotStack = AggregatedStackComponent.createAggregatedStack(newComp);
            slot.setStack(newSlotStack);
        } else {
            splitItem = primary.copy();
            splitItem.remove(ModDataComponents.AGGREGATED_STACK);
            AggregatedStackComponent newComp = comp.removeSubItem(comp.primaryIndex());
            if (newComp == null || newComp.subItems().isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                ItemStack newSlotStack = AggregatedStackComponent.createAggregatedStack(newComp);
                slot.setStack(newSlotStack);
            }
        }
        PlayerInventory inv = player.getInventory();
        if (!inv.insertStack(splitItem)) {
            player.dropItem(splitItem, false);
        }
    }

    @Unique
    private boolean canMergeNormally(ItemStack a, ItemStack b) {
        if (!ItemStack.areItemsAndComponentsEqual(a, b)) return false;
        int total = a.getCount() + b.getCount();
        return total <= a.getMaxCount();
    }

    @Unique
    private void createAggregatedFromTwoStacks(Slot slot, ItemStack slotStack, ItemStack cursorStack) {
        AggregatedStackComponent newComp = new AggregatedStackComponent(
                List.of(cursorStack.copy(), slotStack.copy()), 0);
        ItemStack aggregated = AggregatedStackComponent.createAggregatedStack(newComp);
        slot.setStack(aggregated);
        setCursorStack(ItemStack.EMPTY);
    }

    @Unique
    private void handleRightClick(Slot slot, ItemStack slotStack,
                                  AggregatedStackComponent comp, ItemStack cursorStack) {
        ModConfig config = ModConfig.get();
        if (cursorStack.isEmpty()) {
            ItemStack primary = comp.getPrimary().copy();
            if (primary.getMaxCount() > 1) {
                int half = (int) Math.ceil(primary.getCount() / 2.0);
                ItemStack halfStack = primary.copy();
                halfStack.setCount(half);
                setCursorStack(halfStack);
                primary.setCount(primary.getCount() - half);
                if (primary.getCount() <= 0) {
                    AggregatedStackComponent newComp = comp.removeSubItem(comp.primaryIndex());
                    if (newComp == null || newComp.subItems().isEmpty()) {
                        slot.setStack(ItemStack.EMPTY);
                    } else {
                        ItemStack newSlotStack = AggregatedStackComponent.createAggregatedStack(newComp);
                        slot.setStack(newSlotStack);
                    }
                } else {
                    AggregatedStackComponent newComp = comp.withUpdatedPrimary(primary);
                    ItemStack newSlotStack = AggregatedStackComponent.createAggregatedStack(newComp);
                    slot.setStack(newSlotStack);
                }
            } else {
                setCursorStack(primary);
                AggregatedStackComponent newComp = comp.removeSubItem(comp.primaryIndex());
                if (newComp == null || newComp.subItems().isEmpty()) {
                    slot.setStack(ItemStack.EMPTY);
                } else {
                    ItemStack newSlotStack = AggregatedStackComponent.createAggregatedStack(newComp);
                    slot.setStack(newSlotStack);
                }
            }
        } else {
            if (AggregationHelper.canAggregate(cursorStack, slotStack) &&
                    cursorStack.get(ModDataComponents.AGGREGATED_STACK) == null &&
                    !config.isBlacklisted(cursorStack)) {
                if (comp.getSubItemCount() < config.maxSubItems) {
                    ItemStack toAdd = cursorStack.copy();
                    toAdd.setCount(1);
                    AggregatedStackComponent newComp = comp.addSubItem(toAdd);
                    ItemStack newSlotStack = AggregatedStackComponent.createAggregatedStack(newComp);
                    slot.setStack(newSlotStack);
                    cursorStack.decrement(1);
                    if (cursorStack.isEmpty()) {
                        setCursorStack(ItemStack.EMPTY);
                    } else {
                        setCursorStack(cursorStack);
                    }
                }
            }
        }
    }
}