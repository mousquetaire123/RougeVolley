package org.example;

import org.example.rougevolley.RougeVolleyFXGL;

/**
 * RougeVolley 入口点
 * 委托至 FXGL GameApplication 启动
 */
public class Main {
    public static void main(String[] args) {
        // 支持命令行参数: --seed=12345
        RougeVolleyFXGL.main(args);
    }
}
