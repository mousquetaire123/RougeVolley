package org.example.rougevolley.dungeon;

import javafx.geometry.Point2D;
import org.example.rougevolley.config.GameConfig;

import java.util.*;

/**
 * 地牢生成器 —— 基于迷宫行走器算法的房间拼接。
 * <p>
 * 从起点 (0,0) 开始放置一个随机房间，然后逐步扩展：
 * 从已有房间的未连接门出发，尝试放置邻接房间并连接门。
 * 门中心对齐后才允许连接（N↔S 列对齐，W↔E 行对齐）。
 * <p>
 * 返回一个 <b>连通</b> 的地牢网格，若无法达到目标房间数，
 * 会回溯重试多次，保底返回 ≥1 个房间。
 */
public class DungeonGenerator {

    /** 完整生成流程的最大重试次数 */
    private static final int MAX_GENERATE_ATTEMPTS = 50;

    /** 单次生成中放置房间的最大尝试次数 */
    private static final int MAX_PLACE_ATTEMPTS = 200;

    /** 目标房间数 */
    private final int targetCount;

    private final RoomPool pool;
    private final Random rng;

    // 生成结果
    private Map<java.awt.Point, Room> grid;
    private Room startRoom;
    private Map<String, List<Room>> neighborMap;

    // ── 方向常量 ──
    private static final String N = "N", S = "S", E = "E", W = "W";

    // ============================================================
    //  构造
    // ============================================================

    public DungeonGenerator(RoomPool pool, long seed) {
        this(pool, new Random(seed));
    }

    public DungeonGenerator(RoomPool pool, Random rng) {
        this.pool = Objects.requireNonNull(pool, "RoomPool must not be null");
        this.rng = Objects.requireNonNull(rng, "Random must not be null");
        this.targetCount = GameConfig.DUNGEON_ROOM_COUNT;
        this.grid = new HashMap<>();
        this.neighborMap = new HashMap<>();
    }

    // ============================================================
    //  生成入口
    // ============================================================

    /**
     * 执行地牢生成算法。
     *
     * @return 所有已放置的房间（不可变 Map，key = grid 坐标）
     */
    public Map<java.awt.Point, Room> generate() {
        Map<java.awt.Point, Room> bestGrid = null;
        int bestSize = 0;

        for (int attempt = 0; attempt < MAX_GENERATE_ATTEMPTS; attempt++) {
            Map<java.awt.Point, Room> result = tryGenerate();

            if (result.size() > bestSize) {
                bestSize = result.size();
                bestGrid = new HashMap<>(result);
            }

            if (result.size() >= targetCount) {
                break; // 达到目标
            }
        }

        // 构建最终结果
        this.grid = bestGrid != null ? bestGrid : Collections.emptyMap();
        this.startRoom = findStartRoom();
        this.neighborMap = buildNeighborMap();

        return Collections.unmodifiableMap(this.grid);
    }

    /**
     * 单次生成尝试（可能达不到目标房间数）
     */
    private Map<java.awt.Point, Room> tryGenerate() {
        Map<java.awt.Point, Room> localGrid = new HashMap<>();

        // 1. 选起始模板
        RoomTemplate startTemplate = pool.getRandom();
        Room startRoom = new Room(startTemplate, 0, 0);
        localGrid.put(pt(0, 0), startRoom);

        // 2. 逐步扩展
        int placed = 1;
        int attempts = 0;

        while (placed < targetCount && attempts < MAX_PLACE_ATTEMPTS) {
            attempts++;

            // 收集可扩展的 (room, direction) 组合
            List<ExpansionCandidate> candidates = collectCandidates(localGrid);
            if (candidates.isEmpty()) {
                break; // 真·死胡同
            }

            // 先尝试严格模式（门中心对齐），全部失败后降级宽松模式
            boolean expanded = false;
            for (boolean isRelaxed : new boolean[]{false, true}) {
                if (expanded) break;

                // 每轮重新打乱以保证随机性
                Collections.shuffle(candidates, rng);

                for (ExpansionCandidate cand : candidates) {
                    java.awt.Point adjPt = adjacentPoint(cand.room, cand.direction);
                    if (localGrid.containsKey(adjPt)) continue;

                    RoomTemplate template = findMatchingTemplate(
                        cand.room, cand.direction, isRelaxed);
                    if (template == null) continue;

                    // 放置新房间
                    Room newRoom = new Room(template, adjPt.x, adjPt.y);
                    localGrid.put(adjPt, newRoom);

                    // 连接门
                    String oppDir = oppositeDir(cand.direction);
                    cand.room.setDoorConnected(cand.direction, true);
                    newRoom.setDoorConnected(oppDir, true);

                    placed++;
                    expanded = true;
                    break;
                }
            }

            if (!expanded) {
                // 所有候选都无法扩展 → 放弃本次生成
                break;
            }
        }

        return localGrid;
    }

