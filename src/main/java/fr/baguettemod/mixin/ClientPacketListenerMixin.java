package fr.baguettemod.mixin;

import fr.baguettemod.BaguetteMod;
import fr.baguettemod.DiscordBot;
import fr.baguettemod.ScheduleTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    private static final byte DEATH_EVENT_ID = 3;
    private static final long DEATH_DEDUP_WINDOW_MS = 5000;
    private static final long JOIN_LEAVE_DEDUP_WINDOW_MS = 3000;
    private static final Map<UUID, String> cachedPlayerNames = new ConcurrentHashMap<>();
    private static final Map<Integer, DamageSource> lastDamageSources = new ConcurrentHashMap<>();
    private static final Map<String, Long> reportedDeaths = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> recentJoins = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> recentLeaves = new ConcurrentHashMap<>();

    @Inject(method = "sendChat", at = @At("HEAD"))
    private void baguette_onSendChat(String message, CallbackInfo ci) {
        if (!BaguetteMod.isActive()) return;

        DiscordBot.sendChatMessage(currentPlayerName(), message);
        BaguetteMod.LOGGER.info("[Chat -> Discord] <{}> {}", currentPlayerName(), message);
    }

    @Inject(method = "handleDisguisedChat", at = @At("HEAD"))
    private void baguette_onDisguisedChat(ClientboundDisguisedChatPacket packet, CallbackInfo ci) {
        if (!BaguetteMod.isActive()) return;

        Component text = packet.message();
        if (text == null) return;

        String full = text.getString();
        if (full.startsWith("<") && full.contains(">")) {
            int end = full.indexOf('>');
            String senderName = full.substring(1, end).trim();
            String message = full.substring(end + 1).trim();

            if (senderName.isEmpty() || senderName.equals(currentPlayerName())) return;

            DiscordBot.sendChatMessage(senderName, message);
            BaguetteMod.LOGGER.info("[Chat -> Discord] <{}> {}", senderName, message);
        }
    }

    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void baguette_onDamageEvent(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        if (!BaguetteMod.isActive()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        Entity entity = minecraft.level.getEntity(packet.entityId());
        if (entity instanceof Player) {
            lastDamageSources.put(entity.getId(), packet.getSource(minecraft.level));
        }
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void baguette_onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (!BaguetteMod.isActive()) return;

        if (packet.getEventId() != DEATH_EVENT_ID) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        Entity entity = packet.getEntity(minecraft.level);
        if (!(entity instanceof Player player)) return;

        DamageSource source = lastDamageSources.remove(entity.getId());
        String deathMessage;
        if (source != null) {
            deathMessage = source.getLocalizedDeathMessage(player).getString();
        } else {
            deathMessage = player.getName().getString() + " est mort";
        }

        String dimension = player.level().dimension().identifier().toString();
        int x = player.getBlockX();
        int y = player.getBlockY();
        int z = player.getBlockZ();

        reportedDeaths.put(player.getName().getString(), System.currentTimeMillis());

        BaguetteMod.LOGGER.info("[Mort -> Discord] {} (X:{}, Y:{}, Z:{}, {})", deathMessage, x, y, z, dimension);
        DiscordBot.sendDeathMessage(deathMessage, player.getName().getString(), x, y, z, dimension);
    }

    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void baguette_onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (!BaguetteMod.isActive()) return;

        Component content = packet.content();
        if (content == null) return;
        if (!(content.getContents() instanceof TranslatableContents translatable)) return;

        String key = translatable.getKey();
        if (key == null) return;

        if (key.startsWith("death.")) {
            long now = System.currentTimeMillis();
            String victim = firstArgAsComponentName(translatable, "un joueur");

            Long lastReport = reportedDeaths.get(victim);
            if (lastReport != null && now - lastReport < DEATH_DEDUP_WINDOW_MS) {
                return;
            }
            reportedDeaths.put(victim, now);

            String deathMessage = content.getString();
            BaguetteMod.LOGGER.info("[Mort (monde) -> Discord] {} (par {})", deathMessage, victim);
            DiscordBot.sendDeathMessage(deathMessage, victim);
        } else if (key.startsWith("chat.type.advancement.")) {
            String player = argAsString(translatable, 0, "un joueur");
            String advancement = argAsString(translatable, 1, "un progres");
            BaguetteMod.LOGGER.info("[Progres (monde) -> Discord] {} a obtenu {}", player, advancement);
            DiscordBot.sendAdvancementMessage(player, advancement);
        }
    }

    private static String firstArgAsComponentName(TranslatableContents translatable, String fallback) {
        Object[] args = translatable.getArgs();
        if (args != null && args.length > 0 && args[0] != null) {
            if (args[0] instanceof Component component) {
                return component.getString();
            }
            return String.valueOf(args[0]);
        }
        return fallback;
    }

    private static String argAsString(TranslatableContents translatable, int index, String fallback) {
        Object[] args = translatable.getArgs();
        if (args != null && index < args.length && args[index] != null) {
            if (args[index] instanceof Component component) {
                return component.getString();
            }
            return String.valueOf(args[index]);
        }
        return fallback;
    }

    @Inject(method = "handlePlayerInfoUpdate", at = @At("HEAD"))
    private void baguette_onPlayerInfoUpdate(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
        if (!BaguetteMod.isActive()) return;
        if (!packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) return;

        Minecraft minecraft = Minecraft.getInstance();
        long now = System.currentTimeMillis();

        for (ClientboundPlayerInfoUpdatePacket.Entry entry : packet.newEntries()) {
            UUID id = entry.profileId();
            String name = entry.profile().name();
            cachedPlayerNames.put(id, name);
            if (minecraft.isLocalPlayer(id)) continue;
            if (recentJoins.getOrDefault(id, 0L) > now - JOIN_LEAVE_DEDUP_WINDOW_MS) continue;

            recentJoins.put(id, now);
            ScheduleTracker.playerJoined(id, name);
            DiscordBot.sendJoinMessage(name);
            BaguetteMod.LOGGER.info("[Join -> Discord] {}", name);
        }
    }

    @Inject(method = "handlePlayerInfoRemove", at = @At("HEAD"))
    private void baguette_onPlayerInfoRemove(ClientboundPlayerInfoRemovePacket packet, CallbackInfo ci) {
        if (!BaguetteMod.isActive()) return;

        Minecraft minecraft = Minecraft.getInstance();
        long now = System.currentTimeMillis();

        for (UUID id : packet.profileIds()) {
            if (minecraft.isLocalPlayer(id)) continue;
            if (recentLeaves.getOrDefault(id, 0L) > now - JOIN_LEAVE_DEDUP_WINDOW_MS) continue;

            recentLeaves.put(id, now);
            ScheduleTracker.playerLeft(id);
            String name = cachedPlayerNames.remove(id);
            if (name == null) name = "joueur inconnu";
            DiscordBot.sendLeaveMessage(name);
            BaguetteMod.LOGGER.info("[Leave -> Discord] {}", name);
        }
    }

    private static String currentPlayerName() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            return minecraft.player.getName().getString();
        }
        return minecraft.getUser().getName();
    }
}