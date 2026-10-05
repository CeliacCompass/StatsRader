using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

[assembly: AssemblyTitle("StatsRader")]
[assembly: AssemblyProduct("StatsRader")]
[assembly: AssemblyDescription("Bedwars Companion for Badlion 1.8.9")]
[assembly: AssemblyVersion("0.2.0.0")]

// One file for recipients. Application data stays outside this versioned, verified runtime cache.
internal static class Portable
{
    [STAThread]
    private static int Main(string[] args)
    {
        bool check = args.Length == 2 && args[0] == "--verify-package";
        try
        {
            if (!Environment.Is64BitOperatingSystem) throw new InvalidOperationException("Diese Ausgabe benötigt Windows 64-Bit.");
            if (args.Length != 0 && !check) throw new ArgumentException("Unbekannte Startoption.");
            if (check)
            {
                string root = EnsurePackage(Path.GetFullPath(args[1]));
                File.WriteAllText(Path.Combine(Path.GetFullPath(args[1]), "verified-path.txt"), root, new UTF8Encoding(false));
                return 0;
            }
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            using (Form splash = new Form())
            {
                splash.Text = "StatsRader"; splash.ClientSize = new System.Drawing.Size(480, 170);
                splash.BackColor = System.Drawing.Color.FromArgb(7, 13, 22);
                splash.ForeColor = System.Drawing.Color.FromArgb(236, 245, 255);
                splash.Font = new System.Drawing.Font("Segoe UI", 10);
                splash.Icon = System.Drawing.Icon.ExtractAssociatedIcon(Application.ExecutablePath);
                splash.StartPosition = FormStartPosition.CenterScreen;
                splash.FormBorderStyle = FormBorderStyle.FixedDialog; splash.ControlBox = false;
                Label heading = new Label(); heading.Text = "StatsRader";
                heading.Font = new System.Drawing.Font("Segoe UI", 24, System.Drawing.FontStyle.Bold);
                heading.ForeColor = System.Drawing.Color.FromArgb(0,214,238);
                heading.AutoSize = true; heading.Left = 22; heading.Top = 18; splash.Controls.Add(heading);
                Label label = new Label(); label.Text = "Dein Bedwars Companion wird vorbereitet …";
                label.AutoSize = true; label.Left = 26; label.Top = 72; splash.Controls.Add(label);
                ProgressBar progress = new ProgressBar(); progress.Style = ProgressBarStyle.Marquee;
                progress.Left = 26; progress.Top = 113; progress.Width = 428; progress.Height = 8; splash.Controls.Add(progress);
                Exception failure = null;
                splash.Shown += delegate
                {
                    Task.Run(delegate
                    {
                        try
                        {
                            string data = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "BedwarsTab", "app");
                            string root = EnsurePackage(data);
                            ProcessStartInfo start = new ProcessStartInfo(Path.Combine(root, "runtime", "bin", "javaw.exe"));
                            start.Arguments = "-jar " + Quote(Path.Combine(root, "dist", "launcher.jar")) + " " + Quote(root);
                            start.WorkingDirectory = root; start.UseShellExecute = false; start.CreateNoWindow = true;
                            using (Process process = Process.Start(start))
                            {
                                if (process.WaitForExit(1500) && process.ExitCode != 0)
                                    throw new InvalidOperationException("Die App konnte nicht starten (Java-Code " + process.ExitCode + ").");
                            }
                        }
                        catch (Exception e) { failure = e; }
                        finally { splash.BeginInvoke((Action)delegate { splash.Close(); }); }
                    });
                };
                Application.Run(splash);
                if (failure != null) throw failure;
            }
            return 0;
        }
        catch (Exception e)
        {
            if (check)
            {
                try { Directory.CreateDirectory(args[1]); File.WriteAllText(Path.Combine(args[1], "verification-error.txt"), e.ToString()); } catch { }
            }
            else MessageBox.Show(e.Message, "StatsRader – Start fehlgeschlagen", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }
    private static string Quote(string path)
    {
        if (path.IndexOf('"') >= 0 || path.IndexOf('\r') >= 0 || path.IndexOf('\n') >= 0) throw new IOException("Ungültiger Programmpfad.");
        return "\"" + path + "\"";
    }
    private static string Hash(Stream stream)
    {
        using (SHA256 sha = SHA256.Create()) return BitConverter.ToString(sha.ComputeHash(stream)).Replace("-", "").ToLowerInvariant();
    }
    private static string ResourceText(string name)
    {
        using (Stream stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(name))
        using (StreamReader reader = new StreamReader(stream, Encoding.UTF8)) return reader.ReadToEnd().Trim();
    }
    private static Dictionary<string, string> Manifest()
    {
        Dictionary<string, string> files = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        foreach (string line in ResourceText("payload.manifest").Split('\n'))
        {
            string trimmed = line.TrimEnd('\r'); if (trimmed.Length == 0) continue;
            if (trimmed.Length < 67 || trimmed.Substring(64, 2) != "  ") throw new IOException("Ungültiges Paketmanifest.");
            files.Add(trimmed.Substring(66), trimmed.Substring(0, 64));
        }
        return files;
    }
    private static string Child(string directory, string relative)
    {
        string prefix = Path.GetFullPath(directory).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        string path = Path.GetFullPath(Path.Combine(prefix, relative.Replace('/', Path.DirectorySeparatorChar)));
        if (!path.StartsWith(prefix, StringComparison.OrdinalIgnoreCase) || relative.Contains(":")) throw new IOException("Ungültiger Pfad im Paket.");
        return path;
    }
    private static bool Valid(string directory, Dictionary<string, string> files)
    {
        try
        {
            foreach (KeyValuePair<string, string> file in files)
            {
                string path = Child(directory, file.Key);
                if (!File.Exists(path) || (File.GetAttributes(path) & FileAttributes.ReparsePoint) != 0) return false;
                using (FileStream input = File.OpenRead(path)) if (Hash(input) != file.Value) return false;
            }
            return true;
        }
        catch (IOException) { return false; }
        catch (UnauthorizedAccessException) { return false; }
    }
    private static string EnsurePackage(string directory)
    {
        directory = Path.GetFullPath(directory); Directory.CreateDirectory(directory);
        string digest = ResourceText("payload.sha256");
        Dictionary<string, string> files = Manifest();
        string target = Child(directory, "v-" + digest.Substring(0, 16));
        string mutexId;
        using (MemoryStream bytes = new MemoryStream(Encoding.UTF8.GetBytes(directory.ToLowerInvariant()))) mutexId = Hash(bytes);
        using (Mutex mutex = new Mutex(false, "Local\\BedwarsTab-" + mutexId.Substring(0, 24)))
        {
            bool held = false;
            try
            {
                try { held = mutex.WaitOne(TimeSpan.FromMinutes(3)); } catch (AbandonedMutexException) { held = true; }
                if (!held) throw new IOException("Eine andere Bedwars-Tab-Instanz wird noch eingerichtet. Bitte erneut öffnen.");
                if (Valid(target, files)) return target;
                foreach (string repaired in Directory.EnumerateDirectories(directory, "v-" + digest.Substring(0, 16) + "-repair-*"))
                    if (Valid(repaired, files)) return repaired;
                // Never overwrite a previously extracted version or a running game's JARs.
                if (Directory.Exists(target)) target = Child(directory, "v-" + digest.Substring(0, 16) + "-repair-" + Guid.NewGuid().ToString("N"));
                string stage = Child(directory, "preparing-" + Guid.NewGuid().ToString("N"));
                Directory.CreateDirectory(stage);
                using (Stream input = Assembly.GetExecutingAssembly().GetManifestResourceStream("payload.zip"))
                    if (Hash(input) != digest) throw new IOException("Das Programmpaket ist beschädigt. Die EXE bitte erneut kopieren.");
                using (Stream input = Assembly.GetExecutingAssembly().GetManifestResourceStream("payload.zip"))
                using (ZipArchive zip = new ZipArchive(input, ZipArchiveMode.Read))
                {
                    HashSet<string> extracted = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                    foreach (ZipArchiveEntry entry in zip.Entries)
                    {
                        if (entry.FullName.EndsWith("/")) continue;
                        if (!files.ContainsKey(entry.FullName) || !extracted.Add(entry.FullName)) throw new IOException("Unerwartete Datei im Paket.");
                        string path = Child(stage, entry.FullName); Directory.CreateDirectory(Path.GetDirectoryName(path));
                        using (Stream source = entry.Open())
                        using (FileStream output = new FileStream(path, FileMode.CreateNew, FileAccess.Write)) source.CopyTo(output);
                    }
                    if (extracted.Count != files.Count) throw new IOException("Unvollständiges Programmpaket.");
                }
                if (!Valid(stage, files)) throw new IOException("Prüfung der entpackten Dateien fehlgeschlagen.");
                Directory.Move(stage, target);
                return target;
            }
            finally { if (held) mutex.ReleaseMutex(); }
        }
    }
}
