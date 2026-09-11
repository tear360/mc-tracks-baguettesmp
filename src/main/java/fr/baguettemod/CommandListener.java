package fr.baguettemod;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;

public class CommandListener extends ListenerAdapter {
    @Override
    public void onReady(ReadyEvent event) {
        String chatId = Config.CHANNEL_CHAT;
        if (chatId == null || chatId.isEmpty()) return;

        long id;
        try {
            id = Long.parseLong(chatId);
        } catch (NumberFormatException e) {
            return;
        }

        TextChannel chat = event.getJDA().getTextChannelById(id);
        if (chat == null) return;

        chat.getGuild().upsertCommand(Commands.slash("horaires", "Horaires de connexion des joueurs")
                .addOption(OptionType.STRING, "joueur", "Pseudo du joueur (vide = classement)", false))
                .queue(
                        c -> BaguetteMod.LOGGER.info("[BaguetteMod] Commande /horaires enregistree sur le serveur '{}'.", chat.getGuild().getName()),
                        error -> BaguetteMod.LOGGER.error("[BaguetteMod] Echec d'enregistrement de /horaires.", error)
                );
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("horaires")) return;

        event.deferReply().queue();

        OptionMapping joueur = event.getOption("joueur");
        String content = (joueur == null || joueur.getAsString().isBlank())
                ? ScheduleTracker.directorySummary()
                : ScheduleTracker.playerSummary(joueur.getAsString());

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(0x9b59b6))
                .setTitle(":chart_with_upwards_trend: Horaires de connexion")
                .setDescription(content == null ? "Joueur introuvable (pas encore observe)." : content);

        event.getHook().sendMessageEmbeds(embed.build()).queue(
                null,
                error -> BaguetteMod.LOGGER.error("[BaguetteMod] Echec de la reponse /horaires.", error)
        );
    }
}