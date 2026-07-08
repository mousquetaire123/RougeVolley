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
import org.example.rougevolley.ecs.Entity;
import org.example.rougevolley.ecs.components.*;
import org.example.rougevolley.combat.DamageSystem;
import org.example.rougevolley.combat.WeaponSystem;
import org.example.rougevolley.entity.EntityFactory;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RougeVolley — FXGL 地牢探险游戏入口
 * <p>
 * 架构职责：
 * - 继承 FXGL GameApplication，管理窗口生命周期
 * - 持有 GameState（实体注册表、游戏阶段标记）
 * - 摄像机跟随玩家（平滑插值）
 * - 输入映射（WASD 移动、鼠标瞄准/射击）
 * - onUpdate 驱动：实体更新 → 清理 → 渲染同步 → 摄像机
 */
public class RougeVolleyFXGL extends GameApplication {

    private static final Logger log = Logger.get(RougeVolleyFXGL.class);

    // ── 游戏核心 ──
    private GameState gameState;
    private Entity player;

    // ── 渲染层映射 (entity uuid → JavaFX node) ──
    private final Map<String, javafx.scene.Node> renderNodes = new HashMap<>();

    // ── 输入状态 ──
    private boolean moveUp, moveDown, moveLeft, moveRight;
    private boolean mouseFire;
    private Point2D mouseWorldPos = new Point2D(0, 0);

    // ── 摄像机平滑 ──
    private double cameraX, cameraY;

    // ── FPS 跟踪 ──
    private int frameCount;
    private double fpsAccumulator;
    private double currentFps;

    // ── 调试 UI 引用 ──
    private Text debugText;

    public static void main(String[] args) {
        launch(args);
    }

    // ============================================================
    //  FXGL 生命周期
    // ============================================================

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

        // ── WASD 移动 ──
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

