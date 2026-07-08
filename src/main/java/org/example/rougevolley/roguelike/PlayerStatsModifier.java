package org.example.rougevolley.roguelike;

import org.example.rougevolley.config.GameConfig;
import org.example.rougevolley.ecs.Entity;
import org.example.rougevolley.ecs.components.HealthComponent;
import org.example.rougevolley.ecs.components.WeaponComponent;

/**
 * 将选中的升级应用到玩家组件
 */
public final class PlayerStatsModifier {

    private PlayerStatsModifier() {}

    public static void apply(Entity player, UpgradeOption option) {
        if (player == null || option == null) return;

        switch (option.getType()) {
            case FIRE_RATE -> applyFireRate(player, option);
            case BULLET_COUNT -> applyBulletCount(player, option);
            case HEALTH_RESTORE -> applyHealthRestore(player, option);
        }
    }

    private static void applyFireRate(Entity player, UpgradeOption option) {
        WeaponComponent weapon = player.getComponent(WeaponComponent.class).orElse(null);
        if (weapon != null) {
            weapon.improveFireRate(option.getValue());
        }
    }

    private static void applyBulletCount(Entity player, UpgradeOption option) {
        WeaponComponent weapon = player.getComponent(WeaponComponent.class).orElse(null);
        if (weapon != null) {
            weapon.addBulletCount((int) option.getValue());
            weapon.increaseSpread(GameConfig.SPREAD_ANGLE);
        }
    }

    private static void applyHealthRestore(Entity player, UpgradeOption option) {
        HealthComponent health = player.getComponent(HealthComponent.class).orElse(null);
        if (health != null) {
            health.restoreAndBuff(option.getValue());
        }
    }
}
