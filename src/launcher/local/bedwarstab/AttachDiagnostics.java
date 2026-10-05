package local.bedwarstab;

/** Translate native Attach errors without confusing them with agent failures. */
public final class AttachDiagnostics {
    private AttachDiagnostics() { }
    public static String explain(Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
            if (cause instanceof InternalError && String.valueOf(cause.getMessage()).matches(
                "(?s).*Remote thread failed for unknown reason \\(100\\).*")) {
                return "Java-Attach-Dienst nicht verfügbar (Fehler 100).\n"
                    + "Die Ziel-JVM hat Attach deaktiviert oder den Attach-Dienst nicht initialisiert. "
                    + "Der Agent konnte über diesen Weg nicht geladen werden.\n"
                    + "Mit dem Startparameter -XX:+DisableAttachMechanism ist dieser Ladeweg deaktiviert. "
                    + "Erneutes Injizieren oder andere API-Keys beheben diese Startkonfiguration nicht.\n"
                    + "Keys mit Keys speichern sichern, Minecraft und Badlion vollständig beenden, "
                    + "dann Start-Badlion-Mit-BedwarsTab-App.cmd öffnen und Minecraft 1.8.9 starten. "
                    + "Der Startmodus lädt die App-Verbindung; danach können Inject und Deaktivieren ohne Java-Attach verwendet werden.";
            }
        }
        return "Verbindung fehlgeschlagen: " + failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
    }
}
