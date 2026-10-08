package com.mojang.authlib;
public class GameProfile {
    private final String name;
    private final java.util.UUID id;
    public GameProfile() { this("Player"); }
    public GameProfile(String name) { this(name, java.util.UUID.fromString("c06f8906-4c8a-4911-9c29-ea1dbd1aab82")); }
    public GameProfile(String name, java.util.UUID id) { this.name = name; this.id = id; }
    public String getName() { return name; }
    public java.util.UUID getId() { return id; }
}
