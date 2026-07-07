package org.example.rougevolley.entity;

import javafx.geometry.Point2D;
import org.example.rougevolley.config.GameConfig;
import org.example.rougevolley.ecs.Entity;
import org.example.rougevolley.ecs.components.*;

/**
 * 实体工厂 —— 统一创建各类游戏实体并附加组件
 * <p>
 * 所有实体创建必须经过此工厂，确保组件组合正确。
 * 每个创建方法返回的实体可直接用于游戏逻辑层。
 */
public final class EntityFactory {

    private EntityFactory() {}

    /**
     * 创建玩家实体
     *
     * @param x 初始 X 坐标
     * @param y 初始 Y 坐标
     * @return 携带 Player + Health + Movement + Weapon 组件的实体
     */
    public static Entity createPlayer(double x, double y) {
        Entity player = new Entity(new Point2D(x, y));

        player.addComponent(new PlayerComponent(
            GameConfig.PLAYER_SPEED,
            GameConfig.PLAYER_SIZE
        ));
        player.addComponent(new HealthComponent(GameConfig.PLAYER_MAX_HP));
        player.addComponent(new MovementComponent());
        player.addComponent(new WeaponComponent());

        return player;
    }

    /**
     * 创建敌人实体
     *
     * @param x     初始 X 坐标
     * @param y     初始 Y 坐标
     * @param hp    血量
     * @param speed 移动速度
     * @return 携带 Enemy + Health + Movement 组件的实体
     */
    public static Entity createEnemy(double x, double y, double hp, double speed) {
        Entity enemy = new Entity(new Point2D(x, y));

        enemy.addComponent(new EnemyComponent(
            speed,
            GameConfig.ENEMY_DETECTION_RADIUS,
            GameConfig.ENEMY_SIZE
        ));
        enemy.addComponent(new HealthComponent(hp));
        enemy.addComponent(new MovementComponent());

        return enemy;
    }

    /**
     * 创建默认敌人（使用配置常量）
     */
    public static Entity createDefaultEnemy(double x, double y) {
        return createEnemy(x, y, GameConfig.ENEMY_DEFAULT_HP, GameConfig.ENEMY_SPEED);
    }

    /**
     * 创建子弹实体
     *
     * @param x      初始 X
     * @param y      初始 Y
     * @param vx     X 方向速度
     * @param vy     Y 方向速度
     * @param damage 伤害值
     * @return 携带 Movement 组件的子弹实体
     */
    public static Entity createBullet(double x, double y, double vx, double vy, double damage) {
        Entity bullet = new Entity(new Point2D(x, y));

        MovementComponent move = new MovementComponent();
        move.setVelocity(vx, vy);
        bullet.addComponent(move);

        // 用 HealthComponent 存储伤害值（复用现有组件，currentHealth = 伤害）
        bullet.setUserData(damage);

        // 子弹生命周期由 WeaponSystem 管理，此处仅创建
        return bullet;
    }

    /**
     * 创建可拾取道具实体
     *
     * @param x     初始 X
     * @param y     初始 Y
     * @param type  道具类型标识
     * @param value 道具数值（如回复量）
     */
    public static Entity createPickup(double x, double y, String type, double value) {
        Entity pickup = new Entity(new Point2D(x, y));
        pickup.setUserData(new PickupData(type, value));
        return pickup;
    }

    /** 道具数据（简单 POJO，不必要独立成 Component） */
    public record PickupData(String type, double value) {}
}
