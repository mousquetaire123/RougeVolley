package org.example.rougevolley.dungeon;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import org.example.rougevolley.config.GameConfig;
import org.example.rougevolley.ecs.Entity;
import org.example.rougevolley.ecs.components.HealthComponent;
import org.example.rougevolley.entity.EntityFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 运行时房间实例 —— 持有模板引用、网格坐标、实体列表和门连接状态。
 * <p>
 * 职责：
 * - 根据模板和网格坐标计算世界像素偏移
 * - 管理门连接状态
 * - 在 activate/deactivate 时生成/销毁敌人实体
 * - 查询房间是否已清空
 * <p>
 * 生命周期: new → activate → (玩家进出) → deactivate → (被 GC)
 */
public class Room {

    private final String id;                    // UUID
    private final RoomTemplate template;
    private final int gridX, gridY;             // 地牢网格坐标 (col, row)
    private final double worldX, worldY;        // 世界像素坐标偏移

    // 门连接状态: "N"/"S"/"E"/W" → 是否已连接到另一个房间
    private final Map<String, Boolean> doorConnected = new HashMap<>();

    // 本房间产生的实体引用
    private final List<Entity> enemyEntities = new CopyOnWriteArrayList<>();

    private boolean active = false;
    private boolean cleared = false;

    // ============================================================
    //  构造
    // ============================================================

    /**
     * @param template 房间模板
     * @param gridX    地牢网格列坐标 (col)
     * @param gridY    地牢网格行坐标 (row)
     */
    public Room(RoomTemplate template, int gridX, int gridY) {
        this.id = UUID.randomUUID().toString();
        this.template = template;
        this.gridX = gridX;
        this.gridY = gridY;

        // 世界像素偏移 = grid * 房间像素尺寸
        int roomPixelW = template.getWidthTiles() * GameConfig.TILE_SIZE;
        int roomPixelH = template.getHeightTiles() * GameConfig.TILE_SIZE;
        this.worldX = gridX * roomPixelW;
        this.worldY = gridY * roomPixelH;

        // 初始化门连接：全部未连接
        for (RoomTemplate.DoorDef door : template.getDoors()) {
            doorConnected.put(door.direction, false);
        }
    }

    // ============================================================
    //  生命周期
    // ============================================================

    /**
     * 激活房间：根据模板上的 EnemySpawn 位置创建敌人实体。
     * 多次调用安全（已有敌人则不重复生成）。
     *
     * @param gameState 用于注册实体的游戏状态
     */
    public void activate(org.example.rougevolley.core.GameState gameState) {
        if (active) return;
        if (cleared) {
            active = true;
            return; // 已清空的房间不再生成敌人
        }

        // 根据模板的 EnemySpawn 生成敌人
        for (Point2D spawn : template.getEnemySpawns()) {
            double absX = worldX + spawn.getX();
            double absY = worldY + spawn.getY();

            Entity enemy = EntityFactory.createDefaultEnemy(absX, absY);
            gameState.registerEntity(enemy);
            enemyEntities.add(enemy);
        }

        active = true;
    }

    /**
     * 停用房间：移除本房间的所有敌人实体。
     */
    public void deactivate(org.example.rougevolley.core.GameState gameState) {
        if (!active) return;

        for (Entity e : enemyEntities) {
            if (e.isActive()) {
                e.destroy();
                gameState.unregisterEntity(e);
            }
        }
        enemyEntities.clear();
        active = false;
    }

    // ============================================================
    //  查询
    // ============================================================

    public String getId() { return id; }
    public RoomTemplate getTemplate() { return template; }
    public int getGridX() { return gridX; }
    public int getGridY() { return gridY; }
    public double getWorldX() { return worldX; }
    public double getWorldY() { return worldY; }
    public boolean isActive() { return active; }
    public boolean isCleared() { return cleared; }

    /** 获取所有门的方向列表 */
    public Set<String> getDoorDirections() {
        return Collections.unmodifiableSet(doorConnected.keySet());
    }

    /** 指定方向的门是否已连接 */
    public boolean isDoorConnected(String direction) {
        return doorConnected.getOrDefault(direction, false);
    }

    /** 设置门的连接状态 */
    public void setDoorConnected(String direction, boolean connected) {
        doorConnected.put(direction, connected);
    }

    /** 获取世界空间中门的矩形区域（用于碰撞检测） */
    public Rectangle2D getDoorWorldBounds(String direction) {
        for (RoomTemplate.DoorDef door : template.getDoors()) {
            if (door.direction.equals(direction)) {
                return new Rectangle2D(
                    worldX + door.x - 4,   // 略微扩大碰撞区域
                    worldY + door.y - 4,
                    door.width + 8,
                    door.height + 8
                );
            }
        }
        return null;
    }

    /** 该方向是否有门且可通行（已连接） */
    public boolean canPassThrough(String direction) {
        return doorConnected.containsKey(direction) && doorConnected.get(direction);
    }

    // ============================================================
    //  房间清空判定
    // ============================================================

    /**
     * 检查房间内所有敌人是否已被消灭。
     * 如果全部死亡，标记房间为 cleared 并返回 true。
     */
    public boolean checkAndMarkCleared() {
        if (cleared) return true;
        if (!active) return false;

        boolean allDead = enemyEntities.stream()
            .noneMatch(e -> e.isActive() && e.hasComponent(HealthComponent.class)
                && e.getComponent(HealthComponent.class).get().isAlive());

        if (allDead) {
            cleared = true;
            // 清理已死亡实体的引用
            enemyEntities.removeIf(e -> !e.isActive());
        }
        return cleared;
    }

    // ============================================================
    //  工具
    // ============================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Room room)) return false;
        return id.equals(room.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Room{" + template.getId() + " @(" + gridX + "," + gridY + ")}";
    }
}
