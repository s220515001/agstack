package tfh.agstack.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import tfh.agstack.Agstack;
import tfh.agstack.rule.RuleManager;
import tfh.agstack.rule.StackRule;

import static net.minecraft.server.command.CommandManager.*;

public class RulesReloadCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("agstack")
                .requires(src -> src.hasPermissionLevel(2))
                .then(literal("rules")
                        .then(literal("reload")
                                .executes(RulesReloadCommand::reload))
                        .then(literal("list")
                                .executes(RulesReloadCommand::list))
                )
        );
    }

    private static int reload(CommandContext<ServerCommandSource> ctx) {
        var source = ctx.getSource();
        try {
            RuleManager.getInstance().reload();
            source.sendFeedback(() -> Text.literal("§a已重新加载堆叠规则"), true);
            return 1;
        } catch (Exception e) {
            Agstack.LOGGER.error("Error reloading rules", e);
            source.sendError(Text.literal("§c重载失败，请查看日志"));
            return 0;
        }
    }

    private static int list(CommandContext<ServerCommandSource> ctx) {
        var source = ctx.getSource();
        var rules = RuleManager.getInstance().getRules();
        if (rules.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§e当前没有加载任何规则"), false);
            return 1;
        }
        source.sendFeedback(() -> Text.literal("§6已加载 " + rules.size() + " 条规则："), false);
        for (StackRule rule : rules) {
            String detail = String.format("match=%s max=%d deny=%b priority=%d nbt=%s",
                    rule.match(), rule.max(), rule.deny(), rule.priority(),
                    rule.nbt() != null ? "yes" : "no");
            source.sendFeedback(() -> Text.literal("  §7" + detail), false);
        }
        return 1;
    }
}