    // ============================================================
    //  候选收集
    // ============================================================

    /** 扩展候选：(room, 未连接的门方向) */
    private record ExpansionCandidate(Room room, String direction) {}

    /**
     * 收集所有拥有未连接门的房间作为扩展候选。
     */
    private List<ExpansionCandidate> collectCandidates(
            Map<java.awt.Point, Room> localGrid) {

        List<ExpansionCandidate> candidates = new ArrayList<>();

        for (Room room : localGrid.values()) {
            for (String dir : room.getDoorDirections()) {
                if (!room.isDoorConnected(dir)) {
                    candidates.add(new ExpansionCandidate(room, dir));
                }
            }
        }

        return candidates;
    }

    // ============================================================
    //  门对齐匹配
    // ============================================================

    /**
     * 找一个能与 fromRoom 的 direction 门对齐连接的模板。
     */
    private RoomTemplate findMatchingTemplate(Room fromRoom, String direction, boolean relaxed) {
        String oppDir = oppositeDir(direction);

        List<RoomTemplate> allTemplates = pool.getAllTemplates();
        // 随机打乱以增加多样性
        List<RoomTemplate> shuffled = new ArrayList<>(allTemplates);
        Collections.shuffle(shuffled, rng);

        RoomTemplate fromTemplate = fromRoom.getTemplate();

        for (RoomTemplate candidate : shuffled) {
            // 避免相同模板紧邻（增加视觉多样性），但 LShape 特殊处理以防只剩它可选
            if (candidate.getId().equals(fromTemplate.getId())
                && allTemplates.size() > 1) {
                continue;
            }

            // 检查候选模板是否有对立方向的门
            Point2D candDoor = candidate.getDoorTileCenter(oppDir);
            if (candDoor == null) continue;

            // 从模板必须有这个方向的门
            Point2D fromDoor = fromTemplate.getDoorTileCenter(direction);
            if (fromDoor == null) continue;

            // 对齐检查（宽松模式跳过）
            if (!relaxed && !doorsAlign(fromDoor, candDoor, direction)) {
                continue;
            }

            return candidate;
        }

        return null;
    }

    /**
     * 判定两个门是否对齐:
     * - N↔S: 比较 tile 列中心 (X 坐标)
     * - W↔E: 比较 tile 行中心 (Y 坐标)
     */
    private boolean doorsAlign(Point2D fromDoor, Point2D candDoor, String direction) {
        if (direction.equals(N) || direction.equals(S)) {
            // N↔S: 列对齐
            return Math.abs(fromDoor.getX() - candDoor.getX()) < 0.1;
        } else {
            // W↔E: 行对齐
            return Math.abs(fromDoor.getY() - candDoor.getY()) < 0.1;
        }
    }

    // ============================================================
    //  方向工具
    // ============================================================

    /**
     * 返回对立方向: N↔S, E↔W.
     */
    private static String oppositeDir(String dir) {
        return switch (dir) {
            case N -> S;
            case S -> N;
            case E -> W;
            case W -> E;
            default -> throw new IllegalArgumentException("Invalid direction: " + dir);
        };
    }

    /**
     * 计算房间在指定方向上的相邻网格坐标。
     */
    private static java.awt.Point adjacentPoint(Room room, String dir) {
        return switch (dir) {
            case N -> pt(room.getGridX(),     room.getGridY() - 1);
            case S -> pt(room.getGridX(),     room.getGridY() + 1);
            case W -> pt(room.getGridX() - 1, room.getGridY());
            case E -> pt(room.getGridX() + 1, room.getGridY());
            default -> throw new IllegalArgumentException("Invalid direction: " + dir);
        };
    }

    private static java.awt.Point pt(int x, int y) {
        return new java.awt.Point(x, y);
    }

    // ============================================================
    //  查询方法
    // ============================================================

    /**
     * 获取起始房间（第一个放置的房间）。
     */
    public Room getStartRoom() {
        return startRoom;
    }

    /**
     * 获取所有房间的不可变列表。
     */
    public List<Room> getRooms() {
        return List.copyOf(grid.values());
    }

