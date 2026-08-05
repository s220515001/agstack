package tfh.agstack.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x")
    int getX();

    @Accessor("y")
    int getY();

    @Invoker("getSlotAt")
    Slot invokeGetSlotAt(double x, double y);
}