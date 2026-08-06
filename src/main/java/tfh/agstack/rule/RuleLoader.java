package tfh.agstack.rule;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.util.Identifier;
import tfh.agstack.Agstack;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class RuleLoader {
    private static final Gson GSON = new Gson();
    private static final Path RULES_DIR = FabricLoader.getInstance().getConfigDir().resolve("agstack/rules");

    public static List<StackRule> load() throws IOException {
        List<StackRule> rules = new ArrayList<>();

        // 确保目录存在
        if (!Files.exists(RULES_DIR)) {
            Files.createDirectories(RULES_DIR);
            Agstack.LOGGER.info("Created rules directory: {}", RULES_DIR);
            return rules;
        }

        // 扫描所有 .json 文件
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(RULES_DIR, "*.json")) {
            for (Path file : stream) {
                try {
                    List<StackRule> fileRules = loadFile(file);
                    rules.addAll(fileRules);
                    Agstack.LOGGER.info("Loaded {} rules from {}", fileRules.size(), file.getFileName());
                } catch (Exception e) {
                    Agstack.LOGGER.error("Failed to load rule file {}: {}", file.getFileName(), e.getMessage());
                }
            }
        }

        // 按优先级排序（降序）
        rules.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        Agstack.LOGGER.info("Total loaded {} rules", rules.size());
        return rules;
    }

    private static List<StackRule> loadFile(Path file) throws IOException {
        List<StackRule> rules = new ArrayList<>();

        try (java.io.Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            JsonArray rulesArray = root.getAsJsonArray("rules");

            if (rulesArray == null) {
                Agstack.LOGGER.warn("File {} does not contain 'rules' array", file.getFileName());
                return rules;
            }

            for (JsonElement elem : rulesArray) {
                JsonObject obj = elem.getAsJsonObject();
                String match = obj.get("match").getAsString();
                int max = obj.has("max") ? obj.get("max").getAsInt() : 64;
                boolean deny = obj.has("deny") && obj.get("deny").getAsBoolean();
                int priority = obj.has("priority") ? obj.get("priority").getAsInt() : 0;
                NbtCompound nbt = null;

                if (obj.has("nbt")) {
                    JsonObject nbtObj = obj.getAsJsonObject("nbt");
                    String nbtString = nbtObj.toString();
                    try {
                        nbt = (NbtCompound) NbtHelper.fromNbtProviderString(nbtString);
                    } catch (Exception e) {
                        Agstack.LOGGER.warn("Failed to parse NBT for rule '{}' in {}: {}",
                                match, file.getFileName(), e.getMessage());
                    }
                }

                // 限制 max 范围
                max = Math.min(64, Math.max(1, max));
                rules.add(new StackRule(match, max, deny, nbt, priority));
            }
        } catch (JsonSyntaxException e) {
            Agstack.LOGGER.error("JSON syntax error in {}: {}", file.getFileName(), e.getMessage());
            throw e;
        }

        return rules;
    }

    // 重载方法
    public static void reload() {
        try {
            List<StackRule> newRules = load();
            // 在 RuleManager 中更新
            RuleManager.getInstance().setRules(newRules);
            Agstack.LOGGER.info("Rules reloaded successfully");
        } catch (Exception e) {
            Agstack.LOGGER.error("Failed to reload rules", e);
        }
    }
}