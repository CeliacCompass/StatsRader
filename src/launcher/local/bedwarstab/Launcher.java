package local.bedwarstab;

import com.sun.tools.attach.VirtualMachine;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import local.bedwarstab.config.DataPaths;
import local.bedwarstab.config.DisplaySettings;
import local.bedwarstab.config.ServerHosts;
import local.bedwarstab.config.RuntimeControl;

public final class Launcher {
    private final Path root;
    private final Path dataRoot = DataPaths.directory();
    private final JFrame frame = new JFrame("StatsRader · Bedwars Companion");
    private final JPasswordField hypixel = new JPasswordField(), urchin = new JPasswordField();
    private final JTextField aliases = new JTextField();
    private final JComboBox<GameProcess> processes = new JComboBox<>();
    private final JComboBox<String> modes = new JComboBox<>(new String[]{"Gesamt", "Solo", "Doubles", "3v3v3v3", "4v4v4v4", "4v4"});
    private static final String[] MODE_IDS = {"overall", "eight_one", "eight_two", "four_three", "four_four", "two_four"};
    private final JTextArea status = new JTextArea("Bereit für deine nächste Runde. Speichere deine Keys, starte Badlion über diese App und klicke im laufenden Spiel auf Inject.");
    private final JLabel connection = new JLabel("Spiel wird gesucht …");
    private final JButton inject = new Theme.ActionButton("Inject", true), detach = new Theme.ActionButton("Deaktivieren", false);

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        if (args.length == 2 && args[0].equals("--preview")) {
            Theme.install();
            SwingUtilities.invokeAndWait(() -> {
                Launcher app = new Launcher(Path.of("."));
                try {
                    JPanel view = app.createContent();
                    for (int width : new int[]{1040, 940}) {
                        app.frame.setContentPane(view); app.frame.setSize(width, 820); app.frame.addNotify(); app.frame.validate();
                        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(view.getWidth(), view.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = image.createGraphics(); view.printAll(g); g.dispose();
                        Files.createDirectories(Path.of(args[1]));
                        javax.imageio.ImageIO.write(image, "png", Path.of(args[1], "statsrader-" + width + ".png").toFile());
                    }
                } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
                finally { app.frame.dispose(); }
            });
            return;
        }
        if (args.length > 0 && args[0].startsWith("--")) {
            System.exit(runCommand(args));
            return;
        }
        Path directory = args.length > 0 ? Path.of(args[0]) : ownDistribution();
        Theme.install();
        SwingUtilities.invokeLater(() -> new Launcher(directory.toAbsolutePath()).show());
    }
    private static Path ownDistribution() throws Exception {
        return Path.of(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().getParent();
    }
    static int runCommand(String[] args) {
            VirtualMachine vm = null;
            try {
                if (args == null || args.length == 0 || args[0] == null || !args[0].startsWith("--")) throw new IllegalArgumentException("Befehl fehlt");
                String action = args[0].substring(2);
                if (action.equals("self-check")) {
                    if (args.length != 1) throw new IllegalArgumentException("Verwendung: --self-check");
                    BadlionStartup.startupOptions(ownDistribution(), null);
                    if (com.sun.tools.attach.spi.AttachProvider.providers().isEmpty()) throw new IOException("Attach-Modul fehlt");
                    if (!Files.isRegularFile(Path.of(System.getProperty("java.home"), "bin/java.exe"))) throw new IOException("Java-Helfer fehlt");
                    java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
                    Theme.logo();
                    System.out.println("PASS: portable distribution, bundled Java, fonts and Attach provider available."); return 0;
                }
                if (action.equals("allow-server")) {
                    if (args.length != 2) throw new IllegalArgumentException("Verwendung: --allow-server HOST");
                    String host = ServerHosts.normalize(args[1]);
                    DisplaySettings.update(DataPaths.directory(), p -> {
                        Set<String> configured = new TreeSet<>(ServerHosts.from(p).aliases); configured.add(host);
                        p.setProperty("hypixelAliases", String.join(",", configured));
                    });
                    System.out.println("Zusätzliche Serveradresse gespeichert: " + host); return 0;
                }
                if (!Set.of("probe", "attach", "detach").contains(action) ||
                    args.length != (action.equals("probe") ? 2 : 3) || args[1] == null || !args[1].matches("[1-9][0-9]*"))
                    throw new IllegalArgumentException("Verwendung: --probe PID oder --attach/--detach PID PROJEKTORDNER");
                Path distribution = action.equals("probe") ? null : Path.of(args[2]).toAbsolutePath();
                if (distribution != null) for (String file : new String[]{"agent.jar", "core.jar", "bridge.jar", "screens.jar"}) {
                    if (!Files.isRegularFile(distribution.resolve("dist/" + file))) throw new IOException("Unvollständiges Programmpaket: " + file);
                }
                if (distribution != null) {
                    String controlled = RuntimeControl.request(DataPaths.directory(), distribution, Long.parseLong(args[1]), action);
                    if (controlled != null) { System.out.println(controlled); return 0; }
                }
                if (ProcessHandle.of(Long.parseLong(args[1])).flatMap(p -> p.info().arguments())
                    .map(a -> Arrays.asList(a).contains("-XX:+DisableAttachMechanism")).orElse(false)) {
                    System.err.println("Dieses Spiel wurde ohne die neue App-Verbindung gestartet und sperrt Java-Attach. " +
                        "Einmal Minecraft und Badlion vollständig schließen und in StatsRader über ‚Badlion starten‘ starten. " +
                        "Danach funktionieren Inject, Deaktivieren und Key-Wechsel während des Spiels.");
                    return 1;
                }
                vm = VirtualMachine.attach(args[1]);
                // On Windows attach() alone can succeed even when the command listener is disabled.
                // A read-only command checks that the listener actually responds; never print properties.
                vm.getSystemProperties();
                if (action.equals("probe")) { System.out.println("Java-Attach-Dienst antwortet."); return 0; }
                vm.loadAgent(distribution.resolve("dist/agent.jar").toString(), action + "|" + distribution);
                System.out.println(action.equals("detach") ? "Erweiterung deaktiviert." : "Agent geladen. Tab im Spiel öffnen.");
                return 0;
            } catch (Exception | InternalError e) {
                System.err.println(AttachDiagnostics.explain(e));
                return 1;
            } finally { if (vm != null) try { vm.detach(); } catch (IOException ignored) { } }
    }
    private Launcher(Path root) { this.root = root; }
    private void show() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setIconImage(Theme.logo());
        JScrollPane page = new JScrollPane(createContent()); page.setBorder(null); page.getViewport().setBackground(Theme.BG);
        frame.setContentPane(page);
        Rectangle desktop = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        frame.setMinimumSize(new Dimension(800, 560)); frame.setSize(Math.min(1040,desktop.width), Math.min(820,desktop.height)); frame.setLocationRelativeTo(null);
        load(); refresh(); frame.setVisible(true);
        new javax.swing.Timer(2000, e -> refresh()).start();
    }
    private JPanel createContent() {
        JPanel panel = new Theme.Canvas(); panel.setBackground(Theme.BG); panel.setBorder(new EmptyBorder(24, 26, 20, 26));
        JPanel header = Theme.plain(new BorderLayout()); JPanel title = Theme.column();
        Theme.add(title, Theme.label("StatsRader", 30, Theme.TEXT, true));
        Theme.add(title, Theme.label("Deine Lobby. Deine Stats. Dein Überblick.", 13, Theme.MUTED, false)); header.add(title);
        JLabel badge = Theme.label("  BADLION  /  1.8.9  ", 11, Theme.CYAN, true);
        badge.setBorder(new javax.swing.border.CompoundBorder(new javax.swing.border.LineBorder(Theme.LINE,1,true), new EmptyBorder(10,10,10,10)));
        JPanel badges = Theme.plain(new FlowLayout(FlowLayout.RIGHT,0,10)); badges.add(badge); header.add(badges, BorderLayout.EAST); panel.add(header, BorderLayout.NORTH);

        JPanel sidebar = Theme.card(); sidebar.setPreferredSize(new Dimension(260, 0)); sidebar.setBorder(new EmptyBorder(8,12,18,12));
        Theme.add(sidebar, new Theme.LogoPanel()); Theme.gap(sidebar,16);
        Theme.add(sidebar, Theme.label("DEIN MATCH IM BLICK", 11, Theme.CYAN, true)); Theme.gap(sidebar,10);
        Theme.add(sidebar, Theme.label("Mehr wissen. Besser spielen.", 15, Theme.TEXT, true)); Theme.gap(sidebar,22);
        addStep(sidebar,"01", "Keys hinterlegen", "Hypixel-Stats & Urchin-Tags");
        addStep(sidebar,"02", "Badlion vorbereiten", "Hier starten, dann Minecraft öffnen");
        addStep(sidebar,"03", "Inject & losspielen", "Tab öffnen und Stats sehen");
        sidebar.add(Box.createVerticalGlue());
        Theme.add(sidebar, Theme.label("IM SPIEL", 10, Theme.MUTED, true)); Theme.gap(sidebar,6);
        Theme.add(sidebar, Theme.label("/config", 22, Theme.CYAN, true)); Theme.gap(sidebar,4);
        Theme.add(sidebar, Theme.label("Kategorien & Transparenz einstellen", 11, Theme.MUTED, false));
        panel.add(sidebar, BorderLayout.WEST);

        JPanel right = Theme.column();
        JPanel session = Theme.card();
        Theme.add(session, Theme.label("Verbindung zum Spiel", 18, Theme.TEXT, true)); Theme.gap(session,8);
        connection.setFont(Theme.BODY.deriveFont(12f)); connection.setForeground(Theme.MUTED); Theme.add(session,connection); Theme.gap(session,14);
        Theme.combo(processes); Theme.combo(modes); Theme.input(hypixel); Theme.input(urchin); Theme.input(aliases);
        JPanel processRow = Theme.plain(new BorderLayout(8,0)); processRow.add(processes);
        JButton refresh = new Theme.ActionButton("Aktualisieren",false); refresh.setToolTipText("Spiele aktualisieren"); refresh.getAccessibleContext().setAccessibleName("Spiele aktualisieren");
        refresh.addActionListener(e -> refresh()); processRow.add(refresh,BorderLayout.EAST);
        processRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,40)); Theme.add(session,processRow); Theme.gap(session,12);
        JPanel actions = Theme.plain(new FlowLayout(FlowLayout.LEFT,0,0));
        JButton start = new Theme.ActionButton("Badlion starten",false); start.setToolTipText("Badlion mit StatsRader-Verbindung starten"); start.addActionListener(e -> startBadlion(start));
        actions.add(start); actions.add(Box.createHorizontalStrut(8)); actions.add(inject); actions.add(Box.createHorizontalStrut(8)); actions.add(detach);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE,42)); Theme.add(session,actions);
        Theme.add(right, session); Theme.gap(right,14);

        JPanel settings = Theme.card(); Theme.add(settings, Theme.label("Keys & Einstellungen",18,Theme.TEXT,true)); Theme.gap(settings,16);
        JPanel keys = Theme.plain(new GridLayout(1,2,16,0)); keys.add(Theme.field("Hypixel API-Key",hypixel)); keys.add(Theme.field("Urchin API-Key",urchin));
        keys.setMaximumSize(new Dimension(Integer.MAX_VALUE,69)); Theme.add(settings,keys); Theme.gap(settings,14);
        JPanel options = Theme.plain(new GridLayout(1,2,16,0)); options.add(Theme.field("Bedwars-Modus",modes)); options.add(Theme.field("Proxy-Adresse · optional",aliases));
        aliases.setToolTipText("Zusätzliche Hypixel-Adressen, durch Komma getrennt"); options.setMaximumSize(new Dimension(Integer.MAX_VALUE,69)); Theme.add(settings,options); Theme.gap(settings,14);
        JPanel saveRow = Theme.plain(new BorderLayout(8,0));
        JButton saveOnly = new Theme.ActionButton("Keys speichern",false); saveRow.add(saveOnly,BorderLayout.EAST);
        JPanel privacy = Theme.column(); Theme.add(privacy,Theme.label("Nur auf deinem PC gespeichert",11,Theme.MUTED,false));
        Theme.gap(privacy,3); Theme.add(privacy,Theme.label("Key-Wechsel auch im laufenden Spiel",11,Theme.MUTED,false)); saveRow.add(privacy);
        saveRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,42)); Theme.add(settings,saveRow); Theme.add(right,settings); Theme.gap(right,14);
        saveOnly.addActionListener(e -> {
            try { save(); status.setText("Gespeichert. Die aktive StatsRader-Version übernimmt Keys und Modus automatisch nach laufenden API-Anfragen. Zusätzliche Serveradressen werden beim nächsten Aktivieren geladen."); }
            catch (IOException error) { status.setText("Einstellungen konnten nicht gespeichert werden: " + error.getMessage()); }
        });
        inject.addActionListener(e -> run("attach")); detach.addActionListener(e -> run("detach"));
        JPanel activity = Theme.card(); Theme.add(activity,Theme.label("STATUS",10,Theme.CYAN,true)); Theme.gap(activity,8);
        status.setEditable(false); status.setLineWrap(true); status.setWrapStyleWord(true); status.setRows(3);
        status.setFont(Theme.BODY.deriveFont(12f)); status.setForeground(Theme.MUTED); status.setBackground(Theme.CARD); status.setBorder(null);
        JScrollPane log = new JScrollPane(status); log.setBorder(null); log.getViewport().setBackground(Theme.CARD);
        log.setPreferredSize(new Dimension(100,60)); Theme.add(activity,log); Theme.add(right,activity);
        panel.add(right,BorderLayout.CENTER);
        JPanel footer = Theme.plain(new BorderLayout()); footer.add(Theme.label("HYPIXEL STATS  +  URCHIN TAGS",10,Theme.MUTED,true));
        footer.add(Theme.label("StatsRader  /  Beta",10,Theme.MUTED,false),BorderLayout.EAST); panel.add(footer,BorderLayout.SOUTH);
        return panel;
    }
    private static void addStep(JPanel panel, String number, String title, String description) {
        Theme.add(panel,Theme.label(number + "  " + title,13,Theme.TEXT,true)); Theme.gap(panel,5);
        Theme.add(panel,Theme.label(description,11,Theme.MUTED,false)); Theme.gap(panel,20);
    }
    private void refresh() {
        GameProcess selected = (GameProcess) processes.getSelectedItem();
        java.util.List<GameProcess> found = ProcessHandle.allProcesses().filter(p -> p.info().command().map(c -> {
            String s = c.toLowerCase(Locale.ROOT); return s.contains("badlion") && (s.endsWith("javaw.exe") || s.endsWith("java.exe"));
        }).orElse(false)).map(p -> new GameProcess(p.pid())).sorted(Comparator.comparingLong(GameProcess::pid)).toList();
        boolean changed = found.size() != processes.getItemCount();
        for (int i = 0; !changed && i < found.size(); i++) changed = !found.get(i).equals(processes.getItemAt(i));
        if (changed) {
            processes.removeAllItems(); found.forEach(processes::addItem);
            if (selected != null) for (int i = 0; i < processes.getItemCount(); i++)
                if (processes.getItemAt(i).pid == selected.pid) processes.setSelectedIndex(i);
        }
        GameProcess current = (GameProcess) processes.getSelectedItem();
        boolean ready = current != null && RuntimeControl.available(dataRoot, current.pid);
        connection.setForeground(ready ? Theme.CYAN : Theme.MUTED);
        connection.setText(current == null ? "●  Warte auf Minecraft 1.8.9" : ready ? "●  Verbunden · bereit für Inject" : "●  Spiel gefunden · Verbindung noch nicht vorbereitet");
    }
    private void startBadlion(JButton button) {
        Path detected;
        try {
            detected = BadlionStartup.find(dataRoot);
            if (detected == null) {
                JFileChooser chooser = new JFileChooser(); chooser.setDialogTitle("Badlion Client.exe auswählen");
                chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Badlion Client.exe", "exe"));
                if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
                detected = chooser.getSelectedFile().toPath();
                if (!BadlionStartup.validExecutable(detected)) throw new IOException("Bitte die installierte Datei Badlion Client.exe auswählen.");
            }
            String selectedPath = detected.toAbsolutePath().toString();
            DisplaySettings.update(dataRoot, p -> p.setProperty("badlionLauncher", selectedPath));
        } catch (IOException e) { status.setText(e.getMessage()); return; }
        Path executable = detected;
        button.setEnabled(false);
        status.setText("Starte Badlion mit App-Verbindung. Ein laufendes Spiel wird nicht geschlossen.");
        new SwingWorker<String, Void>() {
            protected String doInBackground() throws Exception {
                BadlionStartup.start(executable, root);
                return "Badlion-Start aufgerufen. Jetzt Minecraft 1.8.9 im Launcher starten und danach hier Inject anklicken.";
            }
            protected void done() {
                try { status.setText(get()); } catch (Exception e) { status.setText("Badlion-Start fehlgeschlagen: " + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage())); }
                button.setEnabled(true); refresh();
            }
        }.execute();
    }
    private void load() {
        try {
            Path file = dataRoot.resolve("settings.properties"); if (!Files.isRegularFile(file)) return;
            Properties p = DisplaySettings.read(dataRoot);
            hypixel.setText(p.getProperty("hypixelKey", "")); urchin.setText(p.getProperty("urchinKey", ""));
            aliases.setText(p.getProperty("hypixelAliases", ""));
            for (int i = 0; i < MODE_IDS.length; i++) if (MODE_IDS[i].equals(p.getProperty("mode"))) modes.setSelectedIndex(i);
        } catch (IOException e) { status.setText("Lokale Einstellungen konnten nicht gelesen werden."); }
    }
    private void save() throws IOException {
        String hypixelValue = new String(hypixel.getPassword()).trim(), urchinValue = new String(urchin.getPassword()).trim();
        String mode = MODE_IDS[modes.getSelectedIndex()];
        String additionalHosts;
        try { additionalHosts = ServerHosts.parse(aliases.getText()).setting(); }
        catch (IllegalArgumentException e) { throw new IOException(e.getMessage(), e); }
        DisplaySettings.update(dataRoot, p -> {
            p.setProperty("hypixelKey", hypixelValue); p.setProperty("urchinKey", urchinValue); p.remove("seraphKey"); p.setProperty("mode", mode);
            p.setProperty("hypixelAliases", additionalHosts);
        });
    }
    private void run(String action) {
        GameProcess game = (GameProcess) processes.getSelectedItem();
        if (game == null) { status.setText("Zuerst Badlion mit Minecraft 1.8.9 starten."); return; }
        try { if (action.equals("attach")) save(); }
        catch (IOException e) { status.setText("Einstellungen konnten nicht gespeichert werden."); return; }
        inject.setEnabled(false); detach.setEnabled(false); status.setText("Verbinde mit Badlion, PID " + game.pid + " …");
        new SwingWorker<String, Void>() {
            protected String doInBackground() throws Exception {
                Path java = Path.of(System.getProperty("java.home"), "bin", "java.exe");
                ProcessBuilder builder = new ProcessBuilder(java.toString(), "-Dfile.encoding=UTF-8", "-Dbedwarstab.dataDir=" + dataRoot, "-cp", root.resolve("dist/launcher.jar").toString(),
                    Launcher.class.getName(), "--" + action, Long.toString(game.pid), root.toString());
                builder.redirectErrorStream(true);
                Files.createDirectories(dataRoot);
                Path output = Files.createTempFile(dataRoot, "attach-", ".log");
                Process child = null;
                try {
                    builder.redirectOutput(output.toFile()); child = builder.start();
                    if (!child.waitFor(30, TimeUnit.SECONDS)) {
                        child.destroyForcibly(); child.waitFor(3, TimeUnit.SECONDS);
                        return "Verbindung nach 30 Sekunden abgebrochen. Status unklar. Protokoll: " + output;
                    }
                    String result = Files.readString(output, StandardCharsets.UTF_8);
                    try { Files.copy(output, dataRoot.resolve("launcher.log"), StandardCopyOption.REPLACE_EXISTING); } catch (IOException ignored) { }
                    if (child.exitValue() != 0) result += "\nProtokoll: " + output + ". agent.log entsteht erst nach dem Start des Agenten.";
                    return result;
                } finally { if (child != null && child.isAlive()) child.destroyForcibly(); }
            }
            protected void done() {
                try { status.setText(get()); } catch (Exception e) { status.setText(AttachDiagnostics.explain(e.getCause() == null ? e : e.getCause())); }
                inject.setEnabled(true); detach.setEnabled(true);
            }
        }.execute();
    }
    private record GameProcess(long pid) { public String toString() { return "Badlion Minecraft · PID " + pid; } }
}
