package com.melkocore.ageoftransport.mixin;

import com.melkocore.ageoftransport.sublevel.binding.SubLevelBindingRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.WorkAtPoi;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.melkocore.ageoftransport.sublevel.binding.ServerLevelContext;
import com.melkocore.ageoftransport.sublevel.binding.SubLevelPoiContext;
@Mixin(WorkAtPoi.class)
public class WorkAtPoiMixin {

    @Inject(method = "checkExtraStartConditions", at = @At("HEAD"), cancellable = true)
    private void onCheckExtraStartConditions(ServerLevel level, Villager villager,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (!SubLevelBindingRegistry.get(level).hasBinding(villager.getUUID())) return;

        Vec3 globalPos = SubLevelBindingRegistry.get(level)
                .resolveGlobalPos(villager.getUUID(), level);
        if (globalPos == null) {
            cir.setReturnValue(false);
            return;
        }

        double distSq = villager.position().distanceToSqr(globalPos);
        cir.setReturnValue(distSq <= 1.73 * 1.73);
    }

    @Inject(method = "canStillUse", at = @At("HEAD"), cancellable = true)
    private void onCanStillUse(ServerLevel level, Villager villager, long gameTime,
                               CallbackInfoReturnable<Boolean> cir) {
        if (!SubLevelBindingRegistry.get(level).hasBinding(villager.getUUID())) return;

        Vec3 globalPos = SubLevelBindingRegistry.get(level)
                .resolveGlobalPos(villager.getUUID(), level);
        if (globalPos == null) {
            cir.setReturnValue(false);
            return;
        }

        double distSq = villager.position().distanceToSqr(globalPos);
        cir.setReturnValue(distSq <= 1.73 * 1.73);
    }
}