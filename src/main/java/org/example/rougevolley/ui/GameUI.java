package org.example.rougevolley.ui;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.logging.Logger;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import org.example.rougevolley.config.GameConfig;
import org.example.rougevolley.core.GameEvent;
import org.example.rougevolley.core.GameState;
import org.example.rougevolley.ecs.Entity;
import org.example.rougevolley.ecs.components.HealthComponent;
import org.example.rougevolley.ecs.components.WeaponComponent;
import org.example.rougevolley.roguelike.PlayerStatsModifier;
import org.example.rougevolley.roguelike.UpgradeManager;
import org.example.rougevolley.roguelike.UpgradeOption;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 游戏 UI —— HUD 血条、开始/GameOver 菜单、三选一升级面板
 */
public class GameUI {

    private static final Logger log = Logger.get(GameUI.class);

    private static final double HP_BAR_WIDTH = GameConfig.VIEWPORT_WIDTH * 0.172;
    private static final double HP_BAR_HEIGHT = 18;

    private GameState gameState;
    private UpgradeManager upgradeManager;

    // ── HUD ──
    private final StackPane hudRoot = new StackPane();
    private final Rectangle hpBarBg = new Rectangle(HP_BAR_WIDTH, HP_BAR_HEIGHT, Color.rgb(40, 40, 40, 0.85));
    private final Rectangle hpBarFill = new Rectangle(HP_BAR_WIDTH, HP_BAR_HEIGHT, Color.LIMEGREEN);
    private final Text hpLabel = new Text("HP");

    // ── 覆盖层 ──
    private StackPane startMenu;
    private StackPane gameOverMenu;
    private StackPane upgradeOverlay;
    private StackPane pauseMenu;
    private VBox upgradePanel;

    private Consumer<UpgradeOption> onUpgradeSelected;
    private Runnable onStartGame;
    private Runnable onRetry;
    private Runnable onQuitToMenu;

    private boolean upgradeVisible;

    /** 玩家已选择的升级列表（用于暂停菜单展示） */
    private final List<UpgradeOption> selectedUpgrades = new ArrayList<>();
    /** 暂停菜单中展示升级/属性的动态区域 */
    private VBox pauseUpgradesList;

    public GameUI(GameState gameState, UpgradeManager upgradeManager) {
        this.gameState = gameState;
        this.upgradeManager = upgradeManager;
    }

    /** 重新开始时切换状态引用 */
    public void bindGameState(GameState gameState, UpgradeManager upgradeManager) {
        this.gameState = gameState;
        this.upgradeManager = upgradeManager;
        upgradeVisible = false;
        selectedUpgrades.clear();
        if (upgradeOverlay != null) {
            upgradeOverlay.setVisible(false);
        }
        if (pauseMenu != null) {
            pauseMenu.setVisible(false);
        }
    }

    /**
     * 初始化 UI 并挂载到场景
     */
    public void init(Runnable onStartGame, Runnable onRetry, Runnable onQuitToMenu,
                     Consumer<UpgradeOption> onUpgradeSelected) {
        this.onStartGame = onStartGame;
        this.onRetry = onRetry;
        this.onQuitToMenu = onQuitToMenu;
        this.onUpgradeSelected = onUpgradeSelected;

        buildHud();
        buildUpgradeOverlay();

        startMenu = MenuFactory.createStartMenu(this::handleStartClick);
        gameOverMenu = MenuFactory.createGameOverMenu(this::handleRetryClick, this::handleQuitClick);
        gameOverMenu.setVisible(false);

        FXGL.getGameScene().addUINode(hudRoot);
        FXGL.getGameScene().addUINode(startMenu);
        FXGL.getGameScene().addUINode(upgradeOverlay);
        FXGL.getGameScene().addUINode(gameOverMenu);

        pauseMenu = buildPauseMenu();
        FXGL.getGameScene().addUINode(pauseMenu);

        subscribeEvents();
    }

