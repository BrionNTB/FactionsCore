package net.factionscore.updater;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.powernukkitx.Server;
import org.powernukkitx.plugin.PluginLogger;
import org.powernukkitx.utils.Config;
import org.powernukkitx.utils.TextFormat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Checks GitHub releases for a newer build of the engine this server is forked from and, if
 * configured to, downloads and stages it so the next restart picks up the update. This never
 * hot-swaps the running JVM's own classes (not safely possible from inside a plugin) -- it
 * replaces the jar file on disk and asks for a restart, so the server must run under a
 * supervisor/start-script that relaunches the process on exit (systemd `Restart=always`, a
 * `while true; do java -jar server.jar; done` loop, pm2, etc.) for "auto-updates itself" to
 * actually be hands-off.
 */
public final class AutoUpdater {

    private final Config config;
    private final PluginLogger logger;
    private final File dataFolder;
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public AutoUpdater(Config config, PluginLogger logger, File dataFolder) {
        this.config = config;
        this.logger = logger;
        this.dataFolder = dataFolder;
    }

    /** Runs on the async scheduler thread; do not call from the main thread. */
    public void checkAndMaybeApply() {
        String repo = config.getString("updater.repo", "PowerNukkitX/PowerNukkitX");
        Optional<ReleaseInfo> latest = fetchLatestRelease(repo);
        if (latest.isEmpty()) {
            return;
        }
        ReleaseInfo release = latest.get();
        String currentVersion = readAppliedVersion();
        if (release.tag.equals(currentVersion)) {
            return; // already up to date with the last release we staged/applied
        }

        logger.info(TextFormat.GOLD + "A newer server build is available: " + release.tag
                + " (currently tracking " + (currentVersion.isEmpty() ? "none" : currentVersion) + ")");

        if (!config.getBoolean("updater.auto-apply", false)) {
            logger.info("updater.auto-apply is false; not downloading automatically. Set it to true to have FactionsCore stage updates for you.");
            return;
        }
        if (release.jarDownloadUrl == null) {
            logger.warning("Latest release " + release.tag + " has no jar asset to download.");
            return;
        }

        try {
            File staged = download(release.jarDownloadUrl);
            applyStaged(staged);
            writeAppliedVersion(release.tag);
            logger.info(TextFormat.GREEN + "Staged server update " + release.tag + ". Restart the server (under your process supervisor) to apply it.");
        } catch (IOException e) {
            logger.warning("Failed to download/stage server update " + release.tag + ": " + e.getMessage());
        }
    }

    private Optional<ReleaseInfo> fetchLatestRelease(String repo) {
        Request request = new Request.Builder()
                .url("https://api.github.com/repos/" + repo + "/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "FactionsCore-AutoUpdater")
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return Optional.empty();
            }
            JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
            String tag = json.has("tag_name") ? json.get("tag_name").getAsString() : null;
            if (tag == null) return Optional.empty();

            String jarUrl = null;
            if (json.has("assets")) {
                JsonArray assets = json.getAsJsonArray("assets");
                for (var element : assets) {
                    JsonObject asset = element.getAsJsonObject();
                    String name = asset.get("name").getAsString();
                    if (name.endsWith(".jar")) {
                        jarUrl = asset.get("browser_download_url").getAsString();
                        break;
                    }
                }
            }
            return Optional.of(new ReleaseInfo(tag, jarUrl));
        } catch (IOException | RuntimeException e) {
            logger.warning("Could not check for server updates: " + e.getMessage());
            return Optional.empty();
        }
    }

    private File download(String url) throws IOException {
        Request request = new Request.Builder().url(url).header("User-Agent", "FactionsCore-AutoUpdater").build();
        File staged = new File(dataFolder, "staged-update.jar");
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            try (var in = response.body().byteStream()) {
                Files.copy(in, staged.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return staged;
    }

    private void applyStaged(File staged) throws IOException {
        String jarName = config.getString("updater.jar-name", "server.jar");
        File runningJar = new File(System.getProperty("user.dir"), jarName);
        File backup = new File(dataFolder, "pre-update-backup.jar");
        if (runningJar.exists()) {
            Files.copy(runningJar.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(staged.toPath(), runningJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private String readAppliedVersion() {
        File file = new File(dataFolder, "update-state.txt");
        if (!file.exists()) return "";
        try {
            return Files.readString(file.toPath()).strip();
        } catch (IOException e) {
            return "";
        }
    }

    private void writeAppliedVersion(String version) {
        try {
            Files.writeString(new File(dataFolder, "update-state.txt").toPath(), version);
        } catch (IOException ignored) {
        }
    }

    private record ReleaseInfo(String tag, String jarDownloadUrl) {
    }
}
