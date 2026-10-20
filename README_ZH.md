# StarEngine

[English](README.md) | **中文**

`starengine` 是 **Astral Dice（星之骰戏）** 系列模组共用的前置库。

它由 [Astral Dice](https://github.com/merlin-kitsune/astral_dice) 的 1.20.1 / 1.21.1 双版本单仓提取而来，供 **Astral Dice Extra（星之骰戏：扩展）** 及同系列模组复用同一套实现。

---

## 1. 核心设计约束

以下三条约束决定了本仓库的结构。

### 1.1 库不新增玩法注册内容

物品、效果、数据组件与能力全部留在消费方 mod。库不新增玩法注册内容，因此库的更新不会改变任何 `ResourceLocation` 的归属，也不会损坏存档或破坏数据包引用。

Fabric 是唯一由库持有附件的平台：`FabricEconomyStorage` 注册 `starengine:star_coin_wallet`（以及一个临时旧 id）作为钱包持久化后端。附件是实体数据，不是物品/方块/效果类注册表条目。其余三条 FML 线把钱包写进玩家 NBT（`ForgeData` / `NeoForgeData`）。

### 1.2 跨 MC 版本共享源码，不共享产物

1.20.1 Forge（SRG 成员名 / Java 17）、1.20.1 Fabric（Mojmap / Java 17）、1.21.1 NeoForge（Mojmap / Java 21）与 26.1.2 NeoForge（Mojmap / Java 25）不能共用同一份编译产物。本项目没有 `common` Gradle 子项目，而是：

```
common/src/main/java        <- 共享源码目录（不是 Gradle 子项目）
neoforge-1.21.1/            <- 平台子项目：Java 21 - Mojmap + Parchment - ModDevGradle 2.0.141
forge-1.20.1/               <- 平台子项目：Java 17 - reobf(SRG) - ModDevGradle Legacy 2.0.144
neoforge-26.1.2/            <- 平台子项目：Java 25 - Mojmap（无 Parchment）- ModDevGradle 2.0.147
fabric-1.20.1/              <- 平台子项目：Java 17 - Mojmap - Fabric Loom 1.14.10（Loader 0.19.5，Fabric API 0.92.12+1.20.1）
```

各平台子项目都加了 `sourceSets.main.java.srcDir('../../common/src/main/java')`，同一份共享源码被编译四次，各产出一个 jar。

### 1.3 共享源码的两条硬约束

`common` 下的源码必须通过每个平台的编译：

| 约束 | 原因 | 由谁强制 |
|---|---|---|
| 不得使用 Java 21 独有语法（record pattern / switch pattern / `SequencedCollection` 等） | 同一份源码要由 Java 17 工具链编译 | `forge-1.20.1` 或 `fabric-1.20.1` 编译失败 |
| 只能使用所有受支持 MC 版本都存在**且签名一致**的 API | 各平台 API 面不同 | 任一平台编译失败 |

**版本分歧留在平台子项目中**，同名类各写一份。当前分歧：

| 分歧点 | neoforge-1.21.1 | forge-1.20.1 | neoforge-26.1.2 | fabric-1.20.1 |
|---|---|---|---|---|
| 客户端帧差/渲染参数 | `DeltaTracker` + `GuiGraphics` | `float partialTick` + `GuiGraphics` | `DeltaTracker` + `GuiGraphicsExtractor` | `float partialTick` + `GuiGraphics` |
| 效果实例类型 | `Holder<MobEffect>` | `MobEffect` | `Holder<MobEffect>` | `MobEffect` |
| 自定义数据键 | `DataComponent`（原版组件体系） | `ItemDataKey`（Forge 扩展点） | `DataComponent` | `ItemDataKey`（NBT 实现） |
| 饰品集成 | 原版/NeoForge 能力 | `CuriosCompat` 适配器（**仅编译期**，非前置） | 原版/NeoForge 能力 | `TrinketsCompat` 适配器（**仅编译期**，非前置） |
| 标识符类 | `ResourceLocation` | `ResourceLocation` | `Identifier`（26.1 起改名） | `ResourceLocation` |
| 实体 tag 判定 | `EntityType#is(TagKey)` | `EntityType#is(TagKey)` | `EntityType#builtInRegistryHolder().is(TagKey)` | `EntityType#is(TagKey)` |

最后两行由 `platform/LoaderTags#isBoss(Entity)` 吸收，共享源码不感知。

历史例外（已于 `1.0.0-SNAPSHOT.5` 消除）：`common/event/AstralEventType` 曾是唯一无法在当时三条线上同源编译的共享文件。它的 record 组件使用旧名 `ResourceLocation`，而 26.1 已把该类型改名为 `Identifier`，因此当时用 `sourceSets.main.java.exclude` 把它排除在 26.1.2 编译之外。事件框架三件套（`AstralEventType` / `EventContext` / `EventEffect`）已在 `1.0.0-SNAPSHOT.5` 合并收尾时删除，当时消费方各线零引用，`exclude` 行也在同批移除。勿再加回。

---

## 2. 加载器差异的收敛方式（shim）

共享源码不含加载器 import 或调用。差异由 `platform/` 下的 shim 吸收：

| shim | 作用 |
|---|---|
| `platform/LoaderEvent` | 抽象基类。NeoForge 继承 `net.neoforged.bus.api.Event`；Forge 继承 `net.minecraftforge.eventbus.api.Event`。Fabric 没有加载器级事件基类，故该侧是空抽象类，事件由消费方派发。共享的 `SignActiveTriggeredEvent` 只继承本类。 |
| `platform/LoaderTags` | `c:bosses` 通用 tag。NeoForge 取自 `net.neoforged.neoforge.common.Tags`；Forge 取自 `net.minecraftforge.common.Tags`。26.1.2 与 Fabric 直接构造 `TagKey`：26.1.2 是因为 `Identifier` 改名，Fabric 是因为没有平台级 tag 常量。共享的 `BossEntityUtil` 只调用 `isBoss(Entity)`。 |

共享源码里唯一的加载器包名是 `item/Rarity` 中指向 `net.neoforged.fml.common.asm.enumextension.EnumProxy` 的 Javadoc 链接；没有加载器 import，也没有运行期引用。新增平台就是新增一个带同一组 shim 的子项目：入口、`ActionBarManager`、经济存储、`EffectTimerAccess`、`ModEffectRemoval`、`AstralRarities`、两个平台 shim，按需再加 `ItemDataKey` 与饰品适配器。共享源码不改；2026-10-01 新增 Fabric 1.20.1 时 `common` 零改动。

---

## 3. 配置：配置文件留在消费方，库只提供值快照

`ModConfigSpec`（NeoForge）、`ForgeConfigSpec`（Forge）与 Fabric 的配置处理不通用，配置文件的读写与配置 GUI 只能由各平台自己做。因此配置不进库：配置文件（`config/astral_dice-common.toml`）、配置屏幕与配置项定义全部留在消费方 mod。库只保留一层平台无关的 seam：

```
库（共享，common/）                                    消费方（各平台）
GameplayConfigValues（纯值快照 record）                config/ModCommonConfig（ModConfigSpec / ForgeConfigSpec）
GameplayConstants.applyConfig(GameplayConfigValues)   -> @Mod 构造期注册配置，并在加载/重载时推送新快照
```

- 库不读配置文件、不持有配置 schema、不提供配置 GUI。它只声明哪些玩法数值可配置（record 字段）以及如何应用（`applyConfig`）。
- 消费方注册自己的配置后，把当前值装进 `GameplayConfigValues` 推给 `GameplayConstants`。改完配置不需重启：`GameplayConstants` 的字段是非 final 的运行时读取。
- `common` 不含加载器 import，也不含第三方配置库。三条 FML 平台的必需前置只有加载器本身。Fabric 还需要 Fabric API（含 `fabric-data-attachment-api-v1`），库的 `fabric.mod.json` 已声明这两项。Forge 的 Curios 与 Fabric 的 Trinkets 都只是适配类的 `modCompileOnly` 依赖（`item/CuriosCompat`、`item/TrinketsCompat`），不构成前置。
- **record 字段即契约。** 消费方按位置构造该 record，增删或改序字段会让消费方编译期失败。调整可配置项必须两侧同步。

本库曾经把配置 schema 放进 `common`（Cloth Config AutoConfig，写入 `config/astral_dice.json`）。该方案已撤回：Cloth Config 前置与 AutoConfig 相关类全部移除，配置回归消费方自己的 TOML，库只保留上述 seam。

---

## 4. 构建与发布

```bash
# 构建四个平台（只产出 jar；不再自动推送到整合包，见 §4.3）
./gradlew build

# 仅构建单平台
./gradlew :neoforge-1.21.1:build
./gradlew :forge-1.20.1:build
./gradlew :neoforge-26.1.2:build
./gradlew :fabric-1.20.1:build

# 发布到本地 Maven，供消费方 modImplementation 解析
./gradlew publishToMavenLocal
```

### 4.1 发布坐标

| 平台 | 坐标 | 发布产物 |
|---|---|---|
| NeoForge 1.21.1 | `com.merlinkitsune.starengine:starengine-neoforge-1.21.1:1.1.0-pre.1` | `jar`（Mojmap，无需重映射） |
| Forge 1.20.1 | `com.merlinkitsune.starengine:starengine-forge-1.20.1:1.1.0-pre.1` | `reobfJar`（**生产 SRG jar**） |
| NeoForge 26.1.2 | `com.merlinkitsune.starengine:starengine-neoforge-26.1.2:1.1.0-pre.1` | `jar`（Mojmap，无需重映射） |
| Fabric 1.20.1 | `com.merlinkitsune.starengine:starengine-fabric-1.20.1:1.1.0-pre.1` | `remapJar`（**生产 intermediary jar**） |

四平台共用同一个 `lib_version`。升版时四份 `gradle.properties` 一起改；`mod_version` 保留各自的平台后缀。

**Forge 侧必须发布 `reobfJar`，不能发 `jar`。** 消费方 MDG LegacyForge 在解析期会把生产 SRG jar 重映射为 dev Mojmap 命名。发布未重混淆的 dev jar 会让生产环境因成员名为 Mojmap 而抛 `NoSuchFieldError`。

**Fabric 侧必须发布 `remapJar`。** Loom 会把生产 intermediary jar 重映射为 dev 命名供 `modImplementation` 使用；dev jar 不能正确解析。

### 4.2 消费方接入方式

```groovy
// NeoForge 1.21.1
repositories { mavenLocal() }
dependencies {
    implementation "com.merlinkitsune.starengine:starengine-neoforge-1.21.1:1.1.0-pre.1"
}

// Forge 1.20.1
repositories { mavenLocal() }
dependencies {
    modImplementation "com.merlinkitsune.starengine:starengine-forge-1.20.1:1.1.0-pre.1"
}

// NeoForge 26.1.2
repositories { mavenLocal() }
dependencies {
    implementation "com.merlinkitsune.starengine:starengine-neoforge-26.1.2:1.1.0-pre.1"
}

// Fabric 1.20.1
repositories { mavenLocal() }
dependencies {
    modImplementation "com.merlinkitsune.starengine:starengine-fabric-1.20.1:1.1.0-pre.1"
}
```

本库在开发期发布到 **mavenLocal**，没有远程 Maven。消费方 clone 本仓并运行 `./gradlew publishToMavenLocal`（CI 场景见 §4.4）。玩家从 Modrinth 或 CurseForge 下载发行 jar（§4.3）。

消费方的依赖声明：

| 平台 | 声明方式 |
|---|---|
| NeoForge 1.21.1 / 26.1.2 | `neoforge.mods.toml`：`type="required"`、`ordering="AFTER"`、`side="BOTH"`；省略 `versionRange`，加载器只判存在 |
| Forge 1.20.1 | `mods.toml`：`mandatory=true`、`versionRange="*"`、`ordering="AFTER"`、`side="BOTH"`。省略 `versionRange` 会让必需依赖永远无法满足 |
| Fabric 1.20.1 | `fabric.mod.json` 的 `depends` 中写 `"starengine": ">=1.1.0-pre.1"` |

示例（NeoForge）：

```toml
[[dependencies.<mod_id>]]
    modId="starengine"
    type="required"
    ordering="AFTER"
    side="BOTH"
```

Forge 侧改用 `mandatory=true` 与 `versionRange="*"`，不写 `type="required"`。

历史说明：旧 pin 曾带版本区间。下表记录 Maven 对快照时代区间的判定，仅用于解读旧 pin，不是现行做法。实测使用 `maven-artifact` 3.8.5；两侧加载器均走 `MavenVersionAdapter.createFromVersionSpec`。

> | 区间 | `.1`（改名前） | `.2` | `.3` | `.4` | `.5` | `.6` | `.7` | `.10` | `1.0.0` | `1.1.0` | `2.0.0` |
> |---|---|---|---|---|---|---|---|---|---|---|---|
> | `[1.0.0,2.0)`（1.0.x 区间） | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ |
> | `[1.0,2.0)` | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ |
> | `[1.0.0-SNAPSHOT,2.0)` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
> | `[1.0.0-SNAPSHOT.2,2.0)` | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
> | `[1.0.0-SNAPSHOT.3,2.0)` | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
> | `[1.0.0-SNAPSHOT.4,2.0)` | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
> | `[1.0.0-SNAPSHOT.5,2.0)` | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
> | `[1.0.0-SNAPSHOT.6,2.0)` | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
> | `[1.0.0-SNAPSHOT.7,2.0)` | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ |
> | `[1.0.0-SNAPSHOT.10,2.0)` | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ❌ |
>
> `.10` 一列验证序号按数值而非字典序比较。`.7` 列保留，因为该号只发布过 mavenLocal、未进入 git，在途修复已并入 `.10`；同区间的判定仍有效。

> 修改库代码后，消费方 Gradle 会缓存 mavenLocal 的解析结果。若未取到新版本，bump `lib_version` 并重新发布；`--refresh-dependencies` 只清本地缓存。

### 4.3 分发与 JarJar 状态

`pushToPack` 已于 2026-09-24 移除。本库不再向任何整合包 `mods` 目录写入内容。该任务及其 `packPushBranches = ['main']` 白名单、`-PdeployToPack` 开关，以及只服务于它的 `packModsDir` / `packPushBranches` / `forcePackPush` / `dotGitEntry` / `jarArchiveProvider` 已全部删除。

分发渠道：

| 渠道 | 提供 jar | 说明 |
|---|---|---|
| GitHub Release | 否 | 只有源码归档；CI 会删除任何 jar 附件 |
| [Modrinth `2dIXA5wO`](https://modrinth.com/mod/2dIXA5wO) | 是 | `1.1.0-pre.1+neoforge_1.21.1`、`1.1.0-pre.1+forge_1.20.1` |
| [CurseForge `1755415`](https://www.curseforge.com/projects/1755415) | 是 | 本批上架 |

消费方（Astral Dice）的 JarJar 状态，自 2026-10-20 起：

| 消费方线 | 库的交付方式 | 玩家安装 |
|---|---|---|
| neoforge-1.21.1 | 无 JarJar；`implementation` 依赖 | 把 `starengine-neoforge-1.21.1-<版本>.jar` 放进 `mods/` |
| forge-1.20.1 | 无 JarJar；`modImplementation` 依赖 | 把 `starengine-forge-1.20.1-<版本>.jar`（生产 SRG jar）放进 `mods/` |
| neoforge-26.1.2 | JarJar 内嵌（待迁移） | 只放 `astral_dice-*.jar`；不要另加独立库 jar |
| fabric-1.20.1 | Loom `include` 内嵌（待迁移） | 只放 `astral_dice-*.jar`；不要另加独立库 jar |

两条内嵌线由 FML 与 Fabric Loader 按 modId 去重。独立库 jar 会优先于内嵌副本；版本较旧时表现为 `NoSuchMethodError` 或 `NoClassDefFoundError`，现象与「库没随模组更新」相同。这两条线的 `mods/` 只放消费方 jar。

1.21.1 与 1.20.1 必须单独安装库 jar。消费方已不再内嵌，不存在被覆盖的内嵌件。

需要独立 jar 做 A/B 测试时，从源码构建（`./gradlew build`，见 §4）；GitHub Release 页不提供 jar。

### 4.4 CI / 自动 Release（GitHub Actions）

GitHub Release 只提供源码。CI 会构建全部平台作为编译校验，但不上传构建产物，也不往 Release 挂 jar；发现 jar 附件即删除。jar 下载见 Modrinth 与 CurseForge（§4.3）。

仓库托管于 <https://github.com/merlin-kitsune/StarEngine>。工作流 `.github/workflows/build.yml` 沿用 Astral Dice 的发布规范：

| 触发 | 行为 |
|---|---|
| push / PR / 手动 | 三套 JDK toolchain（21 / 17 / 25）-> `./gradlew build`：只做编译校验，什么都不上传 |
| push 到 `main` 且版本号为正式版 | 自动打 tag（tag = 基础版本号，如 `1.0.0`，无 `v` 前缀、无 `+加载器` 后缀） |
| push tag `/^[0-9]/` | 创建或更新 GitHub Release：无附件（只有源码归档）；发现 jar 附件即删除 |

tag 规则比消费方多一道守卫：版本号含 `-`（快照或预发布）时不打 tag。否则 `1.1.0-pre.1` 会被 `%%-*` 截成 `1.1.0`，未定型的构建会被打成正式发布。

**消费方 CI 依赖本库。** Astral Dice 的 `build.yml` 先 checkout 本仓并运行 `./gradlew publishToMavenLocal`，再构建自身，因为本库没有远程 Maven。两者版本必须对齐（消费方 `starengine_version` 与本库 `lib_version`），否则消费方 CI 断在依赖解析。该 checkout 用提交 SHA 钉死（消费方 `build.yml` 的 `ref:` 行），必须指向「四份 `gradle.properties` 版本号 = 消费方 pin」的提交。库每次 bump 都要同步改该行。库提交尚未推送到远端时，消费方 CI 无法检出（本地不受影响）。

---

## 5. 目录内容

```
common/src/main/java/com/merlinkitsune/starengine/  # 共享源码（53 个文件）
├── client/       ClientDamageNumbers
├── combat/       CombatFormula, CreatureTargets, DiceBattleResolver, EliteTargets, HostileTargets,
│                 InternalDamageWindows, PartyRelations, PlayerHostilityTracker, TargetBattleStats,
│                 TargetCategory, VanillaMitigation
├── component/    GameplayConfigValues（配置值快照）, GameplayConstants
├── economy/      CommandPermissionGate, EconomyStorage, StarCoinCommand, StarCoinWalletState, StarEngineEconomy
├── effect/       18 个 MobEffect 实现（ReadyEffect 已于 1.0.0-SNAPSHOT.5 删除）
├── event/        AmethystDiceHandler, EffectTimerGuard, EventTargetCollector, SignActiveTriggeredEvent, TimerSource
├── item/         BossEntityUtil, Rarity
├── target/       HoldToSelect, SelectorTargets, SelfTargetable, SignSelectionGate,
│                 TargetSelectionAction, TargetSelectionRegistry, TargetType
└── thirdparty/   ApotheosisMarkers, StarengineThirdParty

neoforge-1.21.1/src/main/java/.../starengine/       # 平台专有（8 个文件）
├── StarEngineLib          @Mod 入口
├── client/ActionBarManager        (DeltaTracker)
├── economy/NeoForgeEconomyStorage
├── event/EffectTimerAccess        (Holder<MobEffect>)
├── event/ModEffectRemoval         (Holder<MobEffect>)
├── item/AstralRarities
└── platform/LoaderEvent, LoaderTags

forge-1.20.1/src/main/java/.../starengine/          # 平台专有（10 个文件）
├── StarEngineLib          @Mod 入口
├── client/ActionBarManager        (float partialTick)
├── component/ItemDataKey
├── economy/ForgeEconomyStorage
├── event/EffectTimerAccess        (MobEffect)
├── event/ModEffectRemoval         (MobEffect)
├── item/AstralRarities
├── item/CuriosCompat      (Curios 仅编译期依赖；库自身不调用它)
└── platform/LoaderEvent, LoaderTags

neoforge-26.1.2/src/main/java/.../starengine/       # 平台专有（8 个文件）
├── StarEngineLib          @Mod 入口
├── client/ActionBarManager        (DeltaTracker + GuiGraphicsExtractor)
├── economy/NeoForgeEconomyStorage
├── event/EffectTimerAccess        (Holder<MobEffect>)
├── event/ModEffectRemoval         (Holder<MobEffect>)
├── item/AstralRarities
└── platform/LoaderEvent, LoaderTags（含 isBoss(Entity)，吸收 26.1 的 tag API 变更）

fabric-1.20.1/src/main/java/.../starengine/         # 平台专有（10 个文件）
├── StarEngineLib          ModInitializer 入口
├── client/ActionBarManager        (float partialTick)
├── component/ItemDataKey
├── economy/FabricEconomyStorage
├── event/EffectTimerAccess        (MobEffect)
├── event/ModEffectRemoval         (MobEffect)
├── item/AstralRarities
├── item/TrinketsCompat    (Trinkets 仅编译期依赖；库自身不调用它)
└── platform/LoaderEvent, LoaderTags
```

**关于 `StarEngineLib`**：入口类让本库成为可加载的 mod（NeoForge/Forge 用 `@Mod`，Fabric 用 `ModInitializer`）。它不新增物品、方块、效果或其他玩法注册内容。各平台入口注入钱包存储；Fabric 侧该存储注册 `starengine:star_coin_wallet` 附件与 `/starcoin` 命令（FML 三线从 `RegisterCommandsEvent` 注册命令）。这是「不改变 `ResourceLocation` 归属」的技术前提。

---

## 6. 版本与兼容

- **兼容性契约（自 `1.0.0` 起生效，四平台一体适用）**：库版本遵循 semver，同一主版本号内（第一位数字不变，当前为 `1.x`）禁止任何破坏性更新。不得删除或改名任何 public 类型、方法、字段、常量，也不得改变其可见性、签名或既有语义；只允许新增（新类型、新成员、新可选入口）与不改变契约的行为修正。确需破坏性变更时，必须把第一位加一（`1.x` -> `2.x`），并在同一次发布里更新消费方 pin；破坏性变更不允许藏在次版本或补丁位里。
  契约不写进加载器版本区间。现行声明：NeoForge 两线用 `type="required"` 且省略 `versionRange`（只判存在）；Forge 用 `mandatory=true` 与 `versionRange="*"`；Fabric 用 `depends.starengine = ">=1.1.0-pre.1"`。实际安装版本由消费方的 `starengine_version` 钉死。`1.1.0-pre.1` 是预发布版，不能与 `1.0.x` 原位互换：它带有下方记录的破坏性改名。禁止破坏性变更的规则适用于正式的 `1.x` 版本，不覆盖预发布版的跨代切换。
- **当前版本 = `1.1.0-pre.1`**，四平台同号。四份 `gradle.properties` 的 `lib_version` 均为该值；`mod_version` 分别追加 `+neoforge_1.21.1`、`+forge_1.20.1`、`+neoforge_26.1.2` 或 `+fabric_1.20.1`。消费方四线的 `starengine_version=1.1.0-pre.1`。`1.0.0` 是首个正式版，由 `1.0.0-SNAPSHOT.16` 规范化而来，库内 Java 源码零改动。**不存在 `1.0.1`**，见下条。
- **2026-09-22 并入 `1.0.0`：移除 Forge 侧的 Curios 前置**（用户裁决）。本改动原拟作为独立的 `1.0.1` 发版，在推送或发布之前已并入 `1.0.0`，因此 **`1.0.1` 无 tag、无 Release、无产物**。Forge 的 `mods.toml` 删掉 `modId="curios"` 依赖块，该侧依赖降为 `modCompileOnly`。`item/CuriosCompat` 只在编译期需要 Curios，库自身零调用。**四个平台的库 jar 都不声明 Curios 前置。** 调用该 shim 的消费方自己声明 Curios（`astral_dice` 的 1.20.1 侧已声明 `mandatory=true [5,6)`）。这是纯放宽，在 `1.x` 内合法。唯一代价：消费方漏声明却调用该 shim 时，失败形态会从加载器的缺前置报错退化为运行期 `NoClassDefFoundError`。
- **2026-09-23 并入 `1.0.0`：补齐 `neoforge-26.1.2` 缺失的平台实现**。同样原拟作为独立的 `1.0.1` 发版，后并入 `1.0.0`。首个正式版只在 `forge-1.20.1`（`ForgeEconomyStorage`）与 `neoforge-1.21.1`（`NeoForgeEconomyStorage`）提供钱包账本的平台存储，26.1.2 的 `StarEngineEconomy.isAvailable()` 恒为 `false`（`/starcoin` 不注册、拾取星币不被吸收、余额条恒 0）。**`1.0.1` 无 tag、无 Release、无产物**；`1.0.0` tag 名称不变，其 Release 说明随本次 push 刷新。
- **快照系列（`1.0.0-SNAPSHOT.*`）已终止，不受上述契约保护。** 快照之间二进制不兼容（改名前的 `.1`、包名改名后的 `.2`、缺 `ReadyEffect` 且已移除配置模块的 `.3`、仍带后来被删过渡符号的 `.4`、`loaderVersion` 写错导致整个 jar 被 FML 拒载的 `.5`），所以快照时代的消费方必须把区间下界精确到序号。否则宽松区间会接受旧库 jar，分别表现为 `NoClassDefFoundError`、编译期 `找不到符号`，或（`.5`）整个库 jar 被加载器拒载。该历史口径与实测矩阵保留在 §4.2，仅供解读旧 pin；自正式版 `1.0.0` 起，正式 `1.x` pin 不再需要精确到快照序号，预发布 pin 仍会在下界写出具体预发布号。
- **加载门槛修复记录（2026-09-17）**：`1.0.0-SNAPSHOT.6` 修掉「库自身被 FML 拒载」。`neoforge-1.21.1` 的 `loaderVersion` 槽（模板 `${loader_version_range}`）曾填成 NeoForge 版本带 `[21.1,21.2)`，而该槽比的是 javafml 语言提供者版本（NeoForge 21.1.235 报 `4.0.42`）。FML 报 `needs language provider javafml:21.1 or above, and below 21.2 to load / We have found 4.0.42` 并拒绝加载库 jar，消费方 `astral_dice` 随即被判「`starengine` is not installed」（崩溃报告：`run/1.21.1/crash-reports/crash-2026-09-17_20.41.28-fml.txt` 与 `..._20.42.17-fml.txt`）。现改为语言区间 `[1,)`（与消费方 `neoforge-1.21.1/gradle.properties:23` 同值）；NeoForge 版本带 `[21.1,21.2)` 仍由模板里 `modId="neoforge"` 依赖承担。同批对齐 Forge 侧：`loaderVersion` 由 `[47,)` 收敛为 `[47,48)`，`modId="forge"` 依赖改用 build.gradle 从 `forge_version` 派生的精确区间 `[47.4.10,48)`。库内 Java 源码零改动，仅元数据与模板注释。
- **资源包元数据修复记录（2026-09-17，随 `.10` 发布）**：给 `forge-1.20.1` 平台补上 `pack.mcmeta`（`forge-1.20.1/src/main/resources/pack.mcmeta`，`pack_format` 15，与消费方同名文件同形）。根因（读本机 Forge 1.20.1 源码取证）：`net.minecraftforge.client.loading.ClientModLoader#clientPackFinder`（行 154-165）对每个模组文件调用 `Pack.readMetaAndCreate(..., PackType.CLIENT_RESOURCES, ...)`，返回 `null` 时执行 `ModLoader.addWarning(new ModLoadingWarning(mod, ModLoadingStage.ERROR, "fml.modloading.brokenresources", ...))`。该语言键的文案是「File ... failed to load a valid ResourcePackInfo」，而 jar 内没有 `pack.mcmeta`（或元数据无法按该 PackType 解析）时，`Pack.readMetaAndCreate` 返回 null。服务端 `net.minecraftforge.server.ServerLifecycleHooks`（行 214）对 `SERVER_DATA` 做同一检查。现象：消费方 `run/1.20.1` 停在 Forge 的 `Warning while loading mods` 界面，`latest.log` 出现 `[net.minecraft.server.packs.repository.Pack/]: Missing metadata in pack mod:starengine`。除警告外，该模组的资源包不会被注册。只改 Forge 1.20.1：NeoForge 1.21.1 / 26.1.2 不走这两段代码，且各 MC 版本的 `pack_format` 不同，其余平台的取值不猜测。库内 Java 源码零改动。该修复原先以 `1.0.0-SNAPSHOT.7` 为号在途（只发布过 mavenLocal、未进入 git），已并入 `1.0.0-SNAPSHOT.10`。
- **过渡符号清理记录（合并收尾）**：`1.0.0-SNAPSHOT.5` 删除了 `EventTargetCollector.collectTargets` / `collectTeamTargets` / `collectMaids` / `isMaidOwnedBy`、`GameplayConstants.EVENT_RANGE` / `EVENT_APPLY_MAID` / `KOMACHI_EXTRA_PLAYS_CAP`、`effect/ReadyEffect`，以及事件框架三件套 `event/AstralEventType` / `EventContext` / `EventEffect`（并移除 `neoforge-26.1.2/build.gradle` 里为 `AstralEventType` 留的 `sourceSets.main.java.exclude` 行）。删除前逐符号 grep 举证，当时消费方各线零引用（唯一的 `collectTargets` 命中是消费方 `RandomCardHandler` 自己的同名方法）。三件套的删除以消费方侧 2 处遗留 import 被清掉为前提（消费方提交 `7dc64cb`），所以 `.5` 的库字节在该前置完成后重新发布过一次：同版本号、不同字节，消费方需用 `--refresh-dependencies` 重新解析。判定细节见 `temp/t5-cleanup-notes-20260917.md`。
- **下沉记录（Phase 1d）**：`1.0.0-SNAPSHOT.4` 把 `common/effect/ReadyEffect` 收进本库。它是当时三条线（`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2`）字节完全一致、且只依赖 MC API 与库内已有类的自包含单元中唯一尚未进库者。其余 52 个同源候选的排除理由逐条记录在 `temp/sink-manifest-20260917.md`。同次核对确认：主线在 dev-next 删除这批类之后对 `GameplayConstants` / `EventTargetCollector` 等副本的改动没有丢失；`BossEntityUtil` / `SignActiveTriggeredEvent` / `ModEffectRemoval` / `ActionBarManager` 的差异全部是 shim 与注释。
- **新平台记录**：`1.0.0-SNAPSHOT.3` 新增 `neoforge-26.1.2` 平台子项目（MC 26.1.2 / NeoForge 26.1.2.109 / Java 25 / ModDevGradle 2.0.147 / 无 Parchment），`common` 由「两版同源」变为「当时三版同源」。为此把两处 26.1 平台差异下沉进 shim：标识符类 `ResourceLocation` -> `Identifier`，实体 tag 判定 `EntityType#is(TagKey)` -> `EntityType#builtInRegistryHolder().is(TagKey)`（后者由 `platform/LoaderTags#isBoss(Entity)` 吸收）。唯一无法同源的是 `common/event/AstralEventType`，它被排除在 26.1.2 之外，后来与其同族一起删除。
- **Fabric 平台记录（2026-10-01）**：新增 `fabric-1.20.1` 平台子项目（Fabric Loader 0.19.5 / Fabric API 0.92.12+1.20.1 / Java 17 / Loom 1.14.10）。它贡献 9 个接线类和 1 个入口类，`common` 零改动。`common` 现由四个平台子项目编译。
- **变更记录**：`1.0.0-SNAPSHOT.4` 只新增类（`effect/ReadyEffect`），不删类、不改签名。对按 `.4` 编译的消费方，它是加载期必需前置：运行环境里若仍是 `.3` 的库 jar，会在加载期以 `NoClassDefFoundError: com/merlinkitsune/starenginelib/effect/ReadyEffect` 崩溃，所以消费方必须把区间下界抬到 `[1.0.0-SNAPSHOT.4,2.0)`。反向（用 `.3` 编译、环境为 `.4`）保持兼容。
- **破坏性变更记录**：`1.0.0-SNAPSHOT.3` 完全移除 Cloth Config 前置与库内公共配置模块（`StarEngineCommonConfig` / `StarEngineConfigs` / `LegacyCommonTomlImporter` / `StarEngineConfigScreen`），把 `GameplayConfigValues` 由 13 字段收敛为 6 字段，并把 `GameplayConstants.refresh()` 改为 `applyConfig(GameplayConfigValues)`。事件框架三件套与 `KOMACHI_EXTRA_PLAYS_CAP` 等过渡符号曾暂留，因为当时尚未合并的消费方主线仍在引用。截至 `1.0.0-SNAPSHOT.5`，该 cap 与三件套均已按期删除。消费方需回归自己的 TOML 配置（`ModConfigSpec` / `ForgeConfigSpec`），并在配置加载后推送值快照（见 §3）。`modId`（当时为 `starengine_lib`）未变更，整合包文件名不受影响。
- **破坏性变更记录**：`1.0.0-SNAPSHOT.2` 将 Java 包名由 `com.merlinkitsune.starengine` 改为 `com.merlinkitsune.starenginelib`（maven group 同步）。消费方所有 `import` 必须改写，且必须把区间下界提到 `[1.0.0-SNAPSHOT.2,...)`。`modId`（当时为 `starengine_lib`）未变更，`mods.toml` 声明与整合包文件名不受影响。
- **改名记录（2026-10-19，破坏性）**：包名与 maven group `com.merlinkitsune.starenginelib` -> `com.merlinkitsune.starengine`；modId `starengine_lib` -> `starengine`；artifactId 前缀 `starengine_lib-` -> `starengine-`。消费方需改 import、`starengine_version` 与 JarJar 坐标。当前 `1.1.0-pre.1` 线使用新名。
- 共享源码对 MC API 的使用受每个平台的编译期约束，破坏性变更会在编译期而非运行期暴露。

---

## 7. 许可

MIT License，见 [LICENSE](LICENSE)。
