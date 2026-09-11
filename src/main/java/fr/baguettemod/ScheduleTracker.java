package fr.baguettemod;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScheduleTracker {
    private static final Path FILE = Config.configDir().resolve("schedules.dat");
    private static final String[] GRADIENT = {"\u25A1", "\u2581", "\u2582", "\u2583", "\u2584", "\u2585", "\u2586", "\u2587", "\u2588"};
    private static final Map<String, int[]> minutesPerHour = new ConcurrentHashMap<>();
    private static final Map<String, int[]> joinsPerHour = new ConcurrentHashMap<>();
    private static final Map<String, Integer> sessionCount = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> activeSessions = new ConcurrentHashMap<>();
    private static final Map<UUID, String> uuidToName = new ConcurrentHashMap<>();
    private static final ZoneId ZONE = ZoneId.systemDefault();

    public static void load() {
        minutesPerHour.clear();
        joinsPerHour.clear();
        sessionCount.clear();
        if (!Files.exists(FILE)) return;
        try (BufferedReader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length < 2) continue;
                String name = parts[0];
                sessionCount.put(name, Integer.parseInt(parts[1]));
                minutesPerHour.put(name, parseBuckets(parts, 2));
                joinsPerHour.put(name, parseBuckets(parts, 3));
            }
            BaguetteMod.LOGGER.info("[BaguetteMod] Horaires charges : {} joueurs suivis.", minutesPerHour.size());
        } catch (IOException | NumberFormatException e) {
            BaguetteMod.LOGGER.error("[BaguetteMod] Erreur de lecture des horaires.", e);
        }
    }

    private static int[] parseBuckets(String[] parts, int index) {
        int[] buckets = new int[24];
        if (parts.length > index && !parts[index].isEmpty()) {
            String[] values = parts[index].split(",");
            for (int i = 0; i < values.length && i < 24; i++) {
                buckets[i] = Integer.parseInt(values[i]);
            }
        }
        return buckets;
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, int[]> entry : minutesPerHour.entrySet()) {
                    String name = entry.getKey();
                    int[] joins = joinsPerHour.getOrDefault(name, new int[24]);
                    writer.write(name);
                    writer.write("\t" + sessionCount.getOrDefault(name, 0));
                    writer.write("\t" + serialize(entry.getValue()));
                    writer.write("\t" + serialize(joins));
                    writer.newLine();
                }
            }
            BaguetteMod.LOGGER.info("[BaguetteMod] Horaires sauvegardes ({} joueurs).", minutesPerHour.size());
        } catch (IOException e) {
            BaguetteMod.LOGGER.error("[BaguetteMod] Erreur de sauvegarde des horaires.", e);
        }
    }

    private static String serialize(int[] buckets) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < buckets.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(buckets[i]);
        }
        return sb.toString();
    }

    public static void playerJoined(UUID uuid, String name) {
        if (uuid == null || name == null || name.isEmpty()) return;
        if (activeSessions.putIfAbsent(uuid, System.currentTimeMillis()) != null) return;
        uuidToName.put(uuid, name);
        sessionCount.merge(name, 1, Integer::sum);
        joinsPerHour.computeIfAbsent(name, k -> new int[24])[currentHour()]++;
        BaguetteMod.LOGGER.info("[Horaires] {} connecte (session #{}).", name, sessionCount.get(name));
    }

    public static void playerLeft(UUID uuid) {
        Long start = activeSessions.remove(uuid);
        String name = uuidToName.remove(uuid);
        if (start == null || name == null) return;
        accountSession(name, start, System.currentTimeMillis());
        BaguetteMod.LOGGER.info("[Horaires] {} deconnecte. Session terminee.", name);
        save();
    }

    public static void closeAllActiveSessions() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : activeSessions.entrySet()) {
            String name = uuidToName.get(entry.getKey());
            if (name != null) accountSession(name, entry.getValue(), now);
        }
        activeSessions.clear();
        uuidToName.clear();
        save();
    }

    private static void accountSession(String name, long start, long end) {
        int[] buckets = minutesPerHour.computeIfAbsent(name, k -> new int[24]);
        for (long minute = start; minute < end && minute < start + 24L * 3600_000L; minute += 60_000L) {
            int hour = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(minute), ZONE).getHour();
            buckets[hour]++;
        }
    }

    private static int currentHour() {
        return ZonedDateTime.now(ZONE).getHour();
    }

    public static String playerSummary(String requestedName) {
        String name = findCaseInsensitive(requestedName);
        if (name == null) return null;
        int[] presence = minutesPerHour.get(name);
        int[] joins = joinsPerHour.getOrDefault(name, new int[24]);
        int sessions = sessionCount.getOrDefault(name, 0);
        int totalMinutes = 0;
        for (int m : presence) totalMinutes += m;

        StringBuilder sb = new StringBuilder();
        sb.append("**").append(name).append("**\n");
        sb.append(formatDuration(totalMinutes)).append(" en ligne · ").append(sessions).append(" session(s)\n\n");

        sb.append("Presence par heure (heure du PC) :\n");
        sb.append(heatmap(presence));
        sb.append("\n");

        int[] peakHours = topHours(presence);
        if (peakHours.length > 0) {
            int bestWindow = bestTwoHourWindow(presence);
            sb.append("_Plage la plus probable (2h glissantes) :_ **")
                    .append(String.format("%02d", bestWindow)).append("h-")
                    .append(String.format("%02d", (bestWindow + 2) % 24)).append("h** (")
                    .append(presence[bestWindow] + presence[(bestWindow + 1) % 24]).append(" min)\n");
        }

        List<String> topPresence = new ArrayList<>();
        for (int i = 0; i < Math.min(3, peakHours.length); i++) {
            topPresence.add(String.format("%02dh (%dh%02d)", peakHours[i],
                    presence[peakHours[i]] / 60, presence[peakHours[i]] % 60));
        }
        if (!topPresence.isEmpty()) {
            sb.append("Top presence : `").append(String.join(" | ", topPresence)).append("`\n");
        }

        int[] joinHours = topHours(joins);
        if (joinHours.length > 0) {
            List<String> topJoins = new ArrayList<>();
            for (int i = 0; i < Math.min(3, joinHours.length); i++) {
                topJoins.add(String.format("%02dh (x%d)", joinHours[i], joins[joinHours[i]]));
            }
            sb.append("Heures de connexion : `").append(String.join(" | ", topJoins)).append("`\n");
        }
        return sb.toString();
    }

    public static String directorySummary() {
        List<Map.Entry<String, Integer>> byMinutes = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : minutesPerHour.entrySet()) {
            int total = 0;
            for (int m : entry.getValue()) total += m;
            byMinutes.add(new java.util.AbstractMap.SimpleEntry<>(entry.getKey(), total));
        }
        byMinutes.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        if (byMinutes.isEmpty()) return "Aucun joueur suivi pour l'instant. Rejoins le serveur et attends des connexions.";

        StringBuilder sb = new StringBuilder("Joueurs suivis par temps de presence :\n");
        int count = 0;
        for (Map.Entry<String, Integer> entry : byMinutes) {
            if (count++ >= 15) break;
            int[] presence = minutesPerHour.get(entry.getKey());
            String range;
            if (entry.getValue() == 0) {
                range = "--";
            } else {
                int win = bestTwoHourWindow(presence);
                range = String.format("%02dh-%02dh", win, (win + 2) % 24);
            }
            sb.append("`").append(range).append("` **").append(entry.getKey()).append("** · ")
                    .append(formatDuration(entry.getValue())).append("\n");
        }
        return sb.toString();
    }

    private static String heatmap(int[] buckets) {
        int max = 0;
        for (int m : buckets) if (m > max) max = m;
        StringBuilder sb = new StringBuilder();
        for (int line = 0; line < 3; line++) {
            sb.append("`");
            for (int h = line * 8; h < line * 8 + 8; h++) {
                int level = max == 0 ? (buckets[h] == 0 ? 0 : 1) : (int) Math.round(((double) buckets[h]) / max * 8);
                if (buckets[h] == 0) level = 0;
                if (buckets[h] > 0 && level < 1) level = 1;
                sb.append(GRADIENT[Math.min(level, 8)]);
            }
            sb.append("` ").append(String.format("%02dh-%02dh", line * 8, line * 8 + 7)).append("\n");
        }
        return sb.toString();
    }

    private static int bestTwoHourWindow(int[] buckets) {
        int best = 0;
        int bestTotal = -1;
        for (int h = 0; h < 24; h++) {
            int total = buckets[h] + buckets[(h + 1) % 24];
            if (total > bestTotal) {
                bestTotal = total;
                best = h;
            }
        }
        return best;
    }

    private static int[] topHours(int[] buckets) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            if (buckets[i] > 0) indexes.add(i);
        }
        indexes.sort((a, b) -> Integer.compare(buckets[b], buckets[a]));
        return indexes.stream().mapToInt(Integer::intValue).toArray();
    }

    private static String findCaseInsensitive(String name) {
        for (String key : minutesPerHour.keySet()) {
            if (key.equalsIgnoreCase(name)) return key;
        }
        return null;
    }

    private static String formatDuration(int totalMinutes) {
        int h = totalMinutes / 60;
        int m = totalMinutes % 60;
        return (h > 0 ? h + "h" : "") + (m > 0 || h == 0 ? m + "min" : "");
    }
}