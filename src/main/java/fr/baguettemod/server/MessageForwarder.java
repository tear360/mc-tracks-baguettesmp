package fr.baguettemod.server;

import fr.baguettemod.BaguetteMod;
import fr.baguettemod.DiscordBot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public class MessageForwarder {
    private static MinecraftServer server;

    public static void setServer(MinecraftServer srv) {
        server = srv;
    }

    public static void forwardChat(String playerName, String message) {
        if (server == null) return;

        server.execute(() -> {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("<" + playerName + "> " + message),
                    false
            );
        });

        DiscordBot.sendChatMessage(playerName, message);
        BaguetteMod.LOGGER.info("[Chat -> MC] <{}> {}", playerName, message);
    }

    public static void forwardCommand(String playerName, String command) {
        if (server == null) return;

        String cmd = command.substring(1);
        server.execute(() -> {
            try {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
            } catch (Exception e) {
                BaguetteMod.LOGGER.error("[Discord -> MC] Echec de la commande /{}", cmd, e);
            }
        });

        DiscordBot.sendCommandMessage(playerName, command);
        BaguetteMod.LOGGER.info("[Discord -> MC] /{} (par {})", cmd, playerName);
    }
}
