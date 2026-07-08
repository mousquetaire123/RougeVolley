package org.example.rougevolley.roguelike;

import org.json.JSONObject;

/**
 * 升级选项 —— 对应 Upgrades.json 中的一项
 */
public class UpgradeOption {

    public enum Type {
        FIRE_RATE,
        BULLET_COUNT,
        HEALTH_RESTORE
    }

    private final String id;
    private final String name;
    private final String description;
    private final Type type;
    private final double value;

    public UpgradeOption(String id, String name, String description, Type type, double value) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.value = value;
    }

    public static UpgradeOption fromJson(JSONObject obj) {
        return new UpgradeOption(
            obj.getString("id"),
            obj.getString("name"),
            obj.getString("description"),
            Type.valueOf(obj.getString("type")),
            obj.getDouble("value")
        );
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Type getType() { return type; }
    public double getValue() { return value; }

    @Override
    public String toString() {
        return name + " — " + description;
    }
}
