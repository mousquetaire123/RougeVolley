package org.example.rougevolley.dungeon;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.core.math.Vec2;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.example.rougevolley.config.GameConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 独立 Tile 渲染器 —— 为房间创建/管理 JavaFX Rectangle 节点。
 * <p>
 * 职责：
 * - 根据 Room 的模板数据创建所有 tile 的 Rectangle 节点
 * - 每帧根据视口偏移同步屏幕坐标
 * - 支持房间切换时的显示/隐藏
 * <p>
 * 不修改 {@code RougeVolleyFXGL} 的现有渲染逻辑，
 * 独立管理自己的 {@code tileNodes}。
 * 集成时由主循环每帧调用 {@link #sync(Vec2)}。
 */
public class TileRenderer {

    /** 每个 tile 节点及其对应的世界坐标配对 */
    private static class TileEntry {
        final Node node;
        final double worldX;
        final double worldY;

        TileEntry(Node node, double worldX, double worldY) {
            this.node = node;
            this.worldX = worldX;
            this.worldY = worldY;
        }
    }

    private final List<TileEntry> tileEntries = new ArrayList<>();
    private Room currentRoom;
    private boolean visible = true;

    // 调色板（与现有项目风格一致）
    private static final Color COLOR_FLOOR   = Color.rgb(48, 48, 58);
    private static final Color COLOR_WALL    = Color.rgb(70, 70, 85);
    private static final Color COLOR_WALL_BORDER = Color.rgb(55, 55, 70);
    private static final Color COLOR_DOOR    = Color.rgb(90, 80, 60);
    private static final double WALL_STROKE = 1.0;

    // ============================================================
    //  构建
    // ============================================================

    /**
     * 为一个 Room 构建所有 tile 的 JavaFX 渲染节点。
     * 调用此方法会清除之前的房间节点。
     *
     * @param room 要渲染的房间（不能为 null）
     */
    public void buildForRoom(Room room) {
        Objects.requireNonNull(room, "Room must not be null");
        clear(); // 先清除旧的

        this.currentRoom = room;
        RoomTemplate template = room.getTemplate();
        int T = GameConfig.TILE_SIZE;
        double baseX = room.getWorldX();
        double baseY = room.getWorldY();

        int[][] ground = template.getGroundLayer();
        int[][] walls  = template.getWallLayer();

        for (int row = 0; row < template.getHeightTiles(); row++) {
            for (int col = 0; col < template.getWidthTiles(); col++) {
                double tileWorldX = baseX + col * T;
                double tileWorldY = baseY + row * T;

                // --- 地板 ---
                if (ground[row][col] == RoomTemplate.GID_FLOOR) {
                    Rectangle floor = new Rectangle(T, T, COLOR_FLOOR);
                    floor.setStroke(Color.rgb(38, 38, 48));
                    floor.setStrokeWidth(0.5);
                    addTile(floor, tileWorldX, tileWorldY);
                }

                // --- 墙壁 ---
                if (walls[row][col] == RoomTemplate.GID_WALL) {
                    Rectangle wall = new Rectangle(T, T, COLOR_WALL);
                    wall.setStroke(COLOR_WALL_BORDER);
                    wall.setStrokeWidth(WALL_STROKE);
                    addTile(wall, tileWorldX, tileWorldY);
                }
            }
        }

        // --- 门标记（半透明色块） ---
        for (RoomTemplate.DoorDef door : template.getDoors()) {
            double doorWorldX = baseX + door.x;
            double doorWorldY = baseY + door.y;
            Rectangle doorRect = new Rectangle(door.width, door.height, COLOR_DOOR);
            doorRect.setOpacity(0.45);
            doorRect.setStroke(Color.rgb(120, 100, 70));
            doorRect.setStrokeWidth(0.5);
            addTile(doorRect, doorWorldX, doorWorldY);
        }

        // 所有节点默认隐藏，由 sync() 控制可见性
        setVisible(visible);
    }

    /**
     * 添加单个 tile 节点并加入场景。
     */
    private void addTile(Node node, double worldX, double worldY) {
        // 加入 FXGL 场景（UI 层，与现有渲染方式一致）
        FXGL.getGameScene().addUINode(node);
        tileEntries.add(new TileEntry(node, worldX, worldY));
    }

    // ============================================================
    //  每帧同步
    // ============================================================

    /**
     * 根据当前视口偏移同步所有 tile 的屏幕坐标。
     * 应在每帧主循环末尾调用。
     * <p>
     * 调用方式（由集成方负责）：
     * {@code renderer.sync(FXGL.getGameScene().getViewport().getXY());}
     *
     * @param viewportOffset 视口左上角世界坐标（FXGL: getViewport().getXY()）
     */
    public void sync(Vec2 viewportOffset) {
        double camX = viewportOffset.x;
        double camY = viewportOffset.y;

        for (TileEntry entry : tileEntries) {
            double screenX = entry.worldX - camX;
            double screenY = entry.worldY - camY;

            entry.node.setTranslateX(screenX);
            entry.node.setTranslateY(screenY);

            // 视锥剔除
            boolean inView =
                screenX > GameConfig.CULL_MARGIN_NEG
                && screenX < GameConfig.VIEWPORT_WIDTH - GameConfig.CULL_MARGIN_NEG
                && screenY > GameConfig.CULL_MARGIN_NEG
                && screenY < GameConfig.VIEWPORT_HEIGHT - GameConfig.CULL_MARGIN_NEG;

            entry.node.setVisible(visible && inView);
        }
    }

    // ============================================================
    //  控制
    // ============================================================

    /**
     * 清除当前所有 tile 节点（从场景移除）。
     */
    public void clear() {
        for (TileEntry entry : tileEntries) {
            FXGL.getGameScene().removeUINode(entry.node);
        }
        tileEntries.clear();
        currentRoom = null;
    }

    /**
     * 显示或隐藏所有 tile。
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
        for (TileEntry entry : tileEntries) {
            entry.node.setVisible(visible);
        }
    }

    /**
     * 获取当前渲染的房间。
     */
    public Room getCurrentRoom() {
        return currentRoom;
    }

    /**
     * 是否包含任何 tile 节点。
     */
    public boolean hasRoom() {
        return currentRoom != null && !tileEntries.isEmpty();
    }

    /**
     * 当前 tile 节点数量。
     */
    public int getTileCount() {
        return tileEntries.size();
    }
}
