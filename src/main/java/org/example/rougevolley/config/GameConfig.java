package org.example.rougevolley.config;

/**
 * 全局游戏配置常量
 * 所有可调参数集中管理，支持外部覆写
 */
public final class GameConfig {

    private GameConfig() {}

    // ── 视口 ──
    /** 窗口宽度 */
    public static final int VIEWPORT_WIDTH = 1280;
    /** 窗口高度 */
    public static final int VIEWPORT_HEIGHT = 720;

    // ── 世界 ──
    /** 游戏世界宽度（摄像机可移动范围） */
    public static final int WORLD_WIDTH = 3000;
    /** 游戏世界高度 */
    public static final int WORLD_HEIGHT = 3000;

    // ── 玩家 ──
    /** 玩家初始血量 */
    public static final double PLAYER_MAX_HP = 100.0;
    /** 玩家移动速度 (px/s) */
    public static final double PLAYER_SPEED = 200.0;
    /** 玩家实体尺寸 */
    public static final double PLAYER_SIZE = 32.0;

    // ── 武器 ──
    /** 默认开火间隔 (秒) */
    public static final double WEAPON_FIRE_RATE = 0.5;
    /** 默认子弹速度 */
    public static final double BULLET_SPEED = 500.0;
    /** 默认子弹数量 */
    public static final int BULLET_COUNT = 1;
    /** 默认散射角 (度) */
    public static final double SPREAD_ANGLE = 5.0;
    /** 默认子弹伤害 */
    public static final double BULLET_DAMAGE = 10.0;

    // ── 敌人 ──
    /** 敌人默认血量 */
    public static final double ENEMY_DEFAULT_HP = 50.0;
    /** 敌人默认移动速度 */
    public static final double ENEMY_SPEED = 80.0;
    /** 敌人检测玩家的半径 */
    public static final double ENEMY_DETECTION_RADIUS = 200.0;
    /** 敌人实体尺寸 */
    public static final double ENEMY_SIZE = 28.0;

    // ── 子弹 ──
    /** 子弹存活时间 (秒) */
    public static final double BULLET_LIFETIME = 2.0;
    /** 子弹尺寸 */
    public static final double BULLET_SIZE = 6.0;

    // ── 地牢 ──
    /** 房间数量 */
    public static final int DUNGEON_ROOM_COUNT = 6;
    /** 房间尺寸 (tile) */
    public static final int ROOM_WIDTH_TILES = 20;
    public static final int ROOM_HEIGHT_TILES = 15;
    /** 每 tile 像素数 */
    public static final int TILE_SIZE = 32;

    // ── 升级 ──
    /** 每次升级选项数量 */
    public static final int UPGRADE_OPTIONS_COUNT = 3;
    /** 射速提升比例 */
    public static final double UPGRADE_FIRE_RATE_BONUS = 0.20;
    /** 弹丸数增加 */
    public static final int UPGRADE_BULLET_COUNT_BONUS = 1;
    /** 生命恢复 + 上限提升 */
    public static final double UPGRADE_HP_BONUS = 20.0;

    // ── 摄像机 ──
    /** 摄像机 lerp 平滑速度 (负指数系数) */
    public static final double CAMERA_LERP_SPEED = -8.0;

    // ── 主循环 ──
    /** dt 上限，防止螺旋死循环 */
    public static final double MAX_DELTA_TIME = 0.05;

    // ── 渲染 ──
    /** 视锥剔除边距 */
    public static final double CULL_MARGIN_NEG = -50;

    // ── 测试 ──
    /** 测试敌人数量 */
    public static final int TEST_ENEMY_COUNT = 8;
    /** 生成边距 (距世界边缘) */
    public static final double SPAWN_MARGIN = 200;
    /** 玩家周围安全半径 */
    public static final double SPAWN_SAFE_RADIUS = 80;
}
