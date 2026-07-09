package org.example.rougevolley;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.input.Input;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.logging.Logger;
import javafx.event.Event;
import javafx.geometry.Point2D;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import org.example.rougevolley.config.GameConfig;
import org.example.rougevolley.core.GameEvent;
import org.example.rougevolley.core.GameState;
import org.example.rougevolley.dungeon.Room;
import org.example.rougevolley.dungeon.RoomPool;
import org.example.rougevolley.dungeon.RoomTemplate;
import org.example.rougevolley.dungeon.TileRenderer;
import org.example.rougevolley.ecs.Entity;
import org.example.rougevolley.ecs.EntityType;
import org.example.rougevolley.ecs.components.*;
import org.example.rougevolley.combat.DamageSystem;
import org.example.rougevolley.combat.WeaponSystem;
import org.example.rougevolley.dungeon.DungeonGenerator;
import org.example.rougevolley.entity.EntityFactory;
import org.example.rougevolley.roguelike.UpgradeManager;
import org.example.rougevolley.ui.GameUI;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RougeVolley — FXGL 地牢探险游戏入口
 */
public class RougeVolleyFXGL extends GameApplication {

    private static final Logger log = Logger.get(RougeVolleyFXGL.class);

    private GameState gameState;
    private UpgradeManager upgradeManager;
    private GameUI gameUI;
    private Entity player;

    private final Map<String, javafx.scene.Node> renderNodes = new HashMap<>();

    private boolean moveUp, moveDown, moveLeft, moveRight;
    private boolean mouseFire;
    private Point2D mouseWorldPos = new Point2D(0, 0);

    private double cameraX, cameraY;

    private TileRenderer tileRenderer;

    private int frameCount;
    private double fpsAccumulator;
    private double currentFps;

    private Text debugText;

    private boolean gameStarted;
    private boolean hadEnemies;
    private boolean upgradeTriggeredThisWave;
    private double lastPlayerDamageTime = -Double.MAX_VALUE / 2;

    private long sessionSeed;

    // ── 地牢系统 ──
    private List<Room> dungeonRooms;
    private Map<String, Room> dungeonMap;  // "col,row" → Room
    private Room currentRoom;

