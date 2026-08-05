package tfh.agstack.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.Registries;
import tfh.agstack.Agstack;

import java.io.*;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class ModConfig {
    public boolean enabled = true;
    public int maxSubItems = 64;
    public boolean autoPickupStack = true;
    public String expandKey = "key.keyboard.left.alt";
    public ExpandBehavior expandBehavior = ExpandBehavior.HOLD;
    public TransferPriority transferPriority = TransferPriority.PRIMARY_FIRST;
    public DeathDrop deathDrop = DeathDrop.SPLIT;
    public boolean creativeAutoStack = false;
    public String splitKey = "key.keyboard.left.ctrl";
    public String configKey = "key.keyboard.p";
    public boolean allowWeaponDifferentMaterial = false;
    public boolean allowArmorDifferentMaterial = false;

    // 垃圾桶配置
    public boolean trashEnabled = true;
    public int trashRetentionSeconds = 30;

    public Set<String> blacklistedItems = new HashSet<>();

    public enum ExpandBehavior { HOLD, TOGGLE, CLICK_OUTSIDE }
    public enum TransferPriority { PRIMARY_FIRST, FIFO }
    public enum DeathDrop { SPLIT, KEEP_ALL }

    public static final PacketCodec<RegistryByteBuf, ModConfig> PACKET_CODEC =
            PacketCodec.of((value, buf) -> {
                buf.writeBoolean(value.enabled);
                buf.writeInt(value.maxSubItems);
                buf.writeBoolean(value.autoPickupStack);
                buf.writeString(value.expandKey);
                buf.writeEnumConstant(value.expandBehavior);
                buf.writeEnumConstant(value.transferPriority);
                buf.writeEnumConstant(value.deathDrop);
                buf.writeBoolean(value.creativeAutoStack);
                buf.writeString(value.splitKey);
                buf.writeString(value.configKey);
                buf.writeInt(value.blacklistedItems.size());
                for (String id : value.blacklistedItems) {
                    buf.writeString(id);
                }
                buf.writeBoolean(value.allowWeaponDifferentMaterial);
                buf.writeBoolean(value.allowArmorDifferentMaterial);
                // 垃圾桶
                buf.writeBoolean(value.trashEnabled);
                buf.writeInt(value.trashRetentionSeconds);
            }, buf -> {
                ModConfig config = new ModConfig();
                config.enabled = buf.readBoolean();
                config.maxSubItems = buf.readInt();
                config.autoPickupStack = buf.readBoolean();
                config.expandKey = buf.readString();
                config.expandBehavior = buf.readEnumConstant(ExpandBehavior.class);
                config.transferPriority = buf.readEnumConstant(TransferPriority.class);
                config.deathDrop = buf.readEnumConstant(DeathDrop.class);
                config.creativeAutoStack = buf.readBoolean();
                config.splitKey = buf.readString();
                config.configKey = buf.readString();
                int size = buf.readInt();
                Set<String> blacklist = new HashSet<>();
                for (int i = 0; i < size; i++) {
                    blacklist.add(buf.readString());
                }
                config.blacklistedItems = blacklist;
                config.allowWeaponDifferentMaterial = buf.readBoolean();
                config.allowArmorDifferentMaterial = buf.readBoolean();
                // 垃圾桶
                config.trashEnabled = buf.readBoolean();
                config.trashRetentionSeconds = buf.readInt();
                return config;
            });

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("agstack.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig INSTANCE = new ModConfig();

    public static ModConfig get() { return INSTANCE; }

    public static void load() {
        if (CONFIG_PATH.toFile().exists()) {
            try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
                INSTANCE = GSON.fromJson(reader, ModConfig.class);
                Agstack.LOGGER.info("Config loaded");
            } catch (IOException e) {
                Agstack.LOGGER.error("Failed to load config", e);
            }
        } else {
            save();
        }
    }

    public static void save() {
        try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            Agstack.LOGGER.error("Failed to save config", e);
        }
    }

    public static void updateFrom(ModConfig newConfig) {
        INSTANCE = newConfig;
        save();
    }

    public boolean isBlacklisted(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String key = getBlacklistKey(stack);
        return blacklistedItems.contains(key);
    }

    public boolean isBlacklisted(Item item) {
        return blacklistedItems.contains(Registries.ITEM.getId(item).toString());
    }

    private String getBlacklistKey(ItemStack stack) {
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        String sig = getComponentSignature(stack);
        if (sig.isEmpty()) return itemId;
        return itemId + "#" + sig;
    }

    public static String getBlacklistKeyStatic(ItemStack stack) {
        return INSTANCE.getBlacklistKey(stack);
    }

    private String getComponentSignature(ItemStack stack) {
        ComponentMap components = stack.getComponents();
        if (components.isEmpty()) return "";

        StringBuilder sig = new StringBuilder();
        var potionContents = components.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents != null) {
            sig.append("potion:").append(potionContents.toString()).append(";");
        }
        var enchantments = components.get(DataComponentTypes.ENCHANTMENTS);
        if (enchantments != null) {
            sig.append("ench:").append(enchantments.toString()).append(";");
        }
        var customName = components.get(DataComponentTypes.CUSTOM_NAME);
        if (customName != null) {
            sig.append("name:").append(customName.getString()).append(";");
        }
        return sig.toString();
    }

    public void addBlacklist(ItemStack stack) {
        blacklistedItems.add(getBlacklistKey(stack));
        save();
    }

    public void removeBlacklist(ItemStack stack) {
        blacklistedItems.remove(getBlacklistKey(stack));
        save();
    }

    public void setBlacklist(Set<String> newBlacklist) {
        this.blacklistedItems = newBlacklist;
        save();
    }
}