package tfh.agstack.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import tfh.agstack.component.AggregatedStackComponent;
import tfh.agstack.component.ModDataComponents;
import tfh.agstack.config.ModConfig;
import tfh.agstack.mixin.HandledScreenAccessor;
import tfh.agstack.network.ExtractSubItemPayload;

import java.util.ArrayList;
import java.util.List;

public class ExpandedPanel {
    private static Slot currentHoveredSlot = null;
    private static boolean panelOpen = false;
    private static boolean toggleState = false;
    private static boolean keyHeld = false;
    private static List<SubItemButton> subItemButtons = new ArrayList<>();
    private static Slot displayedSlot = null;
    private static int hoveredButtonIndex = -1;

    public static void setKeyHeld(boolean held) {
        keyHeld = held;
    }

    public static void onScreenClose() {
        panelOpen = false;
        displayedSlot = null;
        currentHoveredSlot = null;
        subItemButtons.clear();
        hoveredButtonIndex = -1;
    }

    public static boolean onKeyPressed(HandledScreen<?> screen, int keyCode, int scanCode, int modifiers) {
        if (!panelOpen || currentHoveredSlot == null || hoveredButtonIndex == -1) return false;
        boolean isCtrlDown = (modifiers & 2) != 0;
        if (keyCode == 81) {
            discardSubItem(screen, hoveredButtonIndex, isCtrlDown);
            return true;
        }
        return false;
    }

