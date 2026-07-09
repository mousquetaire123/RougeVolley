package org.example.rougevolley.combat;

import com.almasb.fxgl.dsl.FXGL;
import javafx.event.Event;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.example.rougevolley.config.GameConfig;
import org.example.rougevolley.core.GameEvent;
import org.example.rougevolley.core.GameState;
import org.example.rougevolley.ecs.Entity;
import org.example.rougevolley.ecs.components.PlayerComponent;
import org.example.rougevolley.ecs.components.WeaponComponent;
import org.example.rougevolley.entity.EntityFactory;

import java.util.Map;

/**
 * 武器系统 —— 射击逻辑、子弹生成与渲染节点创建
 * <p>
 * 静态工具类，负责：
 * - 武器冷却判定
 * - 瞄准方向计算与弹丸角度散布
 * - 子弹实体创建并注册到 GameState
 * - 子弹渲染节点创建并挂载到渲染层
 */
public final class WeaponSystem {

    private WeaponSystem() {}

    /** 子弹纹理（16×16像素，延迟加载） */
    private static Image bulletTexture;

    /**
     * 尝试发射武器（受冷却时间限制）
     *
     * @param player     玩家实体（需携带 WeaponComponent + PlayerComponent）
     * @param aimPoint    瞄准目标点（世界坐标）
     * @param gameState   全局游戏状态
     * @param renderNodes 渲染节点映射（entity uuid → JavaFX Node）
     */
    public static void fire(Entity player, Point2D aimPoint,
                            GameState gameState, Map<String, Node> renderNodes) {
        if (player == null || !player.isActive()) return;

        WeaponComponent weapon = player.getComponent(WeaponComponent.class).orElse(null);
        if (weapon == null) return;

        double now = gameState.getElapsedTime();
        if (!weapon.canFire(now)) return;

        weapon.markFired(now);

        // 计算射击方向（从玩家指向鼠标）
        double aimDx = aimPoint.getX() - player.getX();
        double aimDy = aimPoint.getY() - player.getY();
        double aimLen = Math.sqrt(aimDx * aimDx + aimDy * aimDy);
        if (aimLen == 0) {
            aimDx = 1;
            aimDy = 0;
            aimLen = 1;
        }
        double baseAngle = Math.toDegrees(Math.atan2(aimDy, aimDx));

        // 玩家尺寸偏移（子弹从玩家中心发射）
        PlayerComponent pc = player.getComponent(PlayerComponent.class).orElse(null);
        if (pc == null) return;
        double halfSize = pc.getSize() / 2.0;

        // 按 bulletCount 生成弹丸
        for (int i = 0; i < weapon.getBulletCount(); i++) {
            double bulletAngle = baseAngle;
            if (weapon.getBulletCount() > 1) {
                // 均匀分布散布
                double offset = (i - (weapon.getBulletCount() - 1) / 2.0) * weapon.getSpreadAngle();
                bulletAngle += offset;
            }
            double rad = Math.toRadians(bulletAngle);
            double vx = Math.cos(rad) * weapon.getBulletSpeed();
            double vy = Math.sin(rad) * weapon.getBulletSpeed();

            Entity bullet = EntityFactory.createBullet(
                player.getX() + halfSize - GameConfig.BULLET_SIZE / 2.0,
                player.getY() + halfSize - GameConfig.BULLET_SIZE / 2.0,
                vx, vy,
                weapon.getBulletDamage(),
                gameState.getElapsedTime()
            );
            gameState.registerEntity(bullet);

            // 创建子弹渲染节点（精灵图或回退方块）
            Node bulletNode;
            if (bulletTexture == null) {
                var resourceUrl = WeaponSystem.class.getResource("/assets/textures/bullet.png");
                if (resourceUrl != null) {
                    bulletTexture = new Image(resourceUrl.toExternalForm());
                }
            }
            if (bulletTexture != null && !bulletTexture.isError()) {
                ImageView bulletView = new ImageView(bulletTexture);
                bulletView.setFitWidth(32);
                bulletView.setFitHeight(32);
                bulletView.setSmooth(false);
                bulletNode = bulletView;
            } else {
                Rectangle bulletRect = new Rectangle(GameConfig.BULLET_SIZE, GameConfig.BULLET_SIZE, Color.ORANGE);
                bulletRect.setStroke(Color.YELLOW);
                bulletRect.setStrokeWidth(1);
                bulletNode = bulletRect;
            }
            FXGL.getGameScene().addUINode(bulletNode);
            renderNodes.put(bullet.getUuid(), bulletNode);
        }

        FXGL.getEventBus().fireEvent(new Event(GameEvent.BULLET_FIRED_EVENT));
    }
}
