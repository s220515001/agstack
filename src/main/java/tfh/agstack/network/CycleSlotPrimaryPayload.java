package tfh.agstack.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import tfh.agstack.Agstack;

public record CycleSlotPrimaryPayload(int syncId, String inventoryClassName, int slotIndex, int direction) implements CustomPayload {
    public static final CustomPayload.Id<CycleSlotPrimaryPayload> ID = new CustomPayload.Id<>(Agstack.id("cycle_slot_primary"));
    public static final PacketCodec<RegistryByteBuf, CycleSlotPrimaryPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, CycleSlotPrimaryPayload::syncId,
            PacketCodecs.STRING, CycleSlotPrimaryPayload::inventoryClassName,
            PacketCodecs.INTEGER, CycleSlotPrimaryPayload::slotIndex,
            PacketCodecs.INTEGER, CycleSlotPrimaryPayload::direction,
            CycleSlotPrimaryPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}