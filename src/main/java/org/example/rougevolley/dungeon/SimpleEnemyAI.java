package org.example.rougevolley.dungeon;

import com.almasb.fxgl.dsl.FXGL;
import javafx.geometry.Point2D;
import org.example.rougevolley.config.GameConfig;
import org.example.rougevolley.ecs.Component;
import org.example.rougevolley.ecs.Entity;
import org.example.rougevolley.ecs.components.EnemyComponent;
import org.example.rougevolley.ecs.components.MovementComponent;
import org.example.rougevolley.RougeVolleyFXGL;

/**
 * 敌人 AI 组件 —— 巡逻/追击双状态行为 + 简单墙壁回避。
 * <p>
 * 工作方式：
 * - 通过 {@code (RougeVolleyFXGL) FXGL.getApp()} 获取玩家位置
 * - 从敌人自身的 {@code userData} 中读取当前所在 {@code Room}（供墙壁检测）
 * - 修改同实体的 {@code MovementComponent} 的速度值来驱动移动
 * <p>
 * 状态切换使用 hysteresis 机制防止在边界来回震荡：
 * - 进入 CHASE：距离 ＜ detectionRadius
 * - 退出 CHASE：距离 ＞ detectionRadius × 1.3
 */
public class SimpleEnemyAI implements Component {

    /** 当前巡逻方向（弧度） */
    private double patrolAngle = 0;

    // ============================================================
    //  onAttach
    // ============================================================

    @Override
    public void onAttach(Entity owner) {
        // 初始随机巡逻方向
        patrolAngle = Math.random() * 2 * Math.PI;

        // 初始设置一个随机速度（避免第一帧停顿）
        owner.getComponent(MovementComponent.class).ifPresent(mc -> {
            mc.setVelocity(
                Math.cos(patrolAngle) * getSpeed(owner),
                Math.sin(patrolAngle) * getSpeed(owner)
            );
        });
    }

    // ============================================================
    //  每帧 AI 决策
    // ============================================================

    @Override
    public void onUpdate(Entity owner, double dt) {
        // 1. 获取玩家实体
        Entity player = getPlayer();
        if (player == null || !player.isActive()) return;

        // 2. 获取必要组件
        EnemyComponent ec = owner.getComponent(EnemyComponent.class).orElse(null);
        MovementComponent mc = owner.getComponent(MovementComponent.class).orElse(null);
        Room room = (owner.getUserData() instanceof Room r) ? r : null;
        if (ec == null || mc == null) return;

        // 3. 计算距离
        double dist = owner.getPosition().distance(player.getPosition());
        double radius = ec.getDetectionRadius();

        // 4. 双状态决策（带 hysteresis 防抖）
        if (dist < radius) {
            // 进入/保持 CHASE
            if (ec.getState() != EnemyComponent.State.CHASE) {
                ec.setState(EnemyComponent.State.CHASE);
            }
            chasePlayer(owner, ec, mc, player.getPosition(), room);

        } else if (dist > radius * 1.3) {
            // 足够远 → 回到 PATROL
            if (ec.getState() != EnemyComponent.State.PATROL) {
                ec.setState(EnemyComponent.State.PATROL);
                ec.setPatrolTimer(0);
            }
            patrol(owner, ec, mc, dt, room);

        } else {
            // hysteresis 区间内保持当前状态
            if (ec.getState() == EnemyComponent.State.CHASE) {
                chasePlayer(owner, ec, mc, player.getPosition(), room);
            } else {
                patrol(owner, ec, mc, dt, room);
            }
        }
    }

    // ============================================================
    //  CHASE 模式
    // ============================================================

