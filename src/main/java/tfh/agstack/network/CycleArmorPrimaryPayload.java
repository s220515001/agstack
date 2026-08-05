package tfh.agstack.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import tfh.agstack.Agstack;

public record CycleArmorPrimaryPayload(int armorSlot, int direction) implements CustomPayload {
    // armorSlot: -1 表示全部，0=靴子, 1=护腿, 2=胸甲, 3=头盔
    public static final CustomPayload.Id<CycleArmorPrimaryPayload> ID = new CustomPayload.Id<>(Agstack.id("cycle_armor_primary"));
    public static final PacketCodec<RegistryByteBuf, CycleArmorPrimaryPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, CycleArmorPrimaryPayload::armorSlot,
            PacketCodecs.INTEGER, CycleArmorPrimaryPayload::direction,
            CycleArmorPrimaryPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}