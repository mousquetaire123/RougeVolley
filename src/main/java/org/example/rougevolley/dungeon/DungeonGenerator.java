package org.example.rougevolley.dungeon;

import org.example.rougevolley.config.GameConfig;

import java.util.*;

/**
 * 简单网格地牢生成器 —— 在 N×M 网格中放置房间并连接相邻房间的门。
 * <p>
 * 布局：{@link GameConfig#DUNGEON_ROOM_COUNT} 个房间按 3×2 网格排列，
 * 每个房间从模板池中随机选取，相邻房间自动连接对应方向的门。
 * <p>
 * 世界坐标计算：由于模板尺寸可能不同，生成器按最大行高/列宽统一对齐，
 * 确保房间之间无间隙或重叠。
 */
public class DungeonGenerator {

    private final RoomPool roomPool;
    private final Random rng;

    public DungeonGenerator(RoomPool roomPool, Random rng) {
        this.roomPool = Objects.requireNonNull(roomPool);
        this.rng = Objects.requireNonNull(rng);
    }

    /**
     * 生成由 {@link GameConfig#DUNGEON_ROOM_COUNT} 个房间组成的连通地牢。
     * <p>
     * 使用 3×2 网格布局，相邻房间自动连接。
     *
     * @return 按行优先顺序排列的房间列表
     */
    public List<Room> generate() {
        int count = GameConfig.DUNGEON_ROOM_COUNT; // 6
        int cols = 3;
        int rows = 2;

        Room[][] grid = new Room[rows][cols];
        List<Room> rooms = new ArrayList<>();

        // 1. 为每个网格位置随机选取模板并创建 Room
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                RoomTemplate template = roomPool.getRandom();
                Room room = new Room(template, c, r);
                grid[r][c] = room;
                rooms.add(room);
            }
        }

        // 2. 连接相邻房间的门
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Room room = grid[r][c];
                // 右边邻居 → E ↔ W
                if (c + 1 < cols) {
                    Room right = grid[r][c + 1];
                    room.setDoorConnected("E", true);
                    right.setDoorConnected("W", true);
                }
                // 下方邻居 → S ↔ N
                if (r + 1 < rows) {
                    Room down = grid[r + 1][c];
                    room.setDoorConnected("S", true);
                    down.setDoorConnected("N", true);
                }
            }
        }

        return rooms;
    }
}
