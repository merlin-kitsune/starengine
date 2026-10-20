# StarEngine

[English](README.md) | **中文**

`starengine` — **Astral Dice（星之骰戏）** 系列模组共用的引擎库。

由 [Astral Dice](https://github.com/) 的 1.20.1 / 1.21.1 双版本单仓中提取，目标是为后续
**Astral Dice Extra（星之骰戏：扩展）** 及同系列模组提供可直接复用的共享实现。

---

## 1. 核心设计约束

理解这三条即可理解本仓库全部结构决策：

### 1.1 库不注册任何注册表条目

物品、效果、附件、数据组件（DataComponent）、能力（Capability）**全部留在消费方 mod**。

这条约束的含义是其价值所在：库的引入与升级**不会改变任何 `ResourceLocation` 的归属**，
因此既不会损坏存档（物品/方块状态丢失），也不会破坏数据包（tag / 配方 / 战利品表引用）。

### 1.2 跨 MC 版本，共享源码而非共享产物

1.20.1（SRG 成员名 / Java 17）、1.21.1（Mojmap / Java 21）与 26.1.2（Mojmap / Java 25）
**不能共用同一份编译产物**。因此本项目不做单一 `common` Gradle 子项目，而是：

```
common/src/main/java        ← 共享源码目录（不是 Gradle 子项目）
neoforge-1.21.1/            ← 平台子项目：Java 21 · Mojmap + Parchment · ModDevGradle 2.0.141
forge-1.20.1/               ← 平台子项目：Java 17 · reobf(SRG) · ModDevGradle Legacy 2.0.144
neoforge-26.1.2/            ← 平台子项目：Java 25 · Mojmap（无 Parchment）· ModDevGradle 2.0.147
```

各平台子项目各自 `sourceSets.main.java.srcDir('../../common/src/main/java')`，
**同一份共享源码被编译三次**，各产出一个 jar。

### 1.3 共享源码的两条硬约束

`common` 下的源码必须同时通过各平台编译，因此：

| 约束 | 原因 | 由谁强制 |
|---|---|---|
| 不得使用 Java 21 独有语法（record pattern / switch pattern / `SequencedCollection` 等） | 同一份源码要由 Java 17 工具链编译 | `forge-1.20.1` 侧编译失败 |
| 只能使用三版 MC 都存在**且签名一致**的 API | 各版 API 差异面很大 | 任一平台编译失败 |

**版本分歧必须留在平台子项目中**，同名类各写一份。已落地的分歧示例：

| 分歧点 | neoforge-1.21.1 | forge-1.20.1 | neoforge-26.1.2 |
|---|---|---|---|
| 客户端帧差/渲染参数 | `DeltaTracker` + `GuiGraphics` | `float partialTick` | `DeltaTracker` + `GuiGraphicsExtractor` |
| 效果实例类型 | `Holder<MobEffect>` | `MobEffect` | `Holder<MobEffect>` |
| 自定义数据键 | `DataComponent`（原版组件体系） | `ItemDataKey`（Forge 扩展点） | `DataComponent` |
| 饰品集成 | 直接使用原版/NeoForge 能力 | `CuriosCompat` 适配器（**仅编译期**，非前置） | 直接使用原版/NeoForge 能力 |
| 标识符类 | `ResourceLocation` | `ResourceLocation` | `Identifier`（26.1 起改名） |
| 实体 tag 判定 | `EntityType#is(TagKey)` | `EntityType#is(TagKey)` | `EntityType#builtInRegistryHolder().is(TagKey)` |

> 最后一行的两处差异由 `platform/LoaderTags` 的 `isBoss(Entity)` 吸收，共享源码不感知。
> **历史例外（已于 `1.0.0-SNAPSHOT.5` 消除）**：`common/event/AstralEventType` 曾是唯一「三线无法同源」的
> 共享文件（26.1 起 `ResourceLocation` 改名 `Identifier`，而该 record 的组件类型直接用旧名，Java 无类型别名），
> 故当时用 `sourceSets.main.java.exclude` 把它排除在 26.1.2 编译之外。该事件框架三件套
> （`AstralEventType` / `EventContext` / `EventEffect`）已于 `1.0.0-SNAPSHOT.5` 随合并收尾整体删除
> （合并后消费方三线零引用），`neoforge-26.1.2/build.gradle` 的 exclude 行与之同批移除 —— **勿再加回**。

---

## 2. 加载器差异的收敛方式（shim）

共享源码中不允许出现加载器专有命名空间，差异统一由 `platform/` 下的 shim 吸收：

| shim | 作用 |
|---|---|
| `platform/LoaderEvent` | 抽象基类，两侧分别 `extends net.neoforged.bus.api.Event` / `net.minecraftforge.eventbus.api.Event`。共享的 `SignActiveTriggeredEvent` 只引用本类。 |
| `platform/LoaderTags` | `c:bosses` 通用 tag。NeoForge 取自 `net.neoforged.neoforge.common.Tags`，Forge 取自 `net.minecraftforge.common.Tags`。共享的 `BossEntityUtil` 只引用本类。 |

这样共享代码里**没有任何 `net.neoforged.*` / `net.minecraftforge.*` 字面量**，
新增平台时只需补一份 shim。

---

## 3. 配置：配置文件留在消费方，库只提供值快照 seam

`ModConfigSpec`（NeoForge）与 `ForgeConfigSpec`（Forge）不通用，而配置文件的读写与配置 GUI
**只能由各平台自己做**。因此配置**不**进库：配置文件（消费方侧的 `config/astral_dice-common.toml`）、
配置屏幕与配置项定义全部留在消费方 mod，库内只保留一层平台无关的 seam：

```
库（共享，common/）                                    消费方（各平台）
GameplayConfigValues（纯值快照 record）                config/ModCommonConfig（ModConfigSpec / ForgeConfigSpec）
GameplayConstants.applyConfig(GameplayConfigValues)   → @Mod 构造期注册配置，并在加载/重载时构造快照推送
```

- 库**不读配置文件、不持有配置 schema、不提供配置 GUI**：它只声明「哪些玩法数值可配置」
  （record 字段）以及如何应用（`applyConfig`）。
- 消费方注册自己的配置后，把当前值装进 `GameplayConfigValues` 推给 `GameplayConstants`；
  **改完配置不需重启**——`GameplayConstants` 的字段是非 final 的运行时读取，重新推送一次即生效。
- 由此 `common` 中既没有 `net.neoforged.*` / `net.minecraftforge.*` 字面量，也没有任何第三方
  配置库依赖；**三平台的必需前置都只剩加载器本身** —— Curios 在 forge 侧只是 `item/CuriosCompat` 的
  **编译期**依赖（`modCompileOnly`），不进 `mods.toml`，故不构成前置。
- **record 字段即契约**：消费方按位置构造该 record，增删/改序字段会让消费方**编译期**失败
  （这正是刻意用 record 而非松散 getter 集合的原因）。调整可配置项时必须两侧同步。

> 本库曾经把配置 schema 下沉进 `common`（Cloth Config AutoConfig，配置文件被改写为
> `config/astral_dice.json`）。该方案已整体撤回：Cloth Config 前置与 AutoConfig 相关类
> 全部移除，配置回归消费方自己的 **TOML**（`ModConfigSpec` / `ForgeConfigSpec`），
> 库只留下上述 seam。

---

## 4. 构建与发布

```bash
# 构建三个平台（只产出 jar；不再自动推送到整合包，见 §4.3）
./gradlew build

# 仅构建单平台
./gradlew :neoforge-1.21.1:build
./gradlew :forge-1.20.1:build
./gradlew :neoforge-26.1.2:build

# 发布到本地 Maven，供消费方 modImplementation 解析
./gradlew publishToMavenLocal
```

### 4.1 发布坐标

| 平台 | 坐标 | 发布产物 |
|---|---|---|
| NeoForge 1.21.1 | `com.merlinkitsune.starengine:starengine-neoforge-1.21.1:1.0.0` | `jar`（NeoForge 编译与生产同为 Mojmap，无需重映射） |
| Forge 1.20.1 | `com.merlinkitsune.starengine:starengine-forge-1.20.1:1.0.0` | `reobfJar`（**生产 SRG jar**） |
| NeoForge 26.1.2 | `com.merlinkitsune.starengine:starengine-neoforge-26.1.2:1.0.0` | `jar`（与 1.21.1 同理，Mojmap 无需重映射） |

> 三侧版本号**同号**，升级时三个 `gradle.properties` 必须一起改。

**Forge 侧必须发布 `reobfJar` 而非 `jar`**：消费方 MDG LegacyForge 在解析期会把生产 SRG jar
重映射为 dev Mojmap 命名；若发布未重混淆的 dev jar，生产环境会因成员名为 Mojmap 而 `NoSuchFieldError`。

### 4.2 消费方接入方式

```groovy
// NeoForge 1.21.1
repositories { mavenLocal() }
dependencies {
    implementation "com.merlinkitsune.starengine:starengine-neoforge-1.21.1:1.0.0"
}

// Forge 1.20.1
repositories { mavenLocal() }
dependencies {
    modImplementation "com.merlinkitsune.starengine:starengine-forge-1.20.1:1.0.0"
}

// NeoForge 26.1.2
repositories { mavenLocal() }
dependencies {
    implementation "com.merlinkitsune.starengine:starengine-neoforge-26.1.2:1.0.0"
}
```

本库目前只发布到 **mavenLocal**（无远程 maven），因此消费方要么本机 clone 本仓并
`./gradlew publishToMavenLocal`（CI 场景见 §4.4），要么由消费方在 `build.gradle` 里用 **JarJar 内嵌**
本库 —— `astral_dice` 现采此方式，故整合包里**不应**再单独放库 jar（见 §4.3）。

两侧 `mods.toml` 需声明为必需前置：

```toml
[[dependencies.<mod_id>]]
    modId="starengine"
    type="required"        # 1.20.1 Forge 用 mandatory=true
    versionRange="[1.0.0,2.0)"
    ordering="AFTER"
    side="BOTH"
```

> ⚠️ **`1.0.0` 起区间下界就写 `[1.0.0,2.0)`**（与 §6 的兼容性契约一致）。
> 注：快照时代这里必须写成 `[1.0.0-SNAPSHOT.<序号>,2.0)` —— 按 Maven `ComparableVersion` 语义，
> `1.0.0-SNAPSHOT.x < 1.0.0`（预发布限定符排在正式版本之前），故 `[1.0.0,2.0)` 曾**不含**任何快照版本，
> 游戏会以「缺失/不满足必需前置」拒绝加载。快照系列已终止，该口径仅用于解读旧 pin。
>
> ⚠️ **（快照时代口径，自 `1.0.0` 起已不适用）下界曾须精确到快照序号（如 `.10`）**，而不是停在
> `[1.0.0-SNAPSHOT,2.0)`：宽松区间会把**改名前的旧库 jar** 一并接受——它 `modId` 相同、版本号也可能相同，
> 但带的是旧包名 `com.merlinkitsune.starengine`，加载后必然 `NoClassDefFoundError` 崩溃；
> 收紧区间可把这种错配变成加载器层面的「缺必需前置」明确报错。
>
> 实测（`maven-artifact` 3.8.5，两侧加载器均走 `MavenVersionAdapter.createFromVersionSpec`）：
>
> | 区间 | `.1`（改名前） | `.2` | `.3` | `.4` | `.5` | `.6` | `.7` | `.10` | `1.0.0` | `1.1.0` | `2.0.0` |
> |---|---|---|---|---|---|---|---|---|---|---|---|
> | `[1.0.0,2.0)` ← **自 1.0.0 起采用** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ |
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
> （注：`.10` 一列验证序号按数值而非字典序比较；`.7` 列保留 —— 该号只发布过 mavenLocal、未进入
> git，在途修复已并入 `.10`，但同区间的判定结果仍是有效的参考。）

> 修改库代码后，消费方 Gradle 会缓存 mavenLocal 的解析结果。
> 若消费方未取到新版本，用 `--refresh-dependencies` 或 bump `lib_version`。

### 4.3 与整合包的关系（`pushToPack` 已于 2026-09-24 移除）

本库**不再**向任何整合包 `mods` 目录写入内容 —— 原先的 `pushToPack` 任务（`build` 末尾触发、
白名单 `packPushBranches = ['main']`、可 `-PdeployToPack` 强推）已整体删除，连同只服务于它的
`packModsDir` / `packPushBranches` / `forcePackPush` / `dotGitEntry` / `jarArchiveProvider`。

原因：消费方 `astral_dice` 自 2026-09-24 起改用 **JarJar 内嵌**本库。FML 的 JarInJar 选择器按
**modId** 识别同源件 —— 顶层 mod 列表里若已存在同名 mod，内嵌的那份会被丢弃
（选择器只留一条 WARN：`JarJarSelector … which was passed in as source`）。
⇒ 整合包里若再出现独立库 jar，它会被优先采用；版本比内嵌件旧时直接表现为
`NoSuchMethodError` / `NoClassDefFoundError`，且**很难定位**（现象与「库没随模组更新」完全相同）。

正确做法：**整合包 `mods` 目录里只放 `astral_dice-*.jar`**，库由它内嵌携带。

⚠️ **本仓库不提供任何 jar 下载**（2026-10-01 起的政策）。这里没有东西可下：Release 页只保留
**GitHub 自动生成的源码归档**，且 CI 会主动删除它发现的任何 jar 附件。若你确实需要一份独立 jar 做
A/B 测试，请自行从源码构建（`./gradlew build`，见 §4）。

### 4.4 CI / 自动 Release（GitHub Actions）

⚠️ **本仓库不分发任何 jar —— 只保留源码**（2026-10-01 起的政策，理由见 §4.3）。
CI 仍会构建全部平台，但那只是**编译校验**：既不上传构建产物，也不往 Release 挂 jar；一旦发现
Release 上存在 jar 附件就删除。（原因不是「舍不得给」：一份被单独下载的库会**静默盖掉**消费方模组
内嵌的那一份，而后者的故障极难定位 —— 见 §4.3。）

仓库托管于 <https://github.com/merlin-kitsune/starengine>，工作流 `.github/workflows/build.yml`
沿用消费方 Astral Dice 的发布规范：

| 触发 | 行为 |
|---|---|
| push / PR / 手动 | 三套 JDK toolchain（21 / 17 / 25）→ `./gradlew build` —— **只做编译校验：什么都不上传**（jar 留在 runner 上） |
| push 到 `main` 且版本号为**正式版** | 自动打 tag（tag = 基础版本号，如 `1.0.0`，无 `v` 前缀、无 `+加载器` 后缀） |
| push tag `/^[0-9]/` | 创建/更新 GitHub Release —— **附件：无**（只有源码归档）；发现 jar 附件即删除 |

> tag 规则比消费方多一道守卫：版本号含 `-`（即快照）时**不打 tag**。
> 否则 `1.0.0-SNAPSHOT.2` 会被 `%%-*` 截成 `1.0.0` 并自动打出正式 tag，把未定型的快照误标为发布。

**消费方 CI 依赖本库**：Astral Dice 的 `build.yml` 会先 checkout 本仓并
`./gradlew publishToMavenLocal`，再构建自身——因为本库只在 mavenLocal 发布，CI 上无法直接解析。
两者版本号必须对齐（消费方 `starengine_version` ↔ 本库 `lib_version`），否则消费方 CI 断在依赖解析。
⚠️ 该 checkout 用**提交 SHA 钉死**（消费方 `build.yml` 的 `ref:` 行），且必须指向「三平台 `gradle.properties`
版本号 = 消费方 `starengine_version`」的那一个提交 —— 库每次 bump 都要同步改消费方该行；
库提交尚未推送到远端时，消费方 CI 无法检出（本地不受影响）。

---

## 5. 目录内容

```
common/src/main/java/com/merlinkitsune/starengine/  # 共享源码（28 个文件）
├── client/       ClientDamageNumbers
├── component/    GameplayConfigValues（配置值快照）, GameplayConstants
├── effect/       18 个 MobEffect 实现（ReadyEffect 已于 1.0.0-SNAPSHOT.5 按期删除）
├── event/        EventTargetCollector（1.0.0-SNAPSHOT.5 起只剩团队收集）
│                 AmethystDiceHandler / SignActiveTriggeredEvent
├── item/         BossEntityUtil
└── target/       TargetSelectionAction / TargetSelectionRegistry / TargetType

neoforge-1.21.1/src/main/java/.../starengine/       # 平台专有（5 个文件）
├── StarEngineLib          @Mod 入口
├── client/ActionBarManager        (DeltaTracker)
├── event/ModEffectRemoval         (Holder<MobEffect>)
└── platform/LoaderEvent, LoaderTags

forge-1.20.1/src/main/java/.../starengine/          # 平台专有（7 个文件）
├── StarEngineLib          @Mod 入口
├── client/ActionBarManager        (float partialTick)
├── component/ItemDataKey
├── event/ModEffectRemoval         (MobEffect)
├── item/CuriosCompat      (Curios 仅编译期依赖;库自身不调用它)
└── platform/LoaderEvent, LoaderTags

neoforge-26.1.2/src/main/java/.../starengine/       # 平台专有（5 个文件）
├── StarEngineLib          @Mod 入口
├── client/ActionBarManager        (DeltaTracker + GuiGraphicsExtractor)
├── event/ModEffectRemoval         (Holder<MobEffect>)
└── platform/LoaderEvent, LoaderTags（含 isBoss(Entity)，吸收 26.1 的 tag API 变更）
```

**关于 `StarEngineLib`**：入口类的唯一作用是让本库成为可被加载的 mod（`@Mod`），
它**不注册任何东西**。这正是「不改变 ResourceLocation 归属」的技术前提。

---

## 6. 版本与兼容

- **兼容性契约（自 `1.0.0` 起生效，三平台一体适用）**：库版本遵循 semver，**同一主版本号内（第一位数字不变，
  当前 = `1.x`）禁止任何破坏性更新** —— 不得删除或改名任何 public 类型、方法、字段、常量，也不得改变其
  可见性、签名或既有语义；只允许**新增**（新类型、新成员、新可选入口）与不改变契约的行为修正。
  确需破坏性变更时**必须**把第一位加一（`1.x` → `2.x`），并在同一次发布里收紧消费方区间下界 ——
  破坏性变更**不允许**藏在次版本/补丁位里。
  ⇒ 消费方 `mods.toml` 声明 `versionRange="[1.0.0,2.0)"`，即本契约的机器可读表达：任何 `1.x` 版本都可原位替换。
- **当前版本 = `1.0.0`**。它是首个正式版（由快照系列的最后一档 `1.0.0-SNAPSHOT.16` 规范化而来，
  **库内 Java 源码零改动**，仅版本号去掉 `-SNAPSHOT` 限定符）。消费方三线的 `starengine_version`
  与 `starengine_version_range=[1.0.0,2.0)` 与此对应。**不存在 `1.0.1`** —— 见下条。
- **2026-09-22 并入 `1.0.0`：移除 forge 侧的 Curios 前置**（用户裁决「使其与另外两个版本保持一致」）。
  本改动原拟作独立的 `1.0.1` 发版，推送/发布之前已并入 `1.0.0` ⇒ **`1.0.1` 无 tag、无 Release、无产物**；
  既有 `1.0.0` tag 名称不变，其 Release 附件由 CI 同批刷新。forge 的 `mods.toml` 删掉 `modId="curios"`
  依赖块，该侧依赖降为 **`modCompileOnly`** —— `item/CuriosCompat` 只在**编译期**需要 Curios，
  而库自身零调用它（它只是给消费方用的 shim）。⇒ **三个平台的库 jar 都不再声明 Curios 前置**
  （neo 两线本来就没有）；**调用该 shim 的消费方自己声明 curios 必需**
  （`astral_dice` 的 1.20.1 侧已声明 `mandatory=true [5,6)`）。本次是**纯放宽**，
  不删/不改任何 public 类型、方法、字段、可见性、签名或语义 ⇒ 1.x 内合法；
  唯一代价：若某消费方漏声明却调用该 shim，失败形态会从加载器的「缺必需前置」明确报错
  退化为运行期 `NoClassDefFoundError`。
- **2026-09-23 并入 `1.0.0`：补齐 `neoforge-26.1.2` 缺失的平台实现** —— 同样原拟作独立的 `1.0.1` 发版，
  推送前经用户裁决并入 `1.0.0`。首个正式版只在 `forge-1.20.1`（`ForgeEconomyStorage`）与
  `neoforge-1.21.1`（`NeoForgeEconomyStorage`）提供了钱包账本的平台存储实现，26.1.2 线缺该类 ⇒ 该线
  `StarEngineEconomy.isAvailable()` 恒为 `false`（`/starcoin` 不注册、拾取星币不被吸收进钱包、余额条恒 0）。
  ⇒ 与上条同理，**`1.0.1` 从未推送、从未发布**，远端仍只有 tag `1.0.0`，其 Release 附件随本次 push 重刷。

- **快照系列（`1.0.0-SNAPSHOT.*`）已终止，不受上条契约保护** —— 快照之间二进制不兼容（改名前的 `.1`、
  含已删除配置类的 `.2`、缺 `ReadyEffect` 的 `.3`、仍带已删过渡符号的 `.4`、**loaderVersion 写错会被 FML
  整体拒载**的 `.5`），故快照时代的消费方曾必须把区间下界**精确到序号**，否则更早的旧库 jar 会被宽松区间
  接受，分别表现为 `NoClassDefFoundError`、编译期 `找不到符号`，或（`.5`）整个库 jar 被加载器拒载。
  该历史口径与实测矩阵保留在 §4.2，仅供解读旧 pin；**自 `1.0.0` 起不再需要精确序号**。
- **加载门槛修复记录（2026-09-17）**：`1.0.0-SNAPSHOT.6` 修掉「库自身被 FML 拒载」——
  `neoforge-1.21.1` 的 `loaderVersion` 槽（模板 `${loader_version_range}`）曾被填成 NeoForge 版本带
  `[21.1,21.2)`，而该槽比的是 **javafml 语言提供者版本**（本机 NeoForge 21.1.235 报 `4.0.42`），
  于是 FML 报 `needs language provider javafml:21.1 or above, and below 21.2 to load / We have found 4.0.42`
  并拒绝加载库 jar，消费方 `astral_dice` 随即被判「`starengine` is not installed」
  （崩溃报告：`run/1.21.1/crash-reports/crash-2026-09-17_20.41.28-fml.txt` 与 `…_20.42.17-fml.txt`）。
  现改为语言区间 `[1,)`（与消费方 `neoforge-1.21.1/gradle.properties:23` 同值）；NeoForge 版本带
  `[21.1,21.2)` 仍由模板里 `modId="neoforge"` 依赖承担（区间未变）。同批对齐 forge 侧的已文档化口径：
  `loaderVersion` 由 `[47,)` 收敛为 `[47,48)`（语言主系列区间），`modId="forge"` 依赖改用 build.gradle
  从 `forge_version` 派生的精确区间 `[47.4.10,48)`。库内 Java 源码零改动，仅元数据与模板注释。
- **资源包元数据修复记录（2026-09-17，随 `.10` 发布）**：给 `forge-1.20.1` 平台补上 **`pack.mcmeta`**
  （`forge-1.20.1/src/main/resources/pack.mcmeta`，`pack_format` 15，与消费方同名文件同形）。
  根因（读本机 Forge 1.20.1 源码取证）：`net.minecraftforge.client.loading.ClientModLoader#clientPackFinder`
  （行 154-165）对**每一个**模组文件调用 `Pack.readMetaAndCreate(…, PackType.CLIENT_RESOURCES, …)`，
  返回 `null` 时执行 `ModLoader.addWarning(new ModLoadingWarning(mod, ModLoadingStage.ERROR,
  "fml.modloading.brokenresources", …))` —— 该语言键的文案正是「File … failed to load a valid
  ResourcePackInfo」，而 `Pack.readMetaAndCreate` 在 jar 内**没有 `pack.mcmeta`**（或元数据无法按该
  PackType 解析）时返回 null；服务端 `net.minecraftforge.server.ServerLifecycleHooks`（行 214）对
  `SERVER_DATA` 做同一检查。现象：消费方 `run/1.20.1` 停在 Forge 的 `Warning while loading mods`
  界面（1 条警告，指向库 jar），`latest.log` 对应 `[net.minecraft.server.packs.repository.Pack/]:
  Missing metadata in pack mod:starengine`。除警告外，该模组的资源包**不会被注册**
  （库当前无资源，故无功能损失，但属静默失效面）。**只改 Forge 1.20.1**：NeoForge 1.21.1 / 26.1.2
  不走这两段代码（两侧运行日志无该警告），且各 MC 版本的 `pack_format` 不同，故其余平台的取值
  **不猜测**（需要时按各自版本客户端源码取值再补）。库内 Java 源码零改动。该修复原先以
  `1.0.0-SNAPSHOT.7` 为号在途（只发布过 mavenLocal、从未进入 git），按仓库「未发布版本的后续改动
  并入原条目」的约定已并入 **`1.0.0-SNAPSHOT.10`**（与消费方 `2.0.0-SNAPSHOT.10` 的 patch 段同号）。
- **过渡符号清理记录（合并收尾）**：`1.0.0-SNAPSHOT.5` 在主线合并完成后按计划删除了
  `EventTargetCollector.collectTargets` / `collectTeamTargets` / `collectMaids` / `isMaidOwnedBy`、
  `GameplayConstants.EVENT_RANGE` / `EVENT_APPLY_MAID` / `KOMACHI_EXTRA_PLAYS_CAP`、`effect/ReadyEffect`，
  以及事件框架三件套 `event/AstralEventType` / `EventContext` / `EventEffect`（并移除
  `neoforge-26.1.2/build.gradle` 里为 `AstralEventType` 留的 `sourceSets.main.java.exclude` 行）。
  **删除前逐符号 grep 举证**合并后消费方三线零引用（唯一的 `collectTargets` 命中是消费方
  `RandomCardHandler` 自己的同名方法）；三件套的删除以其消费方侧 2 处遗留 import 被清掉为前提
  （消费方提交 `7dc64cb`），故 `.5` 的库字节在该前置完成后**重新发布过**一次 —— 同版本号不同字节，
  消费方需以 `--refresh-dependencies` 重新解析。判定细节见 `temp/t5-cleanup-notes-20260917.md`。
- **下沉记录（Phase 1d）**：`1.0.0-SNAPSHOT.4` 把 `common/effect/ReadyEffect` 收进本库 —— 它是三线
  （`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2`）字节完全一致、且只依赖 MC API 与库内已有类的
  **自包含单元中唯一尚未进库者**；其余 52 个同源候选的排除理由（非三线一致 / 依赖消费方专有类 / mixin）
  逐条记录在 `temp/sink-manifest-20260917.md`。同次核对确认：主线在 dev-next 删除这批类之后对
  `GameplayConstants` / `EventTargetCollector` 等副本的改动**未被丢失**（库内副本已取主线的常量值，
  过渡符号按既有约定暂留），`BossEntityUtil` / `SignActiveTriggeredEvent` / `ModEffectRemoval` /
  `ActionBarManager` 的差异全部是 shim 与注释，属有意设计。
- **新平台记录**：`1.0.0-SNAPSHOT.3` 新增 **`neoforge-26.1.2`** 平台子项目（MC 26.1.2 / NeoForge
  26.1.2.109 / Java 25 / ModDevGradle 2.0.147 / 无 Parchment），使 `common` 由「两版同源」变为
  「**三版同源**」。为此把两处 26.1 平台差异下沉进 shim：标识符类 `ResourceLocation` → `Identifier`、
  实体 tag 判定 `EntityType#is(TagKey)` → `EntityType#builtInRegistryHolder().is(TagKey)`
  （后者由 `platform/LoaderTags#isBoss(Entity)` 吸收，共享源码不知情）。唯一无法同源的是
  `common/event/AstralEventType`（record 组件类型直接用旧名，Java 无类型别名），故该文件被
  `sourceSets.main.java.exclude` 排除在 26.1.2 之外 —— 它与其同族在消费方主线已删除，本库合并后即删。
- **变更记录**：`1.0.0-SNAPSHOT.4` 只**新增**类（`effect/ReadyEffect`），不删类、不改签名 ——
  但对「按 `.4` 编译的消费方」它是**加载期必需前置**：运行环境里若仍是 `.3` 的库 jar，会在加载期以
  `NoClassDefFoundError: com/merlinkitsune/starengine/effect/ReadyEffect` 崩溃，故消费方必须把区间
  下界抬到 `[1.0.0-SNAPSHOT.4,2.0)`；反向（用 `.3` 编译、环境为 `.4`）保持兼容。
- **破坏性变更记录**：`1.0.0-SNAPSHOT.3` 完全移除 Cloth Config 前置与库内公共配置模块
  （`StarEngineCommonConfig` / `StarEngineConfigs` / `LegacyCommonTomlImporter` / `StarEngineConfigScreen`），
  并把 `GameplayConfigValues` 由 13 字段收敛为 6 字段、`GameplayConstants.refresh()` 改为
  `applyConfig(GameplayConfigValues)`；事件框架三件套（`AstralEventType` / `EventContext` / `EventEffect`）
  与 `KOMACHI_EXTRA_PLAYS_CAP` 等「合并后即删」的过渡符号曾**暂留**（当时尚未合并主线的消费方仍在引用，删掉会让那一轮提交无法构建）。
  截至 `1.0.0-SNAPSHOT.5`：`KOMACHI_EXTRA_PLAYS_CAP` 与事件框架三件套均已按期删除（三件套以消费方
  侧遗留 import 清理完毕为前提，见 §1.3）。消费方需回归自己的 TOML 配置
  （`ModConfigSpec` / `ForgeConfigSpec`）并在配置加载后推送值快照（见 §3）。
  注意 `modId`（`starengine`）**未**变更，故整合包文件名不受影响。
- **破坏性变更记录**：`1.0.0-SNAPSHOT.2` 将 Java 包名由 `com.merlinkitsune.starengine`
  改为 `com.merlinkitsune.starengine`（maven group 同步改为 `com.merlinkitsune.starengine`）。
  消费方所有 `import` 必须同步改写，且必须把区间下界提到 `[1.0.0-SNAPSHOT.2,...)`。
  注意 `modId`（`starengine`）**未**变更，故 `mods.toml` 的依赖声明与整合包文件名不受影响。
- 共享源码中对 MC API 的使用受两侧编译期约束，破坏性变更会在**编译期**而非运行期暴露。

---

## 7. 许可

MIT License，见 [LICENSE](LICENSE)。
