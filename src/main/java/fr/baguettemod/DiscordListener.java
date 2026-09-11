package fr.baguettemod;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

public class DiscordListener extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String channelId = event.getChannel().getId();
        String message = event.getMessage().getContentRaw();
        String author = event.getAuthor().getName();

        if (!channelId.equals(Config.CHANNEL_CHAT)) return;
        if (message.isEmpty()) return;

        if (message.trim().startsWith("!horaires")) {
            handleHoraires(message);
            return;
        }

        Minecraft.getInstance().execute(() -> {
            if (!BaguetteMod.isActive()) return;

            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection == null) {
                BaguetteMod.LOGGER.warn("[Discord -> MC] Non connecte, message ignore.");
                return;
            }

            if (message.startsWith("/")) {
                String command = message.substring(1);
                connection.sendCommand(command);
                BaguetteMod.LOGGER.info("[Discord -> MC] /{} (par {})", command, author);
            } else {
                connection.sendChat("<[Discord] " + author + "> " + message);
                BaguetteMod.LOGGER.info("[Discord -> MC] <{}> {}", author, message);
            }
        });
    }

    private void handleHoraires(String raw) {
        String rest = raw.substring("!horaires".length()).trim();
        String content;
        if (rest.isEmpty()) {
            content = ScheduleTracker.directorySummary();
        } else {
            content = ScheduleTracker.playerSummary(rest);
        }
        DiscordBot.sendHorairesMessage(content);
    }
}