    /**
     * 追击：向玩家移动，遇墙尝试垂直滑行。
     */
    private void chasePlayer(Entity owner, EnemyComponent ec,
                             MovementComponent mc, Point2D playerPos, Room room) {
        Point2D delta = playerPos.subtract(owner.getPosition());
        // 防御：玩家与敌人重合时停止移动
        if (delta.magnitude() < 0.001) {
            mc.stop();
            return;
        }
        Point2D dir = delta.normalize();

        if (room != null && willHitWall(owner, room, dir.getX(), dir.getY())) {
            // 前方是墙 → 尝试滑行
            double angle = Math.atan2(dir.getY(), dir.getX());
            slideAlongWall(mc, angle, ec.getSpeed(), room, owner);
        } else {
            mc.setVelocity(dir.getX() * ec.getSpeed(), dir.getY() * ec.getSpeed());
        }
    }

    // ============================================================
    //  PATROL 模式
    // ============================================================

    /**
     * 巡逻：向当前方向移动，计时到期或遇墙时随机换向。
     */
    private void patrol(Entity owner, EnemyComponent ec,
                        MovementComponent mc, double dt, Room room) {
        // 更新巡逻计时器
        double timer = ec.getPatrolTimer() + dt;
        boolean needNewDir = timer >= ec.getPatrolSwitchInterval()
            || mc.getSpeed() < 0.01
            || (room != null && willHitWall(owner, room,
                 Math.cos(patrolAngle), Math.sin(patrolAngle)));

        if (needNewDir) {
            patrolAngle = Math.random() * 2 * Math.PI;
            ec.setPatrolTimer(0);
        } else {
            ec.setPatrolTimer(timer);
        }

        mc.setVelocity(
            Math.cos(patrolAngle) * ec.getSpeed(),
            Math.sin(patrolAngle) * ec.getSpeed()
        );
    }

    // ============================================================
    //  墙壁回避
    // ============================================================

    /**
     * 尝试沿墙壁滑行：优先尝试垂直方向，最后回头。
     */
    private void slideAlongWall(MovementComponent mc, double angle,
                                double speed, Room room, Entity owner) {
        // ±90° 垂直方向 + 掉头
        double[][] tries = {
            { Math.cos(angle + Math.PI / 2), Math.sin(angle + Math.PI / 2) },
            { Math.cos(angle - Math.PI / 2), Math.sin(angle - Math.PI / 2) },
            { Math.cos(angle + Math.PI),     Math.sin(angle + Math.PI)     }
        };
        for (double[] dir : tries) {
            if (!willHitWall(owner, room, dir[0], dir[1])) {
                mc.setVelocity(dir[0] * speed, dir[1] * speed);
                return;
            }
        }
        // 全方向被堵 → 停止
        mc.stop();
    }

    /**
     * 预测前方 lookAhead 距离是否会碰到墙壁。
     */
    private boolean willHitWall(Entity owner, Room room, double vx, double vy) {
        if (room == null) return false;

        // 归一化方向向量确保 lookAhead 距离准确
        double len = Math.sqrt(vx * vx + vy * vy);
        if (len < 0.001) return false;
        double nx = vx / len;
        double ny = vy / len;

        double lookAhead = GameConfig.TILE_SIZE * 1.5;
        double futureX = owner.getX() + nx * lookAhead;
        double futureY = owner.getY() + ny * lookAhead;

        int tileX = (int) ((futureX - room.getWorldX()) / GameConfig.TILE_SIZE);
        int tileY = (int) ((futureY - room.getWorldY()) / GameConfig.TILE_SIZE);

        return room.getTemplate().isWall(tileX, tileY);
    }

    // ============================================================
    //  工具
    // ============================================================

    /** 提取 EnemyComponent 中的 speed（或默认值） */
    private double getSpeed(Entity owner) {
        return owner.getComponent(EnemyComponent.class)
            .map(EnemyComponent::getSpeed)
            .orElse(GameConfig.ENEMY_SPEED);
    }

    /** 获取玩家实体（每次从 FXGL 获取，避免缓存过时） */
    private Entity getPlayer() {
        return ((RougeVolleyFXGL) FXGL.getApp()).getPlayer();
    }
}