    /**
     * 获取地牢网格的不可变视图。
     */
    public Map<java.awt.Point, Room> getGrid() {
        return Collections.unmodifiableMap(grid);
    }

    /**
     * 获取邻居关系: Room ID → 相邻 Room 列表。
     */
    public Map<String, List<Room>> getNeighborMap() {
        return neighborMap;
    }

    // ============================================================
    //  内部构建
    // ============================================================

    /**
     * 找到网格中的起始房间（gridX=0, gridY=0 的房间）。
     */
    private Room findStartRoom() {
        return grid.get(pt(0, 0));
    }

    /**
     * 从门连接状态构建邻居关系映射。
     */
    private Map<String, List<Room>> buildNeighborMap() {
        Map<String, List<Room>> result = new HashMap<>();
        Map<java.awt.Point, Room> gridCopy = new HashMap<>(grid);

        for (Room room : gridCopy.values()) {
            List<Room> neighbors = new ArrayList<>();

            for (String dir : room.getDoorDirections()) {
                if (!room.isDoorConnected(dir)) continue;

                java.awt.Point adjPt = adjacentPoint(room, dir);
                Room neighbor = gridCopy.get(adjPt);
                if (neighbor != null) {
                    neighbors.add(neighbor);
                }
            }

            result.put(room.getId(), Collections.unmodifiableList(neighbors));
        }

        return Collections.unmodifiableMap(result);
    }

    // ============================================================
    //  调试/验证
    // ============================================================

    /**
     * 打印地牢拓扑到控制台。
     */
    public void printDebugDungeon() {
        System.out.println("═══ 地牢拓扑 ═══");
        System.out.println("房间数: " + grid.size() + "/" + targetCount);
        System.out.println();

        // 收集所有用的 grid 坐标
        Set<java.awt.Point> allPts = new HashSet<>(grid.keySet());
        int minX = allPts.stream().mapToInt(p -> p.x).min().orElse(0);
        int maxX = allPts.stream().mapToInt(p -> p.x).max().orElse(0);
        int minY = allPts.stream().mapToInt(p -> p.y).min().orElse(0);
        int maxY = allPts.stream().mapToInt(p -> p.y).max().orElse(0);

        // 网格地图
        System.out.println("网格布局 (X→East, Y→South):");
        for (int y = minY; y <= maxY; y++) {
            StringBuilder line = new StringBuilder();
            for (int x = minX; x <= maxX; x++) {
                java.awt.Point p = pt(x, y);
                Room r = grid.get(p);
                if (r != null) {
                    String abbr = switch (r.getTemplate().getId()) {
                        case "room_crossroad" -> "⛩";
                        case "room_corridor_h" -> "═";
                        case "room_corridor_v" -> "║";
                        case "room_lshape" -> "┘";
                        case "room_hall" -> "▣";
                        default -> "□";
                    };
                    line.append(abbr);
                } else {
                    line.append("·");
                }
            }
            System.out.println("  " + line);
        }
        System.out.println();

        // 房间详情
        for (Room room : grid.values()) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("  %s @(%d,%d)",
                room.getTemplate().getName(),
                room.getGridX(), room.getGridY()));

            // 门状态
            List<String> connected = new ArrayList<>();
            List<String> available = new ArrayList<>();
            for (String dir : room.getDoorDirections()) {
                if (room.isDoorConnected(dir)) {
                    connected.add(dir);
                } else {
                    available.add(dir);
                }
            }
            if (!connected.isEmpty()) {
                sb.append(" 连接:").append(String.join(",", connected));
            }
            if (!available.isEmpty()) {
                sb.append(" 空闲:").append(String.join(",", available));
            }
            System.out.println(sb.toString());
        }
        System.out.println();

        // 连通性验证
        System.out.println("连通性: " + (isFullyConnected() ? "✅ 全部可达" : "⚠️ 存在孤立房间"));
        System.out.println("══════════════");
    }

    /**
     * 验证所有房间是否从起点可达（BFS）。
     */
    public boolean isFullyConnected() {
        if (grid.isEmpty() || startRoom == null) return false;

        Set<String> visited = new HashSet<>();
        Queue<Room> queue = new LinkedList<>();
        queue.add(startRoom);
        visited.add(startRoom.getId());

        while (!queue.isEmpty()) {
            Room current = queue.poll();
            List<Room> neighbors = neighborMap.getOrDefault(current.getId(), List.of());
            for (Room n : neighbors) {
                if (!visited.contains(n.getId())) {
                    visited.add(n.getId());
                    queue.add(n);
                }
            }
        }

        return visited.size() == grid.size();
    }
}
