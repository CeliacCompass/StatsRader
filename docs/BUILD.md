# Aus dem Quellcode bauen

## Voraussetzungen

- Windows x64, PowerShell, Git und ein JDK 17.
- Lokal installiertes Badlion mit Minecraft 1.8.9 und OptiFine 1.8.9 HD U M5.
- Die von `tools/build.ps1` referenzierten Minecraft-, OptiFine-, Guava-17.0- und Authlib-1.5.21-JARs in den üblichen AppData-Verzeichnissen. Diese Client-Dateien werden nicht im Repository bereitgestellt.

```powershell
.\tools\bootstrap.ps1
.\tools\build.ps1 -Candidate
.\Start-StatsRader.cmd
```

`bootstrap.ps1` lädt ASM 9.8, Gson 2.13.1 und ein Temurin-JDK 17 von den offiziellen Bezugsquellen. Für einen eigenen JDK-Pfad kann `.deps/jdk-path.txt` angepasst werden. Der Build legt eine neue Version unter `releases/` an, führt die Tests aus und aktualisiert anschließend `releases/app.txt`. Bestehende Versionen werden nicht überschrieben.

## Tests

Der Build prüft unter anderem die Statistik- und Farbschwellen, Tab-Ausrichtung, Transparenz, Settings-Persistenz, Worker-Lebenszyklus, Auto-`/who` und das Aktivieren/Deaktivieren des Agenten in separaten Test-JVMs. Die Tests verwenden keine echten API-Keys und greifen nicht auf ein laufendes Badlion zu.

Die Klassen in `tests/fixture/` sind kleine lokale Test-Doubles für die verwendeten Spielschnittstellen. Ein erfolgreicher Test ersetzt keinen Integrationstest mit einer neuen Badlion-Version.

## Einzelne EXE bauen

```powershell
.\tools\package.ps1
```

Ergebnis: `publish/StatsRader-Portable.exe` und eine SHA-256-Datei. Das Paket enthält eine verkleinerte Java-Laufzeit. Zum Verpacken werden der .NET-Framework-C#-Compiler und `jlink` benötigt.

Der Paketbau ist auf **Temurin 17.0.20.1+1**, JDK-Revision `79597447bd94`, festgelegt. `bootstrap.ps1` lädt dagegen die neueste verfügbare JDK-17-Version; falls diese abweicht, muss für den Paketbau gezielt die festgelegte JDK-Version verwendet werden. Eine Änderung der ausgelieferten Laufzeit erfordert passende Quellen, Prüfsummen und Lizenzhinweise.

Vor dem Paketbau diese unveränderten Quellarchive nach `.deps/redistribution-sources/` laden:

| Datei | Quelle | SHA-256 |
| --- | --- | --- |
| `temurin-jdk17u-79597447bd94.tar.gz` | [OpenJDK-Quellen](https://codeload.github.com/adoptium/jdk17u/tar.gz/79597447bd94) | `c7e2045844b32008ca77079586d209f59e15752ed355c713a1ec311c58ed206c` |
| `temurin-build-e6ba7dec3d07.tar.gz` | [Temurin-Buildskripte](https://codeload.github.com/adoptium/temurin-build/tar.gz/e6ba7dec3d07654074559310376a3ae89da5f4ac) | `b1340378b9ed62b32acfacd012dd069ec2b9d940fc39d3c73f32142d64e89713` |

Die verbindlichen Prüfsummen stehen auch in `tools/package.ps1`. Der Paketbau prüft die EXE aus einem anderen Ordner mit Leerzeichen und Umlauten, die mitgelieferte Laufzeit ohne System-Java sowie die Wiederverwendung des lokalen Caches.

## Struktur

| Ordner | Aufgabe |
| --- | --- |
| `src/launcher` | StatsRader-Desktop-App und Badlion-Start |
| `src/boot`, `src/bridge` | Agent-Start und Verbindung zum Spiel |
| `src/core` | API-Abfragen, Cache, Spielintegration und Farbstufen |
| `src/screens` | Tab-Tabelle und Ingame-Konfiguration |
| `src/shared` | Einstellungen und lokale Agent-Steuerung |
| `packaging` | Windows-Starter und Drittanbieterhinweise |
| `assets`, `previews` | Logo, Icon und Vorschau |

Bestehende Paket- und Klassennamen `bedwarstab` sowie das lokale Datenverzeichnis `BedwarsTab` bleiben aus Kompatibilitätsgründen erhalten.
