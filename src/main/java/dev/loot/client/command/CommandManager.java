package dev.loot.client.command;

import dev.loot.client.LootClient;
import dev.loot.client.config.Config;
import dev.loot.client.module.Module;
import dev.loot.client.module.basefinding.BaseFinder;
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
                ChatUtil.info(".settings <module>, .friend add|remove <name>, .macro, .config, .bases [clear], .save. Open the GUI with Right Shift.");
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
            case "friend", "f" -> {
                if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
                    ChatUtil.info("Friends: " + (LootClient.friends().all().isEmpty() ? "none" : String.join(", ", LootClient.friends().all())));
                } else if (args.length >= 3 && args[1].equalsIgnoreCase("add")) {
                    ChatUtil.info(LootClient.friends().add(args[2]) ? "Added " + args[2] : args[2] + " is already a friend");
                } else if (args.length >= 3 && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("del"))) {
                    ChatUtil.info(LootClient.friends().remove(args[2]) ? "Removed " + args[2] : args[2] + " isn't a friend");
                } else {
                    throw new IllegalArgumentException("usage: .friend [list|add <name>|remove <name>]");
                }
            }
            case "macro", "macros" -> {
                var cm = LootClient.modules().get(dev.loot.client.module.client.ChatMacro.class);
                if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
                    if (cm.macros().isEmpty()) ChatUtil.info("No macros. .macro add <key> <message>");
                    cm.macros().forEach((k, v) -> ChatUtil.info("  " + k + " -> " + v));
                } else if (args.length >= 4 && args[1].equalsIgnoreCase("add")) {
                    String msg = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                    if (!cm.put(args[2], msg)) throw new IllegalArgumentException("unknown key " + args[2]);
                    ChatUtil.info("Macro " + args[2] + " -> " + msg + (cm.isEnabled() ? "" : " (turn on Chat Macro to use it)"));
                } else if (args.length >= 3 && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("del"))) {
                    ChatUtil.info(cm.remove(args[2]) ? "Removed macro " + args[2] : "No macro on " + args[2]);
                } else {
                    throw new IllegalArgumentException("usage: .macro [list|add <key> <message>|remove <key>]");
                }
            }
            case "config", "configs", "cfg" -> {
                if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
                    var list = Config.profiles();
                    ChatUtil.info(list.isEmpty() ? "No saved configs. .config save <name>" : "Configs: " + String.join(", ", list));
                } else if (args.length >= 3) {
                    String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                    switch (args[1].toLowerCase(Locale.ROOT)) {
                        case "save" -> {
                            Config.saveProfile(name);
                            ChatUtil.info("Saved config " + name);
                        }
                        case "load" -> ChatUtil.info(Config.loadProfile(name) ? "Loaded config " + name : "No config named " + name);
                        case "delete", "del", "remove" -> ChatUtil.info(Config.deleteProfile(name) ? "Deleted config " + name : "No config named " + name);
                        default -> throw new IllegalArgumentException("usage: .config [list|save|load|delete <name>]");
                    }
                } else {
                    throw new IllegalArgumentException("usage: .config [list|save|load|delete <name>]");
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
