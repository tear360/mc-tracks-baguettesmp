package fr.baguettemod;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class AutoUpdater {
    private static final String GITHUB_API = "https://api.github.com/repos/tear360/mc-tracks-baguettesmp/releases/latest";
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static boolean updatePending = false;

    public static void checkAndUpdate(String currentVersion) {
        CompletableFuture.runAsync(() -> {
            try {
                BaguetteMod.LOGGER.info("[AutoUpdate] Verification de la version {}...", currentVersion);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(GITHUB_API))
                        .header("Accept", "application/vnd.github.v3+json")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    BaguetteMod.LOGGER.warn("[AutoUpdate] Impossible d'acceder a l'API GitHub (code {})", response.statusCode());
                    return;
                }

                JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
                String latestTag = release.get("tag_name").getAsString().replace("v", "");

                if (!isNewerVersion(currentVersion, latestTag)) {
                    BaguetteMod.LOGGER.info("[AutoUpdate] A jour (version {}).", currentVersion);
                    return;
                }

                JsonArray assets = release.getAsJsonArray("assets");
                if (assets == null || assets.isEmpty()) {
                    BaguetteMod.LOGGER.warn("[AutoUpdate] Aucun asset dans la release {}", latestTag);
                    return;
                }

                for (int i = 0; i < assets.size(); i++) {
                    JsonObject asset = assets.get(i).getAsJsonObject();
                    String name = asset.get("name").getAsString();

                    if (name.endsWith(".jar") && name.contains("baguette-server-bot")) {
                        String downloadUrl = asset.get("browser_download_url").getAsString();
                        downloadAndSchedule(downloadUrl, latestTag);
                        return;
                    }
                }

                BaguetteMod.LOGGER.warn("[AutoUpdate] Aucun jar dans la release {}", latestTag);

            } catch (Exception e) {
                BaguetteMod.LOGGER.error("[AutoUpdate] Erreur lors de la verification.", e);
            }
        });
    }

    private static void downloadAndSchedule(String downloadUrl, String newVersion) {
        try {
            Path currentJar = findCurrentJar();
            BaguetteMod.LOGGER.info("[AutoUpdate] Nouvelle version disponible : {}. Telechargement...", newVersion);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .header("Accept", "application/octet-stream")
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                BaguetteMod.LOGGER.error("[AutoUpdate] Echec du telechargement (code {})", response.statusCode());
                return;
            }

            Path updateFile = currentJar.resolveSibling("baguette-server-bot-update.jar");
            Files.copy(response.body(), updateFile, StandardCopyOption.REPLACE_EXISTING);

            updatePending = true;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> applyUpdate(currentJar, updateFile)));

            BaguetteMod.LOGGER.info("=========================================");
            BaguetteMod.LOGGER.info("[AutoUpdate] Mise a jour vers {} telechargee.", newVersion);
            BaguetteMod.LOGGER.info("[AutoUpdate] Redemarrez Minecraft pour l'appliquer.");
            BaguetteMod.LOGGER.info("=========================================");

        } catch (IOException | InterruptedException e) {
            BaguetteMod.LOGGER.error("[AutoUpdate] Erreur lors du telechargement.", e);
        }
    }

    private static void applyUpdate(Path currentJar, Path updateFile) {
        if (!updatePending || !Files.exists(updateFile)) return;

        try {
            BaguetteMod.LOGGER.info("[AutoUpdate] Application de la mise a jour...");
            Files.deleteIfExists(currentJar);
            Files.move(updateFile, currentJar, StandardCopyOption.REPLACE_EXISTING);
            BaguetteMod.LOGGER.info("[AutoUpdate] Mod mis a jour ! Le nouveau jar est actif au prochain lancement.");
        } catch (IOException e) {
            BaguetteMod.LOGGER.warn("[AutoUpdate] Impossible de remplacer le mod automatiquement (fichier utilise).");
            BaguetteMod.LOGGER.warn("[AutoUpdate] Supprimez manuellement l'ancien mod puis renommez : {}",
                    updateFile.getFileName());
        }
    }

    private static Path findCurrentJar() {
        try {
            return Path.of(BaguetteMod.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            BaguetteMod.LOGGER.warn("[AutoUpdate] Impossible de localiser le jar du mod.");
            return Path.of("mods", "baguette-server-bot.jar");
        }
    }

    public static boolean isNewerVersion(String current, String latest) {
        String[] currentParts = current.split("\\.");
        String[] latestParts = latest.split("\\.");

        int maxLen = Math.max(currentParts.length, latestParts.length);

        for (int i = 0; i < maxLen; i++) {
            int c = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;
            int l = i < latestParts.length ? parseVersionPart(latestParts[i]) : 0;

            if (l > c) return true;
            if (l < c) return false;
        }

        return false;
    }

    private static int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part.split("-")[0]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}