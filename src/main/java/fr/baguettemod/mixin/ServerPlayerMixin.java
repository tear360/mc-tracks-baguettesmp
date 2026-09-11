package fr.baguettemod.mixin;

import fr.baguettemod.BaguetteMod;
import fr.baguettemod.DiscordBot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "die", at = @At("TAIL"))
    private void baguette_onPlayerDeath(DamageSource source, CallbackInfo ci) {
        if (!BaguetteMod.isActive()) return;

        ServerPlayer self = (ServerPlayer) (Object) this;
        String deathMsg = self.getCombatTracker().getDeathMessage().getString();
        int x = self.getBlockX();
        int y = self.getBlockY();
        int z = self.getBlockZ();
        String dimension = self.level().dimension().identifier().toString();

        BaguetteMod.LOGGER.info("[Mort] {} (X:{}, Y:{}, Z:{}, {})", deathMsg, x, y, z, dimension);
        DiscordBot.sendDeathMessage(deathMsg, x, y, z, dimension);
    }
}