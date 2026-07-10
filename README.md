# RougeVolley 🏰

**俯视角射击 + 随机拼接房间 + 属性叠加成长** 的 Roguelike 地牢 Demo。

基于 [FXGL 21.1](https://github.com/AlmasB/FXGL)（JavaFX 游戏框架）构建，采用自定义 Entity-Component System (ECS)、程序化地牢生成和事件驱动架构。

---

## 🎮 游戏概述

- **俯视角射击**：WASD 移动，鼠标瞄准射击
- **程序化地牢**：6 个房间随机拼接（3×2 网格），每次开局都不同
- **5 种房间模板**：十字路口、横走廊、竖走廊、L 型房间、大厅
- **传送门系统**：走廊 ↔ 房间类型互传，每个门分配到唯一目标
- **Roguelike 成长**：清空房间敌人后三选一升级，属性叠加
- **敌人 AI**：巡逻/追击双状态，墙壁回避滑行
- **种子随机**：所有生成确定性可重放

---

## 🚀 快速开始

### 环境要求

- **JDK 21** 或更高
- **Maven 3.8+**

### 运行游戏
- 从源代码进入
```bash 
# 编译
mvn clean compile

# 运行（通过 JavaFX Maven Plugin）
mvn javafx:run

# 打包为 JAR
mvn clean package
```
-直接安装release也可运行  
-也可直接在 IDE 中运行 `org.example.Main` 主类。

---

## 🧱 项目架构

```
src/main/java/org/example/rougevolley/
├── RougeVolleyFXGL.java          # FXGL 入口，游戏主循环
├── config/
│   └── GameConfig.java           # 全局配置常量（窗口、物理、地牢参数）
├── core/
│   ├── GameEvent.java            # 事件总线常量（EventBus）
│   └── GameState.java            # 全局状态（实体注册、时间、暂停）
├── ecs/
│   ├── Component.java            # 组件接口（onAttach/onUpdate/onDetach）
│   ├── Entity.java               # 实体容器（UUID + 组件映射）
│   ├── EntityType.java           # 实体类型枚举
│   └── components/
│       ├── PlayerComponent.java   # 玩家（速度、尺寸）
│       ├── EnemyComponent.java    # 敌人（AI状态、检测半径）
│       ├── HealthComponent.java   # 生命值（受伤、恢复、死亡判定）
│       ├── WeaponComponent.java   # 武器属性（射速、弹丸、散射、伤害）
│       ├── MovementComponent.java # 移动速度向量
│       └── PickupComponent.java   # 可拾取道具
├── entity/
│   └── EntityFactory.java        # 实体工厂（玩家/敌人/Boss/子弹/道具）
├── dungeon/
│   ├── RoomTemplate.java         # 房间模板（Tiled JSON / 紧凑格式 / 内建）
│   ├── RoomPool.java             # 模板池管理（随机选取）
│   ├── Room.java                 # 运行时房间实例（坐标、敌人、门状态）
│   ├── DungeonGenerator.java     # 地牢生成器（3×2 网格 + 门连接验证）
│   ├── TeleportMapping.java      # 传送门映射（走廊↔房间配对）
│   ├── SimpleEnemyAI.java        # 敌人AI组件（巡逻/追击 + 墙壁回避）
│   └── TileRenderer.java         # Tile渲染器（地板/墙壁/门节点管理）
├── combat/
│   ├── WeaponSystem.java         # 射击逻辑（冷却、散布、子弹生成）
│   └── DamageSystem.java         # 碰撞检测与伤害结算
├── roguelike/
│   ├── UpgradeOption.java        # 升级选项数据结构
│   ├── UpgradeManager.java       # 升级池管理（JSON加载 + 随机三选一）
│   └── PlayerStatsModifier.java  # 属性修改应用器
└── ui/
    ├── GameUI.java               # HUD 血条、升级面板、暂停菜单
    └── MenuFactory.java          # 开始界面/GameOver 界面
```

### 数据文件

```
src/main/resources/
├── data/
│   ├── RoomTemplates.json        # 5 种房间模板（紧凑字符网格格式）
│   └── Upgrades.json             # 5 种升级项配置
└── assets/textures/
    ├── player.png                # 玩家精灵（16×16→32×32）
    ├── undead.png                # 敌人精灵
    ├── bullet.png                # 子弹精灵
    ├── floor.png                 # 地板纹理
    ├── wall.png                  # 墙壁纹理
    └── door.png                  # 门纹理
```

---

## 🏗️ 核心设计

### Entity-Component System

- **Entity**：UUID 标识，持有 `Map<Class<?>, Component>`，每类型最多一个组件
- **Component** 接口：`onAttach(Entity)` → `onUpdate(Entity, dt)` → `onDetach(Entity)`
- **EntityType** 枚举：`PLAYER` / `ENEMY_NORMAL` / `ENEMY_ELITE` / `ENEMY_BOSS` / `BULLET` / `PICKUP` 等
- 组件按类型存储，通过 `entity.getComponent(Class<T>)` 查询

### 地牢生成

1. **DungeonGenerator** 在 3×2 网格中放置 6 个房间
2. 起始房间 `(0,0)` 强制使用全方向连通模板（十字路口/大厅）
3. 相邻方向的房门配对连接（E↔W, S↔N），仅当双方都定义了对应方向的门
4. **TeleportMapping** 建立传送网络：走廊门 → 房间门，确保不重复目标
5. 房间激活时根据 `enemySpawns` 列表生成敌人实体

### 战斗系统

- **WeaponComponent** 存储所有武器属性（射速、弹丸数、散射角、伤害、弹速）
- **WeaponSystem.fire()** 按冷却判定生成子弹，支持多弹丸均匀散布
- **DamageSystem** 每帧遍历子弹-敌人对进行 AABB 碰撞检测
- 敌人接触玩家造成伤害，1 秒无敌时间保护

### 敌人 AI

- **PATROL** 模式：随机方向移动，每 2 秒或遇墙时换向
- **CHASE** 模式：向玩家方向移动，遇墙尝试垂直滑行
- Hysteresis 防抖：离开检测半径 ×1.3 后才切回巡逻

### Roguelike 成长

清空房间所有敌人后触发三选一升级：

| 升级 | 效果 |
|------|------|
| 急速 | 射速 +20% |
| 弹幕 | 弹丸数 +1，散射角 +5° |
| 生命恢复 | 回满生命，上限 +20 |
| 强力弹 | 伤害 +30% |
| 高速弹 | 弹速 +25% |

---

## 🎯 MVP 功能清单

- [x] WASD / 方向键 移动，鼠标瞄准射击
- [x] 6 个不同形状房间随机拼接（3×2 网格）
- [x] 至少 1 种敌人（Undead），含巡逻与追击 AI
- [x] 5 种升级选项，清空房间敌人后三选一
- [x] 属性叠加可感知（射速、弹丸数、伤害、弹速变化）
- [x] 玩家死亡 → GameOver 界面，可重新开始或返回主菜单
- [x] 开始界面、暂停菜单（含属性/升级展示）
- [x] ESC 暂停/继续
- [x] 像素精灵渲染（玩家/敌人/子弹/瓦片）
- [x] 种子确定性生成（可重放）

---

## 🔧 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 | 编程语言 |
| FXGL | 21.1 | 游戏引擎（实体、输入、物理、UI） |
| JavaFX | 21 | GUI 渲染 |
| Maven | 3.8+ | 构建管理 |
| org.json | 20231013 | JSON 配置解析 |
| JUnit 5 | 5.11 | 单元测试 |

---

## 📐 设计原则

- **所有可调参数** 集中在 `GameConfig`（无魔法数字）
- **跨模块通信** 通过 FXGL `EventBus` + `GameEvent` 常量（无直接方法调用）
- **实体标识** 基于 UUID；组件查询按 `Class<?>`（每实体每类型仅一个）
- **种子 RNG** 贯穿地牢生成、升级抽取，确保可重放性
- **CopyOnWriteArrayList** 保护实体列表的线程安全
- **容错回退**：JSON 加载失败 → 内建模板；纹理加载失败 → 纯色矩形

---

## 📝 开发笔记

### 添加新房间模板

编辑 `src/main/resources/data/RoomTemplates.json`，使用紧凑字符网格格式：

```json
{
  "id": "room_mynew",
  "name": "新房间",
  "widthTiles": 20,
  "heightTiles": 15,
  "tileWidth": 32,
  "tileHeight": 32,
  "pattern": ["W=墙 .="],
  "doors": { "N": {"x": 288, "y": 0, "w": 64, "h": 32} },
  "playerSpawn": {"x": 320, "y": 256},
  "enemySpawns": [{"x": 128, "y": 128}]
}
```

### 添加新升级类型

编辑 `src/main/resources/data/Upgrades.json`，在 `UpgradeOption.Type` 枚举中添加新类型，在 `PlayerStatsModifier.apply()` 中添加对应处理。

---

## 📄 许可

[![MIT License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

---

*Made with FXGL & Java 21*
