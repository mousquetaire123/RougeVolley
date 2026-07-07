# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RougeVolley is a roguelike dungeon exploration game built with **FXGL 21.1** (a JavaFX-based game framework) on **Java 21** with **Maven**. The project uses a custom Entity-Component System (ECS), BSP-based procedural dungeon generation, and an event-driven architecture via FXGL's EventBus.

## Build & Run

```bash
# Compile
mvn clean compile

# Run tests
mvn test

# Package as JAR
mvn clean package

# Run the game (after rebuild)
mvn javafx:run
# Or run the main class directly from IDE:
#   org.example.Main
```

## Architecture

The codebase was reset to a minimal stub (`Main.java` with a hello-world placeholder). The full architecture from git history (`ab8b5dd`) is the intended rebuild target:

### Entry Point & Core Loop
- `RougeVolleyFXGL` extends FXGL's `GameApplication` — the game entry point calling `launch(args)`.
- `GameConfig` — single source of truth for all tunable constants (viewport 1280×720, BSP depth, spawn weights, thread pool size, timestep).
- `GameLoop` — owns a `FixedTimestepLoop` (60Hz accumulator pattern) and a daemon thread pool (`THREAD_POOL_SIZE=4`) for async work. Drives scene updates at fixed step, decoupled from render frame rate.
- `GameState` — shared mutable state (seed, scene manager, entity registry). Seed-based RNG enables deterministic replays.
- `GameEvent` — string constants for the FXGL EventBus. All cross-module communication (entity spawn/destroy, combat, dungeon events, item pickup) uses these.

### ECS (Entity-Component System)
- `Entity` — UUID-identified container with `Map<Class<?>, Component>`. Components are stored by class, so only one of each component type per entity.
- `Component` interface — lifecycle hooks: `onAttach(Entity)`, `onUpdate(Entity, double dt)`, `onDetach(Entity)`.
- `EntityType` enum — categories: `PLAYER`, `ENEMY_NORMAL`, `ENEMY_ELITE`, `ENEMY_BOSS`, `ITEM_COMMON`, `ITEM_RARE`, `ITEM_LEGEND`.
- Concrete components: `Health`, `AIControl`, `Collidable`, `PhysicsComponent`, `PlayerControl`, `Pickup`, `CameraFollow`.

### Scene Management (Stack Model)
- `SceneManager` — `Deque<GameScene>` with push/pop/replace. Max depth capped by `GameConfig.SCENE_STACK_MAX_DEPTH` (5). Pushing pauses the current scene; popping resumes the one below. Atomic `replace()` = pop + push.
- `GameScene` — abstract base with lifecycle: `onInit()` (once), `onEnter()`/`onExit()` (every push/pop), `onUpdate(dt)`.
- Concrete scenes: `MenuScene`, `DungeonScene`.

### Dungeon Generation (BSP)
- `BSPGenerator` — recursively splits space with random split positions (40%-60% range), favoring the longer axis. Leaf nodes get rooms sized at 60%-90% of their bounding box. Connects sibling rooms with L-shaped corridors (random horizontal-first or vertical-first).
- Object pooling: `BSPNodePool` recycles `BSPNode` instances to reduce GC pressure.
- `DungeonData` is the output: tree root, room/corridor lists, map dimensions, generation seed.
- `SeedUtils` / seed-based `Random` throughout ensures identical output for the same seed.

### Entity Spawning
- `SpawnManager` populates a generated dungeon: boss + legendary item in the largest room, weighted enemy/item spawns in leaf rooms. Weights defined in `GameConfig`.
- `EntityFactory` — factory for creating pre-configured entities.
- `WeightedRandomPicker` — generic weighted random selection utility.

### AI & Difficulty
- `AStarPathfinder` — async A* pathfinding for enemy AI.
- `ZoneManager` — dynamic difficulty zones (safe/normal/danger) based on player position. Thresholds in `GameConfig`.

### Utility
- `ObjectPool<T>` — generic object pool.
- `CollisionUtils` — AABB and distance helpers.
- `NoiseGenerator` — Perlin-like noise for map variation.
- `SemanticTag` — string tagging for entity filtering/grouping.

## Current State (Post-Reset)

The working tree was reset to a minimal starting point:
- `Main.java` contains a placeholder hello-world with embedded Chinese comments describing the rebuild plan.
- The `pom.xml` retains FXGL 21.1 and JavaFX 21 dependencies; JUnit 5 was removed.
- The previous game code (97 files, ~14k lines) exists only in git history at commit `ab8b5dd` and can be referenced for architecture guidance.
- No test directory exists in the working tree.

## Rebuild Plan (from embedded comments in Main.java)

1. Create `RougeVolley` class extending FXGL `GameApplication`, override `initSettings()` for window size/title.
2. In `initGame()`, initialize the game world and spawn player.
3. Implement `EntityFactory` with `createPlayer()`.
4. Define `ComponentType` enum and base `Component` interface/class.
5. Verify by running the project — a black window with a movable scene confirms the framework is working.

## Key Conventions

- All tunable parameters go in `GameConfig` (no magic numbers).
- Cross-module communication uses FXGL `EventBus` with `GameEvent` string constants — never direct method calls across modules.
- The `FixedTimestepLoop` accumulator pattern ensures deterministic physics: logic always runs at 60Hz regardless of render FPS.
- Entity identity is by UUID; component lookup is by `Class<?>`. Only one component instance per type per entity.
- Scene transitions go through `SceneManager` — never instantiate or swap scenes directly.