    private static void discardSubItem(HandledScreen<?> screen, int subIndex, boolean wholeStack) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) return;
        AggregatedStackComponent comp = currentHoveredSlot.getStack().get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null || subIndex >= comp.subItems().size()) return;
        int syncId = screen.getScreenHandler().syncId;
        int slotId = currentHoveredSlot.id;
        ClientPlayNetworking.send(new ExtractSubItemPayload(syncId, slotId, subIndex, 3, false));
        // 丢弃后也保持面板打开，但槽位可能变空，render 会检测并关闭
    }

    public static void render(DrawContext context, HandledScreen<?> screen, int mouseX, int mouseY) {
        ModConfig config = ModConfig.get();
        if (!config.enabled) return;

        int screenX = ((HandledScreenAccessor) screen).getX();
        int screenY = ((HandledScreenAccessor) screen).getY();

        Slot hoveredSlot = getSlotAt(screen, screenX, screenY, mouseX, mouseY);
        boolean isHoveringAggregated = hoveredSlot != null &&
                hoveredSlot.getStack().get(ModDataComponents.AGGREGATED_STACK) != null;

        boolean shouldOpen = false;

        switch (config.expandBehavior) {
            case HOLD:
                if (keyHeld) {
                    if (displayedSlot == null && isHoveringAggregated) {
                        displayedSlot = hoveredSlot;
                    }
                    if (isHoveringAggregated && hoveredSlot != displayedSlot) {
                        displayedSlot = hoveredSlot;
                    }
                    shouldOpen = displayedSlot != null;
                    if (shouldOpen) {
                        panelOpen = true;
                        currentHoveredSlot = displayedSlot;
                    } else {
                        if (panelOpen) panelOpen = false;
                        currentHoveredSlot = null;
                    }
                } else {
                    if (panelOpen) {
                        panelOpen = false;
                        currentHoveredSlot = null;
                    }
                    displayedSlot = null;
                }
                break;

            case TOGGLE:
                if (keyHeld && isHoveringAggregated && currentHoveredSlot != hoveredSlot) {
                    toggleState = !toggleState;
                    currentHoveredSlot = hoveredSlot;
                }
                shouldOpen = toggleState && currentHoveredSlot != null;
                break;

            case CLICK_OUTSIDE:
                if (keyHeld && isHoveringAggregated && currentHoveredSlot != hoveredSlot) {
                    panelOpen = true;
                    currentHoveredSlot = hoveredSlot;
                }
                shouldOpen = panelOpen;
                if (panelOpen && currentHoveredSlot != null) {
                    int panelWidth = getPanelWidth();
                    int panelHeight = getPanelHeight(currentHoveredSlot);
                    int panelX = getPanelX(screenX, currentHoveredSlot, panelWidth);
                    int panelY = getPanelY(screenY, currentHoveredSlot, panelHeight);
                    if (mouseX < panelX || mouseX > panelX + panelWidth ||
                            mouseY < panelY || mouseY > panelY + panelHeight) {
                        panelOpen = false;
                        currentHoveredSlot = null;
                    }
                }
                break;
        }

        // 如果面板打开，但当前槽位已无效（比如槽位变空），自动关闭
        if (panelOpen && currentHoveredSlot != null) {
            ItemStack stack = currentHoveredSlot.getStack();
            AggregatedStackComponent comp = stack.get(ModDataComponents.AGGREGATED_STACK);
            if (stack.isEmpty() || comp == null || comp.isEmpty()) {
                panelOpen = false;
                currentHoveredSlot = null;
                displayedSlot = null;
                subItemButtons.clear();
                hoveredButtonIndex = -1;
                return;
            }
        }

        if (!panelOpen || currentHoveredSlot == null) {
            subItemButtons.clear();
            hoveredButtonIndex = -1;
            return;
        }

        // 重新获取组件，确保数据最新
        ItemStack stack = currentHoveredSlot.getStack();
        AggregatedStackComponent comp = stack.get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null || comp.isEmpty()) {
            panelOpen = false;
            currentHoveredSlot = null;
            displayedSlot = null;
            subItemButtons.clear();
            hoveredButtonIndex = -1;
            return;
        }

        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight(currentHoveredSlot);
        int panelX = getPanelX(screenX, currentHoveredSlot, panelWidth);
        int panelY = getPanelY(screenY, currentHoveredSlot, panelHeight);

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 500);
        renderPanel(context, panelX, panelY, comp, mouseX, mouseY);
        context.getMatrices().pop();
    }

    private static void renderPanel(DrawContext context, int panelX, int panelY,
                                    AggregatedStackComponent comp, int mouseX, int mouseY) {
        int slotWidth = 18;
        int slotHeight = 18;
        int columns = 7;
        int rows = (int) Math.ceil(comp.subItems().size() / (double) columns);
        int panelWidth = columns * slotWidth + 4;
        int panelHeight = rows * slotHeight + 4;

        context.fill(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, 0xFF000000);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF8B8B8B);
        context.drawBorder(panelX - 1, panelY - 1, panelWidth + 2, panelHeight + 2, 0xFFAAAAAA);

        subItemButtons.clear();
        hoveredButtonIndex = -1;
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        for (int i = 0; i < comp.subItems().size(); i++) {
            ItemStack subItem = comp.subItems().get(i);
            int col = i % columns;
            int row = i / columns;
            int itemX = panelX + col * slotWidth + 2;
            int itemY = panelY + row * slotHeight + 2;

            boolean isHovered = mouseX >= itemX && mouseX <= itemX + slotWidth &&
                    mouseY >= itemY && mouseY <= itemY + slotHeight;
            if (isHovered) {
                hoveredButtonIndex = i;
            }

            context.drawItem(subItem, itemX, itemY);
            if (subItem.getCount() > 1) {
                String count = String.valueOf(subItem.getCount());
                context.getMatrices().push();
                context.getMatrices().translate(0, 0, 300);
                context.drawText(textRenderer, count, itemX + 16 - textRenderer.getWidth(count),
                        itemY + 16 - 10, 0xFFFFFF, true);
                context.getMatrices().pop();
            }
            if (subItem.isDamageable()) {
                renderDamageBar(context, subItem, itemX, itemY);
            }
            if (i == comp.primaryIndex()) {
                context.drawBorder(itemX - 1, itemY - 1, slotWidth, slotHeight, 0xFFFFFF00);
            }
            subItemButtons.add(new SubItemButton(itemX, itemY, slotWidth, slotHeight, i));
        }

        if (hoveredButtonIndex != -1 && hoveredButtonIndex < comp.subItems().size()) {
            ItemStack hoveredStack = comp.subItems().get(hoveredButtonIndex);
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world != null) {
                Item.TooltipContext tooltipContext = Item.TooltipContext.create(client.world);
                TooltipType tooltipType = client.options.advancedItemTooltips ? TooltipType.ADVANCED : TooltipType.BASIC;
                List<Text> tooltip = hoveredStack.getTooltip(tooltipContext, client.player, tooltipType);
                context.drawTooltip(textRenderer, tooltip, mouseX, mouseY);
            } else {
                List<Text> tooltip = new ArrayList<>();
                tooltip.add(hoveredStack.getName().copy());
                if (hoveredStack.getCount() > 1) {
                    tooltip.add(Text.literal("数量: " + hoveredStack.getCount()).formatted(Formatting.GRAY));
                }
                context.drawTooltip(textRenderer, tooltip, mouseX, mouseY);
            }
        }
    }

    private static void renderDamageBar(DrawContext context, ItemStack stack, int x, int y) {
        if (stack.isDamageable()) {
            int damage = stack.getDamage();
            int maxDamage = stack.getMaxDamage();
            if (damage > 0) {
                int barWidth = Math.round(13.0F - (float) damage * 13.0F / (float) maxDamage);
                context.fill(x + 2, y + 13, x + 2 + barWidth, y + 14, 0xFFFF0000);
            }
        }
    }

    public static boolean onMouseClick(HandledScreen<?> screen, double mouseX, double mouseY, int button) {
        if (!panelOpen || currentHoveredSlot == null) return false;
        if (button != 0) return false; // 只处理左键

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        ItemStack cursor = client.player.currentScreenHandler.getCursorStack();

        ItemStack stack = currentHoveredSlot.getStack();
        AggregatedStackComponent comp = stack.get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null || comp.isEmpty()) return false;

        int screenX = ((HandledScreenAccessor) screen).getX();
        int screenY = ((HandledScreenAccessor) screen).getY();
        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight(currentHoveredSlot);
        int panelX = getPanelX(screenX, currentHoveredSlot, panelWidth);
        int panelY = getPanelY(screenY, currentHoveredSlot, panelHeight);

        if (mouseX < panelX - 2 || mouseX > panelX + panelWidth + 2 ||
                mouseY < panelY - 2 || mouseY > panelY + panelHeight + 2) {
            return false;
        }

        for (SubItemButton btn : subItemButtons) {
            if (mouseX >= btn.x && mouseX <= btn.x + btn.width &&
                    mouseY >= btn.y && mouseY <= btn.y + btn.height) {

                // 前置校验：光标必须为空
                if (!cursor.isEmpty()) {
                    // 光标有物品，操作无效，面板保持打开
                    return true;
                }

                // 允许取出任意子物品（包括顶层）
                if (client.getNetworkHandler() == null) return true;
                int syncId = screen.getScreenHandler().syncId;
                int slotId = currentHoveredSlot.id;
                ClientPlayNetworking.send(new ExtractSubItemPayload(syncId, slotId, btn.index, 0, false));

                // 不关闭面板，让 render 自动刷新显示
                // 面板会在下一次渲染时更新
                return true;
            }
        }

        return true; // 点击面板空白，阻止原版操作
    }

    private static int getPanelX(int screenX, Slot slot, int panelWidth) {
        int slotRight = screenX + slot.x + 16;
        int screenWidth = MinecraftClient.getInstance().getWindow().getScaledWidth();
        if (slotRight + panelWidth + 5 <= screenWidth) {
            return slotRight + 2;
        } else {
            return screenX + slot.x - panelWidth - 2;
        }
    }

    private static int getPanelY(int screenY, Slot slot, int panelHeight) {
        int slotTop = screenY + slot.y;
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        int y = slotTop;
        if (y + panelHeight > screenHeight - 10) {
            y = screenHeight - panelHeight - 10;
        }
        if (y < 10) y = 10;
        return y;
    }

    private static int getPanelWidth() {
        return 7 * 18 + 4;
    }

    private static int getPanelHeight(Slot slot) {
        if (currentHoveredSlot == null) return 0;
        AggregatedStackComponent comp = currentHoveredSlot.getStack().get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null) return 0;
        int rows = (int) Math.ceil(comp.subItems().size() / 7.0);
        return rows * 18 + 4;
    }

    private static Slot getSlotAt(HandledScreen<?> screen, int screenX, int screenY, int mouseX, int mouseY) {
        for (Slot slot : screen.getScreenHandler().slots) {
            int slotX = screenX + slot.x;
            int slotY = screenY + slot.y;
            if (mouseX >= slotX && mouseX <= slotX + 16 && mouseY >= slotY && mouseY <= slotY + 16) {
                return slot;
            }
        }
        return null;
    }

    private static class SubItemButton {
        final int x, y, width, height, index;
        SubItemButton(int x, int y, int width, int height, int index) {
            this.x = x; this.y = y; this.width = width; this.height = height; this.index = index;
        }
    }
}