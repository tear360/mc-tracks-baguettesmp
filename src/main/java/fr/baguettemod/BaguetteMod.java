package fr.baguettemod;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class BaguetteMod implements DedicatedServerModInitializer {
    public static final String MOD_ID = "baguette-server-bot";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final String TARGET_IP = "baguette.mine.fun";
    private static boolean active = false;

    @Override
    public void onInitializeServer() {
        LOGGER.info("[BaguetteMod] Chargement...");

        String version = FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        LOGGER.info("[BaguetteMod] Version : {}", version);

        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        ServerPlayConnectionEvents.JOIN.register(this::onPlayerJoin);
        ServerPlayConnectionEvents.DISCONNECT.register(this::onPlayerLeave);

        AutoUpdater.checkAndUpdate(version);
    }

    private void onServerStarting(MinecraftServer server) {
        String ip = server.getLocalIp();
        if (ip == null) ip = "";

        if (ip.equalsIgnoreCase(TARGET_IP) || ip.contains(TARGET_IP)) {
            active = true;
            LOGGER.info("[BaguetteMod] IP detectee : {}. Activation du mod.", ip);
            Config.load();
            DiscordBot.start();
        } else {
            LOGGER.info("[BaguetteMod] IP '{}' ne correspond pas a '{}'. Mod desactive.", ip, TARGET_IP);
        }
    }

    private void onServerStopping(MinecraftServer server) {
        if (active) {
            DiscordBot.stop();
            LOGGER.info("[BaguetteMod] Arrete.");
        }
    }

    private void onPlayerJoin(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server) {
        if (!active) return;

        ServerPlayer player = handler.player;
        if (player == null) return;

        String name = player.getName().getString();
        LOGGER.info("[Join] {} a rejoint le serveur.", name);
        DiscordBot.sendJoinMessage(name);
    }

    private void onPlayerLeave(ServerGamePacketListenerImpl handler, MinecraftServer server) {
        if (!active) return;

        if (handler.player != null) {
            String name = handler.player.getName().getString();
            LOGGER.info("[Leave] {} a quitte le serveur.", name);
            DiscordBot.sendLeaveMessage(name);
        }
    }

    public static boolean isActive() {
        return active;
    }
}