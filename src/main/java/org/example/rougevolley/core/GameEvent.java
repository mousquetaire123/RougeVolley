package org.example.rougevolley.core;

import javafx.event.Event;
import javafx.event.EventType;

/**
 * 游戏事件常量 —— 通过 FXGL EventBus 发布/订阅
 * <p>
 * 每个事件同时提供 String 标识（用于日志/调试）和 EventType（用于 EventBus）。
 * 所有跨模块通信必须使用此处定义的事件标识，不得使用硬编码字符串。
 */
public final class GameEvent {

    private GameEvent() {}

    // ── 引擎事件 ──
    public static final String ENGINE_INIT = "engine.init";
    public static final EventType<Event> ENGINE_INIT_EVENT = new EventType<>(Event.ANY, ENGINE_INIT);

    public static final String ENGINE_STOP = "engine.stop";
    public static final EventType<Event> ENGINE_STOP_EVENT = new EventType<>(Event.ANY, ENGINE_STOP);

    // ── 场景事件 ──
    public static final String SCENE_CHANGED = "scene.changed";
    public static final EventType<Event> SCENE_CHANGED_EVENT = new EventType<>(Event.ANY, SCENE_CHANGED);

    // ── 实体事件 ──
    public static final String ENTITY_SPAWNED = "entity.spawned";
    public static final EventType<Event> ENTITY_SPAWNED_EVENT = new EventType<>(Event.ANY, ENTITY_SPAWNED);

    public static final String ENTITY_DESTROYED = "entity.destroyed";
    public static final EventType<Event> ENTITY_DESTROYED_EVENT = new EventType<>(Event.ANY, ENTITY_DESTROYED);

    public static final String PLAYER_MOVED = "player.moved";
    public static final EventType<Event> PLAYER_MOVED_EVENT = new EventType<>(Event.ANY, PLAYER_MOVED);

    // ── 战斗事件 ──
    public static final String DAMAGE_DEALT = "combat.damage";
    public static final EventType<Event> DAMAGE_DEALT_EVENT = new EventType<>(Event.ANY, DAMAGE_DEALT);

    public static final String ENTITY_KILLED = "combat.kill";
    public static final EventType<Event> ENTITY_KILLED_EVENT = new EventType<>(Event.ANY, ENTITY_KILLED);

    public static final String BULLET_FIRED = "combat.bullet.fired";
    public static final EventType<Event> BULLET_FIRED_EVENT = new EventType<>(Event.ANY, BULLET_FIRED);

    // ── 地牢事件 ──
    public static final String DUNGEON_GENERATED = "dungeon.generated";
    public static final EventType<Event> DUNGEON_GENERATED_EVENT = new EventType<>(Event.ANY, DUNGEON_GENERATED);

    public static final String ROOM_ENTERED = "dungeon.room.entered";
    public static final EventType<Event> ROOM_ENTERED_EVENT = new EventType<>(Event.ANY, ROOM_ENTERED);

    public static final String ROOM_CLEARED = "dungeon.room.cleared";
    public static final EventType<Event> ROOM_CLEARED_EVENT = new EventType<>(Event.ANY, ROOM_CLEARED);

    public static final String ZONE_CHANGED = "dungeon.zone.changed";
    public static final EventType<Event> ZONE_CHANGED_EVENT = new EventType<>(Event.ANY, ZONE_CHANGED);

    // ── 物品事件 ──
    public static final String ITEM_PICKED_UP = "item.pickup";
    public static final EventType<Event> ITEM_PICKED_UP_EVENT = new EventType<>(Event.ANY, ITEM_PICKED_UP);

    // ── 升级事件 ──
    public static final String UPGRADE_TRIGGERED = "upgrade.triggered";
    public static final EventType<Event> UPGRADE_TRIGGERED_EVENT = new EventType<>(Event.ANY, UPGRADE_TRIGGERED);

    public static final String UPGRADE_SELECTED = "upgrade.selected";
    public static final EventType<Event> UPGRADE_SELECTED_EVENT = new EventType<>(Event.ANY, UPGRADE_SELECTED);

    // ── UI 事件 ──
    public static final String GAME_OVER = "ui.gameover";
    public static final EventType<Event> GAME_OVER_EVENT = new EventType<>(Event.ANY, GAME_OVER);

    public static final String GAME_RESTART = "ui.restart";
    public static final EventType<Event> GAME_RESTART_EVENT = new EventType<>(Event.ANY, GAME_RESTART);
}
