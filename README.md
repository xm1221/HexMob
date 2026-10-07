# Hex Mob

[![powered by hexdoc](https://img.shields.io/endpoint?url=https://hexxy.media/api/v0/badge/hexdoc?label=1)](https://github.com/hexdoc-dev/hexdoc)

[中文](#中文) | [English](#english)

---

## 中文

Hex Casting（咒法学）附属模组，加入一批咒术主题的生物、一片被诅咒的结晶之地，以及一条从晶洞生灵一路通向大环的线索。

- 网页版手册：<https://yggdyy.github.io/HexMob>
- 源码：<https://github.com/yggdyy/HexMob>

### 支持版本

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| 加载器 | Fabric / Forge（Architectury 双端） |
| 模组版本 | 0.0.1.1 |
| 许可 | MIT |

### 依赖

- [Hex Casting](https://www.curseforge.com/minecraft/mc-mods/hexcasting)（必需）
- [Architectury API](https://www.curseforge.com/minecraft/mc-mods/architectury-api)（必需）
- [Cloth Config API](https://www.curseforge.com/minecraft/mc-mods/cloth-config)（必需）
- [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib)（必需，生物动画）
- [TerraBlender](https://www.curseforge.com/minecraft/mc-mods/terrablender)（Fabric 端，生成本模组生物群系）
- Fabric 端另需 Fabric API 与 Fabric Language Kotlin；Forge 端另需 Kotlin for Forge

### 内容一览

#### 结晶之地（The Crystal Land）

新增生物群系 `hexmob:crystal_spikes`（主世界），成就「探访结晶之地」在踏入时解锁。地表铺满板岩与巨大的紫水晶尖刺，并会生成成建制的板岩守卫；紫水晶晶洞与大环的竞技场都藏在这片土地里。

#### 板岩守卫

| 生物 | 说明 |
| --- | --- |
| 板岩兵 `guard_brute` | 士兵兼哨卫，主人未醒时与地上的石块无异 |
| 板岩弩手 `guard_archer` | 远程守卫 |
| 板岩傀儡 `guard_golem` | 体型更大的守卫 |

#### 晶洞生灵

| 生物 | 说明 |
| --- | --- |
| 活化图案 `stimulated_pattern` | 紫水晶晶洞里悬浮的咒法图案，友善；其研究产出活化石板与「转化活化图案」 |
| 尖叫紫水晶 `crying_amethyst` | 伪装成萌芽紫水晶，受击时发出恶魂般的嚎叫 |
| 咒念羊 `iota_sheep` | 把一枚 Iota 系进羊毛，颜色随 Iota 类型变化，剪毛可得结念绳 |
| 淬晶悦灵 `quench_allay` | 由淬灵晶碎片淬炼悦灵而来，可被法术操控，亦能以自身名义施法 |

#### 大环（Ur Circle）

结晶之地深处的浮空祭坛。被靠近才会苏醒：巨石升空、投掷石板与光束、召唤仆从，弹射物与爆炸近乎无效，实体 Iota 也对其失效。击败后可从其核心取出大环法术，并解锁手册条目与成就「卓伟回环」。

### 新增图案

均需在另一只手握着大环的核心时才能施放；另有一只悦灵（消耗淬灵晶碎片）与活化图案相关的图案，详见手册。

| 图案 ID | 名称 | 签名 | 说明 |
| --- | --- | --- | --- |
| `hexmob:ur_amethyst_trap` | 紫水晶封锁 | `entity →` | 召唤紫水晶困住目标，消耗 5 紫水晶粉 |
| `hexmob:ur_beam` | 大环之光 | `vector, entity, number →` | 自指定位置向目标放出光束直至时限耗尽，媒质消耗随持续时长累积 |
| `hexmob:ur_serpent` | 板岩冲击 | `vector, vector →` | 在指定位置召唤沿指定方向移动的板岩长蛇，沿途造成伤害，消耗 5 紫水晶粉 |
| `hexmob:ur_slate_projectile` | 石板弹 | `vector, vector →` | 自该处射出一枚石板弹，消耗 3 紫水晶粉 |
| `hexmob:transform_stimulated_pattern` | 转化活化图案 | `vector, entity, pattern →` | 把活化图案实体转化为活化石板，消耗 10 紫水晶粉 |
| `hexmob:quench_allay` | 转化淬晶悦灵 | `allay →` | 消耗一枚淬灵晶碎片，把悦灵淬炼为淬晶悦灵（随身物品保留） |
| `hexmob:quench_allay/move` | 提线木偶之策略 | `quench_allay, vector →` | 命令淬晶悦灵飞向指定点 |
| `hexmob:quench_allay/cast` | 傀儡师之策略 | `quench_allay, list →` | 让淬晶悦灵以自己的媒质施放给定的法术 |

### 新增物品与方块

| 物品 / 方块 | 说明 |
| --- | --- |
| 活化石板 `stimulated_slate` | 由 1 块石板 + 8 紫水晶粉合成。其上图案除用「转化活化图案」写入外不可更改；参与法术环时只放行媒质波、不执行图案，但为后续施放提供 **10% 媒质折扣**。同一法术环中出现两块笔迹相同的活化石板会触发事故 |
| 大环核心 `ur_circle_core` | 击败大环的掉落物，兼作大环法术的施法媒介 |
| 淬灵媒质立方 `everything_in_now` | 由大环核心 + 8 块淬灵晶块合成，极大容量的媒质容器，储存的媒质可随时以任意结晶态取出，并保留大环核心本身的功能 |
| 被侵染的启迪木板 / 面板 / 瓦 | 结晶之地与竞技场的结构建材 |

### 配置

生成于 `config/hexmob/server.toml`：

```toml
opTransformStimulatedPatternCost = 100000   # 转化活化图案的基础媒质消耗
opStimulatedSlateMediaDiscount = 0.1        # 活化石板的媒质折扣
stimulatedSlateBlacklist = []               # 不享受折扣的图案
stimulatedPatternSpawnRate = 0.5            # 晶洞中活化图案的生成率
cryingAmethystSpawnRate = 0.5               # 晶洞中尖叫紫水晶的生成率
urCircleScale = 1.0                         # 大环的属性缩放
```

### 手册

内容全部收录在 Hexxy 之书的新分类「奇妙的生物」下（`hexmob:hexmob`），条目随成就逐步解锁。手册提供中英双语文本，也可直接用 hexdoc 生成网页版。

### 从源码构建

```sh
./gradlew build               # 产物在 fabric/build/libs 与 forge/build/libs
./gradlew :fabric:runClient   # 开发环境运行
```

- 模块：`common`（双端通用）、`fabric`、`forge`
- 语言：Kotlin（Architectury + Fabric Language Kotlin / Kotlin for Forge）
- JDK 17，Gradle 版本见 `gradle/wrapper`

### 许可与署名

以 MIT 许可发布。Hex Casting 由 object-Object 及其贡献者开发，本模组与官方无隶属关系。

---

## English

An addon for Hex Casting that adds a set of hex-themed mobs, a cursed land of crystal, and a trail leading from the creatures of amethyst geodes all the way to the Ur Circle.

- Web book: <https://yggdyy.github.io/HexMob>
- Sources: <https://github.com/yggdyy/HexMob>

### Supported versions

| | |
| --- | --- |
| Minecraft | 1.20.1 |
| Loaders | Fabric / Forge (Architectury, both sides) |
| Mod version | 0.0.1.1 |
| License | MIT |

### Dependencies

- [Hex Casting](https://www.curseforge.com/minecraft/mc-mods/hexcasting) (required)
- [Architectury API](https://www.curseforge.com/minecraft/mc-mods/architectury-api) (required)
- [Cloth Config API](https://www.curseforge.com/minecraft/mc-mods/cloth-config) (required)
- [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) (required, mob animations)
- [TerraBlender](https://www.curseforge.com/minecraft/mc-mods/terrablender) (Fabric, biome generation)
- Fabric additionally needs Fabric API and Fabric Language Kotlin; Forge additionally needs Kotlin for Forge

### What's inside

#### The Crystal Land

A new overworld biome, `hexmob:crystal_spikes`, with an advancement — *Visiting the Crystal Land* — that unlocks the moment you set foot in it. Its surface is paved in slate and crowned with great amethyst spikes, and it spawns slate guards in formation; both amethyst geodes and the Ur Circle's arena are hidden somewhere in this land.

#### Slate guards

| Mob | Notes |
| --- | --- |
| Slate Soldier `guard_brute` | Soldier and sentinel alike; until their master wakes, they are as unremarkable as the rocks of this land |
| Slate Crossbowman `guard_archer` | Ranged guard |
| Slate Golem `guard_golem` | The larger guard |

#### Dwellers of the geodes

| Mob | Notes |
| --- | --- |
| Stimulated Pattern `stimulated_pattern` | A friendly hex pattern floating inside amethyst geodes; research on it yields the Stimulated Slate and Transform Stimulated Pattern |
| Crying Amethyst `crying_amethyst` | Disguised as budding amethyst; wails like a ghast when struck |
| Iota Sheep `iota_sheep` | Carries a single iota knotted into its fleece; the wool shifts colour with the iota's type, and shearing yields Thought-Knots |
| Quench Allay `quench_allay` | An allay quenched with a Shard of Quenched Allay; steered by spells with ease, and able to cast in its own name |

#### Ur Circle

A floating altar deep in the Crystal Land. It wakes only when approached: great stones dance in the air, it hurls slate and releases beams of light, and attendants gather about it. Projectiles and explosions barely touch it, and entity iotas fail against it. Fell it to take its spells from its core, and to unlock a book entry and the advancement *The Grand Ring*.

### New patterns

The Ur Circle patterns can only be cast while the other hand holds the Ur Circle Core. Patterns for the allay (costing a Shard of Quenched Allay) and for the Stimulated Pattern are also included — see the book for details.

| Pattern ID | Name | Signature | Notes |
| --- | --- | --- | --- |
| `hexmob:ur_amethyst_trap` | Amethyst Seal | `entity →` | Summons amethyst to trap the target. Costs 5 Amethyst Dust |
| `hexmob:ur_beam` | Ur Circle Light | `vector, entity, number →` | A beam extends from the given position toward the target until the time runs out; the media cost accrues with duration |
| `hexmob:ur_serpent` | Slate Impact | `vector, vector →` | Summons a slate serpent at the given position that moves along the given direction, harming what it passes. Costs 5 Amethyst Dust |
| `hexmob:ur_slate_projectile` | Slate Shot | `vector, vector →` | Fires a single slate shot from there. Costs 3 Amethyst Dust |
| `hexmob:transform_stimulated_pattern` | Transform Stimulated Pattern | `vector, entity, pattern →` | Transforms a Stimulated Pattern entity into a Stimulated Slate. Costs 10 Amethyst Dust |
| `hexmob:quench_allay` | Transform Quench Allay | `allay →` | Costs one Shard of Quenched Allay, and transmutes the allay into a Quench Allay, keeping what it carries |
| `hexmob:quench_allay/move` | Marionette's Gambit | `quench_allay, vector →` | Commands the Quench Allay to fly to that point |
| `hexmob:quench_allay/cast` | Puppeteer's Gambit | `quench_allay, list →` | Makes the Quench Allay cast the given spell with its own media |

### New items and blocks

| Item / block | Notes |
| --- | --- |
| Stimulated Slate `stimulated_slate` | Crafted from 1 slate + 8 Amethyst Dust. Its pattern can only be written with Transform Stimulated Pattern; taking part in a spell circle, it lets the media wave pass through without executing anything, but grants a **10% media discount** to the executions that follow. Two Stimulated Slates with the same pattern stroke in one circle cause a mishap |
| Ur Circle Core `ur_circle_core` | Dropped by the Ur Circle; also the focus that lets you cast the Ur Circle spells |
| Quenched Media Cube `everything_in_now` | Crafted from an Ur Circle Core + 8 Quenched Allay blocks; a media container of enormous capacity whose stored media can be drawn out in any crystalline form at any moment, and which keeps the Ur Circle Core's own function |
| Infested Edified Planks / Panel / Tile | Building blocks of the Crystal Land and its arena |

### Configuration

Generated at `config/hexmob/server.toml`:

```toml
opTransformStimulatedPatternCost = 100000   # base media cost of Transform Stimulated Pattern
opStimulatedSlateMediaDiscount = 0.1        # media discount of Stimulated Slate
stimulatedSlateBlacklist = []               # patterns the discount does not apply to
stimulatedPatternSpawnRate = 0.5            # Stimulated Pattern spawn rate in geodes
cryingAmethystSpawnRate = 0.5               # Crying Amethyst spawn rate in geodes
urCircleScale = 1.0                         # stat scaling of the Ur Circle
```

### The book

Everything above is recorded in Hexxy's Book under a new category, *Amazing Mobs* (`hexmob:hexmob`), with entries unlocking as advancements are earned. The book text is available in both Chinese and English, and can also be built into a website with hexdoc.

### Building from source

```sh
./gradlew build               # artifacts in fabric/build/libs and forge/build/libs
./gradlew :fabric:runClient   # run the dev client
```

- Modules: `common` (both loaders), `fabric`, `forge`
- Language: Kotlin (Architectury + Fabric Language Kotlin / Kotlin for Forge)
- JDK 17; see `gradle/wrapper` for the Gradle version

### License and credits

Released under the MIT license. Hex Casting is developed by object-Object and contributors; this mod is not affiliated with it.
