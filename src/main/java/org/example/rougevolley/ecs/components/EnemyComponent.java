package org.example.rougevolley.ecs.components;

import org.example.rougevolley.ecs.Component;
import org.example.rougevolley.config.GameConfig;

/**
 * 敌人组件 —— 标记实体为敌人，存储 AI 状态与行为参数
 */
public class EnemyComponent implements Component {

    /** 敌人行为状态 */
    public enum State {
        /** 巡逻：随机方向移动 */
        PATROL,
        /** 追击：向玩家位置移动 */
        CHASE
    }

    private State state = State.PATROL;
    private double speed;
    private double detectionRadius;
    private double size; // 碰撞尺寸

    /** 巡逻方向切换计时器 */
    private double patrolTimer;
    /** 巡逻方向切换间隔 */
    private static final double PATROL_SWITCH_INTERVAL = 2.0;

    public EnemyComponent() {
        this(GameConfig.ENEMY_SPEED, GameConfig.ENEMY_DETECTION_RADIUS, GameConfig.ENEMY_SIZE);
    }

    public EnemyComponent(double speed, double detectionRadius, double size) {
        this.speed = speed;
        this.detectionRadius = detectionRadius;
        this.size = size;
        this.patrolTimer = 0;
    }

    // ── Getters / Setters ──

    public State getState() { return state; }
    public void setState(State state) { this.state = state; }

    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }

    public double getDetectionRadius() { return detectionRadius; }
    public void setDetectionRadius(double detectionRadius) { this.detectionRadius = detectionRadius; }

    public double getSize() { return size; }
    public void setSize(double size) { this.size = size; }

    public double getPatrolTimer() { return patrolTimer; }
    public void setPatrolTimer(double patrolTimer) { this.patrolTimer = patrolTimer; }

    public double getPatrolSwitchInterval() { return PATROL_SWITCH_INTERVAL; }
}