    private void buildHud() {
        hpBarBg.setArcWidth(8);
        hpBarBg.setArcHeight(8);
        hpBarFill.setArcWidth(8);
        hpBarFill.setArcHeight(8);

        hpLabel.setFont(Font.font("Monospaced", 13));
        hpLabel.setFill(Color.WHITE);

        HBox hpBox = new HBox(8, hpLabel, new StackPane(hpBarBg, hpBarFill));
        hpBox.setAlignment(Pos.CENTER_LEFT);
        hpBox.setPadding(new Insets(12, 0, 0, 12));

        StackPane.setAlignment(hpBox, Pos.TOP_LEFT);
        hudRoot.getChildren().add(hpBox);
        hudRoot.setPickOnBounds(false);
        hudRoot.setVisible(false);
    }

    private void buildUpgradeOverlay() {
        upgradeOverlay = new StackPane();
        upgradeOverlay.setVisible(false);
        upgradeOverlay.setPickOnBounds(true);

        Rectangle dim = new Rectangle(GameConfig.VIEWPORT_WIDTH, GameConfig.VIEWPORT_HEIGHT);
        dim.setFill(Color.rgb(0, 0, 0, 0.6));

        Text title = new Text("选择一项升级");
        title.setFont(Font.font("Arial", 28));
        title.setFill(Color.WHITE);

        upgradePanel = new VBox(16);
        upgradePanel.setAlignment(Pos.CENTER);
        upgradePanel.setPadding(new Insets(20));

        VBox content = new VBox(24, title, upgradePanel);
        content.setAlignment(Pos.CENTER);

        upgradeOverlay.getChildren().addAll(dim, content);
    }

    private void subscribeEvents() {
        FXGL.getEventBus().addEventHandler(GameEvent.ROOM_CLEARED_EVENT, this::onRoomCleared);
    }

    private void onRoomCleared(Event event) {
        if (!gameState.isGameOver()) {
            showUpgradeSelection();
        }
    }

    // ── 公开 API ──

    public void showStartMenu() {
        startMenu.setVisible(true);
        startMenu.toFront();
        gameOverMenu.setVisible(false);
        hideUpgradeSelection();
        hidePauseMenu();
        hudRoot.setVisible(false);
    }

    public void hideStartMenu() {
        startMenu.setVisible(false);
        hudRoot.setVisible(true);
    }

    public void showGameOver() {
        gameOverMenu.setVisible(true);
        gameOverMenu.toFront();
        hideUpgradeSelection();
        hudRoot.setVisible(false);
    }

    public void hideGameOver() {
        gameOverMenu.setVisible(false);
    }

    // ── 暂停菜单 ──

    /** 切换暂停菜单显示/隐藏，并同步游戏暂停状态 */
    public void togglePauseMenu() {
        if (pauseMenu == null || upgradeVisible) return;
        if (pauseMenu.isVisible()) {
            hidePauseMenu();
        } else {
            showPauseMenu();
        }
    }

    private void showPauseMenu() {
        refreshPauseUpgrades();
        pauseMenu.setVisible(true);
        pauseMenu.toFront();
        gameState.setPaused(true);
        hudRoot.setVisible(false);
    }

    private void hidePauseMenu() {
        pauseMenu.setVisible(false);
        gameState.setPaused(false);
        hudRoot.setVisible(true);
    }

    private StackPane buildPauseMenu() {
        StackPane root = new StackPane();
        root.setVisible(false);
        root.setPickOnBounds(true);

        Rectangle dim = new Rectangle(GameConfig.VIEWPORT_WIDTH, GameConfig.VIEWPORT_HEIGHT);
        dim.setFill(Color.rgb(0, 0, 0, 0.65));

        VBox content = new VBox(14);
        content.setAlignment(Pos.CENTER);

        Text title = new Text("暂停");
        title.setFont(Font.font("Arial", 36));
        title.setFill(Color.WHITE);

        // ── 已选升级 / 属性展示区域（每次打开暂停菜单时动态刷新） ──
        pauseUpgradesList = new VBox(6);
        pauseUpgradesList.setAlignment(Pos.CENTER);
        pauseUpgradesList.setPadding(new Insets(8, 0, 8, 0));

        Button continueBtn = createPauseButton("继续游戏", this::handlePauseContinue);
        Button restartBtn  = createPauseButton("重新开始", this::handlePauseRestart);
        Button quitBtn     = createPauseButton("退出游戏", this::handlePauseQuit);

        content.getChildren().addAll(title, pauseUpgradesList, continueBtn, restartBtn, quitBtn);
        root.getChildren().addAll(dim, content);
        return root;
    }

