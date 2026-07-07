package org.example.rougevolley.ecs.components;

import org.example.rougevolley.ecs.Component;

/**
 * 玩家组件 —— 标记实体为玩家，存储玩家专属属性
 */
public class PlayerComponent implements Component {

    private double speed;
    private double size;   // 碰撞半径/边长

    public PlayerComponent() {
        this(200, 32);
    }

    public PlayerComponent(double speed, double size) {
        this.speed = speed;
        this.size = size;
    }

    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }

    public double getSize() { return size; }
    public void setSize(double size) { this.size = size; }
}
