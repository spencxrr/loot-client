package dev.loot.client.module;

public enum Category {
    COMBAT("Combat"),
    MISC("Misc"),
    BASEFINDING("BaseFinding"),
    RENDER("Render"),
    CLIENT("Client");

    public final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }
}
