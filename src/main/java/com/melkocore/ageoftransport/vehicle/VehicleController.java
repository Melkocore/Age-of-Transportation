package com.melkocore.ageoftransport.vehicle;

import java.util.UUID;

public interface VehicleController {
    void setBearingAngle(float angle); // -35 a +35, directo al bearing
    void setThrottle(float throttle);
    void stop();
    float getCurrentHeading();
    boolean isValid();
    UUID getSubLevelId();
}