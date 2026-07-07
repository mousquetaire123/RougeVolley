**完整项目架构与开发流程**
目标是确保 5 人团队在一周内能协作落地一个可玩的 MVP，结构清晰、职责分离、易于并行开发。

---

## 🎯 项目愿景（一句话）
**俯视角射击 + 随机拼接房间 + 属性叠加成长** 的 Roguelike 地牢 Demo。

---

## 🧱 技术选型与核心框架
- **游戏引擎**：FXGL（JavaFX 游戏框架，内置实体系统、物理、动画、UI）
- **地图编辑**：Tiled Map Editor（手动预设房间）
- **数据格式**：JSON（房间模板配置、道具配置）
- **AI 辅助范围**：生成重复性代码（Getter/Setter、工厂方法）、JSON 配置文件、房间连接逻辑的伪代码验证

---

## 📁 项目代码结构（包划分）
```
com.yourteam.RougeVolley
├── RougeVolley.java                 // FXGL 入口，初始化 GameApplication
├── config
│   └── GameConfig.java            // 常量：窗口大小、物理参数、房间尺寸等
├── ecs
│   ├── Component                  // 实体组件基类
│   ├── ComponentType              // 组件类型枚举（Health, Weapon, EnemyAI...）
│   └── components
│       ├── PlayerComponent.java
│       ├── EnemyComponent.java
│       ├── HealthComponent.java
│       ├── WeaponComponent.java
│       ├── MovementComponent.java
│       └── PickupComponent.java
├── entity
│   └── EntityFactory.java        // 创建玩家、敌人、子弹、道具的工厂
├── dungeon
│   ├── RoomTemplate.java         // 单个房间模板（加载 Tiled 地图）
│   ├── RoomPool.java             // 预设房间池管理
│   ├── DungeonGenerator.java    // 简单迷宫行走器，连接房间
│   └── Room.java                 // 运行时房间实例（坐标、敌人列表、门位置）
├── combat
│   ├── WeaponSystem.java         // 射击逻辑、子弹属性
│   ├── DamageSystem.java         // 碰撞伤害处理
│   └── ProjectileControl.java   // 子弹移动与生命周期
├── ai
│   └── SimpleEnemyAI.java        // 两状态AI：巡逻/追击
├── roguelike
│   ├── UpgradeOption.java        // 升级选项数据结构
│   ├── UpgradeManager.java      // 升级池、三选一逻辑
│   └── PlayerStatsModifier.java // 属性叠加（射速、子弹数、伤害）
├── ui
│   ├── GameUI.java               // HUD、血条、升级选择界面
│   └── MenuFactory.java          // 开始界面、GameOver界面
└── data
    ├── RoomTemplates.json        // 房间模板配置
    └── Upgrades.json             // 升级项配置
```

---

## 🔁 核心架构设计（数据流与控制流）
### 1. 实体组件系统（简化版 ECS）
FXGL 原本就是基于实体的，我们只需**扩展 Component 模式**，避免逻辑全塞在 Entity 的子类里。

- **EntityFactory**：统一创建实体并附加组件，如：
  ```java
  Entity player = new Entity();
  player.addComponent(new PlayerComponent());
  player.addComponent(new HealthComponent(100));
  player.addComponent(new WeaponComponent(fireRate=0.5, bulletCount=1, spread=5));
  ```
- 每个系统（WeaponSystem, DamageSystem, SimpleEnemyAI）通过 FXGL 的 `onUpdate` 循环遍历拥有特定组件的实体。

### 2. 房间系统（模板拼接代替 BSP）
- **RoomTemplate**：从 Tiled 文件加载，记录了 `尺寸(20x15 tile)`、`门位置(上/下/左/右开)`、`初始敌人出生点`。
- **RoomPool**：一个集合，存放 5 种不同模板（十字路口、长走廊、小单间、L 型、大房间）。
- **DungeonGenerator**：  
  - 维护一个 `Map<Point, Room>` 已放置房间的网格坐标。  
  - 从起点 `(0,0)` 开始，循环 6 次：  
    1. 随机选一个已放置房间的**未使用的门**。  
    2. 随机选方向，计算相邻坐标，若无房间则从池中随机取一个模板实例化，并把门对齐。  
    3. 新房间的门与旧房间的门互相标记为“已连接”。  
  - 结果：一个6个房间互相咬合的地牢网格。  
- 切换房间：摄像机跟随玩家，当玩家通过门时，隐藏上一个房间的实体，加载当前房间的实体（敌人、道具），简单 `entity.setActive(false)` 控制。

### 3. 战斗系统：属性叠加武器
- **WeaponComponent** 是玩家的可修改属性集合：
  ```java
  double fireRate;     // 开火间隔，越小越快
  int bulletCount;     // 一次发射弹丸数
  float spreadAngle;   // 散射角（度）
  float damageMult;    // 伤害倍率
  ```
- **升级获取**时，直接修改 WeaponComponent 的属性并更新对应子弹生成逻辑。
- **子弹生成**：`WeaponSystem` 每 `fireRate` 秒触发，按 `bulletCount` 循环创建子弹实体，角度 = 指向鼠标方向 ± 散布。
- 子弹实体只有速度、伤害、存活时间，碰撞到敌人时调用 `DamageSystem`。

