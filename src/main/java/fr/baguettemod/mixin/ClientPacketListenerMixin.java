package fr.baguettemod.mixin;

import fr.baguettemod.BaguetteMod;
import fr.baguettemod.DiscordBot;
import fr.baguettemod.ScheduleTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    private static final byte DEATH_EVENT_ID = 3;
    private static final long DEATH_DEDUP_WINDOW_MS = 5000;
    private static final long JOIN_LEAVE_DEDUP_WINDOW_MS = 3000;
    private static final Map<UUID, String> cachedPlayerNames = new ConcurrentHashMap<>();
    private static final Map<String, Long> reportedDeaths = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> recentJoins = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> recentLeaves = new ConcurrentHashMap<>();
    private static final Map<String, Long> recentChats = new ConcurrentHashMap<>();
    private static final long CHAT_DEDUP_WINDOW_MS = 2000;
    private static final long DEATH_POSITION_WINDOW_MS = 5000;
    private static final long DEATH_SEND_DELAY_MS = 800;
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "baguette-death");

        t.setDaemon(true);
        return t;
    });
    private static final Map<String, DeathPosition> pendingDeathPositions = new ConcurrentHashMap<>();
    private static final Map<String, String> pendingDeathMessages = new ConcurrentHashMap<>();

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
            if (!checkChatDedup(senderName, message)) return;

            DiscordBot.sendChatMessage(senderName, message);
            BaguetteMod.LOGGER.info("[Chat -> Discord] <{}> {}", senderName, message);
        } else if (!full.isEmpty() && seenUnknownChatFormats.add(full.length() > 60 ? full.substring(0, 60) : full)) {
            BaguetteMod.LOGGER.info("[Chat] Format non reconnu (1x): '{}'", full);
        }
    }

    private static final Set<String> seenUnknownChatFormats = ConcurrentHashMap.newKeySet();

    @Inject(method = "handlePlayerChat", at = @At("HEAD"))
    private void baguette_onPlayerChat(ClientboundPlayerChatPacket packet, CallbackInfo ci) {
        if (!BaguetteMod.isActive()) return;
        if (packet == null) return;

        UUID senderId = packet.sender();
        if (senderId == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isLocalPlayer(senderId)) return;

        String senderName = cachedPlayerNames.get(senderId);
        if (senderName == null || senderName.isEmpty()) return;

        String message = null;
        if (packet.body() != null && packet.body().content() != null) {
            message = packet.body().content();
        }
        if (message == null || message.isEmpty()) return;

        if (!checkChatDedup(senderName, message)) return;

        DiscordBot.sendChatMessage(senderName, message);
        BaguetteMod.LOGGER.info("[Chat signe -> Discord] <{}> {}", senderName, message);
    }

    private static boolean checkChatDedup(String senderName, String message) {
        long now = System.currentTimeMillis();
        String key = senderName + "\u0000" + message;
        Long last = recentChats.get(key);
        if (last != null && now - last < CHAT_DEDUP_WINDOW_MS) return false;
        recentChats.put(key, now);
        return true;
    }

    private static void scheduleDeathSend(String victim) {
        SCHEDULER.schedule(() -> {
            try {
                long now = System.currentTimeMillis();
                DeathPosition pos = pendingDeathPositions.remove(victim);
                String deathMessage = pendingDeathMessages.remove(victim);
                if (deathMessage == null || deathMessage.isEmpty()) {
                    deathMessage = victim + " est mort";
                }
                if (pos != null && now - pos.time <= DEATH_POSITION_WINDOW_MS) {
                    DiscordBot.sendDeathMessage(deathMessage, victim, pos.x, pos.y, pos.z, pos.dimension);
                } else {
                    DiscordBot.sendDeathMessage(deathMessage, victim);
                }
            } catch (Throwable t) {
                BaguetteMod.LOGGER.error("[BaguetteMod] Erreur scheduleDeathSend", t);
            }
        }, DEATH_SEND_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private record DeathPosition(int x, int y, int z, String dimension, long time) {}

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void baguette_onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (!BaguetteMod.isActive()) return;

        if (packet.getEventId() != DEATH_EVENT_ID) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        Entity entity = packet.getEntity(minecraft.level);
        if (!(entity instanceof Player player)) return;

        long now = System.currentTimeMillis();
        String victim = player.getName().getString();

        DeathPosition pos = new DeathPosition(
                player.getBlockX(),
                player.getBlockY(),
                player.getBlockZ(),
                player.level().dimension().identifier().toString(),
                now
        );
        pendingDeathPositions.put(victim, pos);
        pendingDeathMessages.putIfAbsent(victim, sourceDeathMessage(player));

        Long lastReport = reportedDeaths.get(victim);
        if (lastReport != null && now - lastReport < DEATH_DEDUP_WINDOW_MS) {
            return;
        }
        reportedDeaths.put(victim, now);

        BaguetteMod.LOGGER.info("[Mort -> position] {} (X:{}, Y:{}, Z:{}, {})", victim, pos.x, pos.y, pos.z, pos.dimension);
        scheduleDeathSend(victim);
    }

    private static String sourceDeathMessage(Player player) {
        return player.getName().getString() + " est mort";
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

            String deathMessage = content.getString();
            pendingDeathMessages.put(victim, deathMessage);

            Long lastReport = reportedDeaths.get(victim);
            if (lastReport != null && now - lastReport < DEATH_DEDUP_WINDOW_MS) {
                return;
            }
            reportedDeaths.put(victim, now);

            BaguetteMod.LOGGER.info("[Mort (monde) -> Discord] {} (par {})", deathMessage, victim);
            scheduleDeathSend(victim);
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