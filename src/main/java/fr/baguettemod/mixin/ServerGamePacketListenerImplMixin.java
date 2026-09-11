package fr.baguettemod.mixin;

import fr.baguettemod.BaguetteMod;
import fr.baguettemod.DiscordBot;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleChat", at = @At("HEAD"))
    private void baguette_onChatMessage(ServerboundChatPacket packet, CallbackInfo ci) {
        if (!BaguetteMod.isActive()) return;

        String message = packet.message();
        String playerName = player.getName().getString();

        DiscordBot.sendChatMessage(playerName, message);
        BaguetteMod.LOGGER.info("[Chat -> Discord] <{}> {}", playerName, message);
    }

    @Inject(method = "handleChatCommand", at = @At("HEAD"))
    private void baguette_onChatCommand(ServerboundChatCommandPacket packet, CallbackInfo ci) {
        if (!BaguetteMod.isActive()) return;

        String command = packet.command();
        String playerName = player.getName().getString();

        DiscordBot.sendCommandMessage(playerName, "/" + command);
        BaguetteMod.LOGGER.info("[Cmd -> Discord] /{} (par {})", command, playerName);
    }

    @Inject(method = "handleSignedChatCommand", at = @At("HEAD"))
    private void baguette_onSignedChatCommand(ServerboundChatCommandSignedPacket packet, CallbackInfo ci) {
        if (!BaguetteMod.isActive()) return;

        String command = packet.command();
        String playerName = player.getName().getString();

        DiscordBot.sendCommandMessage(playerName, "/" + command);
        BaguetteMod.LOGGER.info("[Cmd signe -> Discord] /{} (par {})", command, playerName);
    }
}