### 4. 敌人 AI：双状态轮询
- **EnemyComponent** 标记了状态（PATROL / CHASE）和移动速度。
- **SimpleEnemyAI**（继承 FXGL 的 `Component` 或单独系统）：
  ```java
  void onUpdate(double tpf) {
      if (distanceToPlayer < detectionRadius) {
          state = CHASE;
          moveTowards(player.getPosition());
      } else {
          state = PATROL;
          // 随机选择方向，每2秒换一次方向
          randomWalk(tpf);
      }
  }
  ```
- 不需要复杂寻路，直接用向量移动，遇到墙壁弹开即可（FXGL 物理碰撞自动处理）。

### 5. Roguelike 成长循环
- **升级触发**：玩家消灭一个房间内所有敌人后，弹出三选一界面（暂停游戏）。
- **UpgradeManager** 从 `Upgrades.json` 随机抽取3个不同的 `UpgradeOption`。
- 选项类型（仅3种，减少平衡成本）：
  1. **急速**：射速 +20%
  2. **弹幕**：子弹数量 +1，散射角 +5°
  3. **生命恢复**：恢复全部血量并提高生命上限 20 点
- **效果叠加**：直接修改 `WeaponComponent` 或 `HealthComponent`，非常直观。

---

## 🗓️ 团队冲刺任务拆解（并行分工）
**5 人团队建议角色分工**：
- **A（架构/主程）**：FXGL 入口、ECS 核心、系统调度、集成
- **B（玩法逻辑）**：战斗系统、子弹、碰撞、属性修改
- **C（地牢与敌人）**：房间模板制作、房间生成器、敌人 AI
- **D（UI/数据）**：UI 界面、JSON 配置、升级管理器
- **E（资源与测试）**：AI 生成像素美术、音效、打包、整体测试

### 1 - 框架与移动
- A: 搭建项目结构，完成 `DungeonApp` 启动，实体工厂，摄像机设置
- B: 玩家移动（WASD）、鼠标瞄准、基础子弹发射（单一子弹）
- C: 用 Tiled 制作第一个测试房间（一个空方框），加载到 FXGL 中显示
- D: 开始界面的占位 UI、血条占位
- E: 准备方块占位图，开始调研 AI 生成像素风素材的 Prompt

### 2 - 第一个房间可玩
- A: 完善组件通信，接入武器系统
- B: 子弹碰撞检测，敌人受伤/死亡（方块消失）
- C: 实现巡逻/追击 AI，将敌人放入房间模板
- D: 显示玩家血条、敌人血条（简易）
- E: 提供敌人、玩家简单精灵图（哪怕只有一个朝向）

### 3 - 地牢生成（关键突破）
- A: 设计 `Room` 类、`DungeonGenerator` 核心数据结构，统筹接口
- B: 保证门切换时实体状态正确，子弹跨房间处理
- C: **核心开发**：实现迷宫行走器，连接 6 个房间，摄像机平滑跟随
- D: 编写房间模板 JSON，由 C 调用加载
- E: 手动测试房间拼接，修正坐标，保证无缝衔接

### 4 - 核心循环闭环
- A: 集成升级系统，暂停/恢复游戏
- B: 实现属性修改（射速、弹丸数、散布）对子弹的影响
- C: 房间内敌人全灭检测，触发升级信号
- D: 制作三选一升级 UI，连接 `UpgradeManager`
- E: 升级后的数值验证，确保爽快感

### 5 - 收尾与展示
- A: 整合所有模块，修复集成 Bug
- B: 最终平衡调整（射速下限、弹丸数上限等，避免崩溃）
- C: GameOver 条件（血量归零），返回开始界面
- D: 完成开始界面、GameOver 界面，加入简单音效（AI 生成或免费素材）
- E: 导出可执行 JAR/EXE，录制演示视频，准备展示

---

## 📦 最小可交付功能清单（MVP 验证标准）
- [ ] WASD 移动，鼠标瞄准射击
- [ ] 6 个不同形状的房间随机拼接生成
- [ ] 至少 1 种敌人，会巡逻和追击
- [ ] 3 种升级选项，击败房间内所有敌人后弹出
- [ ] 属性叠加可明显感知（射速变化、子弹变多）
- [ ] 玩家死亡后显示 GameOver 界面，可重新开始
- [ ] 开始界面可以进入游戏
- [ ] 打包为可运行文件（无 IDE 依赖）

---

## 🤖 AI 辅助的具体切入点
- **JSON 生成**：描述房间连接需求，让 AI 生成符合格式的 `RoomTemplates.json` 示例。
- **重复性代码**：所有组件的 getter/setter、简单的系统遍历模板。
- **UI 样板代码**：FXGL 的 UI 按钮样式、菜单布局代码。
- **美术建议**：提供 Prompt，如“2D top-down pixel art dungeon tileset, 16x16, stone floor, wooden walls”，快速产出可用资源。

---

这个架构流程严格遵循“**砍内容、保框架**”的原则，每个模块都有明确的输入输出和并行点，即使地牢生成遇到困难，也能回退到手动拼接的保底方案，确保一周内必定产出可玩 Demo。