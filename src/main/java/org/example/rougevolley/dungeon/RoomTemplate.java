package org.example.rougevolley.dungeon;

import javafx.geometry.Point2D;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 房间模板 —— 加载/解析 Tiled JSON 格式，提供 tile 网格查询。
 * <p>
 * 支持两种构建方式：
 * 1. {@link #loadFromTiledJson(String)} — 从 Tiled 导出的 JSON 字符串解析
 * 2. {@link #buildDefaultTemplates()} — 内建 5 种预设模板（程序化生成）
 * <p>
 * Tiled 图层约定：
 * - ground: 地板层，GID 1 = 地板
 * - walls:  墙壁层，GID 2 = 墙壁，GID 0 = 空
 * - objects: 对象层，name="PlayerSpawn" / "EnemySpawn"
 * - doors:   对象层，name="Door", type="N"/"S"/"E"/W"
 */
public class RoomTemplate {

    /** 门定义：方向和像素矩形 */
    public static class DoorDef {
        public final String direction; // "N" "S" "E" "W"
        public final double x, y, width, height;

        public DoorDef(String direction, double x, double y, double width, double height) {
            this.direction = direction;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        /** 门在 tile 坐标系中的中心列（用于房门对齐） */
        public int getTileCenterX(int tileSize) {
            return (int) ((x + width / 2) / tileSize);
        }

        /** 门在 tile 坐标系中的中心行 */
        public int getTileCenterY(int tileSize) {
            return (int) ((y + height / 2) / tileSize);
        }
    }

    // ── 模板元数据 ──
    private final String id;
    private final String name;
    private final int widthTiles;
    private final int heightTiles;
    private final int tileWidth;
    private final int tileHeight;

    // tile 网格: [row][col], GID 值
    private final int[][] groundLayer;
    private final int[][] wallLayer;

    // 对象层
    private final List<DoorDef> doors;
    private final Point2D playerSpawn;          // 像素坐标
    private final List<Point2D> enemySpawns;    // 像素坐标列表

    // ── GID 常量 ──
    public static final int GID_EMPTY = 0;
    public static final int GID_FLOOR = 1;
    public static final int GID_WALL = 2;

    // ============================================================
    //  构造
    // ============================================================

    private RoomTemplate(String id, String name,
                         int widthTiles, int heightTiles,
                         int tileWidth, int tileHeight,
                         int[][] groundLayer, int[][] wallLayer,
                         List<DoorDef> doors,
                         Point2D playerSpawn, List<Point2D> enemySpawns) {
        this.id = id;
        this.name = name;
        this.widthTiles = widthTiles;
        this.heightTiles = heightTiles;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.groundLayer = groundLayer;
        this.wallLayer = wallLayer;
        this.doors = Collections.unmodifiableList(doors);
        this.playerSpawn = playerSpawn;
        this.enemySpawns = Collections.unmodifiableList(enemySpawns);
    }

    // ============================================================
    //  加载 — 自动检测格式
    // ============================================================

    /**
     * 从 JSON 字符串自动检测格式并加载。
     * 支持：
     * - Tiled 标准 JSON（单个地图对象）
     * - 紧凑模式 JSON（含 pattern 字段的字符串网格）
     * - JSON 数组（含多个上述格式元素）
     */
    public static List<RoomTemplate> loadAllFromJsonString(String jsonContent) {
        String trimmed = jsonContent.trim();
        if (trimmed.startsWith("[")) {
            // JSON 数组 → 每个元素是一个模板
            JSONArray arr = new JSONArray(trimmed);
            List<RoomTemplate> result = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                result.add(parseSingleTemplate(obj));
            }
            return result;
        } else {
            // 单个 JSON 对象
            JSONObject obj = new JSONObject(trimmed);
            return List.of(parseSingleTemplate(obj));
        }
    }

    /**
     * 解析单个模板 JSON 对象（自动检测 Tiled 格式或紧凑格式）
     */
    private static RoomTemplate parseSingleTemplate(JSONObject obj) {
        // 判断格式: Tiled 格式有 "layers" 字段，紧凑格式有 "pattern" 字段
        if (obj.has("layers")) {
            return loadFromTiledJson(obj.toString());
        } else if (obj.has("pattern")) {
            return loadFromCompactJson(obj);
        } else if (obj.has("height") && obj.has("width")) {
            // 也可能是 Tiled 格式（无 layers 的空地图），尝试 Tiled
            return loadFromTiledJson(obj.toString());
        }
        throw new IllegalArgumentException(
            "Unknown room template JSON format. Need 'layers' (Tiled) or 'pattern' (compact). Object keys: " +
            obj.keySet());
    }

    // ============================================================
    //  加载 Tiled JSON
    // ============================================================

    /**
     * 从 Tiled JSON 字符串解析单个房间模板。
     * 适用于独立加载 Tiled 导出的单个地图文件。
     */
    public static RoomTemplate loadFromTiledJson(String jsonContent) {
        JSONObject root = new JSONObject(jsonContent);

        int mapWidth = root.getInt("width");
        int mapHeight = root.getInt("height");
        int tileW = root.optInt("tilewidth", 32);
        int tileH = root.optInt("tileheight", 32);

        int[][] groundData = null;
        int[][] wallData = null;
        List<DoorDef> doorList = new ArrayList<>();
        Point2D playerSpawn = null;
        List<Point2D> enemySpawns = new ArrayList<>();

        JSONArray layers = root.getJSONArray("layers");
        for (int i = 0; i < layers.length(); i++) {
            JSONObject layer = layers.getJSONObject(i);
            String layerType = layer.getString("type");
            String layerName = layer.optString("name", "");

            if ("tilelayer".equals(layerType)) {
                int[][] tileGrid = parseTileLayer(layer, mapWidth, mapHeight);
                switch (layerName) {
                    case "ground" -> groundData = tileGrid;
                    case "walls"  -> wallData = tileGrid;
                }
            } else if ("objectgroup".equals(layerType)) {
                JSONArray objects = layer.optJSONArray("objects");
                if (objects == null) continue;

                for (int j = 0; j < objects.length(); j++) {
                    JSONObject obj = objects.getJSONObject(j);
                    String objName = obj.optString("name", "");
                    String objType = obj.optString("type", obj.optString("class", ""));

                    switch (layerName) {
                        case "doors" -> {
                            if ("Door".equals(objName)) {
                                doorList.add(new DoorDef(
                                    objType,
                                    obj.getDouble("x"),
                                    obj.getDouble("y"),
                                    obj.optDouble("width", 32),
                                    obj.optDouble("height", 32)
                                ));
                            }
                        }
                        case "objects" -> {
                            double ox = obj.getDouble("x");
                            double oy = obj.getDouble("y");
                            if ("PlayerSpawn".equals(objName)) {
                                playerSpawn = new Point2D(ox, oy);
                            } else if ("EnemySpawn".equals(objName)) {
                                enemySpawns.add(new Point2D(ox, oy));
                            }
                        }
                    }
                }
            }
        }

        // 容错：若 ground 层缺失则自动补全（全地板）
        if (groundData == null) {
            groundData = new int[mapHeight][mapWidth];
            for (int r = 0; r < mapHeight; r++)
                Arrays.fill(groundData[r], GID_FLOOR);
        }
        // 容错：若 walls 层缺失则建空层
        if (wallData == null) {
            wallData = new int[mapHeight][mapWidth];
        }

        // 房间id 从文件名推断或使用 "room_unknown"
        String id = root.optString("id", "room_unknown");
        String name = root.optString("name", id);

        return new RoomTemplate(id, name, mapWidth, mapHeight,
            tileW, tileH, groundData, wallData,
            doorList, playerSpawn, enemySpawns);
    }

    /**
     * 从 assets 资源路径加载单个 Tiled JSON 文件。
     * 例如: {@code RoomTemplate.loadFromJsonFile("data/room_crossroad.json")}
     */
    public static RoomTemplate loadFromJsonFile(String resourcePath) {
        String content = readResource(resourcePath);
        if (content == null) {
            throw new RuntimeException("Cannot load resource: " + resourcePath);
        }
        return loadFromTiledJson(content);
    }


    // ============================================================
    //  加载 — 紧凑模式 JSON（带 pattern 字符网格）
    // ============================================================

    /**
     * 从紧凑格式 JSON 对象解析房间模板。
     * <p>
     * 紧凑格式约定：
     * <pre>{@code
     * {
     *   "id": "room_crossroad",
     *   "name": "十字路口",
     *   "widthTiles": 20,
     *   "heightTiles": 15,
     *   "tileWidth": 32,
     *   "tileHeight": 32,
     *   "pattern": [
     *     "WWWWWWWWW..WWWWWWWWW",   // 每行 widthTiles 个字符
     *     "....................",
     *     ...
     *   ],
     *   "doors": {
     *     "N": {"x": 288, "y": 0, "w": 64, "h": 32},
     *     "S": {"x": 288, "y": 448, "w": 64, "h": 32},
     *     "W": {"x": 0, "y": 224, "w": 32, "h": 64},
     *     "E": {"x": 608, "y": 224, "w": 32, "h": 64}
     *   },
     *   "playerSpawn": {"x": 320, "y": 256},
     *   "enemySpawns": [{"x": 96, "y": 96}, ...]
     * }
     * }</pre>
     * <p>
     * 字符约定：{@code W}=墙, {@code .}=地板, 其他=地板
     */
    public static RoomTemplate loadFromCompactJson(JSONObject obj) {
        int W = obj.getInt("widthTiles");
        int H = obj.getInt("heightTiles");
        int tileW = obj.optInt("tileWidth", 32);
        int tileH = obj.optInt("tileHeight", 32);
        String id = obj.getString("id");
        String name = obj.optString("name", id);

        // 解析 pattern
        JSONArray patternArr = obj.getJSONArray("pattern");
        int[][] ground = fillAll(H, W, GID_FLOOR);
        int[][] walls = new int[H][W];

        for (int r = 0; r < Math.min(H, patternArr.length()); r++) {
            String line = patternArr.getString(r);
            for (int c = 0; c < Math.min(W, line.length()); c++) {
                char ch = line.charAt(c);
                if (ch == 'W' || ch == 'w') {
                    walls[r][c] = GID_WALL;
                } else {
                    ground[r][c] = GID_FLOOR;
                    walls[r][c] = GID_EMPTY;
                }
            }
        }

        // 解析 doors
        List<DoorDef> doorList = new ArrayList<>();
        JSONObject doorsObj = obj.optJSONObject("doors");
        if (doorsObj != null) {
            for (String dir : doorsObj.keySet()) {
                JSONObject d = doorsObj.getJSONObject(dir);
                doorList.add(new DoorDef(
                    dir,
                    d.getDouble("x"),
                    d.getDouble("y"),
                    d.optDouble("w", tileW * 2),
                    d.optDouble("h", tileH)
                ));
            }
        }

        // 解析 spawns
        Point2D playerSpawn = null;
        JSONObject spawnObj = obj.optJSONObject("playerSpawn");
        if (spawnObj != null) {
            playerSpawn = new Point2D(spawnObj.getDouble("x"), spawnObj.getDouble("y"));
        }
        List<Point2D> enemySpawns = new ArrayList<>();
        JSONArray enemyArr = obj.optJSONArray("enemySpawns");
        if (enemyArr != null) {
            for (int i = 0; i < enemyArr.length(); i++) {
                JSONObject e = enemyArr.getJSONObject(i);
                enemySpawns.add(new Point2D(e.getDouble("x"), e.getDouble("y")));
            }
        }

        return new RoomTemplate(id, name, W, H, tileW, tileH,
            ground, walls, doorList, playerSpawn, enemySpawns);
    }

    /**
     * 从资源路径加载 JSON 文件（自动检测 Tiled / 紧凑 / 数组格式）。
     */
    public static List<RoomTemplate> loadAllFromJsonFile(String resourcePath) {
        String content = readResource(resourcePath);
        if (content == null) {
            throw new RuntimeException("Cannot load resource: " + resourcePath);
        }
        return loadAllFromJsonString(content);
    }

    // ============================================================
    //  内建默认模板（5 种房间，程序化生成）
    // ============================================================

    /**
     * 生成 5 种内建预设模板（程序化，无需外部 JSON 文件）。
     * 当资源文件不可用时作为回退。
     */
    public static List<RoomTemplate> buildDefaultTemplates() {
        return List.of(
            createCrossroad(),
            createCorridorH(),
            createCorridorV(),
            createLShape(),
            createHall()
        );
    }

    // ── 1. 十字路口 ──
    private static RoomTemplate createCrossroad() {
        int W = 20, H = 15, T = 32;
        int[][] ground = fillAll(H, W, GID_FLOOR);
        int[][] walls = new int[H][W];

        // 外框 + 十字走廊结构（墙在角落，走廊在十字方向上）
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                boolean inVerticalPassage   = (c >= 9 && c <= 10);
                boolean inHorizontalPassage = (r >= 7 && r <= 8);

                if (inVerticalPassage || inHorizontalPassage) {
                    walls[r][c] = GID_EMPTY; // 走廊区域
                } else if (r == 0 || r == H - 1 || c == 0 || c == W - 1) {
                    walls[r][c] = GID_WALL;  // 外框
                } else {
                    walls[r][c] = GID_WALL;  // 角落填充
                }
            }
        }

        List<DoorDef> doors = List.of(
            new DoorDef("N", 9 * T, 0,  2 * T, T),
            new DoorDef("S", 9 * T, 14 * T, 2 * T, T),
            new DoorDef("W", 0,  7 * T, T, 2 * T),
            new DoorDef("E", 19 * T, 7 * T, T, 2 * T)
        );
        carveDoorGaps(walls, doors, T);

        return new RoomTemplate("room_crossroad", "十字路口",
            W, H, T, T, ground, walls, doors,
            new Point2D(10 * T, 8 * T),
            List.of() /* 十字路口不刷怪 */);
    }

    // ── 2. 横走廊 ──
    private static RoomTemplate createCorridorH() {
        int W = 20, H = 15, T = 32;
        int[][] ground = fillAll(H, W, GID_FLOOR);
        int[][] walls = new int[H][W];

        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                if (r >= 6 && r <= 8) {
                    walls[r][c] = GID_EMPTY; // 水平走廊
                } else {
                    walls[r][c] = GID_WALL;
                }
            }
        }

        List<DoorDef> doors = List.of(
            new DoorDef("W", 0,  6 * T, T, 3 * T),
            new DoorDef("E", 19 * T, 6 * T, T, 3 * T)
        );
        carveDoorGaps(walls, doors, T);

        return new RoomTemplate("room_corridor_h", "横走廊",
            W, H, T, T, ground, walls, doors,
            new Point2D(10 * T, 7 * T),
            List.of() /* 横走廊不刷怪 */);
    }

    // ── 3. 竖走廊 ──
    private static RoomTemplate createCorridorV() {
        int W = 20, H = 15, T = 32;
        int[][] ground = fillAll(H, W, GID_FLOOR);
        int[][] walls = new int[H][W];

        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                if (c >= 9 && c <= 10) {
                    walls[r][c] = GID_EMPTY; // 垂直走廊
                } else {
                    walls[r][c] = GID_WALL;
                }
            }
        }

        List<DoorDef> doors = List.of(
            new DoorDef("N", 9 * T, 0,  2 * T, T),
            new DoorDef("S", 9 * T, 14 * T, 2 * T, T)
        );
        carveDoorGaps(walls, doors, T);

        return new RoomTemplate("room_corridor_v", "竖走廊",
            W, H, T, T, ground, walls, doors,
            new Point2D(10 * T, 7 * T),
            List.of() /* 竖走廊不刷怪 */);
    }

    // ── 4. L 型房间 ──
    private static RoomTemplate createLShape() {
        int W = 20, H = 15, T = 32;
        int[][] ground = fillAll(H, W, GID_FLOOR);
        int[][] walls = new int[H][W];

        // L 型：顶部水平区域 + 左侧垂直区域 = 开放空间
        // 右下角填充墙形成 L 的转角
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                // L 形开放区域：行 0-11 全开（除边界），列 12+ 在行 12+ 填充
                boolean inOpenV = (r <= 11);          // 上方的水平区域
                boolean inOpenH = (c <= 11);          // 左侧的垂直区域
                if (inOpenV || inOpenH) {
                    // 是开放区域，只有边界需要墙
                    if (r == 0 || r == H - 1 || c == 0 || c == W - 1) {
                        walls[r][c] = GID_WALL;
                    } else {
                        walls[r][c] = GID_EMPTY;
                    }
                } else {
                    walls[r][c] = GID_WALL;           // 右下角填充
                }
            }
        }
        // 开放区域内确保边界完整
        for (int c = 0; c < W; c++) { walls[0][c] = GID_WALL; walls[H - 1][c] = GID_WALL; }
        for (int r = 0; r < H; r++) { walls[r][0] = GID_WALL; walls[r][W - 1] = GID_WALL; }

        List<DoorDef> doors = List.of(
            new DoorDef("S", 9 * T, 14 * T, 2 * T, T),
            new DoorDef("E", 19 * T, 3 * T, T, 2 * T)
        );
        carveDoorGaps(walls, doors, T);

        return new RoomTemplate("room_lshape", "L型房间",
            W, H, T, T, ground, walls, doors,
            new Point2D(3 * T, 3 * T),
            List.of(new Point2D(6 * T, 3 * T), new Point2D(3 * T, 8 * T),
                    new Point2D(10 * T, 7 * T)));
    }

    // ── 5. 大厅 ──
    private static RoomTemplate createHall() {
        int W = 20, H = 15, T = 32;
        int[][] ground = fillAll(H, W, GID_FLOOR);
        int[][] walls = new int[H][W];

        // 只有外框有墙，内部全部开放
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                if (r == 0 || r == H - 1 || c == 0 || c == W - 1) {
                    walls[r][c] = GID_WALL;
                } else {
                    walls[r][c] = GID_EMPTY;
                }
            }
        }
        // 内部加 4 根柱子装饰
        int[][] pillars = {{4,4}, {4,15}, {10,4}, {10,15}};
        for (int[] p : pillars) {
            int pr = p[0], pc = p[1];
            if (pr > 0 && pr < H - 1 && pc > 0 && pc < W - 1) {
                walls[pr][pc] = GID_WALL;
            }
        }

        List<DoorDef> doors = List.of(
            new DoorDef("N", 9 * T, 0,  2 * T, T),
            new DoorDef("S", 9 * T, 14 * T, 2 * T, T),
            new DoorDef("W", 0,  6 * T, T, 3 * T),
            new DoorDef("E", 19 * T, 6 * T, T, 3 * T)
        );
        carveDoorGaps(walls, doors, T);

        return new RoomTemplate("room_hall", "大厅",
            W, H, T, T, ground, walls, doors,
            new Point2D(10 * T, 7 * T),
            List.of(new Point2D(4 * T, 4 * T), new Point2D(15 * T, 4 * T),
                    new Point2D(4 * T, 10 * T), new Point2D(15 * T, 10 * T),
                    new Point2D(10 * T, 7 * T)));
    }

    // ============================================================
    //  查询方法
    // ============================================================

    public String getId() { return id; }
    public String getName() { return name; }
    public int getWidthTiles() { return widthTiles; }
    public int getHeightTiles() { return heightTiles; }
    public int getTileWidth() { return tileWidth; }
    public int getTileHeight() { return tileHeight; }
    public int getWidthPixels() { return widthTiles * tileWidth; }
    public int getHeightPixels() { return heightTiles * tileHeight; }

    public int[][] getGroundLayer() { return groundLayer; }
    public int[][] getWallLayer() { return wallLayer; }

    public List<DoorDef> getDoors() { return doors; }
    public Point2D getPlayerSpawn() { return playerSpawn; }
    public List<Point2D> getEnemySpawns() { return enemySpawns; }

    /** 门在 tile 坐标中的中心位置 */
    public Point2D getDoorTileCenter(String direction) {
        for (DoorDef d : doors) {
            if (d.direction.equals(direction)) {
                return new Point2D(d.getTileCenterX(tileWidth), d.getTileCenterY(tileHeight));
            }
        }
        return null;
    }

    public boolean isWall(int tileX, int tileY) {
        if (tileX < 0 || tileX >= widthTiles || tileY < 0 || tileY >= heightTiles)
            return true; // 界外视为墙
        return wallLayer[tileY][tileX] == GID_WALL;
    }

    public boolean isFloor(int tileX, int tileY) {
        if (tileX < 0 || tileX >= widthTiles || tileY < 0 || tileY >= heightTiles)
            return false;
        return groundLayer[tileY][tileX] == GID_FLOOR && wallLayer[tileY][tileX] == GID_EMPTY;
    }

    public boolean isInBounds(int tileX, int tileY) {
        return tileX >= 0 && tileX < widthTiles && tileY >= 0 && tileY < heightTiles;
    }

    // ============================================================
    //  内部工具
    // ============================================================

    /**
     * 在 wall 层上为所有门位置开凿缺口，确保门区域没有墙阻挡。
     * 所有 createXxx() 方法必须在返回前调用此方法。
     */
    private static void carveDoorGaps(int[][] walls, List<DoorDef> doors, int tileSize) {
        for (DoorDef door : doors) {
            int startTileX = Math.max(0, (int) (door.x / tileSize));
            int startTileY = Math.max(0, (int) (door.y / tileSize));
            int endTileX = Math.min(walls[0].length - 1,
                (int) ((door.x + door.width - 1) / tileSize));
            int endTileY = Math.min(walls.length - 1,
                (int) ((door.y + door.height - 1) / tileSize));
            for (int r = startTileY; r <= endTileY; r++) {
                for (int c = startTileX; c <= endTileX; c++) {
                    walls[r][c] = GID_EMPTY;
                }
            }
        }
    }

    private static int[][] parseTileLayer(JSONObject layer, int mapW, int mapH) {
        // 检测不支持的编码格式（仅支持 CSV/JSON 数组）
        String encoding = layer.optString("encoding", "csv");
        if (!"csv".equals(encoding)) {
            throw new UnsupportedOperationException(
                "Tile layer uses unsupported encoding: " + encoding +
                ". Please export Tiled maps without compression/base64.");
        }
        String compression = layer.optString("compression", "");
        if (!compression.isEmpty()) {
            throw new UnsupportedOperationException(
                "Tile layer uses unsupported compression: " + compression +
                ". Please export Tiled maps without compression.");
        }

        JSONArray data = layer.getJSONArray("data");
        int[][] grid = new int[mapH][mapW];
        for (int i = 0; i < data.length() && i < mapW * mapH; i++) {
            int gid = data.getInt(i);
            // 清除 Tiled GID 高 4 位中的翻转/旋转标志
            // 0x80000000=水平翻转, 0x40000000=垂直翻转, 0x20000000=对角线翻转
            gid = gid & 0x0FFFFFFF;
            int row = i / mapW;
            int col = i % mapW;
            grid[row][col] = gid;
        }
        return grid;
    }

    private static int[][] fillAll(int rows, int cols, int value) {
        int[][] grid = new int[rows][cols];
        for (int[] row : grid) Arrays.fill(row, value);
        return grid;
    }

    private static String readResource(String path) {
        // 按优先级尝试多个路径：直接 classpath → classloader → assets 回退
        String directPath = path.startsWith("/") ? path : "/" + path;
        InputStream is = RoomTemplate.class.getResourceAsStream(directPath);
        if (is == null) {
            is = RoomTemplate.class.getClassLoader().getResourceAsStream(path);
        }
        if (is == null) {
            is = RoomTemplate.class.getResourceAsStream("/assets/" + path);
        }
        if (is == null) return null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return br.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return null;
        }
    }
}
