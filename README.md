# Pathswalked | 路自人开

> 世上本没有路，走的人多了，也便成了路。

Pathswalked 是一个 Spigot 插件。它会让玩家在草地上反复行走时，逐渐踩出一条自然形成的路径。

## 兼容性

| 项目 | 兼容范围 |
| --- | --- |
| Minecraft / Spigot API | 1.17+ |
| 已验证目标版本 | 1.17 ~ 26.1.2 及以上同 API 兼容版本 |
| Java 编译目标 | Java 16 |
| 较新服务端版本 | 1.21、26.1 通常可直接使用 |

## 功能特性

- 玩家反复走过草方块后，方块会逐步退化成道路。
- 路径形成顺序：

```text
草方块 → 泥土 → 砂土 → 草径
```

- 当前默认踩踏次数：

```text
草方块踩 4 次 → 泥土
泥土踩 6 次 → 砂土
砂土踩 8 次 → 草径
```

- 插件只会继续处理自己踩出来的路径。
- 不会影响村庄自然生成的草径。
- 不会影响玩家手动用锹铲出来的草径。
- 玩家飞行或骑乘时不会触发踩踏。
- 玩家只在真正跨到另一个方块时才会计数。
- 路径长时间没人走时会逐步恢复。

## 路径恢复机制

插件生成的路径如果长时间没人行走，会按相反顺序慢慢恢复：

```text
草径 → 砂土 → 泥土 → 草方块
```

每个阶段的恢复等待时间是随机的：

```text
1~3 个游戏日
```

恢复计时使用 Minecraft 世界时间，而不是现实时间。因此：

- 玩家睡觉跳过夜晚会加快恢复进度。
- 使用 `/time add` 推进时间也会影响恢复进度。
- 不同世界之间按各自世界时间独立计算。

## 性能设计

Pathswalked 针对高频移动事件和大量路径方块做了基础优化：

- 移动事件中只在玩家跨方块时处理。
- 尽量减少不必要的对象创建和重复查询。
- 恢复任务每次最多检查 256 个路径方块，避免单次扫描过多造成卡顿。
- 被检查但暂时不需要恢复的路径会轮转到队尾，保证大范围路径也能被公平处理。

## 安装方式

1. 下载或构建插件 jar。
2. 将 jar 放入服务端的 `plugins` 文件夹。
3. 重启服务器。
4. 在草地上反复行走，即可看到路径逐步形成。

## 构建方式

本项目使用 Maven 构建。

在项目根目录执行：

```bash
mvn clean package
```

构建完成后，插件文件位于：

```text
target/Pathswalked-1.0.0.jar
```

## 当前可调整参数

主要参数位于：

```text
src/main/java/cn/kafei/pathswalked/Pathswalked.java
```

包括：

```java
private static final int GRASS_TO_DIRT_STEPS = 4;
private static final int DIRT_TO_COARSE_DIRT_STEPS = 6;
private static final int COARSE_DIRT_TO_PATH_STEPS = 8;
private static final int MAX_RESTORE_BLOCKS_PER_CHECK = 256;
private static final long GAME_DAY_TICKS = 24_000L;
private static final long MIN_RESTORE_IDLE_TICKS = GAME_DAY_TICKS;
private static final long MAX_RESTORE_IDLE_TICKS = GAME_DAY_TICKS * 3L;
```

## 注意事项

- 当前路径记录保存在内存中，服务器重启后不会保留未恢复路径的记录。
- 因为只恢复插件记录过的路径，所以服务器重启前踩出的路径在重启后不会再被插件自动恢复。
- 如果你希望路径记录跨重启保存，可以后续加入数据持久化功能。

## 灵感来源

鲁迅《故乡》：

> 希望是本无所谓有，无所谓无的。这正如地上的路；其实地上本没有路，走的人多了，也便成了路。
