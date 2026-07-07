package org.example.rougevolley.ecs.components;

import org.example.rougevolley.ecs.Component;
import org.example.rougevolley.ecs.Entity;

/**
 * 生命值组件 —— 管理实体血量、受伤、死亡判定
 */
public class HealthComponent implements Component {

    private double maxHealth;
    private double currentHealth;

    public HealthComponent() {
        this(100);
    }

    public HealthComponent(double maxHealth) {
        this.maxHealth = maxHealth;
        this.currentHealth = maxHealth;
    }

    /** 受到伤害，返回实际造成的伤害量 */
    public double takeDamage(double amount) {
        double actual = Math.min(currentHealth, amount);
        currentHealth = Math.max(0, currentHealth - amount);
        return actual;
    }

    /** 恢复生命 */
    public void heal(double amount) {
        currentHealth = Math.min(maxHealth, currentHealth + amount);
    }

    /** 完全恢复并提升上限 */
    public void restoreAndBuff(double buffAmount) {
        maxHealth += buffAmount;
        currentHealth = maxHealth;
    }

    public boolean isDead() {
        return currentHealth <= 0;
    }

    public boolean isAlive() {
        return currentHealth > 0;
    }

    public double getCurrentHealth() { return currentHealth; }
    public double getMaxHealth() { return maxHealth; }
    public void setMaxHealth(double maxHealth) { this.maxHealth = maxHealth; }

    /** 返回血量百分比 [0, 1] */
    public double getHealthPercent() {
        return maxHealth > 0 ? currentHealth / maxHealth : 0;
    }

    @Override
    public void onUpdate(Entity owner, double dt) {
        // 死亡实体标记为非活跃
        if (isDead()) {
            owner.setActive(false);
        }
    }
}
