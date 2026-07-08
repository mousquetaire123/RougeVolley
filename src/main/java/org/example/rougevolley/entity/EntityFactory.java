package org.example.rougevolley.entity;

import javafx.geometry.Point2D;
import org.example.rougevolley.config.GameConfig;
import org.example.rougevolley.ecs.Entity;
import org.example.rougevolley.ecs.EntityType;
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
        Entity player = new Entity(new Point2D(x, y), EntityType.PLAYER);

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
        Entity enemy = new Entity(new Point2D(x, y), EntityType.ENEMY_NORMAL);

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
     * 创建精英敌人 — 更高血量、更快速度、更大体型。
     */
    public static Entity createEliteEnemy(double x, double y) {
        Entity enemy = new Entity(new Point2D(x, y), EntityType.ENEMY_ELITE);
        enemy.addComponent(new EnemyComponent(
            GameConfig.ENEMY_SPEED * 1.5,
            GameConfig.ENEMY_DETECTION_RADIUS * 1.2,
            GameConfig.ENEMY_SIZE * 1.3
        ));
        enemy.addComponent(new HealthComponent(GameConfig.ENEMY_DEFAULT_HP * 2.0));
        enemy.addComponent(new MovementComponent());
        return enemy;
    }

    /**
     * 创建 Boss 敌人 — 极高血量、较慢速度、大体型、更远检测距离。
     */
    public static Entity createBossEnemy(double x, double y) {
        Entity enemy = new Entity(new Point2D(x, y), EntityType.ENEMY_BOSS);
        enemy.addComponent(new EnemyComponent(
            GameConfig.ENEMY_SPEED * 0.7,
            GameConfig.ENEMY_DETECTION_RADIUS * 1.5,
            GameConfig.ENEMY_SIZE * 1.7
        ));
        enemy.addComponent(new HealthComponent(GameConfig.ENEMY_DEFAULT_HP * 5.0));
        enemy.addComponent(new MovementComponent());
        return enemy;
    }

    /**
     * 创建子弹实体
     *
     * @param x      初始 X
     * @param y      初始 Y
     * @param vx     X 方向速度
     * @param vy     Y 方向速度
     * @param damage   伤害值
     * @param spawnTime 生成时间戳（用于生命周期管理）
     * @return 携带 Movement 组件的子弹实体
     */
    public static Entity createBullet(double x, double y, double vx, double vy, double damage, double spawnTime) {
        Entity bullet = new Entity(new Point2D(x, y), EntityType.BULLET);

        MovementComponent move = new MovementComponent();
        move.setVelocity(vx, vy);
        bullet.addComponent(move);

        // 子弹数据：伤害 + 生成时间戳（用于生命周期管理）
        bullet.setUserData(new BulletData(damage, spawnTime));

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
        // 根据数值自动分配稀有度
        EntityType rarity;
        if (value >= 50) {
            rarity = EntityType.ITEM_LEGENDARY;
        } else if (value >= 25) {
            rarity = EntityType.ITEM_RARE;
        } else {
            rarity = EntityType.ITEM_COMMON;
        }
        Entity pickup = new Entity(new Point2D(x, y), rarity);
        pickup.addComponent(new PickupComponent(type, value));
        return pickup;
    }

    /** @deprecated 使用 PickupComponent 替代 */
    @Deprecated
    public record PickupData(String type, double value) {}

    /** 子弹数据（伤害 + 生成时间戳，供生命周期管理用） */
    public record BulletData(double damage, double spawnTime) {}
}
