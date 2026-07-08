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
import org.example.rougevolley.roguelike.PlayerStatsModifier;
import org.example.rougevolley.roguelike.UpgradeManager;
import org.example.rougevolley.roguelike.UpgradeOption;

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
    private VBox upgradePanel;

    private Consumer<UpgradeOption> onUpgradeSelected;
    private Runnable onStartGame;
    private Runnable onRetry;
    private Runnable onQuitToMenu;

    private boolean upgradeVisible;

    public GameUI(GameState gameState, UpgradeManager upgradeManager) {
        this.gameState = gameState;
        this.upgradeManager = upgradeManager;
    }

    /** 重新开始时切换状态引用 */
    public void bindGameState(GameState gameState, UpgradeManager upgradeManager) {
        this.gameState = gameState;
        this.upgradeManager = upgradeManager;
        upgradeVisible = false;
        if (upgradeOverlay != null) {
            upgradeOverlay.setVisible(false);
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
        gameOverMenu.setVisible(false);
        hideUpgradeSelection();
        hudRoot.setVisible(false);
    }

    public void hideStartMenu() {
        startMenu.setVisible(false);
        hudRoot.setVisible(true);
    }

    public void showGameOver() {
        gameOverMenu.setVisible(true);
        hideUpgradeSelection();
        hudRoot.setVisible(false);
    }

    public void hideGameOver() {
        gameOverMenu.setVisible(false);
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
