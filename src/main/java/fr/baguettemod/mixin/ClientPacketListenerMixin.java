package fr.baguettemod.mixin;

import fr.baguettemod.BaguetteMod;
import fr.baguettemod.DiscordBot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
    private static final Map<UUID, String> cachedPlayerNames = new ConcurrentHashMap<>();
    private static final Map<Integer, DamageSource> lastDamageSources = new ConcurrentHashMap<>();

    @Inject(method = "sendChat", at = @At("HEAD"))
    private void baguette_onSendChat(String message, CallbackInfo ci) {
        if (!BaguetteMod.isActive()) return;

        DiscordBot.sendChatMessage(currentPlayerName(), message);
        BaguetteMod.LOGGER.info("[Chat -> Discord] <{}> {}", currentPlayerName(), message);
    }

    @Inject(method = "sendCommand", at = @At("HEAD"))
    private void baguette_onSendCommand(String command, CallbackInfo ci) {
        if (!BaguetteMod.isActive()) return;

        DiscordBot.sendCommandMessage(currentPlayerName(), "/" + command);
        BaguetteMod.LOGGER.info("[Commande -> Discord] /{} (par {})", command, currentPlayerName());
    }

    @Inject(method = "handlePlayerChat", at = @At("HEAD"))
    private void baguette_onPlayerChat(ClientboundPlayerChatPacket packet, CallbackInfo ci) {
        if (!BaguetteMod.isActive()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isLocalPlayer(packet.sender())) return;

        Component text = packet.unsignedContent();
        if (text == null) return;

        String senderName = cachedPlayerNames.get(packet.sender());
        if (senderName == null) senderName = "?";

        DiscordBot.sendChatMessage(senderName, text.getString());
        BaguetteMod.LOGGER.info("[Chat -> Discord] <{}> {}", senderName, text.getString());
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

        BaguetteMod.LOGGER.info("[Mort -> Discord] {} (X:{}, Y:{}, Z:{}, {})", deathMessage, x, y, z, dimension);
        DiscordBot.sendDeathMessage(deathMessage, x, y, z, dimension);
    }

    @Inject(method = "handlePlayerInfoUpdate", at = @At("HEAD"))
    private void baguette_onPlayerInfoUpdate(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
        if (!BaguetteMod.isActive()) return;
        if (!packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) return;

        Minecraft minecraft = Minecraft.getInstance();

        for (ClientboundPlayerInfoUpdatePacket.Entry entry : packet.newEntries()) {
            UUID id = entry.profileId();
            String name = entry.profile().name();
            cachedPlayerNames.put(id, name);
            if (!minecraft.isLocalPlayer(id)) {
                DiscordBot.sendJoinMessage(name);
                BaguetteMod.LOGGER.info("[Join -> Discord] {}", name);
            }
        }
    }

    @Inject(method = "handlePlayerInfoRemove", at = @At("HEAD"))
    private void baguette_onPlayerInfoRemove(ClientboundPlayerInfoRemovePacket packet, CallbackInfo ci) {
        if (!BaguetteMod.isActive()) return;

        Minecraft minecraft = Minecraft.getInstance();

        for (UUID id : packet.profileIds()) {
            if (minecraft.isLocalPlayer(id)) continue;
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