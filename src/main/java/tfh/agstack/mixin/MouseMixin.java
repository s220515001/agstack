package tfh.agstack.mixin;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tfh.agstack.AgstackClient;
import tfh.agstack.component.AggregatedStackComponent;
import tfh.agstack.component.ModDataComponents;
import tfh.agstack.network.CycleArmorPrimaryPayload;
import tfh.agstack.network.CyclePrimaryPayload;
import tfh.agstack.network.CycleSlotPrimaryPayload;

@Mixin(Mouse.class)
public class MouseMixin {

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean ctrlDown = net.minecraft.client.util.InputUtil.isKeyPressed(
                client.getWindow().getHandle(),
                GLFW.GLFW_KEY_LEFT_CONTROL
        ) || net.minecraft.client.util.InputUtil.isKeyPressed(
                client.getWindow().getHandle(),
                GLFW.GLFW_KEY_RIGHT_CONTROL
        );

        int armorSwitchKey = AgstackClient.getArmorSwitchKeyCode();
        boolean armorSwitchDown = net.minecraft.client.util.InputUtil.isKeyPressed(
                client.getWindow().getHandle(),
                armorSwitchKey
        );

        // === Ctrl + 滚轮：切换聚合物品（GUI 和无 GUI 均支持） ===
        if (ctrlDown) {
            // 有 GUI 时：检测悬停槽位
            if (client.currentScreen instanceof HandledScreen<?> handledScreen) {
                HandledScreenAccessor accessor = (HandledScreenAccessor) handledScreen;
                double scaleFactor = client.getWindow().getScaleFactor();
                double mouseX = client.mouse.getX() / scaleFactor;
                double mouseY = client.mouse.getY() / scaleFactor;

                // 手动遍历槽位
                Slot hoveredSlot = null;
                int guiLeft = accessor.getX();
                int guiTop = accessor.getY();
                for (Slot slot : handledScreen.getScreenHandler().slots) {
                    int slotLeft = guiLeft + slot.x;
                    int slotTop = guiTop + slot.y;
                    if (mouseX >= slotLeft && mouseX < slotLeft + 16 &&
                            mouseY >= slotTop && mouseY < slotTop + 16) {
                        hoveredSlot = slot;
                        break;
                    }
                }

                if (hoveredSlot != null) {
                    ItemStack stack = hoveredSlot.getStack();
                    AggregatedStackComponent comp = stack.get(ModDataComponents.AGGREGATED_STACK);
                    if (comp != null && !comp.isEmpty()) {
                        ci.cancel();
                        int direction = vertical > 0 ? 1 : -1;
                        // 使用新构造器：syncId, inventory类名, 槽位索引, direction
                        String invClassName = hoveredSlot.inventory.getClass().getName();
                        int slotIndex = hoveredSlot.getIndex();
                        ClientPlayNetworking.send(new CycleSlotPrimaryPayload(
                                handledScreen.getScreenHandler().syncId,
                                invClassName,
                                slotIndex,
                                direction
                        ));
                        return;
                    }
                }
                // 未命中聚合槽，不拦截
                return;
            } else {
                // 无 GUI：切换主手聚合物品
                if (client.player == null) return;
                ItemStack mainHand = client.player.getMainHandStack();
                AggregatedStackComponent comp = mainHand.get(ModDataComponents.AGGREGATED_STACK);
                if (comp == null) return;

                ci.cancel();
                int direction = vertical > 0 ? 1 : -1;
                int newIndex = comp.primaryIndex() + direction;
                if (newIndex < 0) newIndex = comp.subItems().size() - 1;
                if (newIndex >= comp.subItems().size()) newIndex = 0;
                if (newIndex != comp.primaryIndex()) {
                    ClientPlayNetworking.send(new CyclePrimaryPayload(direction));
                }
            }
            return;
        }

        // === 装备快速切换（Tab + 滚轮） ===
        if (armorSwitchDown && client.currentScreen == null && client.player != null) {
            ci.cancel();
            int direction = vertical > 0 ? 1 : -1;
            int armorSlot = -1;
            if (net.minecraft.client.util.InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_1)) {
                armorSlot = 3; // 头盔
            } else if (net.minecraft.client.util.InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_2)) {
                armorSlot = 2; // 胸甲
            } else if (net.minecraft.client.util.InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_3)) {
                armorSlot = 1; // 护腿
            } else if (net.minecraft.client.util.InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_4)) {
                armorSlot = 0; // 靴子
            }
            ClientPlayNetworking.send(new CycleArmorPrimaryPayload(armorSlot, direction));
            return;
        }
        // 不拦截其他情况
    }
}