package tfh.agstack;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfh.agstack.component.ModDataComponents;
import tfh.agstack.config.ModConfig;
import tfh.agstack.network.*;
import tfh.agstack.trash.TrashManager;

public class Agstack implements ModInitializer {
	public static final String MOD_ID = "agstack";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModDataComponents.register();
		ModConfig.load();

		// 原有网络注册
		PayloadTypeRegistry.playC2S().register(CycleSlotPrimaryPayload.ID, CycleSlotPrimaryPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CycleSlotPrimaryPayload.ID, new CycleSlotPrimaryHandler());
		PayloadTypeRegistry.playC2S().register(CyclePrimaryPayload.ID, CyclePrimaryPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CyclePrimaryPayload.ID, new CyclePrimaryHandler());
		PayloadTypeRegistry.playC2S().register(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.ID, new ConfigSyncHandler());
		PayloadTypeRegistry.playC2S().register(ExtractSubItemPayload.ID, ExtractSubItemPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ExtractSubItemPayload.ID, new ExtractSubItemHandler());

		// 注册快速切换装备包
		PayloadTypeRegistry.playC2S().register(CycleArmorPrimaryPayload.ID, CycleArmorPrimaryPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CycleArmorPrimaryPayload.ID, new CycleArmorPrimaryHandler());

		// 注册垃圾桶网络包
		PayloadTypeRegistry.playC2S().register(TrashDeletePayload.ID, TrashDeletePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TrashDeletePayload.ID, new TrashDeleteHandler());
		PayloadTypeRegistry.playC2S().register(TrashUndoPayload.ID, TrashUndoPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TrashUndoPayload.ID, new TrashUndoHandler());

		// 注册服务端 tick 事件，用于超时清理
		ServerTickEvents.END_SERVER_TICK.register(server -> TrashManager.tickRecords());

		// 玩家断开时清除记录
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				TrashManager.removePlayer(handler.getPlayer().getUuid())
		);

		LOGGER.info("Aggregated Stack Mod initialized successfully");
	}

	public static net.minecraft.util.Identifier id(String path) {
		return net.minecraft.util.Identifier.of(MOD_ID, path);
	}
}