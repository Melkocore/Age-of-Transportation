package com.melkocore.ageoftransport.mixin;

import com.melkocore.ageoftransport.sublevel.binding.SubLevelPoiContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.world.entity.ai.Brain.class)
public class BrainMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private <E extends LivingEntity> void onTickStart(ServerLevel level, E entity,
                                                      CallbackInfo ci) {
        if (entity instanceof Villager villager) {
            //System.out.println("[AOT-MIXIN] Brain tick villager=" + villager.getUUID());
            SubLevelPoiContext.setCurrentVillager(villager.getUUID());
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private <E extends LivingEntity> void onTickEnd(ServerLevel level, E entity,
                                                    CallbackInfo ci) {
        if (entity instanceof Villager) {
            SubLevelPoiContext.clear();
        }
    }

}