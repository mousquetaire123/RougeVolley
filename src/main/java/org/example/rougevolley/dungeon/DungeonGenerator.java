package org.example.rougevolley.dungeon;

import org.example.rougevolley.config.GameConfig;

import java.util.*;

/**
 * 简单网格地牢生成器 —— 在 N×M 网格中放置房间并连接相邻房间的门。
 * <p>
 * 布局：{@link GameConfig#DUNGEON_ROOM_COUNT} 个房间按 3×2 网格排列，
 * 每个房间从模板池中随机选取。
 * <p>
 * 连接规则：仅当两个相邻房间的模板都定义了对应方向的门时才连接。
 * 起始房间 (0,0) 强制使用十字路口或大厅模板（全方向有门），
 * 若起始房间无法连接到任何邻居则重新洗牌（最多 10 次）。
 */
public class DungeonGenerator {

    private static final int MAX_RETRIES = 10;

    private final RoomPool roomPool;
    private final Random rng;

    public DungeonGenerator(RoomPool roomPool, Random rng) {
        this.roomPool = Objects.requireNonNull(roomPool);
        this.rng = Objects.requireNonNull(rng);
    }

    /**
     * 生成由 {@link GameConfig#DUNGEON_ROOM_COUNT} 个房间组成的连通地牢。
     * <p>
     * 使用 3×2 网格布局。仅连接模板双方都定义了对应方向的门。
     * 起始房间始终为全方向连通（十字路口或大厅），确保玩家总有一条出路。
     *
     * @return 按行优先顺序排列的房间列表
     */
    public List<Room> generate() {
        int cols = 3;
        int rows = 2;

        // 获取起始房间模板：优先十字路口，其次大厅
        RoomTemplate startTemplate = roomPool.getById("room_crossroad")
            .or(() -> roomPool.getById("room_hall"))
            .orElse(null);

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            Room[][] grid = new Room[rows][cols];
            List<Room> rooms = new ArrayList<>();

            // 1. 洗牌后轮询选取模板
            List<RoomTemplate> shuffled = new ArrayList<>(roomPool.getAllTemplates());
            Collections.shuffle(shuffled, rng);
            int templateIdx = 0;

            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    RoomTemplate template;
                    if (r == 0 && c == 0 && startTemplate != null) {
                        // 起始房间强制使用全方向连通模板
                        template = startTemplate;
                        // 将该模板从 shuffled 中移除（避免重复占位）
                        shuffled.remove(template);
                    } else {
                        template = shuffled.get(templateIdx % shuffled.size());
                        templateIdx++;
                    }
                    Room room = new Room(template, c, r);
                    grid[r][c] = room;
                    rooms.add(room);
                }
            }

            // 2. 连接相邻房间的门（仅当双方模板都定义了对应方向的门）
            connectDoors(grid, rows, cols);

            // 3. 验证起始房间至少有一个可通行的门
            Room startRoom = grid[0][0];
            boolean hasExit = false;
            for (String dir : startRoom.getDoorDirections()) {
                if (startRoom.canPassThrough(dir)) {
                    hasExit = true;
                    break;
                }
            }

            if (hasExit) {
                return rooms;
            }
            // 起始房间无出口 → 重新洗牌再试
        }

        // 所有重试都失败（极端罕见）：回退到强制全连接
        return generateFallback(cols, rows, startTemplate);
    }

    /**
     * 连接相邻房间的门（仅当双方模板都定义了对应方向的门时才连接）。
     */
    private void connectDoors(Room[][] grid, int rows, int cols) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Room room = grid[r][c];
                // 右边邻居 → E ↔ W
                if (c + 1 < cols) {
                    Room right = grid[r][c + 1];
                    if (room.getDoorDirections().contains("E") && right.getDoorDirections().contains("W")) {
                        room.setDoorConnected("E", true);
                        right.setDoorConnected("W", true);
                    }
                }
                // 下方邻居 → S ↔ N
                if (r + 1 < rows) {
                    Room down = grid[r + 1][c];
                    if (room.getDoorDirections().contains("S") && down.getDoorDirections().contains("N")) {
                        room.setDoorConnected("S", true);
                        down.setDoorConnected("N", true);
                    }
                }
            }
        }
    }

    /**
     * 极端回退：生成一个全连接的网格（所有相邻房间强制互通）。
     * 仅在所有模板门匹配重试都失败时使用。
     */
    private List<Room> generateFallback(int cols, int rows, RoomTemplate startTemplate) {
        Room[][] grid = new Room[rows][cols];
        List<Room> rooms = new ArrayList<>();
        List<RoomTemplate> all = new ArrayList<>(roomPool.getAllTemplates());

        int idx = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                RoomTemplate template;
                if (r == 0 && c == 0 && startTemplate != null) {
                    template = startTemplate;
                } else {
                    template = all.get(idx % all.size());
                    idx++;
                }
                Room room = new Room(template, c, r);
                grid[r][c] = room;
                rooms.add(room);
            }
        }

        // 全连接（原始行为：所有相邻房间都互通）
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Room room = grid[r][c];
                if (c + 1 < cols) {
                    Room right = grid[r][c + 1];
                    room.ensureDoor("E");
                    right.ensureDoor("W");
                    room.setDoorConnected("E", true);
                    right.setDoorConnected("W", true);
                }
                if (r + 1 < rows) {
                    Room down = grid[r + 1][c];
                    room.ensureDoor("S");
                    down.ensureDoor("N");
                    room.setDoorConnected("S", true);
                    down.setDoorConnected("N", true);
                }
            }
        }

        return rooms;
    }
}
