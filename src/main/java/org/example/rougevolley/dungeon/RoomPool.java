package org.example.rougevolley.dungeon;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 房间模板池 —— 管理所有可用房间模板，支持随机选取。
 * <p>
 * 加载策略：
 * 1. 优先从 {@code assets/data/RoomTemplates.json} 读取（Tiled 格式）
 * 2. 若文件不存在，回退到 {@link RoomTemplate#buildDefaultTemplates()} 的内建模板
 * <p>
 * 线程安全：所有公开方法均可安全并发调用。
 */
public class RoomPool {

    private final List<RoomTemplate> templates;
    private final Random rng;

    private RoomPool(List<RoomTemplate> templates, Random rng) {
        this.templates = Collections.unmodifiableList(templates);
        this.rng = rng;
    }

    // ============================================================
    //  工厂方法
    // ============================================================

    /**
     * 从默认位置 {@code data/RoomTemplates.json} 加载，失败则回退内建模板。
     */
    public static RoomPool loadDefault() {
        return loadDefault(ThreadLocalRandom.current());
    }

    /**
     * 带指定随机种子的加载。
     */
    public static RoomPool loadDefault(Random rng) {
        // 尝试读取 RoomTemplates.json 资源文件
        try {
            List<RoomTemplate> loaded = RoomTemplate.loadAllFromJsonFile("data/RoomTemplates.json");
            if (!loaded.isEmpty()) {
                System.getLogger(RoomPool.class.getName()).log(
                    System.Logger.Level.INFO,
                    "Loaded " + loaded.size() + " room templates from RoomTemplates.json");
                return new RoomPool(loaded, rng);
            }
        } catch (Exception e) {
            System.getLogger(RoomPool.class.getName()).log(
                System.Logger.Level.WARNING,
                "Failed to load RoomTemplates.json, falling back to built-in templates", e);
        }

        // 回退：使用内建模板
        List<RoomTemplate> builtin = RoomTemplate.buildDefaultTemplates();
        System.getLogger(RoomPool.class.getName()).log(
            System.Logger.Level.INFO,
            "Using " + builtin.size() + " built-in room templates as fallback");
        return new RoomPool(builtin, rng);
    }

    /**
     * 从 RoomTemplate 列表直接构造。
     */
    public static RoomPool fromTemplates(List<RoomTemplate> templates) {
        return new RoomPool(new ArrayList<>(templates), ThreadLocalRandom.current());
    }

    /**
     * 从 RoomTemplate 列表直接构造（指定随机源）。
     */
    public static RoomPool fromTemplates(List<RoomTemplate> templates, Random rng) {
        return new RoomPool(new ArrayList<>(templates), rng);
    }

    // ============================================================
    //  查询
    // ============================================================

    /**
     * 随机选取一个模板（等概率）。
     */
    public RoomTemplate getRandom() {
        return templates.get(rng.nextInt(templates.size()));
    }

    /**
     * 按 ID 查找模板。
     */
    public Optional<RoomTemplate> getById(String id) {
        return templates.stream()
            .filter(t -> t.getId().equals(id))
            .findFirst();
    }

    /**
     * 获取所有模板的不可变视图。
     */
    public List<RoomTemplate> getAllTemplates() {
        return templates;
    }

    /**
     * 获取模板数量。
     */
    public int size() {
        return templates.size();
    }
}