    /**
     * 刷新暂停菜单中的玩家属性与已选升级列表。
     * 每次打开暂停菜单时调用，确保展示最新状态。
     */
    private void refreshPauseUpgrades() {
        pauseUpgradesList.getChildren().clear();

        Entity player = gameState.getPlayer();
        if (player == null) return;

        // ── 当前属性摘要 ──
        Text statsTitle = new Text("—— 当前属性 ——");
        statsTitle.setFont(Font.font("Arial", 16));
        statsTitle.setFill(Color.rgb(148, 163, 184)); // slate-400
        pauseUpgradesList.getChildren().add(statsTitle);

        // 生命值
        HealthComponent health = player.getComponent(HealthComponent.class).orElse(null);
        if (health != null) {
            Text hpText = new Text(String.format("生命: %.0f / %.0f",
                health.getCurrentHealth(), health.getMaxHealth()));
            hpText.setFont(Font.font("Arial", 14));
            hpText.setFill(Color.rgb(74, 222, 128)); // green-400
            pauseUpgradesList.getChildren().add(hpText);
        }

        // 武器属性
        WeaponComponent weapon = player.getComponent(WeaponComponent.class).orElse(null);
        if (weapon != null) {
            String weaponInfo = String.format(
                "射速: %.2fs | 弹丸: %d | 伤害: %.0f | 弹速: %.0f | 散射: %.0f°",
                weapon.getFireRate(),
                weapon.getBulletCount(),
                weapon.getBulletDamage(),
                weapon.getBulletSpeed(),
                weapon.getSpreadAngle()
            );
            Text weaponText = new Text(weaponInfo);
            weaponText.setFont(Font.font("Arial", 12));
            weaponText.setFill(Color.rgb(250, 204, 21)); // yellow-400
            pauseUpgradesList.getChildren().add(weaponText);
        }

        // ── 已选升级 ──
        if (!selectedUpgrades.isEmpty()) {
            Text upgradesTitle = new Text("—— 已选升级 (" + selectedUpgrades.size() + ") ——");
            upgradesTitle.setFont(Font.font("Arial", 16));
            upgradesTitle.setFill(Color.rgb(148, 163, 184)); // slate-400
            pauseUpgradesList.getChildren().add(upgradesTitle);

            for (int i = 0; i < selectedUpgrades.size(); i++) {
                UpgradeOption up = selectedUpgrades.get(i);
                Text upText = new Text((i + 1) + ". " + up.getName() + " — " + up.getDescription());
                upText.setFont(Font.font("Arial", 13));
                upText.setFill(Color.rgb(167, 139, 250)); // violet-400
                pauseUpgradesList.getChildren().add(upText);
            }
        } else {
            Text noUpgrades = new Text("尚未选择升级");
            noUpgrades.setFont(Font.font("Arial", 13));
            noUpgrades.setFill(Color.rgb(100, 116, 139)); // slate-500
            pauseUpgradesList.getChildren().add(noUpgrades);
        }
    }

    private void handlePauseContinue() {
        hidePauseMenu();
    }

    private void handlePauseRestart() {
        hidePauseMenu();
        if (onRetry != null) onRetry.run();
    }

    private void handlePauseQuit() {
        hidePauseMenu();
        if (onQuitToMenu != null) onQuitToMenu.run();
    }

