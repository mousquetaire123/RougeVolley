package org.example.rougevolley.ecs.components;

import org.example.rougevolley.ecs.Component;
import org.example.rougevolley.config.GameConfig;

/**
 * 武器组件 —— 玩家射击属性集合，升级直接修改此组件数值
 * <p>
 * 属性：开火间隔、弹丸数、散射角、子弹速度、伤害、最后开火时间
 */
public class WeaponComponent implements Component {

    /** 开火间隔 (秒)，越小射速越快 */
    private double fireRate;

    /** 一次发射弹丸数 */
    private int bulletCount;

    /** 散射角 (度)，弹丸偏离瞄准方向的最大角度 */
    private double spreadAngle;

    /** 子弹飞行速度 (px/s) */
    private double bulletSpeed;

    /** 单发子弹伤害 */
    private double bulletDamage;

    /** 上次开火时间戳（秒），用于冷却判定 */
    private double lastFireTime;

    public WeaponComponent() {
        this(
            GameConfig.WEAPON_FIRE_RATE,
            GameConfig.BULLET_COUNT,
            GameConfig.SPREAD_ANGLE,
            GameConfig.BULLET_SPEED,
            GameConfig.BULLET_DAMAGE
        );
    }

    public WeaponComponent(double fireRate, int bulletCount,
                           double spreadAngle, double bulletSpeed, double bulletDamage) {
        this.fireRate = fireRate;
        this.bulletCount = bulletCount;
        this.spreadAngle = spreadAngle;
        this.bulletSpeed = bulletSpeed;
        this.bulletDamage = bulletDamage;
        this.lastFireTime = -999.0;
    }

    /** 是否可以开火 */
    public boolean canFire(double currentTime) {
        return (currentTime - lastFireTime) >= fireRate;
    }

    /** 记录开火时间 */
    public void markFired(double currentTime) {
        this.lastFireTime = currentTime;
    }

    // ── 升级修改 ──

    /** 射速提升 (百分比) */
    public void improveFireRate(double percent) {
        this.fireRate = Math.max(0.05, fireRate * (1 - percent));
    }

    /** 增加弹丸数 */
    public void addBulletCount(int count) {
        this.bulletCount = Math.min(20, bulletCount + count);
    }

    /** 增加散射角（弹幕代价） */
    public void increaseSpread(double degrees) {
        this.spreadAngle = Math.min(60, spreadAngle + degrees);
    }

    /** 提高伤害倍率 */
    public void improveDamage(double multiplier) {
        this.bulletDamage *= multiplier;
    }

    // ── Getters / Setters ──

    public double getFireRate() { return fireRate; }
    public void setFireRate(double fireRate) { this.fireRate = fireRate; }

    public int getBulletCount() { return bulletCount; }
    public void setBulletCount(int bulletCount) { this.bulletCount = bulletCount; }

    public double getSpreadAngle() { return spreadAngle; }
    public void setSpreadAngle(double spreadAngle) { this.spreadAngle = spreadAngle; }

    public double getBulletSpeed() { return bulletSpeed; }
    public void setBulletSpeed(double bulletSpeed) { this.bulletSpeed = bulletSpeed; }

    public double getBulletDamage() { return bulletDamage; }
    public void setBulletDamage(double bulletDamage) { this.bulletDamage = bulletDamage; }

    public double getLastFireTime() { return lastFireTime; }
    public void setLastFireTime(double lastFireTime) { this.lastFireTime = lastFireTime; }
}
