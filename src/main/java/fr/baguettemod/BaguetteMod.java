package fr.baguettemod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

public class BaguetteMod implements ClientModInitializer {
    public static final String MOD_ID = "baguette-server-bot";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final String TARGET_IP = "baguette.mine.fun";
    private static boolean active = false;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[BaguetteMod] Chargement (client)...");

        String version = FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        LOGGER.info("[BaguetteMod] Version : {}", version);

        Config.init();
        Config.load();
        ScheduleTracker.load();

        AutoUpdater.checkAndUpdate(version);

        ClientPlayConnectionEvents.JOIN.register(this::onJoin);
        ClientPlayConnectionEvents.DISCONNECT.register(this::onDisconnect);

        ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> {
            if (active) {
                ScheduleTracker.closeAllActiveSessions();
                DiscordBot.stop();
                active = false;
            }
        });
    }

    private void onJoin(ClientPacketListener handler, PacketSender sender, Minecraft client) {
        ServerData serverData = handler.getServerData();
        if (serverData == null) {
            serverData = client.getCurrentServer();
        }

        String serverAddress = serverData != null ? serverData.ip : "";
        if (serverAddress == null) serverAddress = "";

        if (serverAddress.toLowerCase().contains(TARGET_IP)) {
            active = true;
            LOGGER.info("[BaguetteMod] Connecte a '{}'. Activation du mod.", serverAddress);
            DiscordBot.start();
            String self = client.getUser().getName();
            UUID selfId = client.getUser().getProfileId();
            if (selfId != null) {
                ScheduleTracker.playerJoined(selfId, self);
            }
            DiscordBot.sendJoinMessage(self);
        } else {
            LOGGER.info("[BaguetteMod] Serveur '{}' != '{}'. Mod desactive.", serverAddress, TARGET_IP);
        }
    }

    private void onDisconnect(ClientPacketListener handler, Minecraft client) {
        if (active) {
            String self = client.getUser().getName();
            ScheduleTracker.closeAllActiveSessions();
            DiscordBot.sendLeaveMessage(self);
            DiscordBot.stop();
            active = false;
            LOGGER.info("[BaguetteMod] Deconnecte. Mod desactive.");
        }
    }

    public static boolean isActive() {
        return active;
    }
}