    private Button createPauseButton(String label, Runnable action) {
        Button btn = new Button(label);
        btn.setFont(Font.font("Arial", 18));
        btn.setPrefWidth(240);
        btn.setPrefHeight(48);
        btn.setStyle(
            "-fx-background-color: #334155; " +
            "-fx-text-fill: #e2e8f0; " +
            "-fx-background-radius: 8; " +
            "-fx-border-color: #6366f1; " +
            "-fx-border-radius: 8; " +
            "-fx-border-width: 2; " +
            "-fx-cursor: hand;"
        );
        btn.setOnAction(e -> { if (action != null) action.run(); });
        return btn;
    }

    /**
     * 显示三选一升级面板并暂停游戏
     */
    public void showUpgradeSelection() {
        if (upgradeVisible || gameState.isGameOver()) return;

        List<UpgradeOption> options = upgradeManager.randomPick3();
        if (options.isEmpty()) return;

        upgradePanel.getChildren().clear();
        for (UpgradeOption option : options) {
            upgradePanel.getChildren().add(createUpgradeButton(option));
        }

        upgradeVisible = true;
        upgradeOverlay.setVisible(true);
        upgradeOverlay.toFront();
        gameState.setPaused(true);
        FXGL.getEventBus().fireEvent(new Event(GameEvent.UPGRADE_TRIGGERED_EVENT));
        log.info("升级面板已显示，选项数: " + options.size());
    }

    public void hideUpgradeSelection() {
        upgradeVisible = false;
        upgradeOverlay.setVisible(false);
        gameState.setPaused(false);
    }

    /**
     * 每帧更新血条
     */
    public void updateHud(Entity player) {
        if (player == null || !hudRoot.isVisible()) return;

        HealthComponent health = player.getComponent(HealthComponent.class).orElse(null);
        if (health == null) return;

        double percent = health.getHealthPercent();
        hpBarFill.setWidth(HP_BAR_WIDTH * Math.max(0, Math.min(1, percent)));
        hpBarFill.setFill(percent > 0.3 ? Color.LIMEGREEN : Color.ORANGERED);

        hpLabel.setText(String.format("HP %.0f/%.0f",
            health.getCurrentHealth(), health.getMaxHealth()));
    }

    public boolean isUpgradeVisible() {
        return upgradeVisible;
    }

    // ── 内部事件 ──

    private Button createUpgradeButton(UpgradeOption option) {
        Button btn = new Button(option.getName() + "\n" + option.getDescription());
        btn.setPrefWidth(320);
        btn.setPrefHeight(72);
        btn.setWrapText(true);
        btn.setFont(Font.font("Arial", 14));
        btn.setStyle(
            "-fx-background-color: #1e293b; " +
            "-fx-text-fill: #e2e8f0; " +
            "-fx-background-radius: 10; " +
            "-fx-border-color: #6366f1; " +
            "-fx-border-radius: 10; " +
            "-fx-border-width: 2; " +
            "-fx-cursor: hand;"
        );
        btn.setOnAction(e -> selectUpgrade(option));
        return btn;
    }

    private void selectUpgrade(UpgradeOption option) {
        Entity player = gameState.getPlayer();
        if (player != null) {
            PlayerStatsModifier.apply(player, option);
        }

        selectedUpgrades.add(option);

        hideUpgradeSelection();
        FXGL.getEventBus().fireEvent(new Event(GameEvent.UPGRADE_SELECTED_EVENT));
        log.info("玩家选择升级: " + option.getName());

        if (onUpgradeSelected != null) {
            onUpgradeSelected.accept(option);
        }
    }

    private void handleStartClick() {
        hideStartMenu();
        if (onStartGame != null) onStartGame.run();
    }

    private void handleRetryClick() {
        hideGameOver();
        if (onRetry != null) onRetry.run();
    }

    private void handleQuitClick() {
        hideGameOver();
        if (onQuitToMenu != null) onQuitToMenu.run();
    }
}
