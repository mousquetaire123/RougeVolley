package org.example.rougevolley.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * 菜单工厂 —— 开始界面与 GameOver 界面
 */
public final class MenuFactory {

    private MenuFactory() {}

    /**
     * 开始菜单：全屏半透明遮罩 + 标题 + 开始按钮
     */
    public static StackPane createStartMenu(Runnable onStart) {
        StackPane root = createOverlay();

        VBox content = new VBox(24);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40));

        Text title = new Text("RougeVolley");
        title.setFont(Font.font("Arial", 48));
        title.setFill(Color.WHITE);

        Text subtitle = new Text("俯视角 Roguelike 地牢");
        subtitle.setFont(Font.font("Arial", 18));
        subtitle.setFill(Color.LIGHTGRAY);

        Button startBtn = createMenuButton("开始游戏", onStart);

        content.getChildren().addAll(title, subtitle, startBtn);
        root.getChildren().add(content);
        return root;
    }

    /**
     * GameOver 菜单：重试 / 退出
     */
    public static StackPane createGameOverMenu(Runnable onRetry, Runnable onQuit) {
        StackPane root = createOverlay();

        VBox content = new VBox(20);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40));

        Text title = new Text("Game Over");
        title.setFont(Font.font("Arial", 42));
        title.setFill(Color.CRIMSON);

        Text hint = new Text("你已阵亡，再试一次？");
        hint.setFont(Font.font("Arial", 16));
        hint.setFill(Color.LIGHTGRAY);

        Button retryBtn = createMenuButton("重新开始", onRetry);
        Button quitBtn = createMenuButton("返回主菜单", onQuit);

        content.getChildren().addAll(title, hint, retryBtn, quitBtn);
        root.getChildren().add(content);
        return root;
    }

    private static StackPane createOverlay() {
        StackPane root = new StackPane();
        root.setPickOnBounds(true);

        Rectangle dim = new Rectangle(1280, 720);
        dim.setFill(Color.rgb(0, 0, 0, 0.75));
        root.getChildren().add(dim);
        return root;
    }

    private static Button createMenuButton(String label, Runnable action) {
        Button btn = new Button(label);
        btn.setFont(Font.font("Arial", 18));
        btn.setPrefWidth(220);
        btn.setPrefHeight(44);
        btn.setStyle(
            "-fx-background-color: #4338ca; " +
            "-fx-text-fill: white; " +
            "-fx-background-radius: 8; " +
            "-fx-cursor: hand;"
        );
        btn.setOnAction(e -> {
            if (action != null) action.run();
        });
        return btn;
    }
}
