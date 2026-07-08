package org.example.rougevolley.combat;

import com.almasb.fxgl.logging.Logger;
import javafx.event.Event;
import org.example.rougevolley.config.GameConfig;
import org.example.rougevolley.core.GameEvent;
import org.example.rougevolley.core.GameState;
import org.example.rougevolley.ecs.Entity;
import org.example.rougevolley.ecs.components.EnemyComponent;
import org.example.rougevolley.ecs.components.HealthComponent;
import org.example.rougevolley.ecs.components.PlayerComponent;

/**
 * 伤害系统 —— 子弹与敌人的碰撞检测、伤害结算、击杀处理
 * <p>
 * 每帧由游戏主循环调用，遍历所有子弹-敌人对进行碰撞判定。
 * 碰撞后子弹销毁、敌人扣血；敌人血量归零时自动标记非活跃。
 */
public final class DamageSystem {

    private static final Logger log = Logger.get(DamageSystem.class);

    private DamageSystem() {}

    /**
     * 检测所有子弹与敌人之间的碰撞，执行伤害结算
     *
     * @param gameState 全局游戏状态（提供实体列表）
     */
    public static void checkBulletEnemyCollisions(GameState gameState) {
        for (Entity bullet : gameState.getEntities()) {
            // 子弹判断：有 MovementComponent、不是敌人、不是玩家（排除自爆）
            if (!bullet.isActive()) continue;
            if (bullet.hasComponent(EnemyComponent.class)) continue;
            if (bullet.hasComponent(PlayerComponent.class)) continue;

            // 取出子弹伤害（由 EntityFactory.createBullet 写入 userData）
            Object userData = bullet.getUserData();
            if (!(userData instanceof Double damage)) continue;

            double bulletX = bullet.getX();
            double bulletY = bullet.getY();
            double bulletSize = GameConfig.BULLET_SIZE;

            for (Entity enemy : gameState.getEntities()) {
                if (enemy == bullet || !enemy.isActive()) continue;
                if (!enemy.hasComponent(EnemyComponent.class)) continue;

                // 敌人碰撞尺寸
                EnemyComponent ec = enemy.getComponent(EnemyComponent.class).get();
                double enemySize = ec.getSize();

                // 矩形 vs 矩形（子弹方块 vs 敌人方块，左上角 = position）
                if (rectIntersectsRect(bulletX, bulletY, bulletSize, bulletSize,
                        enemy.getX(), enemy.getY(), enemySize, enemySize)) {

                    // ── 伤害结算 ──
                    HealthComponent health = enemy.getComponent(HealthComponent.class).orElse(null);
                    if (health != null) {
                        health.takeDamage(damage);
                    }

                    // 子弹命中后销毁
                    bullet.setActive(false);

                    // 触发伤害事件
                    com.almasb.fxgl.dsl.FXGL.getEventBus().fireEvent(new Event(GameEvent.DAMAGE_DEALT_EVENT));

                    // 敌人死亡
                    if (health != null && health.isDead()) {
                        log.info("Enemy destroyed");
                        com.almasb.fxgl.dsl.FXGL.getEventBus().fireEvent(new Event(GameEvent.ENTITY_KILLED_EVENT));
                    }

                    // 子弹已销毁，跳出内层循环
                    break;
                }
            }
        }
    }

    /**
     * 矩形与矩形碰撞检测（AABB vs AABB）
     *
     * @param x1 矩形1左上角X
     * @param y1 矩形1左上角Y
     * @param w1 矩形1宽度
     * @param h1 矩形1高度
     * @param x2 矩形2左上角X
     * @param y2 矩形2左上角Y
     * @param w2 矩形2宽度
     * @param h2 矩形2高度
     * @return 是否相交
     */
    private static boolean rectIntersectsRect(double x1, double y1, double w1, double h1,
                                               double x2, double y2, double w2, double h2) {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }
}
