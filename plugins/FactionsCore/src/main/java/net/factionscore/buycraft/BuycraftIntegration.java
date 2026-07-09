package net.factionscore.buycraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.powernukkitx.Server;
import org.powernukkitx.plugin.PluginLogger;
import org.powernukkitx.utils.Config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Implements Tebex's (formerly Buycraft) "Command Queue" API: poll for offline commands queued by
 * store purchases, run them on the console, then acknowledge them so Tebex doesn't resend them.
 * See https://docs.tebex.io/plugin/plugin-command-queue for the API this mirrors.
 */
public final class BuycraftIntegration {

    private static final String BASE_URL = "https://plugin.tebex.io/";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final Config config;
    private final PluginLogger logger;
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    public BuycraftIntegration(Config config, PluginLogger logger) {
        this.config = config;
        this.logger = logger;
    }

    public boolean isEnabled() {
        String secret = config.getString("buycraft.secret-key", "");
        return config.getBoolean("buycraft.enabled", false) && secret != null && !secret.isBlank();
    }

    /** Runs on the async scheduler thread; do not call from the main thread. */
    public void pollAndExecute() {
        if (!isEnabled()) return;

        List<QueuedCommand> commands = fetchOfflineCommands();
        if (commands.isEmpty()) return;

        List<Integer> executedIds = new ArrayList<>();
        for (QueuedCommand command : commands) {
            // Command execution must happen on the main thread; scheduling keeps this poll loop async.
            Server.getInstance().getScheduler().scheduleTask(() -> {
                try {
                    Server.getInstance().executeCommand(Server.getInstance().getConsoleSender(), command.command());
                } catch (Exception e) {
                    logger.warning("Failed to run queued Tebex/Buycraft command '" + command.command() + "': " + e.getMessage());
                }
            });
            executedIds.add(command.id());
        }
        acknowledge(executedIds);
    }

    private List<QueuedCommand> fetchOfflineCommands() {
        List<QueuedCommand> results = new ArrayList<>();
        Request request = new Request.Builder()
                .url(BASE_URL + "queue")
                .header("X-Tebex-Secret", config.getString("buycraft.secret-key", ""))
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return results;
            }
            JsonObject root = JsonParser.parseString(response.body().string()).getAsJsonObject();
            if (!root.has("commands")) return results;
            JsonArray commands = root.getAsJsonArray("commands");
            for (var element : commands) {
                JsonObject cmd = element.getAsJsonObject();
                int id = cmd.get("id").getAsInt();
                String commandLine = cmd.get("command").getAsString();
                results.add(new QueuedCommand(id, commandLine));
            }
        } catch (IOException | RuntimeException e) {
            logger.warning("Failed to poll Tebex/Buycraft command queue: " + e.getMessage());
        }
        return results;
    }

    private void acknowledge(List<Integer> ids) {
        if (ids.isEmpty()) return;
        JsonArray idsArray = new JsonArray();
        ids.forEach(idsArray::add);
        JsonObject body = new JsonObject();
        body.add("ids", idsArray);

        Request request = new Request.Builder()
                .url(BASE_URL + "queue")
                .header("X-Tebex-Secret", config.getString("buycraft.secret-key", ""))
                .put(RequestBody.create(body.toString(), JSON))
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.warning("Failed to acknowledge Tebex/Buycraft commands: HTTP " + response.code());
            }
        } catch (IOException e) {
            logger.warning("Failed to acknowledge Tebex/Buycraft commands: " + e.getMessage());
        }
    }

    private record QueuedCommand(int id, String command) {
    }
}
