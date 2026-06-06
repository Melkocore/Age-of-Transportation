package com.melkocore.ageoftransport.mixin;

import com.melkocore.ageoftransport.sublevel.binding.SubLevelBindingRegistry;
import com.melkocore.ageoftransport.sublevel.binding.SubLevelPoiContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.melkocore.ageoftransport.sublevel.binding.ServerLevelContext;
import com.melkocore.ageoftransport.sublevel.binding.SubLevelPoiContext;
import java.util.UUID;
import java.util.function.Predicate;

@Mixin(PoiManager.class)
public class PoiManagerMixin {

    @Inject(method = "exists", at = @At("HEAD"), cancellable = true)
    private void onExists(BlockPos pos, Predicate<Holder<PoiType>> typePredicate,
                          CallbackInfoReturnable<Boolean> cir) {
        UUID villagerUUID = SubLevelPoiContext.getCurrentVillager();
        if (villagerUUID == null) return;

        var server = ServerLevelContext.getServer();
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            SubLevelBindingRegistry registry = SubLevelBindingRegistry.get(level);
            if (registry.hasBinding(villagerUUID) && registry.isValid(villagerUUID, level)) {
                cir.setReturnValue(true);
                return;
            }
        }
    }
}