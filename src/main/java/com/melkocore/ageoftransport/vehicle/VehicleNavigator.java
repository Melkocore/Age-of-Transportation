package com.melkocore.ageoftransport.vehicle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class VehicleNavigator {

    public enum Mode { IDLE, PURSUE }

    private final VehicleController controller;
    private Mode mode = Mode.IDLE;
    private LivingEntity pursuitTarget;

    private static final float PURSUIT_THROTTLE = 1.0f;
    private static final double PURSUIT_STOP_DIST_SQ = 8.0 * 8.0;
    private static final double PURSUIT_MAX_DIST_SQ = 64.0 * 64.0;

    private static final float MAX_BEARING = 35f;
    private static final float FULL_TURN_THRESHOLD = 25f;  // error > 25° → máximo
    private static final float COAST_THRESHOLD = 8f;       // error < 8° → soltar
// Entre 8° y 25° → proporcional

    private float lastHeading = -1f;
    private float angularVelocity = 0f;
    public VehicleNavigator(VehicleController controller) {
        this.controller = controller;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        if (mode == Mode.IDLE) controller.stop();
    }

    public void setPursuitTarget(LivingEntity target) {
        this.pursuitTarget = target;
        this.mode = Mode.PURSUE;
    }

    public void tick(ServerLevel level) {
        if (!controller.isValid()) {
            System.out.println("[AOT] Controller inválido");
            return;
        }
        System.out.println("[AOT] Navigator tick - mode: " + mode);

        switch (mode) {
            case IDLE -> controller.stop();
            case PURSUE -> tickPursue(level);
        }
    }

    private void tickPursue(ServerLevel level) {
        if (pursuitTarget == null || !pursuitTarget.isAlive()) {
            setMode(Mode.IDLE);
            return;
        }

        var subLevel = dev.ryanhcode.sable.api.sublevel.SubLevelContainer
                .getContainer(level)
                .getSubLevel(controller.getSubLevelId());
        if (subLevel == null) { setMode(Mode.IDLE); return; }

        Vec3 vehiclePos = new Vec3(
                subLevel.logicalPose().position().x(),
                subLevel.logicalPose().position().y(),
                subLevel.logicalPose().position().z());
        Vec3 targetPos = pursuitTarget.position();

        double distSq = vehiclePos.distanceToSqr(targetPos);

        if (distSq > PURSUIT_MAX_DIST_SQ) {
            setMode(Mode.IDLE);
            return;
        }

        // Calcular velocidad angular real del barco
        float currentHeading = controller.getCurrentHeading();
        if (lastHeading >= 0) {
            float delta = angleDiff(currentHeading, lastHeading);
            // Suavizado exponencial para evitar ruido
            angularVelocity = angularVelocity * 0.5f + delta * 0.5f;
        }
        lastHeading = currentHeading;

        // Heading target
        double dx = targetPos.x - vehiclePos.x;
        double dz = targetPos.z - vehiclePos.z;
        float targetHeading = (float) Math.toDegrees(Math.atan2(dx, -dz));
        targetHeading = ((targetHeading % 360) + 360) % 360;

        float headingError = angleDiff(targetHeading, currentHeading);
        // Velocidad angular sin EWMA — delta crudo con cap
        float angVel = 0f;
        if (lastHeading >= 0) {
            angVel = angleDiff(currentHeading, lastHeading);
            angVel = Math.max(-8f, Math.min(8f, angVel)); // cap anti-runaway
        }
        lastHeading = currentHeading;
        // Anticipar inercia: si ya estamos girando hacia el target,
        // reducir el threshold efectivo para soltar antes
        float coastThreshold = COAST_THRESHOLD + Math.abs(angVel) * 2f;

        float bearingAngle;
        float absError = Math.abs(headingError);

        if (absError < coastThreshold) {
            // Zona de coast — soltar timón
            bearingAngle = 0f;
        } else if (absError > FULL_TURN_THRESHOLD) {
            // Error grande — giro máximo
            bearingAngle = MAX_BEARING * Math.signum(headingError);
        } else {
            // Zona proporcional
            bearingAngle = MAX_BEARING * (headingError / FULL_TURN_THRESHOLD);
        }

        System.out.println("[AOT] error=" + String.format("%.1f", headingError)
                + " angVel=" + String.format("%.2f", angVel)
                + " coast=" + String.format("%.1f", coastThreshold)
                + " bearing=" + String.format("%.1f", bearingAngle));

        if (distSq <= PURSUIT_STOP_DIST_SQ) {
            controller.setThrottle(0);
            controller.setBearingAngle(Math.abs(headingError) > 5f ? bearingAngle : 0f);
            return;
        }

        controller.setBearingAngle(bearingAngle);
        controller.setThrottle(PURSUIT_THROTTLE);
    }

    // Diferencia angular más corta (-180 a 180)
    private float angleDiff(float target, float current) {
        float diff = ((target - current) % 360 + 360) % 360;
        if (diff > 180) diff -= 360;
        return diff;
    }
    // Registro temporal para testing — reemplazar con sistema formal después
    public static final java.util.List<VehicleNavigator> ACTIVE_NAVIGATORS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    public Mode getMode() { return mode; }
    public VehicleController getController() { return controller; }
}