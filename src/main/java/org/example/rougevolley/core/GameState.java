package org.example.rougevolley.core;

import org.example.rougevolley.ecs.Entity;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 游戏全局状态 —— 持有所有活跃实体、事件总线引用和游戏阶段标志
 * <p>
 * 实体注册/注销操作线程安全（CopyOnWriteArrayList），
 * 适合在多线程环境下读写。
 */
public class GameState {

    private final long seed;
    private final List<Entity> entities = new CopyOnWriteArrayList<>();

    private Entity player;
    private boolean paused;
    private boolean gameOver;

    /** 游戏运行总时间 (秒) */
    private double elapsedTime;

    public GameState(long seed) {
        this.seed = seed;
        this.paused = false;
        this.gameOver = false;
        this.elapsedTime = 0;
    }

    // ── 实体管理 ──

    /**
     * 注册实体到全局列表
     */
    public void registerEntity(Entity entity) {
        Objects.requireNonNull(entity);
        entities.add(entity);
    }

    /**
     * 注销实体（不调用 destroy，仅从列表移除）
     */
    public void unregisterEntity(Entity entity) {
        entities.remove(entity);
    }

    /**
     * 获取所有活跃实体（不可变快照）
     */
    public List<Entity> getEntities() {
        return Collections.unmodifiableList(entities);
    }

    /**
     * 清理所有已标记为非活跃的实体
     * @return 被移除实体的 UUID 列表
     */
    public List<String> cleanupDeadEntities() {
        List<String> removedUuids = new ArrayList<>();
        // CopyOnWriteArrayList 不支持 iterator.remove()，必须用 removeIf
        entities.removeIf(e -> {
            if (!e.isActive()) {
                removedUuids.add(e.getUuid());
                return true;
            }
            return false;
        });
        return removedUuids;
    }

    /**
     * 更新所有活跃实体
     */
    public void updateEntities(double dt) {
        for (Entity e : entities) {
            if (e.isActive()) {
                e.onUpdate(dt);
            }
        }
    }

    // ── Getters / Setters ──

    public long getSeed() { return seed; }

    public Entity getPlayer() { return player; }
    public void setPlayer(Entity player) { this.player = player; }

    public boolean isPaused() { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }

    public boolean isGameOver() { return gameOver; }
    public void setGameOver(boolean gameOver) { this.gameOver = gameOver; }

    public double getElapsedTime() { return elapsedTime; }
    public void addTime(double dt) { this.elapsedTime += dt; }
}
