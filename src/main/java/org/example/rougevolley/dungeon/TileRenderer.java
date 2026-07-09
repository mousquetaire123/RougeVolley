package org.example.rougevolley.dungeon;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.core.math.Vec2;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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

    /** 门纹理（16×16→32×32，延迟加载） */
    private static Image doorTexture;

    /** 地板纹理（16×16→32×32，延迟加载） */
    private static Image floorTexture;

    /** 墙壁纹理（16×16→32×32，延迟加载） */
    private static Image wallTexture;

    // ── 调色板（高对比度地牢风格） ──
    private static final Color COLOR_FLOOR        = Color.rgb(105, 95, 82);
    private static final Color COLOR_WALL         = Color.rgb(38, 35, 42);
    private static final Color COLOR_WALL_BORDER  = Color.rgb(22, 20, 28);
    private static final Color COLOR_DOOR         = Color.rgb(175, 95, 55);
    private static final Color COLOR_FLOOR_STROKE = Color.rgb(88, 80, 70);
    private static final Color COLOR_DOOR_STROKE  = Color.rgb(210, 130, 80);
    private static final double WALL_STROKE = 2.0;

    // ── 各房间类型地板色相偏移（增强房间辨识度） ──
    private static final Color TINT_CROSSROAD   = Color.rgb(105, 95, 82);   // 默认暖石色
    private static final Color TINT_CORRIDOR_H  = Color.rgb(95, 97, 105);   // 偏蓝灰
    private static final Color TINT_CORRIDOR_V  = Color.rgb(108, 88, 78);   // 偏红棕
    private static final Color TINT_LSHAPE      = Color.rgb(88, 100, 85);   // 偏绿灰
    private static final Color TINT_HALL        = Color.rgb(115, 108, 95);  // 偏亮金

    // ============================================================
    //  构建
    // ============================================================

    /** 根据模板 ID 返回对应的地板颜色，增强不同房间类型的辨识度 */
    private static Color getFloorColorFor(String templateId) {
        if (templateId == null) return COLOR_FLOOR;
        return switch (templateId) {
            case "room_corridor_h" -> TINT_CORRIDOR_H;
            case "room_corridor_v" -> TINT_CORRIDOR_V;
            case "room_lshape"     -> TINT_LSHAPE;
            case "room_hall"       -> TINT_HALL;
            default                -> TINT_CROSSROAD; // room_crossroad 及其他
        };
    }

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

        // 根据房间模板选择地板颜色，增强不同房间类型的辨识度
        Color floorColor = getFloorColorFor(template.getId());

        for (int row = 0; row < template.getHeightTiles(); row++) {
            for (int col = 0; col < template.getWidthTiles(); col++) {
                double tileWorldX = baseX + col * T;
                double tileWorldY = baseY + row * T;

                // --- 地板 ---
                if (ground[row][col] == RoomTemplate.GID_FLOOR) {
                    if (floorTexture == null) {
                        var resourceUrl = TileRenderer.class.getResource("/assets/textures/floor.png");
                        if (resourceUrl != null) {
                            floorTexture = new Image(resourceUrl.toExternalForm());
                        }
                    }
                    Node floorNode;
                    if (floorTexture != null && !floorTexture.isError()) {
                        ImageView floorView = new ImageView(floorTexture);
                        floorView.setFitWidth(T);
                        floorView.setFitHeight(T);
                        floorView.setSmooth(false);
                        floorNode = floorView;
                    } else {
                        Rectangle floor = new Rectangle(T, T, floorColor);
                        floor.setStroke(COLOR_FLOOR_STROKE);
                        floor.setStrokeWidth(0.5);
                        floorNode = floor;
                    }
                    addTile(floorNode, tileWorldX, tileWorldY);
                }

                // --- 墙壁 ---
                if (walls[row][col] == RoomTemplate.GID_WALL) {
                    if (wallTexture == null) {
                        var resourceUrl = TileRenderer.class.getResource("/assets/textures/wall.png");
                        if (resourceUrl != null) {
                            wallTexture = new Image(resourceUrl.toExternalForm());
                        }
                    }
                    Node wallNode;
                    if (wallTexture != null && !wallTexture.isError()) {
                        ImageView wallView = new ImageView(wallTexture);
                        wallView.setFitWidth(T);
                        wallView.setFitHeight(T);
                        wallView.setSmooth(false);
                        wallNode = wallView;
                    } else {
                        Rectangle wall = new Rectangle(T, T, COLOR_WALL);
                        wall.setStroke(COLOR_WALL_BORDER);
                        wall.setStrokeWidth(WALL_STROKE);
                        wallNode = wall;
                    }
                    addTile(wallNode, tileWorldX, tileWorldY);
                }
            }
        }

        // --- 门（使用精灵纹理） ---
        if (doorTexture == null) {
            var resourceUrl = TileRenderer.class.getResource("/assets/textures/door.png");
            if (resourceUrl != null) {
                doorTexture = new Image(resourceUrl.toExternalForm());
            }
        }
        for (RoomTemplate.DoorDef door : template.getDoors()) {
            double doorWorldX = baseX + door.x;
            double doorWorldY = baseY + door.y;
            Node doorNode;
            if (doorTexture != null && !doorTexture.isError()) {
                ImageView doorView = new ImageView(doorTexture);
                doorView.setFitWidth(door.width);
                doorView.setFitHeight(door.height);
                doorView.setSmooth(false);
                doorView.setOpacity(0.9);
                doorNode = doorView;
            } else {
                Rectangle doorRect = new Rectangle(door.width, door.height, COLOR_DOOR);
                doorRect.setOpacity(0.75);
                doorRect.setStroke(COLOR_DOOR_STROKE);
                doorRect.setStrokeWidth(2.5);
                doorRect.setArcWidth(4);
                doorRect.setArcHeight(4);
                doorNode = doorRect;
            }
            addTile(doorNode, doorWorldX, doorWorldY);
        }

        // 所有节点默认隐藏，由 sync() 控制可见性
        setVisible(visible);
    }

    /**
     * 添加单个 tile 节点并加入场景。
     */
    private void addTile(Node node, double worldX, double worldY) {
        // 加入 FXGL 场景（UI 层）。添加顺序决定 z-order：地板→墙壁→门→实体
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
