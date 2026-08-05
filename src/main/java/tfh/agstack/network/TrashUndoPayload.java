package tfh.agstack.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import tfh.agstack.Agstack;

public record TrashUndoPayload(int syncId) implements CustomPayload {
    public static final CustomPayload.Id<TrashUndoPayload> ID = new CustomPayload.Id<>(Agstack.id("trash_undo"));
    public static final PacketCodec<RegistryByteBuf, TrashUndoPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, TrashUndoPayload::syncId,
            TrashUndoPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}