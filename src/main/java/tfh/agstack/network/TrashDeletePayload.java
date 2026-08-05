package tfh.agstack.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import tfh.agstack.Agstack;

public record TrashDeletePayload(int syncId, int slotIndex) implements CustomPayload {
    public static final CustomPayload.Id<TrashDeletePayload> ID = new CustomPayload.Id<>(Agstack.id("trash_delete"));
    public static final PacketCodec<RegistryByteBuf, TrashDeletePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, TrashDeletePayload::syncId,
            PacketCodecs.INTEGER, TrashDeletePayload::slotIndex,
            TrashDeletePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}