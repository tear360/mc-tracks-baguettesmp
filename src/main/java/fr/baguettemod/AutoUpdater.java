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
    private static final String REPO_URL = "https://github.com/tear360/mc-tracks-baguettesmp/releases";
    private static final HttpClient httpClient = HttpClient.newHttpClient();

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

                if (isNewerVersion(currentVersion, latestTag)) {
                    BaguetteMod.LOGGER.info("[AutoUpdate] Nouvelle version disponible : {} (actuelle : {})", latestTag, currentVersion);

                    JsonArray assets = release.getAsJsonArray("assets");
                    if (assets == null || assets.isEmpty()) {
                        BaguetteMod.LOGGER.warn("[AutoUpdate] Aucun asset trouve dans la release {}", latestTag);
                        return;
                    }

                    for (int i = 0; i < assets.size(); i++) {
                        JsonObject asset = assets.get(i).getAsJsonObject();
                        String name = asset.get("name").getAsString();

                        if (name.endsWith(".jar") && name.contains("baguette-server-bot")) {
                            String downloadUrl = asset.get("browser_download_url").getAsString();
                            downloadAndUpdate(downloadUrl, name, latestTag);
                            return;
                        }
                    }

                    BaguetteMod.LOGGER.warn("[AutoUpdate] Aucun jar trouve dans la release {}", latestTag);
                } else {
                    BaguetteMod.LOGGER.info("[AutoUpdate] A jour (version {}).", currentVersion);
                }

            } catch (Exception e) {
                BaguetteMod.LOGGER.error("[AutoUpdate] Erreur lors de la verification.", e);
            }
        });
    }

    private static void downloadAndUpdate(String downloadUrl, String fileName, String newVersion) {
        try {
            BaguetteMod.LOGGER.info("[AutoUpdate] Telechargement de {}...", fileName);

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

            Path tempFile = Files.createTempFile("baguette-update-", ".jar");
            Files.copy(response.body(), tempFile, StandardCopyOption.REPLACE_EXISTING);

            Path currentJar = Path.of(System.getProperty("java.class.path"));
            if (currentJar.toString().endsWith(".jar") && Files.exists(currentJar)) {
                Path backup = currentJar.resolveSibling(currentJar.getFileName() + ".bak");
                Files.copy(currentJar, backup, StandardCopyOption.REPLACE_EXISTING);

                Files.move(tempFile, currentJar, StandardCopyOption.REPLACE_EXISTING);

                BaguetteMod.LOGGER.info("=========================================");
                BaguetteMod.LOGGER.info("[AutoUpdate] Mod mis a jour vers la version {} !", newVersion);
                BaguetteMod.LOGGER.info("[AutoUpdate] Redemarrez le serveur pour appliquer.");
                BaguetteMod.LOGGER.info("[AutoUpdate] Ancienne version sauvegardee : {}", backup.getFileName());
                BaguetteMod.LOGGER.info("=========================================");
            } else {
                BaguetteMod.LOGGER.warn("[AutoUpdate] Chemin du jar actuel invalide : {}", currentJar);
                Files.deleteIfExists(tempFile);
            }

        } catch (IOException | InterruptedException e) {
            BaguetteMod.LOGGER.error("[AutoUpdate] Erreur lors du telechargement.", e);
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