package fr.baguettemod;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class DiscordListener extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String channelId = event.getChannel().getId();
        String message = event.getMessage().getContentRaw();
        String author = event.getAuthor().getName();

        if (channelId.equals(Config.CHANNEL_CHAT)) {
            if (message.startsWith("!")) {
                fr.baguettemod.server.MessageForwarder.forwardCommand(author, message);
            } else {
                fr.baguettemod.server.MessageForwarder.forwardChat(author, message);
            }
        }
    }
}
