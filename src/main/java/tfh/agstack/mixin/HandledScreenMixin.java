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

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ExpandedPanel.render(context, getScreen(), mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        // 1. 先让 ExpandedPanel 处理
        if (ExpandedPanel.onMouseClick(getScreen(), mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }

        ModConfig config = ModConfig.get();
        HandledScreen<?> screen = getScreen();
        MinecraftClient client = MinecraftClient.getInstance();

        // 2. 检测 Delete 键是否按住（用于垃圾桶删除）
        boolean deleteDown = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_DELETE) == GLFW.GLFW_PRESS;
        if (config.trashEnabled && deleteDown && button == 0) { // 左键
            // 检查光标是否为空
            if (!screen.getScreenHandler().getCursorStack().isEmpty()) {
                return; // 光标有物品，忽略
            }

            Slot clickedSlot = getSlotAt(mouseX, mouseY);
            if (clickedSlot != null && !clickedSlot.getStack().isEmpty()) {
                ClientPlayNetworking.send(new TrashDeletePayload(
                        screen.getScreenHandler().syncId,
                        clickedSlot.id
                ));
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }
        }

        // 3. 原有光标拦截逻辑（聚合槽拖拽拦截）
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

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        // 先让 ExpandedPanel 处理（如 Q 键丢弃子物品）
        if (ExpandedPanel.onKeyPressed(getScreen(), keyCode, scanCode, modifiers)) {
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }

        ModConfig config = ModConfig.get();
        if (!config.trashEnabled) return;

        HandledScreen<?> screen = getScreen();

        // 检测 Backspace（撤销，不按 Shift）
        boolean shiftDown = Screen.hasShiftDown();
        if (!shiftDown && keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            ClientPlayNetworking.send(new TrashUndoPayload(screen.getScreenHandler().syncId));
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}