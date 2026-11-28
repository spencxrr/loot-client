package dev.loot.client.module.misc;

import dev.loot.client.module.Category;
import dev.loot.client.module.Module;
import dev.loot.client.setting.StringSetting;
import dev.loot.client.util.StatSpoof;
import dev.loot.client.util.StatSpoof.Rule;
import dev.loot.client.util.StatSpoof.Seg;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shows different numbers for your stats (money, shards, kills...) on your own screen: the
 * scoreboard sidebar, tab list, chat, titles, anything drawn as text. Purely visual and local.
 * Nothing is sent to the server and nobody else sees it.
 */
public class FakeStats extends Module {
    private final StringSetting money = add(new StringSetting("Money", "Shown for money / balance / bal / coins / $ amounts. Empty = leave as is.", "1.5B"));
    private final StringSetting shards = add(new StringSetting("Shards", "Shown for shards / gems. Empty = leave as is.", "25K"));
    private final StringSetting kills = add(new StringSetting("Kills", "Empty = leave as is.", ""));
    private final StringSetting deaths = add(new StringSetting("Deaths", "Empty = leave as is.", ""));
    private final StringSetting playtime = add(new StringSetting("Playtime", "Like 40d 2h. Empty = leave as is.", ""));
    private final StringSetting custom = add(new StringSetting("Custom", "Anything else, as Label=Value pairs: Keys=50, Level=100, Tokens=9K", ""));

    private List<Rule> rules = List.of();
    private final String[] seen = new String[6];

    public FakeStats() {
        super("Fake Stats", "Changes the money, shards and other stats you see on your own screen. Client side only.", Category.MISC);
    }

    /** Rebuilt only when a setting's text actually changes (this runs for every string drawn). */
    private List<Rule> rules() {
        String[] now = {money.get(), shards.get(), kills.get(), deaths.get(), playtime.get(), custom.get()};
        boolean same = true;
        for (int i = 0; i < now.length; i++) if (now[i] != seen[i]) same = false;
        if (same) return rules;
        System.arraycopy(now, 0, seen, 0, now.length);
        List<Rule> r = new ArrayList<>();
        r.add(new Rule(List.of("money", "balance", "bal", "coins", "purse", "$"), money.get(), false));
        r.add(new Rule(List.of("shards", "shard", "gems"), shards.get(), false));
        r.add(new Rule(List.of("kills"), kills.get(), false));
        r.add(new Rule(List.of("deaths"), deaths.get(), false));
        r.add(new Rule(List.of("playtime", "play time", "time played"), playtime.get(), true));
        r.addAll(StatSpoof.parseCustom(custom.get()));
        r.removeIf(x -> x.value().isEmpty());
        rules = r;
        return r;
    }

    private boolean active() {
        return isEnabled() && !rules().isEmpty();
    }

    public String apply(String text) {
        if (text == null || !active()) return text;
        return StatSpoof.apply(text, rules);
    }

    public Text apply(Text text) {
        if (text == null || !active()) return text;
        List<Seg<Style>> segs = new ArrayList<>();
        text.<Object>visit((style, s) -> {
            if (!s.isEmpty()) segs.add(new Seg<>(s, style));
            return Optional.empty();
        }, Style.EMPTY);
        List<Seg<Style>> out = StatSpoof.apply(segs, rules);
        if (out == null) return text;
        MutableText t = Text.empty();
        for (Seg<Style> s : out) t.append(Text.literal(s.text()).setStyle(s.style()));
        return t;
    }

    public StringVisitable apply(StringVisitable text) {
        return text instanceof Text t ? apply(t) : text;
    }

    public OrderedText apply(OrderedText text) {
        if (text == null || !active()) return text;
        List<Seg<Style>> segs = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        Style[] curStyle = {null};
        text.accept((index, style, cp) -> {
            if (curStyle[0] != null && !style.equals(curStyle[0])) {
                segs.add(new Seg<>(cur.toString(), curStyle[0]));
                cur.setLength(0);
            }
            curStyle[0] = style;
            cur.appendCodePoint(cp);
            return true;
        });
        if (!cur.isEmpty()) segs.add(new Seg<>(cur.toString(), curStyle[0]));
        List<Seg<Style>> out = StatSpoof.apply(segs, rules);
        if (out == null) return text;
        List<OrderedText> parts = new ArrayList<>(out.size());
        for (Seg<Style> s : out) parts.add(OrderedText.styledForwardsVisitedString(s.text(), s.style()));
        return OrderedText.concat(parts);
    }
}
