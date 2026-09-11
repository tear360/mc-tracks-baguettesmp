package fr.baguettemod;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

public class DiscordBot {
    private static JDA jda;

    public static void start() {
        if (Config.DISCORD_TOKEN.isEmpty() || Config.DISCORD_TOKEN.equals("METTRE_TOKEN_ICI")) {
            BaguetteMod.LOGGER.error("[BaguetteMod] Token Discord invalide. Le bot ne demarrera pas.");
            return;
        }

        try {
            jda = JDABuilder.createDefault(Config.DISCORD_TOKEN)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.MESSAGE_CONTENT
                    )
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .addEventListeners(new DiscordListener())
                    .build();

            BaguetteMod.LOGGER.info("[BaguetteMod] Bot Discord demarre !");
        } catch (Exception e) {
            BaguetteMod.LOGGER.error("[BaguetteMod] Echec de connexion au bot Discord.", e);
        }
    }

    public static void stop() {
        if (jda != null) {
            jda.shutdown();
            BaguetteMod.LOGGER.info("[BaguetteMod] Bot Discord arrete.");
        }
    }

    public static void sendMessage(String channelId, String message) {
        if (jda == null) return;
        if (channelId.isEmpty()) return;

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel != null) {
            channel.sendMessage(message).queue(
                    null,
                    error -> BaguetteMod.LOGGER.error("[BaguetteMod] Erreur envoi message: {}", error.getMessage())
            );
        }
    }

    public static void sendChatMessage(String playerName, String message) {
        sendMessage(Config.CHANNEL_CHAT, "**[" + playerName + "]** " + message);
    }

    public static void sendJoinMessage(String playerName) {
        sendMessage(Config.CHANNEL_JOINS, ":green_circle: **" + playerName + "** a rejoint le serveur.");
    }

    public static void sendLeaveMessage(String playerName) {
        sendMessage(Config.CHANNEL_JOINS, ":red_circle: **" + playerName + "** a quitte le serveur.");
    }

    public static void sendDeathMessage(String deathMessage, int x, int y, int z, String dimension) {
        String dim = switch (dimension) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_end" -> "The End";
            case "minecraft:the_nether" -> "Nether";
            default -> dimension;
        };
        sendMessage(Config.CHANNEL_DEATHS,
                ":skull: " + deathMessage + "\n:round_pushpin: **Position de la mort :** `" + x + ", " + y + ", " + z + "` (" + dim + ")");
    }

    public static void sendDeathMessage(String deathMessage) {
        sendMessage(Config.CHANNEL_DEATHS,
                ":skull: " + deathMessage + "\n:pushpin: Position inconnue (joueur hors de portee de rendering).");
    }

    public static void sendAdvancementMessage(String playerName, String advancement) {
        sendMessage(Config.CHANNEL_ADVANCEMENTS, ":trophy: **" + playerName + "** a obtenu " + advancement);
    }

    public static void sendServerMessage(String message) {
        sendMessage(Config.CHANNEL_CHAT, ":mega: " + message);
    }

    public static JDA getJda() {
        return jda;
    }
}
