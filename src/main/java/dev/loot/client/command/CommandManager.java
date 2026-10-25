package dev.loot.client.command;

import dev.loot.client.LootClient;
import dev.loot.client.config.Config;
import dev.loot.client.module.Module;
import dev.loot.client.module.world.BaseFinder;
import dev.loot.client.setting.Setting;
import dev.loot.client.util.ChatUtil;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Chat commands starting with the prefix. They never reach the server. */
public final class CommandManager {
    public static final String PREFIX = ".";

    private CommandManager() {}

    /** Returns true if the message was a client command and should not be sent. */
    public static boolean handle(String message) {
        if (!message.startsWith(PREFIX) || message.length() == PREFIX.length()) return false;
        String[] args = message.substring(PREFIX.length()).trim().split("\\s+");
        try {
            run(args);
        } catch (Exception e) {
            ChatUtil.error("Command failed: " + e.getMessage());
        }
        return true;
    }

    private static void run(String[] args) {
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help", "h" -> {
                ChatUtil.info(".modules, .t <module>, .bind <module> <key|none>, .set <module> <setting> <value>,");
                ChatUtil.info(".settings <module>, .bases [clear], .save. Open the GUI with Right Shift.");
            }
            case "modules", "mods" -> ChatUtil.info(LootClient.modules().getModules().stream()
                .map(m -> (m.isEnabled() ? "§a" : "§7") + m.getName())
                .collect(Collectors.joining("§8, ")));
            case "t", "toggle" -> {
                Module m = module(args, 1);
                m.toggle();
                ChatUtil.info(m.getName() + (m.isEnabled() ? " §aon" : " §coff"));
                Config.save();
            }
            case "bind" -> {
                Module m = module(args, 1);
                if (args.length < 3) throw new IllegalArgumentException("usage: .bind <module> <key|none>");
                String keyName = args[2].toLowerCase(Locale.ROOT);
                if (keyName.equals("none") || keyName.equals("clear")) {
                    m.setKey(GLFW.GLFW_KEY_UNKNOWN);
                    ChatUtil.info("Unbound " + m.getName());
                } else {
                    InputUtil.Key key = InputUtil.fromTranslationKey("key.keyboard." + keyName);
                    if (key == InputUtil.UNKNOWN_KEY) throw new IllegalArgumentException("unknown key " + args[2]);
                    m.setKey(key.getCode());
                    ChatUtil.info("Bound " + m.getName() + " to " + keyName);
                }
                Config.save();
            }
            case "settings", "s" -> {
                Module m = module(args, 1);
                ChatUtil.info("§f" + m.getName() + "§7: " + m.getDescription());
                for (Setting<?> s : m.getSettings())
                    ChatUtil.info("  §f" + s.getName() + " §7= §e" + s.display() + " §8(" + s.getDescription() + ")");
            }
            case "set" -> {
                Module m = module(args, 1);
                if (args.length < 4) throw new IllegalArgumentException("usage: .set <module> <setting> <value>");
                Setting<?> s = m.getSetting(args[2]);
                if (s == null) throw new IllegalArgumentException("no setting " + args[2] + " on " + m.getName());
                String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                if (!s.parse(value)) throw new IllegalArgumentException("bad value '" + value + "'");
                ChatUtil.info(m.getName() + " " + s.getName() + " = " + s.display());
                Config.save();
            }
            case "bases" -> {
                BaseFinder bf = LootClient.modules().get(BaseFinder.class);
                if (args.length > 1 && args[1].equalsIgnoreCase("clear")) {
                    bf.clearHits();
                    ChatUtil.info("Cleared base hits.");
                } else if (bf.describeHits().isEmpty()) {
                    ChatUtil.info("No bases flagged this session.");
                } else {
                    bf.describeHits().forEach(ChatUtil::info);
                }
            }
            case "save" -> {
                Config.save();
                ChatUtil.info("Config saved.");
            }
            default -> ChatUtil.error("Unknown command. Try .help");
        }
    }

    private static Module module(String[] args, int index) {
        if (args.length <= index) throw new IllegalArgumentException("missing module name");
        Module m = LootClient.modules().get(args[index]);
        if (m == null) throw new IllegalArgumentException("no module named " + args[index]);
        return m;
    }
}