    // ── 动态世界边界（由地牢生成后计算） ──
    private double worldMinX, worldMinY, worldMaxX, worldMaxY;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setTitle("RougeVolley — 地牢探险");
        settings.setVersion("0.1.0");
        settings.setWidth(GameConfig.VIEWPORT_WIDTH);
        settings.setHeight(GameConfig.VIEWPORT_HEIGHT);
        settings.setMainMenuEnabled(false);
        settings.setGameMenuEnabled(false);
        settings.setPreserveResizeRatio(true);
    }

    @Override
    protected void initInput() {
        Input input = FXGL.getInput();

        input.addAction(new UserAction("Move Up") {
            @Override protected void onActionBegin() { moveUp = true; }
            @Override protected void onActionEnd() { moveUp = false; }
        }, KeyCode.W);
        input.addAction(new UserAction("Move Up Arrow") {
            @Override protected void onActionBegin() { moveUp = true; }
            @Override protected void onActionEnd() { moveUp = false; }
        }, KeyCode.UP);

        input.addAction(new UserAction("Move Down") {
            @Override protected void onActionBegin() { moveDown = true; }
            @Override protected void onActionEnd() { moveDown = false; }
        }, KeyCode.S);
        input.addAction(new UserAction("Move Down Arrow") {
            @Override protected void onActionBegin() { moveDown = true; }
            @Override protected void onActionEnd() { moveDown = false; }
        }, KeyCode.DOWN);

        input.addAction(new UserAction("Move Left") {
            @Override protected void onActionBegin() { moveLeft = true; }
            @Override protected void onActionEnd() { moveLeft = false; }
        }, KeyCode.A);
        input.addAction(new UserAction("Move Left Arrow") {
            @Override protected void onActionBegin() { moveLeft = true; }
            @Override protected void onActionEnd() { moveLeft = false; }
        }, KeyCode.LEFT);

        input.addAction(new UserAction("Move Right") {
            @Override protected void onActionBegin() { moveRight = true; }
            @Override protected void onActionEnd() { moveRight = false; }
        }, KeyCode.D);
        input.addAction(new UserAction("Move Right Arrow") {
            @Override protected void onActionBegin() { moveRight = true; }
            @Override protected void onActionEnd() { moveRight = false; }
        }, KeyCode.RIGHT);

        input.addAction(new UserAction("Fire") {
            @Override protected void onActionBegin() { mouseFire = true; }
            @Override protected void onActionEnd() { mouseFire = false; }
        }, MouseButton.PRIMARY);

        input.addAction(new UserAction("Pause") {
            private boolean toggleGuard;
            @Override protected void onActionBegin() {
                if (!toggleGuard) {
                    toggleGuard = true;
                    togglePause();
                }
            }
            @Override protected void onActionEnd() { toggleGuard = false; }
        }, KeyCode.ESCAPE);
    }

    @Override
    protected void initGame() {
        sessionSeed = ThreadLocalRandom.current().nextLong();
        log.info("RougeVolley initializing... seed=" + sessionSeed);

        gameState = new GameState(sessionSeed);
        upgradeManager = new UpgradeManager(sessionSeed);

        gameUI = new GameUI(gameState, upgradeManager);
        gameUI.init(
            this::startNewGame,
            this::startNewGame,
            this::returnToMainMenu,
            option -> upgradeTriggeredThisWave = true
        );
        gameUI.showStartMenu();

        debugText = new Text(10, 40, "");
        debugText.setFill(Color.LIME);
        debugText.setFont(javafx.scene.text.Font.font("Monospaced", 13));
        FXGL.getGameScene().addUINode(debugText);

        log.info("RougeVolley initialized. Waiting for player to start.");

        FXGL.getEventBus().fireEvent(new Event(GameEvent.ENGINE_INIT_EVENT));
    }

    @Override
    protected void onUpdate(double tpf) {
        if (gameState == null || !gameStarted) return;

        gameUI.updateHud(player);

        if (gameState.isGameOver()) return;
        if (gameState.isPaused()) return;

        double dt = Math.min(tpf, GameConfig.MAX_DELTA_TIME);

        handleInput(dt);

        // 检测门碰撞与房间切换
        checkDoorTransitions();

        gameState.updateEntities(dt);
        gameState.addTime(dt);

        // ── 墙壁碰撞校正（实体移动后将陷入墙壁的实体弹回地板） ──
        if (currentRoom != null) {
            correctWallCollisions(currentRoom);
        }

        // ── 子弹生命周期过期检查 ──
        double now = gameState.getElapsedTime();
        for (Entity e : gameState.getEntities()) {
            if (e.isActive() && e.getType() == EntityType.BULLET) {
                Object ud = e.getUserData();
                if (ud instanceof EntityFactory.BulletData bd) {
                    if (now - bd.spawnTime() > GameConfig.BULLET_LIFETIME) {
                        e.setActive(false);
                    }
                }
            }
        }

        DamageSystem.checkBulletEnemyCollisions(gameState);

        // ── 敌人-玩家碰撞伤害（无敌时间保护） ──
        if (now - lastPlayerDamageTime >= GameConfig.INVINCIBILITY_DURATION) {
            if (DamageSystem.checkEnemyPlayerCollisions(player, gameState)) {
                lastPlayerDamageTime = now;
            }
        }

        // ── 道具拾取碰撞检测 ──
        if (player != null && player.isActive()) {
            double ps = GameConfig.PLAYER_SIZE;
            for (Entity e : gameState.getEntities()) {
                if (!e.isActive() || !e.hasComponent(PickupComponent.class)) continue;
                PickupComponent pickup = e.getComponent(PickupComponent.class).orElse(null);
                if (pickup == null || pickup.isCollected()) continue;
                // AABB 重叠检测
                if (player.getX() < e.getX() + 16 && player.getX() + ps > e.getX()
                    && player.getY() < e.getY() + 16 && player.getY() + ps > e.getY()) {
                    pickup.markCollected();
                    e.setActive(false);
                    FXGL.getEventBus().fireEvent(new Event(GameEvent.ITEM_PICKED_UP_EVENT));
                    // 移除渲染节点
                    javafx.scene.Node node = renderNodes.remove(e.getUuid());
                    if (node != null) {
                        FXGL.getGameScene().removeUINode(node);
                    }
                }
            }
        }

        List<String> removedUuids = gameState.cleanupDeadEntities();
        for (String uuid : removedUuids) {
            javafx.scene.Node node = renderNodes.remove(uuid);
            if (node != null) {
                FXGL.getGameScene().removeUINode(node);
            }
        }

        syncRenderNodes();

        if (tileRenderer != null) {
            tileRenderer.sync(new com.almasb.fxgl.core.math.Vec2(FXGL.getGameScene().getViewport().getOrigin()));
        }

        updateCamera(dt);
        checkPlayerDeath();
        checkRoomCleared();
        updateDebugInfo(dt);
    }

    // ── 游戏流程 ──

    private void startNewGame() {
        clearWorld();

        sessionSeed = ThreadLocalRandom.current().nextLong();
        gameState = new GameState(sessionSeed);
        upgradeManager = new UpgradeManager(sessionSeed);
        gameUI.bindGameState(gameState, upgradeManager);
        gameUI.hideStartMenu();
        gameUI.hideGameOver();

        gameStarted = true;
        hadEnemies = false;
        upgradeTriggeredThisWave = false;
        lastPlayerDamageTime = -Double.MAX_VALUE / 2;
        gameState.setGameOver(false);
        gameState.setPaused(false);

        FXGL.getEventBus().fireEvent(new Event(GameEvent.GAME_RESTART_EVENT));

        // ── 生成地牢（使用全局确定性随机源） ──
        Random gameRng = gameState.getRandom();
        RoomPool roomPool = RoomPool.loadDefault(gameRng);
        DungeonGenerator dungeonGenerator = new DungeonGenerator(roomPool, gameRng);
        dungeonRooms = dungeonGenerator.generate();

        // 构建网格坐标查找表
        dungeonMap = new HashMap<>();
        for (Room room : dungeonRooms) {
            String key = room.getGridX() + "," + room.getGridY();
            dungeonMap.put(key, room);
        }

        // 计算动态世界边界
        computeWorldBounds();

        spawnPlayerInRoom();
        ensureEnemyRenderNodes();

        cameraX = Math.min(player.getX() - GameConfig.VIEWPORT_WIDTH / 2.0,
            worldMaxX - GameConfig.VIEWPORT_WIDTH);
        cameraY = Math.min(player.getY() - GameConfig.VIEWPORT_HEIGHT / 2.0,
            worldMaxY - GameConfig.VIEWPORT_HEIGHT);
        applyCameraPosition();

        log.info("New game started. Player at (" + player.getX() + ", " + player.getY() + ")");
    }

    private void returnToMainMenu() {
        clearWorld();
        gameStarted = false;
        player = null;
        currentRoom = null;

        sessionSeed = ThreadLocalRandom.current().nextLong();
        gameState = new GameState(sessionSeed);
        upgradeManager = new UpgradeManager(sessionSeed);
        gameUI.bindGameState(gameState, upgradeManager);
        gameUI.showStartMenu();

        log.info("Returned to main menu.");
    }

    private void spawnPlayerInRoom() {
        // ── 激活起始房间 (0,0) ──
        currentRoom = dungeonMap.get("0,0");
        if (currentRoom == null) {
            currentRoom = dungeonRooms.get(0);
        }
        currentRoom.activate(gameState);

        // ── 创建 TileRenderer 并构建起始房间 ──
        tileRenderer = new TileRenderer();
        tileRenderer.buildForRoom(currentRoom);

        // ── 创建玩家（在起始房间的玩家生成点） ──
        RoomTemplate startTemplate = currentRoom.getTemplate();
        Point2D playerSpawn = startTemplate.getPlayerSpawn();
        double playerWorldX, playerWorldY;
        if (playerSpawn != null) {
            playerWorldX = currentRoom.getWorldX() + playerSpawn.getX();
            playerWorldY = currentRoom.getWorldY() + playerSpawn.getY();
        } else {
            // 回退：房间中心
            playerWorldX = currentRoom.getWorldX() + startTemplate.getWidthPixels() / 2.0;
            playerWorldY = currentRoom.getWorldY() + startTemplate.getHeightPixels() / 2.0;
        }
        player = EntityFactory.createPlayer(playerWorldX, playerWorldY);
        gameState.setPlayer(player);
        gameState.registerEntity(player);

        Rectangle playerRect = new Rectangle(
            GameConfig.PLAYER_SIZE, GameConfig.PLAYER_SIZE,
            Color.DODGERBLUE
        );
        playerRect.setArcWidth(6);
        playerRect.setArcHeight(6);
        playerRect.setStroke(Color.WHITE);
        playerRect.setStrokeWidth(1.5);
        FXGL.getGameScene().addUINode(playerRect);
        renderNodes.put(player.getUuid(), playerRect);

        // ── 为起始房间已生成的敌人创建渲染节点 ──
        for (Entity e : gameState.getEntities()) {
            if (!renderNodes.containsKey(e.getUuid()) && e.isActive()) {
                createRenderNodeFor(e);
            }
        }

        log.info("RougeVolley initialized. Dungeon: " + dungeonRooms.size() + " rooms. Player at ("
            + player.getX() + ", " + player.getY() + ")");

        // 触发地牢生成事件
        FXGL.getEventBus().fireEvent(new Event(GameEvent.DUNGEON_GENERATED_EVENT));
    }

    private void clearWorld() {
        if (gameState != null) {
            for (Entity e : new ArrayList<>(gameState.getEntities())) {
                javafx.scene.Node node = renderNodes.remove(e.getUuid());
                if (node != null) {
                    FXGL.getGameScene().removeUINode(node);
                }
                e.destroy();
            }
            gameState.clearAllEntities();
        }

        renderNodes.clear();

        // ── Tile 渲染清理 ──
        if (tileRenderer != null) {
            tileRenderer.clear();
            tileRenderer = null;
        }
    }

    private void checkRoomCleared() {
        if (gameUI.isUpgradeVisible() || upgradeTriggeredThisWave) return;
        if (currentRoom == null) return;

        // 使用房间级别的清空判定（仅检查当前房间的敌人）
        boolean roomCleared = currentRoom.checkAndMarkCleared();

        if (hadEnemies && roomCleared) {
            FXGL.getEventBus().fireEvent(new Event(GameEvent.ROOM_CLEARED_EVENT));
        }
    }

    private void checkPlayerDeath() {
        if (player == null || gameState.isGameOver()) return;

        HealthComponent health = player.getComponent(HealthComponent.class).orElse(null);
        if (health != null && health.isDead()) {
            gameState.setGameOver(true);
            gameState.setPaused(true);
            gameUI.showGameOver();
            FXGL.getEventBus().fireEvent(new Event(GameEvent.GAME_OVER_EVENT));
            log.info("Player died — Game Over");
        }
    }

    /** ESC 暂停/继续切换（仅在游戏进行中有效） */
    private void togglePause() {
        if (!gameStarted || gameState.isGameOver()) return;
        gameUI.togglePauseMenu();
    }

    // ── 输入 ──

    private void handleInput(double dt) {
        if (player == null || !player.isActive()) return;

        PlayerComponent pc = player.getComponent(PlayerComponent.class).orElse(null);
        MovementComponent mc = player.getComponent(MovementComponent.class).orElse(null);
        if (pc == null || mc == null) return;

        double dx = 0, dy = 0;
        if (moveUp)    dy -= 1;
        if (moveDown)  dy += 1;
        if (moveLeft)  dx -= 1;
        if (moveRight) dx += 1;

        double len = Math.sqrt(dx * dx + dy * dy);
        if (len > 0) {
            dx /= len;
            dy /= len;
            double vx = dx * pc.getSpeed();
            double vy = dy * pc.getSpeed();

            // ── 墙壁物理碰撞（轴分离预判，防止穿墙） ──
            if (currentRoom != null) {
                RoomTemplate t = currentRoom.getTemplate();
                double rwx = currentRoom.getWorldX();
                double rwy = currentRoom.getWorldY();
                int T = GameConfig.TILE_SIZE;
                double size = pc.getSize();

                // X 轴独立检测：若下一步会进入墙壁则阻止 X 方向移动
                double nextX = player.getX() + vx * dt;
                if (!isPositionValid(t, rwx, rwy, T, nextX, player.getY(), size)) {
                    vx = 0;
                }
                // Y 轴独立检测：允许沿墙滑动
                double nextY = player.getY() + vy * dt;
                if (!isPositionValid(t, rwx, rwy, T, player.getX(), nextY, size)) {
                    vy = 0;
                }
            }

            if (vx == 0 && vy == 0) {
                mc.stop();
            } else {
                mc.setVelocity(vx, vy);
            }
            FXGL.getEventBus().fireEvent(new Event(GameEvent.PLAYER_MOVED_EVENT));
        } else {
            mc.stop();
        }

        double screenMouseX = FXGL.getInput().getMouseXUI();
        double screenMouseY = FXGL.getInput().getMouseYUI();
        mouseWorldPos = new Point2D(screenMouseX + cameraX, screenMouseY + cameraY);

        if (mouseFire) {
            WeaponSystem.fire(player, mouseWorldPos, gameState, renderNodes);
        }
    }

    // ── 摄像机 ──

    /**
     * 平滑跟随玩家。仅限制右/下边界防止滚动超出地牢，
     * 左/上允许负值偏移，确保玩家始终在视口中心。
     */
    private void updateCamera(double dt) {
        if (player == null) return;

        double targetX = player.getX() - GameConfig.VIEWPORT_WIDTH / 2.0;
        double targetY = player.getY() - GameConfig.VIEWPORT_HEIGHT / 2.0;

        double lerpFactor = 1.0 - Math.exp(GameConfig.CAMERA_LERP_SPEED * dt);
        cameraX += (targetX - cameraX) * lerpFactor;
        cameraY += (targetY - cameraY) * lerpFactor;

        // 仅限制右/下边界，允许左/上越界以居中玩家
        double boundRight = Math.max(worldMaxX - GameConfig.VIEWPORT_WIDTH, worldMinX);
        double boundBottom = Math.max(worldMaxY - GameConfig.VIEWPORT_HEIGHT, worldMinY);
        cameraX = Math.min(cameraX, boundRight);
        cameraY = Math.min(cameraY, boundBottom);

        applyCameraPosition();
    }

    private void applyCameraPosition() {
        FXGL.getGameScene().getViewport().setX(cameraX);
        FXGL.getGameScene().getViewport().setY(cameraY);
    }

    // ── 渲染同步 ──

    // ============================================================
    //  门碰撞检测与房间切换 (Problem 5.2)
    // ============================================================

    /**
     * 检测玩家是否进入当前房间的门区域，若进入则切换房间。
     */
    private void checkDoorTransitions() {
        if (currentRoom == null || player == null) return;

        double ps = GameConfig.PLAYER_SIZE;
        double px = player.getX();
        double py = player.getY();

        for (String dir : currentRoom.getDoorDirections()) {
            if (!currentRoom.canPassThrough(dir)) continue;

            var doorBounds = currentRoom.getDoorWorldBounds(dir);
            if (doorBounds == null) continue;

            // AABB 重叠检测：玩家包围盒 vs 门区域（而非仅检测左上角点）
            if (px < doorBounds.getMaxX() && px + ps > doorBounds.getMinX()
                && py < doorBounds.getMaxY() && py + ps > doorBounds.getMinY()) {
                transitionToRoom(dir);
                break;
            }
        }
    }

    /**
     * 沿指定方向切换到相邻房间。
     * <p>
     * 流程：停用当前房间 → 清除 tiles → 激活目标房间 → 重建 tiles →
     * 将玩家传送到目标房间的对面门位置。
     */
    private void transitionToRoom(String direction) {
        // 计算目标房间网格坐标
        int dx = 0, dy = 0;
        switch (direction) {
            case "N" -> dy = -1;
            case "S" -> dy = 1;
            case "W" -> dx = -1;
            case "E" -> dx = 1;
            default -> { return; }
        }

        int targetGx = currentRoom.getGridX() + dx;
        int targetGy = currentRoom.getGridY() + dy;
        String key = targetGx + "," + targetGy;
        Room target = dungeonMap.get(key);
        if (target == null) return;

        log.info("Transitioning " + direction + " → room (" + targetGx + "," + targetGy + ")");

        // ── 先移除当前房间实体的渲染节点（必须在 deactivate 前，因为 deactivate 会从实体列表移除敌人） ──
        for (Entity e : gameState.getEntities()) {
            if (e != player && renderNodes.containsKey(e.getUuid())) {
                javafx.scene.Node node = renderNodes.remove(e.getUuid());
                if (node != null) {
                    FXGL.getGameScene().removeUINode(node);
                }
            }
        }
        // ── 停用当前房间（销毁敌人实体并从全局列表移除） ──
        currentRoom.deactivate(gameState);

        // ── 清除 tiles ──
        if (tileRenderer != null) {
            tileRenderer.clear();
        }

        // ── 激活目标房间 ──
        target.activate(gameState);
        if (tileRenderer != null) {
            tileRenderer.buildForRoom(target);
        }
        currentRoom = target;

        // ── 为新生实体创建渲染节点 ──
        for (Entity e : gameState.getEntities()) {
            if (e != player && !renderNodes.containsKey(e.getUuid()) && e.isActive()) {
                createRenderNodeFor(e);
            }
        }

        // ── 将所有实体置顶（tile 重建后新 tile 会覆盖旧实体节点） ──
        for (javafx.scene.Node node : renderNodes.values()) {
            node.toFront();
        }

        // ── 将玩家传送到目标房间的对面门位置 ──
        String oppositeDir = getOppositeDirection(direction);
        var oppositeDoor = target.getDoorWorldBounds(oppositeDir);
        if (oppositeDoor != null) {
            // 放置在门内侧一点，防止立即再次触发切换
            double spawnX = oppositeDoor.getMinX() + oppositeDoor.getWidth() / 2.0;
            double spawnY = oppositeDoor.getMinY() + oppositeDoor.getHeight() / 2.0;
            // 推向房间内侧足够远，防止立即重新触发门碰撞
            double pushIn = GameConfig.PLAYER_SIZE * 2 + GameConfig.TILE_SIZE;
            switch (oppositeDir) {
                case "N" -> spawnY += pushIn;
                case "S" -> spawnY -= pushIn;
                case "W" -> spawnX += pushIn;
                case "E" -> spawnX -= pushIn;
            }
            player.setPosition(new Point2D(spawnX, spawnY));
        }

        // ── 触发房间进入事件 ──
        FXGL.getEventBus().fireEvent(new Event(GameEvent.ROOM_ENTERED_EVENT));
        // ── 触发区域变化事件（难度/环境切换） ──
        FXGL.getEventBus().fireEvent(new Event(GameEvent.ZONE_CHANGED_EVENT));

        // ── 重置房间状态标志，允许新房间触发升级 ──
        hadEnemies = false;
        upgradeTriggeredThisWave = false;
        ensureEnemyRenderNodes();

        log.info("Entered room " + target.getTemplate().getName() + " @(" + targetGx + "," + targetGy + ")");
    }

    /**
     * 获取相反方向。
     */
    private static String getOppositeDirection(String dir) {
        return switch (dir) {
            case "N" -> "S";
            case "S" -> "N";
            case "W" -> "E";
            case "E" -> "W";
            default -> dir;
        };
    }

    // ============================================================
    //  世界边界计算 (Problem 5.3)
    // ============================================================

    /**
     * 根据地牢所有房间的包围盒计算动态世界边界。
     * 在 {@link #initGame()} 中地牢生成后调用。
     */
    private void computeWorldBounds() {
        if (dungeonRooms == null || dungeonRooms.isEmpty()) {
            // 回退到固定边界
            worldMinX = 0;
            worldMinY = 0;
            worldMaxX = GameConfig.WORLD_WIDTH;
            worldMaxY = GameConfig.WORLD_HEIGHT;
            return;
        }

        worldMinX = Double.MAX_VALUE;
        worldMinY = Double.MAX_VALUE;
        worldMaxX = Double.MIN_VALUE;
        worldMaxY = Double.MIN_VALUE;

        for (Room room : dungeonRooms) {
            double rx = room.getWorldX();
            double ry = room.getWorldY();
            double rw = room.getTemplate().getWidthPixels();
            double rh = room.getTemplate().getHeightPixels();

            worldMinX = Math.min(worldMinX, rx);
            worldMinY = Math.min(worldMinY, ry);
            worldMaxX = Math.max(worldMaxX, rx + rw);
            worldMaxY = Math.max(worldMaxY, ry + rh);
        }
    }

    // ============================================================
    //  渲染节点辅助
    // ============================================================

    /**
     * 为实体自动创建渲染节点（用于房间生成的敌人等未预创建节点的实体）。
     */
    private void createRenderNodeFor(Entity e) {
        var enemyComp = e.getComponent(EnemyComponent.class);
        if (enemyComp.isPresent()) {
            double size = enemyComp.get().getSize();
            Rectangle rect = new Rectangle(size, size, Color.CRIMSON);
            rect.setArcWidth(4);
            rect.setArcHeight(4);
            rect.setStroke(Color.DARKRED);
            rect.setStrokeWidth(1);
            FXGL.getGameScene().addUINode(rect);
            renderNodes.put(e.getUuid(), rect);
        }
    }

    // ============================================================
    //  渲染同步
    // ============================================================

    /**
     * 将每个实体的世界坐标转换为屏幕坐标并更新渲染节点。
     * 对尚未有渲染节点的实体自动创建默认节点。
     */
    private void syncRenderNodes() {
        for (Entity e : gameState.getEntities()) {
            javafx.scene.Node node = renderNodes.get(e.getUuid());
            if (node == null) {
                // 自动创建尚未注册渲染节点的实体（如房间生成的敌人）
                createRenderNodeFor(e);
                node = renderNodes.get(e.getUuid());
                if (node == null) continue;
            }

            double screenX = e.getX() - cameraX;
            double screenY = e.getY() - cameraY;

            if (screenX < GameConfig.CULL_MARGIN_NEG || screenX > GameConfig.VIEWPORT_WIDTH - GameConfig.CULL_MARGIN_NEG ||
                screenY < GameConfig.CULL_MARGIN_NEG || screenY > GameConfig.VIEWPORT_HEIGHT - GameConfig.CULL_MARGIN_NEG) {
                node.setVisible(false);
                continue;
            }
            node.setVisible(true);

            node.setTranslateX(screenX);
            node.setTranslateY(screenY);
        }
    }

    private void ensureEnemyRenderNodes() {
        for (Entity e : gameState.getEntities()) {
            if (!renderNodes.containsKey(e.getUuid())
                && e.hasComponent(EnemyComponent.class)
                && e.isActive()) {
                Rectangle rect = new Rectangle(GameConfig.ENEMY_SIZE, GameConfig.ENEMY_SIZE, Color.CRIMSON);
                rect.setArcWidth(4);
                rect.setArcHeight(4);
                rect.setStroke(Color.DARKRED);
                rect.setStrokeWidth(1);
                FXGL.getGameScene().addUINode(rect);
                renderNodes.put(e.getUuid(), rect);
            }
        }
        if (gameState.getEntities().stream().anyMatch(e -> e.isActive() && e.hasComponent(EnemyComponent.class))) {
            hadEnemies = true;
        }
    }

    private void updateDebugInfo(double dt) {
        frameCount++;
        fpsAccumulator += dt;
        if (fpsAccumulator >= 0.5) {
            currentFps = frameCount / fpsAccumulator;
            frameCount = 0;
            fpsAccumulator = 0;
        }

        if (debugText != null) {
            String roomInfo = (currentRoom != null)
                ? currentRoom.getTemplate().getName() + " (" + currentRoom.getGridX() + "," + currentRoom.getGridY() + ")"
                : "none";
            debugText.setText(String.format(
                "FPS: %.0f | Player: (%.0f, %.0f) | Entities: %d | Room: %s | Cam: (%.0f, %.0f)",
                currentFps,
                player != null ? player.getX() : 0,
                player != null ? player.getY() : 0,
                gameState != null ? gameState.getEntities().size() : 0,
                roomInfo,
                cameraX, cameraY
            ));
        }
    }

    // ============================================================
    //  墙壁物理碰撞
    // ============================================================

    /**
     * 检测包围盒四个角是否全部在地板 tile 上（不被墙壁阻挡）。
     * 用于玩家预判移动和敌人物理校正。
     */
    private static boolean isPositionValid(RoomTemplate template, double rwx, double rwy, int T,
                                            double x, double y, double size) {
        int tlx = (int) ((x - rwx) / T);
        int tly = (int) ((y - rwy) / T);
        int brx = (int) ((x + size - 1 - rwx) / T);
        int bry = (int) ((y + size - 1 - rwy) / T);
        return !template.isWall(tlx, tly)
            && !template.isWall(brx, tly)
            && !template.isWall(tlx, bry)
            && !template.isWall(brx, bry);
    }

    /**
     * 移动后校正：检测所有实体包围盒四角是否陷入墙壁，若陷入则弹回最近地板。
     * 作为玩家轴分离预判的兜底 + 敌人 AI 穿墙的最后防线。
     */
    private void correctWallCollisions(Room room) {
        RoomTemplate template = room.getTemplate();
        double rwx = room.getWorldX();
        double rwy = room.getWorldY();
        int T = GameConfig.TILE_SIZE;

        for (Entity e : gameState.getEntities()) {
            if (!e.isActive()) continue;
            double size = getEntitySize(e);
            if (size <= 0) continue;

            // 四角检测：只要有一个角在墙壁中就校正
            int tlx = (int) ((e.getX() - rwx) / T);
            int tly = (int) ((e.getY() - rwy) / T);
            int brx = (int) ((e.getX() + size - 1 - rwx) / T);
            int bry = (int) ((e.getY() + size - 1 - rwy) / T);

            boolean inWall = template.isWall(tlx, tly) || template.isWall(brx, tly)
                          || template.isWall(tlx, bry) || template.isWall(brx, bry);
            if (!inWall) continue;

            // 用实体中心定位，螺旋搜索最近可行走 tile 并弹回
            int cx = (int) ((e.getX() + size / 2.0 - rwx) / T);
            int cy = (int) ((e.getY() + size / 2.0 - rwy) / T);
            for (int r = 1; r < 12; r++) {
                boolean found = false;
                for (int dy = -r; dy <= r && !found; dy++) {
                    for (int dx = -r; dx <= r && !found; dx++) {
                        if (Math.abs(dx) < r && Math.abs(dy) < r) continue;
                        int nx = cx + dx;
                        int ny = cy + dy;
                        double candX = rwx + nx * T + (T - size) / 2.0;
                        double candY = rwy + ny * T + (T - size) / 2.0;
                        if (isPositionValid(template, rwx, rwy, T, candX, candY, size)) {
                            e.setPosition(new Point2D(candX, candY));
                            found = true;
                        }
                    }
                }
                if (found) break;
            }
        }
    }

    /** 返回实体的碰撞尺寸，用于墙壁检测。子弹返回 0（不参与墙壁碰撞）。 */
    private double getEntitySize(Entity e) {
        if (e.hasComponent(PlayerComponent.class)) {
            return e.getComponent(PlayerComponent.class).get().getSize();
        }
        if (e.hasComponent(EnemyComponent.class)) {
            return e.getComponent(EnemyComponent.class).get().getSize();
        }
        return 0;
    }

    // ============================================================
    //  工具
    // ============================================================

    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    public GameState getGameState() { return gameState; }
    public Entity getPlayer() { return player; }
}
