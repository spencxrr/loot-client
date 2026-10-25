package dev.loot.client.module;

public enum Category {
    RENDER("Render"),
    WORLD("World"),
    PLAYER("Player"),
    MOVEMENT("Movement"),
    CLIENT("Client");

    public final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }
}
