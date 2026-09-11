package fr.baguettemod;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import java.awt.Color;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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

    public static void sendChatMessage(String playerName, String message) {
        try {
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(new Color(0x58a6ff))
                    .setAuthor(playerName, null, headUrl(playerName))
                    .setDescription(message)
                    .setThumbnail(bodyUrl(playerName));
            sendEmbed(Config.CHANNEL_CHAT, embed);
        } catch (Throwable t) {
            BaguetteMod.LOGGER.error("[BaguetteMod] Erreur sendChatMessage", t);
        }
    }

    public static void sendJoinMessage(String playerName) {
        try {
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(new Color(0x2ea043))
                    .setAuthor(playerName, null, headUrl(playerName))
                    .setDescription(":green_circle: **" + playerName + "** a rejoint le serveur.")
                    .setThumbnail(bodyUrl(playerName));
            sendEmbed(Config.CHANNEL_JOINS, embed);
        } catch (Throwable t) {
            BaguetteMod.LOGGER.error("[BaguetteMod] Erreur sendJoinMessage", t);
        }
    }

    public static void sendLeaveMessage(String playerName) {
        try {
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(new Color(0xf85149))
                    .setAuthor(playerName, null, headUrl(playerName))
                    .setDescription(":red_circle: **" + playerName + "** a quitte le serveur.")
                    .setThumbnail(bodyUrl(playerName));
            sendEmbed(leavesChannelId(), embed);
        } catch (Throwable t) {
            BaguetteMod.LOGGER.error("[BaguetteMod] Erreur sendLeaveMessage", t);
        }
    }

    public static void sendDeathMessage(String deathMessage, String victim, int x, int y, int z, String dimension) {
        try {
            String dim = switch (dimension) {
                case "minecraft:overworld" -> "Overworld";
                case "minecraft:the_end" -> "The End";
                case "minecraft:the_nether" -> "Nether";
                default -> dimension;
            };
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(new Color(0xe3b341))
                    .setAuthor(victim, null, headUrl(victim))
                    .setDescription(":skull: " + deathMessage + "\n:round_pushpin: **Position de la mort :** `" + x + ", " + y + ", " + z + "` (" + dim + ")")
                    .setThumbnail(bodyUrl(victim));
            sendEmbed(Config.CHANNEL_DEATHS, embed);
        } catch (Throwable t) {
            BaguetteMod.LOGGER.error("[BaguetteMod] Erreur sendDeathMessage", t);
        }
    }

    public static void sendDeathMessage(String deathMessage, String victim) {
        try {
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(new Color(0xe3b341))
                    .setAuthor(victim, null, headUrl(victim))
                    .setDescription(":skull: " + deathMessage + "\n:pushpin: Position inconnue (joueur hors de portee de rendering).")
                    .setThumbnail(bodyUrl(victim));
            sendEmbed(Config.CHANNEL_DEATHS, embed);
        } catch (Throwable t) {
            BaguetteMod.LOGGER.error("[BaguetteMod] Erreur sendDeathMessage", t);
        }
    }

    public static void sendAdvancementMessage(String playerName, String advancement) {
        try {
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(new Color(0xa371f7))
                    .setAuthor(playerName, null, headUrl(playerName))
                    .setDescription(":trophy: **" + playerName + "** a obtenu " + advancement)
                    .setThumbnail(bodyUrl(playerName));
            sendEmbed(Config.CHANNEL_ADVANCEMENTS, embed);
        } catch (Throwable t) {
            BaguetteMod.LOGGER.error("[BaguetteMod] Erreur sendAdvancementMessage", t);
        }
    }

    public static void sendServerMessage(String message) {
        try {
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(new Color(0x57606a))
                    .setDescription(":mega: " + message);
            sendEmbed(Config.CHANNEL_CHAT, embed);
        } catch (Throwable t) {
            BaguetteMod.LOGGER.error("[BaguetteMod] Erreur sendServerMessage", t);
        }
    }

    private static String leavesChannelId() {
        return Config.CHANNEL_LEAVES.isEmpty() ? Config.CHANNEL_JOINS : Config.CHANNEL_LEAVES;
    }

    private static void sendEmbed(String channelId, EmbedBuilder embed) {
        if (jda == null) return;
        if (channelId == null || channelId.isEmpty()) return;

        long id;
        try {
            id = Long.parseLong(channelId);
        } catch (NumberFormatException e) {
            BaguetteMod.LOGGER.warn("[BaguetteMod] ID de salon invalide '{}'.", channelId);
            return;
        }

        TextChannel channel = jda.getTextChannelById(id);
        if (channel != null) {
            channel.sendMessageEmbeds(embed.build()).queue(
                    null,
                    error -> BaguetteMod.LOGGER.error("[BaguetteMod] Erreur envoi embed: {}", error.getMessage())
            );
        }
    }

    private static String headUrl(String playerName) {
        if (playerName == null || playerName.isEmpty()) return null;
        try {
            String encoded = URLEncoder.encode(playerName, StandardCharsets.UTF_8);
            return "https://mc-heads.net/avatar/" + encoded + "/64";
        } catch (Throwable t) {
            return null;
        }
    }

    private static String bodyUrl(String playerName) {
        if (playerName == null || playerName.isEmpty()) return null;
        try {
            String encoded = URLEncoder.encode(playerName, StandardCharsets.UTF_8);
            return "https://mc-heads.net/body/" + encoded + "/110";
        } catch (Throwable t) {
            return null;
        }
    }

    public static JDA getJda() {
        return jda;
    }
}