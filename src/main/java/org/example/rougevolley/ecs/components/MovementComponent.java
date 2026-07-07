package org.example.rougevolley.ecs.components;

import org.example.rougevolley.ecs.Component;
import org.example.rougevolley.ecs.Entity;

/**
 * 移动组件 —— 存储速度向量，驱动实体位置变化
 */
public class MovementComponent implements Component {

    private double velocityX;
    private double velocityY;
    private double maxSpeed = Double.MAX_VALUE;

    public MovementComponent() {
        this(0, 0);
    }

    public MovementComponent(double velocityX, double velocityY) {
        this.velocityX = velocityX;
        this.velocityY = velocityY;
    }

    @Override
    public void onUpdate(Entity owner, double dt) {
        if (velocityX == 0 && velocityY == 0) return;
        owner.setPosition(owner.getPosition().add(velocityX * dt, velocityY * dt));
    }

    public void setVelocity(double vx, double vy) {
        // 限速
        double speed = Math.sqrt(vx * vx + vy * vy);
        if (speed > maxSpeed) {
            double scale = maxSpeed / speed;
            vx *= scale;
            vy *= scale;
        }
        this.velocityX = vx;
        this.velocityY = vy;
    }

    public double getVelocityX() { return velocityX; }
    public double getVelocityY() { return velocityY; }

    public double getSpeed() {
        return Math.sqrt(velocityX * velocityX + velocityY * velocityY);
    }

    public void setMaxSpeed(double maxSpeed) { this.maxSpeed = maxSpeed; }
    public double getMaxSpeed() { return maxSpeed; }

    /** 停止移动 */
    public void stop() {
        this.velocityX = 0;
        this.velocityY = 0;
    }
}
