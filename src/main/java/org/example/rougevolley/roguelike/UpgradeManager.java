package org.example.rougevolley.roguelike;

import com.almasb.fxgl.logging.Logger;
import org.example.rougevolley.config.GameConfig;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 升级池管理 —— 从 Upgrades.json 加载并随机抽取选项
 */
public class UpgradeManager {

    private static final Logger log = Logger.get(UpgradeManager.class);
    private static final String UPGRADES_PATH = "/data/Upgrades.json";

    private final List<UpgradeOption> allUpgrades = new ArrayList<>();
    private final Random random;

    public UpgradeManager(long seed) {
        this.random = new Random(seed);
        loadUpgrades();
    }

    private void loadUpgrades() {
        try (InputStream in = UpgradeManager.class.getResourceAsStream(UPGRADES_PATH)) {
            if (in == null) {
                throw new IllegalStateException("找不到配置文件: " + UPGRADES_PATH);
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                allUpgrades.add(UpgradeOption.fromJson(array.getJSONObject(i)));
            }
            log.info("已加载 " + allUpgrades.size() + " 个升级选项");
        } catch (Exception e) {
            throw new IllegalStateException("加载 Upgrades.json 失败", e);
        }
    }

    /**
     * 随机抽取不重复的 N 个升级选项（默认 3 个）
     */
    public List<UpgradeOption> randomPick3() {
        if (allUpgrades.isEmpty()) {
            return List.of();
        }
        List<UpgradeOption> pool = new ArrayList<>(allUpgrades);
        Collections.shuffle(pool, random);
        int count = Math.min(GameConfig.UPGRADE_OPTIONS_COUNT, pool.size());
        return new ArrayList<>(pool.subList(0, count));
    }

    public List<UpgradeOption> getAllUpgrades() {
        return Collections.unmodifiableList(allUpgrades);
    }
}