        // ── 鼠标射击 ──
        input.addAction(new UserAction("Fire") {
            @Override protected void onActionBegin() { mouseFire = true; }
            @Override protected void onActionEnd() { mouseFire = false; }
        }, MouseButton.PRIMARY);
    }

    @Override
    protected void initGame() {
        long seed = ThreadLocalRandom.current().nextLong();
        log.info("RougeVolley initializing... seed=" + seed);

        gameState = new GameState(seed);

        // ── 创建玩家 ──
        player = EntityFactory.createPlayer(
            GameConfig.WORLD_WIDTH / 2.0,
            GameConfig.WORLD_HEIGHT / 2.0
        );
        gameState.setPlayer(player);
        gameState.registerEntity(player);

        // ── 创建玩家渲染节点 ──
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

        // ── 创建测试敌人 ──
        spawnTestEnemies();

        // ── 初始化摄像机位置 ──
        cameraX = clamp(player.getX() - GameConfig.VIEWPORT_WIDTH / 2.0,
            0, GameConfig.WORLD_WIDTH - GameConfig.VIEWPORT_WIDTH);
        cameraY = clamp(player.getY() - GameConfig.VIEWPORT_HEIGHT / 2.0,
            0, GameConfig.WORLD_HEIGHT - GameConfig.VIEWPORT_HEIGHT);
        applyCameraPosition();

        // ── 调试信息 ──
        debugText = new Text(10, 20, "");
        debugText.setFill(Color.LIME);
        debugText.setFont(javafx.scene.text.Font.font("Monospaced", 13));
        FXGL.getGameScene().addUINode(debugText);

        log.info("RougeVolley initialized. Player at (" + player.getX() + ", " + player.getY() + ")");
    }

    @Override
    protected void onUpdate(double tpf) {
        if (gameState == null || gameState.isGameOver()) return;

        // 限制 dt 避免螺旋
        double dt = Math.min(tpf, GameConfig.MAX_DELTA_TIME);

        // ── 处理输入 ──
        handleInput(dt);

        // ── 更新所有实体 ──
        gameState.updateEntities(dt);
        gameState.addTime(dt);

        // ── 子弹-敌人碰撞检测 ──
        DamageSystem.checkBulletEnemyCollisions(gameState);

        // ── 清理死亡实体 ──
        List<String> removedUuids = gameState.cleanupDeadEntities();
        for (String uuid : removedUuids) {
            javafx.scene.Node node = renderNodes.remove(uuid);
            if (node != null) {
                FXGL.getGameScene().removeUINode(node);
            }
        }

        // ── 同步渲染节点位置 ──
        syncRenderNodes();

        // ── 摄像机平滑跟随玩家 ──
        updateCamera(dt);

        // ── 更新调试信息 ──
        updateDebugInfo(dt);
    }

    // ============================================================
    //  输入处理
    // ============================================================

    private void handleInput(double dt) {
        if (player == null || !player.isActive()) return;

        PlayerComponent pc = player.getComponent(PlayerComponent.class).orElse(null);
        MovementComponent mc = player.getComponent(MovementComponent.class).orElse(null);
        if (pc == null || mc == null) return;

        // ── WASD 移动 ──
        double dx = 0, dy = 0;
        if (moveUp)    dy -= 1;
        if (moveDown)  dy += 1;
        if (moveLeft)  dx -= 1;
        if (moveRight) dx += 1;

        // 归一化对角线移动
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len > 0) {
            dx /= len;
            dy /= len;
            mc.setVelocity(dx * pc.getSpeed(), dy * pc.getSpeed());
        } else {
            mc.stop();
        }

        // ── 鼠标瞄准 ──
        // FXGL Input 提供 getMouseXUI/YUI 获取屏幕坐标，转换为世界坐标
        double screenMouseX = FXGL.getInput().getMouseXUI();
        double screenMouseY = FXGL.getInput().getMouseYUI();
        mouseWorldPos = new Point2D(
            screenMouseX + cameraX,
            screenMouseY + cameraY
        );

        // ── 鼠标射击 ──
        if (mouseFire) {
            tryFireWeapon();
        }
    }

    /**
     * 尝试发射武器（受冷却时间限制）—— 委托给 WeaponSystem
     */
    private void tryFireWeapon() {
        WeaponSystem.fire(player, mouseWorldPos, gameState, renderNodes);
    }

    // ============================================================
    //  摄像机
    // ============================================================

    /**
     * 平滑跟随玩家并限制在世界边界内
     */
    private void updateCamera(double dt) {
        if (player == null) return;

        double targetX = player.getX() - GameConfig.VIEWPORT_WIDTH / 2.0;
        double targetY = player.getY() - GameConfig.VIEWPORT_HEIGHT / 2.0;

        // 平滑插值（lerp）
        double lerpFactor = 1.0 - Math.exp(GameConfig.CAMERA_LERP_SPEED * dt);
        cameraX += (targetX - cameraX) * lerpFactor;
        cameraY += (targetY - cameraY) * lerpFactor;

        // 限制在世界边界内
        cameraX = clamp(cameraX, 0, GameConfig.WORLD_WIDTH - GameConfig.VIEWPORT_WIDTH);
        cameraY = clamp(cameraY, 0, GameConfig.WORLD_HEIGHT - GameConfig.VIEWPORT_HEIGHT);

        applyCameraPosition();
    }

    private void applyCameraPosition() {
        FXGL.getGameScene().getViewport().setX(cameraX);
        FXGL.getGameScene().getViewport().setY(cameraY);
    }

    // ============================================================
    //  渲染同步
    // ============================================================

    /**
     * 将每个实体的世界坐标转换为屏幕坐标并更新渲染节点
     */
    private void syncRenderNodes() {
        for (Entity e : gameState.getEntities()) {
            javafx.scene.Node node = renderNodes.get(e.getUuid());
            if (node == null) continue;

            double screenX = e.getX() - cameraX;
            double screenY = e.getY() - cameraY;

            // 视锥剔除
            if (screenX < GameConfig.CULL_MARGIN_NEG || screenX > GameConfig.VIEWPORT_WIDTH - GameConfig.CULL_MARGIN_NEG ||
                screenY < GameConfig.CULL_MARGIN_NEG || screenY > GameConfig.VIEWPORT_HEIGHT - GameConfig.CULL_MARGIN_NEG) {
                node.setVisible(false);
                continue;
            }
            node.setVisible(true);

            // Node 基类的 setTranslateX/Y 适用于所有子类
            node.setTranslateX(screenX);
            node.setTranslateY(screenY);
        }
    }

    // ============================================================
    //  调试
    // ============================================================

    private void updateDebugInfo(double dt) {
        // 手动计算 FPS
        frameCount++;
        fpsAccumulator += dt;
        if (fpsAccumulator >= 0.5) { // 每0.5秒更新一次 FPS
            currentFps = frameCount / fpsAccumulator;
            frameCount = 0;
            fpsAccumulator = 0;
        }

        if (debugText != null) {
            debugText.setText(String.format(
                "FPS: %.0f | Player: (%.0f, %.0f) | Entities: %d | Cam: (%.0f, %.0f)",
                currentFps,
                player != null ? player.getX() : 0,
                player != null ? player.getY() : 0,
                gameState != null ? gameState.getEntities().size() : 0,
                cameraX, cameraY
            ));
        }
    }

    // ============================================================
    //  测试工具
    // ============================================================

    /**
     * 在世界中生成一些测试敌人
     */
    private void spawnTestEnemies() {
        Random rng = new Random(gameState.getSeed());
        int count = GameConfig.TEST_ENEMY_COUNT;
        for (int i = 0; i < count; i++) {
            double x = GameConfig.SPAWN_MARGIN + rng.nextDouble() * (GameConfig.WORLD_WIDTH - GameConfig.SPAWN_MARGIN * 2);
            double y = GameConfig.SPAWN_MARGIN + rng.nextDouble() * (GameConfig.WORLD_HEIGHT - GameConfig.SPAWN_MARGIN * 2);

            // 避免生成在玩家身上
            if (Math.abs(x - player.getX()) < GameConfig.SPAWN_SAFE_RADIUS
                && Math.abs(y - player.getY()) < GameConfig.SPAWN_SAFE_RADIUS) continue;

            Entity enemy = EntityFactory.createDefaultEnemy(x, y);
            gameState.registerEntity(enemy);

            // 创建敌人渲染节点
            Rectangle rect = new Rectangle(GameConfig.ENEMY_SIZE, GameConfig.ENEMY_SIZE, Color.CRIMSON);
            rect.setArcWidth(4);
            rect.setArcHeight(4);
            rect.setStroke(Color.DARKRED);
            rect.setStrokeWidth(1);
            FXGL.getGameScene().addUINode(rect);
            renderNodes.put(enemy.getUuid(), rect);
        }
        log.info("Spawned " + count + " test enemies");
    }

    // ============================================================
    //  工具
    // ============================================================

    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    // ============================================================
    //  Getters
    // ============================================================

    public GameState getGameState() { return gameState; }
    public Entity getPlayer() { return player; }
}
