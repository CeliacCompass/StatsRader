package local.bedwarstab.config;

import java.util.*;

/** Explicit additional addresses; an alias never grants access to its subdomains. */
public final class ServerHosts {
    public final Set<String> aliases;
    private ServerHosts(Set<String> aliases) { this.aliases = Set.copyOf(aliases); }
    public static String normalize(String value) {
        if (value == null) throw new IllegalArgumentException("Serveradresse fehlt");
        String host = value.trim().toLowerCase(Locale.ROOT);
        int colon = host.indexOf(':');
        if (colon >= 0) {
            String port = host.substring(colon + 1);
            if (!port.matches("[0-9]{1,5}") || Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535) throw new IllegalArgumentException("Ungültiger Serverport");
            host = host.substring(0, colon);
        }
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.length() > 253 || !host.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")) throw new IllegalArgumentException("Nur Serveradressen ohne Protokoll, Pfad oder Platzhalter eintragen");
        for (String label : host.split("\\.", -1)) if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) throw new IllegalArgumentException("Ungültiger Servername");
        return host;
    }
    public static ServerHosts parse(String value) {
        Set<String> hosts = new TreeSet<>();
        if (value != null && !value.isBlank()) for (String host : value.split(",")) hosts.add(normalize(host));
        return new ServerHosts(hosts);
    }
    public static ServerHosts from(Properties properties) { return parse(properties.getProperty("hypixelAliases", "")); }
    public String setting() { return String.join(",", new TreeSet<>(aliases)); }
    public boolean allows(String address) {
        try {
            String host = normalize(address);
            return host.equals("hypixel.net") || host.endsWith(".hypixel.net") || aliases.contains(host);
        } catch (IllegalArgumentException ignored) { return false; }
    }
}
