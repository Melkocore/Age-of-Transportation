package com.melkocore.ageoftransport.vehicle;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.UUID;

public class SteeringWheelController implements VehicleController {

    private final UUID subLevelId;
    private final ServerLevel level;
    private final BlockPos steeringWheelLocalPos;
    private final BlockPos throttleLocalPos; // palanca o botón


    public SteeringWheelController(UUID subLevelId, ServerLevel level,
                                   BlockPos steeringWheelLocalPos,
                                   BlockPos throttleLocalPos) {
        this.subLevelId = subLevelId;
        this.level = level;
        this.steeringWheelLocalPos = steeringWheelLocalPos;
        this.throttleLocalPos = throttleLocalPos;
    }

    private void setInUse(SteeringWheelBlockEntity sw, int value) {
        try {
            var field = sw.getClass().getDeclaredField("inUse");
            field.setAccessible(true);
            field.set(sw, value);
        } catch (Exception e) {
            // ignorar — si falla el steering wheel vuelve al centro
        }
    }

    @Override
    public void setBearingAngle(float angle) {
        SteeringWheelBlockEntity sw = getSteeringWheel();
        if (sw == null) return;

        sw.held = true;
        setInUse(sw, 20);
        sw.startHolding();
        sw.updateTargetAngle(-angle);
        sw.targetAngleToUpdate = -angle;
    }

    @Override
    public void setThrottle(float throttle) {
        SubLevel subLevel = getSubLevel();
        if (subLevel == null) return;

        boolean shouldBeActive = throttle > 0;

        BlockState currentState = subLevel.getLevel().getBlockState(throttleLocalPos);
        if (currentState.hasProperty(net.minecraft.world.level.block.LeverBlock.POWERED)) {
            boolean currentlyPowered = currentState.getValue(
                    net.minecraft.world.level.block.LeverBlock.POWERED);
            if (currentlyPowered != shouldBeActive) {
                subLevel.getLevel().setBlock(
                        throttleLocalPos,
                        currentState.setValue(
                                net.minecraft.world.level.block.LeverBlock.POWERED,
                                shouldBeActive),
                        3);
            }
        }
    }

    @Override
    public void stop() {
        setThrottle(0);
        SteeringWheelBlockEntity sw = getSteeringWheel();
        if (sw != null) {
            sw.stopHolding();
            sw.held = false;
            sw.updateTargetAngle(0);
        }
    }

    @Override
    public float getCurrentHeading() {
        SubLevel subLevel = getSubLevel();
        if (subLevel == null) return 0;

        Vector3d euler = new Vector3d();
        subLevel.logicalPose().orientation().getEulerAnglesYXZ(euler);

        float yawDegrees = (float) Math.toDegrees(euler.y);
        // Negar para invertir dirección + offset de 93°
        float heading = ((360f - yawDegrees) % 360 + 360) % 360;
        return heading;
    }

    @Override
    public boolean isValid() {
        return getSubLevel() != null && getSteeringWheel() != null;
    }

    @Override
    public UUID getSubLevelId() {
        return subLevelId;
    }

    @Nullable
    private SubLevel getSubLevel() {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        return container.getSubLevel(subLevelId);
    }

    @Nullable
    private SteeringWheelBlockEntity getSteeringWheel() {
        SubLevel subLevel = getSubLevel();
        if (subLevel == null) return null;

        BlockEntity be = subLevel.getLevel().getBlockEntity(steeringWheelLocalPos);
        if (be instanceof SteeringWheelBlockEntity sw) return sw;
        return null;
    }

    // Diferencia angular más corta entre dos ángulos (-180 a 180)
    private float angleDiff(float target, float current) {
        float diff = ((target - current) % 360 + 360) % 360;
        if (diff > 180) diff -= 360;
        return diff;
    }
}