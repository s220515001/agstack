package tfh.agstack.mixin;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tfh.agstack.component.AggregatedStackComponent;
import tfh.agstack.component.ModDataComponents;
import tfh.agstack.config.ModConfig;
import tfh.agstack.network.TrashDeletePayload;
import tfh.agstack.network.TrashUndoPayload;
import tfh.agstack.screen.ExpandedPanel;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow protected int x;
    @Shadow protected int y;

    @Shadow public abstract Slot getSlotAt(double x, double y);

    @Unique
    private HandledScreen<?> getScreen() {
        return (HandledScreen<?>) (Object) this;
    }

    // 拖拽删除状态
    @Unique private boolean deleteKeyDown = false;
    @Unique private boolean deleteDragging = false;
    @Unique private int lastDeletedSlotId = -1;

    // ==================== 渲染（聚合槽显示） ====================

    @Inject(method = "drawSlot", at = @At("HEAD"), cancellable = true)
    private void renderAggregatedSlot(DrawContext context, Slot slot, CallbackInfo ci) {
        ItemStack stack = slot.getStack();
        AggregatedStackComponent comp = stack.get(ModDataComponents.AGGREGATED_STACK);
        if (comp != null && !comp.subItems().isEmpty()) {
            ItemStack primary = comp.getPrimary();
            if (!primary.isEmpty()) {
                int slotX = slot.x;
                int slotY = slot.y;
                context.getMatrices().push();
                context.getMatrices().translate(slotX, slotY, 0);
                context.drawItem(primary, 0, 0);
                if (primary.isDamageable()) {
                    renderItemDamageBar(context, primary, 0, 0);
                }
                if (primary.hasGlint()) {
                    context.drawItem(primary, 0, 0);
                }
                String countStr = String.valueOf(comp.totalCount());
                var textRenderer = MinecraftClient.getInstance().textRenderer;
                int countX = 16 - textRenderer.getWidth(countStr);
                int countY = 16 - 10;
                context.getMatrices().push();
                context.getMatrices().translate(0, 0, 300);
                context.drawText(textRenderer, countStr, countX, countY, 0xFFFFFF, true);
                context.getMatrices().pop();
                context.getMatrices().pop();
                ci.cancel();
            }
        }
    }

    @Unique
    private void renderItemDamageBar(DrawContext context, ItemStack stack, int x, int y) {
        if (stack.isDamageable()) {
            int damage = stack.getDamage();
            int maxDamage = stack.getMaxDamage();
            if (damage > 0) {
                int barWidth = Math.round(13.0F - (float) damage * 13.0F / (float) maxDamage);
                context.fill(x + 2, y + 13, x + 2 + barWidth, y + 14, 0xFFFF0000);
            }
        }
    }

    // ==================== 渲染循环（检测拖拽删除 + Delete 键释放） ====================

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // 渲染聚合扩展面板
        ExpandedPanel.render(context, getScreen(), mouseX, mouseY);

        // 检测 Delete 键释放（在渲染循环中处理）
        if (deleteKeyDown) {
            long handle = MinecraftClient.getInstance().getWindow().getHandle();
            if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_DELETE) == GLFW.GLFW_RELEASE) {
                deleteKeyDown = false;
                deleteDragging = false;
                lastDeletedSlotId = -1;
            }
        }

        // 拖拽删除：Delete 键按住且左键按下时，检测鼠标下槽位并删除
        if (deleteKeyDown && ModConfig.get().trashEnabled) {
            long handle = MinecraftClient.getInstance().getWindow().getHandle();
            boolean leftDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            if (leftDown) {
                handleTrashDrag(mouseX, mouseY);
            } else {
                // 左键释放，重置拖拽状态
                deleteDragging = false;
                lastDeletedSlotId = -1;
            }
        } else {
            deleteDragging = false;
            lastDeletedSlotId = -1;
        }
    }

    @Unique
    private void handleTrashDrag(int mouseX, int mouseY) {
        HandledScreen<?> screen = getScreen();
        ModConfig config = ModConfig.get();
        if (!config.trashEnabled) return;
        if (!screen.getScreenHandler().getCursorStack().isEmpty()) return;

        Slot slot = getSlotAt(mouseX, mouseY);
        if (slot == null) return;
        if (slot.id == lastDeletedSlotId) return; // 防止重复删除同一槽位
        if (slot.getStack().isEmpty()) return;

        // 发送删除包
        ClientPlayNetworking.send(new TrashDeletePayload(screen.getScreenHandler().syncId, slot.id));
        lastDeletedSlotId = slot.id;
        deleteDragging = true;
    }

    // ==================== 鼠标点击（保留原有点击删除+其他逻辑） ====================

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        // 1. ExpandedPanel 处理（选择子物品）
        if (ExpandedPanel.onMouseClick(getScreen(), mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }

        ModConfig config = ModConfig.get();
        HandledScreen<?> screen = getScreen();
        MinecraftClient client = MinecraftClient.getInstance();

        // 2. 原有点击删除（保留，作为备用）
        boolean deleteDown = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_DELETE) == GLFW.GLFW_PRESS;
        if (config.trashEnabled && deleteDown && button == 0) {
            if (!screen.getScreenHandler().getCursorStack().isEmpty()) {
                return;
            }
            Slot clickedSlot = getSlotAt(mouseX, mouseY);
            if (clickedSlot != null && !clickedSlot.getStack().isEmpty()) {
                ClientPlayNetworking.send(new TrashDeletePayload(screen.getScreenHandler().syncId, clickedSlot.id));
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }
        }

        // 3. 聚合槽拖拽拦截（原有逻辑）
        if (client.player == null) return;
        ItemStack cursor = client.player.currentScreenHandler.getCursorStack();
        if (cursor.isEmpty()) return;
        if (cursor.get(ModDataComponents.AGGREGATED_STACK) != null) {
            Slot slot = getSlotAt(mouseX, mouseY);
            if (slot == null) {
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }

    // ==================== 键盘事件 ====================

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        // ExpandedPanel 处理（如 Q 键丢弃子物品）
        if (ExpandedPanel.onKeyPressed(getScreen(), keyCode, scanCode, modifiers)) {
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }

        ModConfig config = ModConfig.get();
        HandledScreen<?> screen = getScreen();

        // Delete 键按下（设置状态）
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            deleteKeyDown = true;
            return;
        }

        // Backspace 撤销（不按 Shift）
        if (config.trashEnabled) {
            boolean shiftDown = Screen.hasShiftDown();
            if (!shiftDown && keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                ClientPlayNetworking.send(new TrashUndoPayload(screen.getScreenHandler().syncId));
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }
}