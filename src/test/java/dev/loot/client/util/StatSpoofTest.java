package dev.loot.client.util;

import dev.loot.client.util.StatSpoof.Rule;
import dev.loot.client.util.StatSpoof.Seg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StatSpoofTest {
    private static final Rule MONEY = new Rule(List.of("money", "balance", "bal", "$"), "1.5B", false);
    private static final Rule SHARDS = new Rule(List.of("shards"), "9,999", false);
    private static final Rule TIME = new Rule(List.of("playtime"), "40d 2h", true);
    private static final List<Rule> ALL = List.of(MONEY, SHARDS, TIME);

    @Test
    void sidebarStyles() {
        assertEquals("Money: $1.5B", StatSpoof.apply("Money: $12.4K", ALL));
        assertEquals(" $ Money 1.5B", StatSpoof.apply(" $ Money 230", ALL));
        assertEquals("Shards » 9,999", StatSpoof.apply("Shards » 540", ALL));
        assertEquals("Balance: $1.5B", StatSpoof.apply("Balance: $5,000.25", ALL));
        assertEquals("Playtime: 40d 2h", StatSpoof.apply("Playtime: 3d 4h 12m", ALL));
    }

    @Test
    void bareDollarAmount() {
        assertEquals("You have $1.5B", StatSpoof.apply("You have $77", ALL));
    }

    @Test
    void leavesOtherTextAlone() {
        String s = "global chat: 5 players online";
        assertSame(s, StatSpoof.apply(s, ALL), "'bal' inside 'global' must not match");
        String t = "Money: none";
        assertSame(t, StatSpoof.apply(t, ALL));
        String k = "Kills: 5";
        assertSame(k, StatSpoof.apply(k, ALL));
    }

    @Test
    void emptyValueMeansOff() {
        String s = "Money: $50";
        assertSame(s, StatSpoof.apply(s, List.of(new Rule(List.of("money"), "", false))));
    }

    @Test
    void keepsColorsAcrossSegments() {
        // label gray, value green, unit white: value stays green, the rest untouched
        List<Seg<String>> in = List.of(new Seg<>("Money ", "gray"), new Seg<>("$12", "green"), new Seg<>(".4K", "white"), new Seg<>(" today", "gray"));
        List<Seg<String>> out = StatSpoof.apply(in, ALL);
        assertNotNull(out);
        assertEquals(List.of(new Seg<>("Money ", "gray"), new Seg<>("$1.5B", "green"), new Seg<>(" today", "gray")), out);
    }

    @Test
    void twoStatsOnOneLine() {
        assertEquals("Money $1.5B | Shards 9,999", StatSpoof.apply("Money $3 | Shards 2", ALL));
    }

    @Test
    void sameValueIsNoChange() {
        String s = "Money: 1.5B";
        assertSame(s, StatSpoof.apply(s, ALL));
    }

    @Test
    void customRules() {
        List<Rule> custom = StatSpoof.parseCustom("Kills=500, Keys = 12;bad,=x");
        assertEquals(2, custom.size());
        assertEquals("Kills: 500 Keys 12", StatSpoof.apply("Kills: 3 Keys 1", custom));
    }

    @Test
    void suffixDoesNotEatWords() {
        // "5 kd" must not read as "5k"
        assertEquals("Money 1.5B kd", StatSpoof.apply("Money 5 kd", ALL));
    }
}
