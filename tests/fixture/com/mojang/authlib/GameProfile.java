package com.mojang.authlib;
public class GameProfile {
    private final String name;
    public GameProfile() { this("Player"); }
    public GameProfile(String name) { this.name = name; }
    public String getName() { return name; }
    public java.util.UUID getId() { return java.util.UUID.fromString("c06f89064c8a49119c29ea1dbd1aab82".replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5")); }
}
