package com.melkocore.ageoftransport.vehicle;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.UUID;

public class VehicleControllerFactory {

    @Nullable
    public static SteeringWheelController createSteeringWheelController(
            UUID subLevelId, ServerLevel level) {

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;

        SubLevel subLevel = container.getSubLevel(subLevelId);
        if (subLevel == null) return null;

        var localBounds = subLevel.getPlot().getBoundingBox();
        if (localBounds == null) return null;

        BlockPos steeringWheelPos = null;
        BlockPos throttlePos = null;

        var subLevelLevel = subLevel.getLevel();

        for (int x = localBounds.minX(); x <= localBounds.maxX(); x++) {
            for (int y = localBounds.minY(); y <= localBounds.maxY(); y++) {
                for (int z = localBounds.minZ(); z <= localBounds.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockEntity be = subLevelLevel.getBlockEntity(pos);

                    if (be instanceof SteeringWheelBlockEntity && steeringWheelPos == null) {
                        steeringWheelPos = pos.immutable();
                    }

                    if (subLevelLevel.getBlockState(pos).getBlock() instanceof LeverBlock
                            && throttlePos == null) {
                        throttlePos = pos.immutable();
                    }

                    if (steeringWheelPos != null && throttlePos != null) break;
                }
                if (steeringWheelPos != null && throttlePos != null) break;
            }
            if (steeringWheelPos != null && throttlePos != null) break;
        }

        if (steeringWheelPos == null || throttlePos == null) return null;

        return new SteeringWheelController(subLevelId, level, steeringWheelPos, throttlePos);
    }
}