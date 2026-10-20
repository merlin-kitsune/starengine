# AGENTS.md — StarEngine（前置库）工程约定

> 本文件是本仓库的**唯一工程约定入口**（消费方 `astral_dice_multiloader` 有同名文件，两侧口径互相引用）。
> 新增约定请**就近插节**，不要另开文档；本文件纯中文、**CRLF** 行尾。

## 0. 仓库结构与硬约束

- 多加载器单仓，**没有 Gradle `common` 子项目**：`common/src/main/java` 是**共享源码目录**，被三个平台子项目各编译一次。
  - `neoforge-1.21.1`：Java 21 + Mojmap + ModDevGradle
  - `forge-1.20.1`：Java 17 + SRG(reobf) + ModDevGradle Legacy
  - `neoforge-26.1.2`：Java 25 + Mojmap（无 Parchment）+ ModDevGradle
- 包名 `com.merlinkitsune.starengine`；发布坐标 `com.merlinkitsune:starengine-<平台>:<lib_version>`。
- ⚠️ **`common` 只能使用「三个 MC 版本都存在且签名一致」的 API**：禁止 Java 21 独有语法（record pattern / switch pattern / `SequencedCollection` 等）。
  任何版本分歧（`DeltaTracker` / `float partialTick`、`Holder<MobEffect>` / `MobEffect`、`GuiGraphics` / `GuiGraphicsExtractor` …）
  **必须留在平台子项目**，同名类各写一份。
- 红线：本库**不注册注册表条目、不注册事件、不反向依赖消费方**；玩家数据持久化由消费方负责，库侧不引入 `AttachmentType` 等消费方专有依赖。

## 1. 版本号递增约定（每提交一版）— 必须遵守（2026-09-29 用户裁决）

**`next` 分支每提交一次，`lib_version` 与 `mod_version` 的 SNAPSHOT 号 +1 —— 含只动文档 / CI / 约定的提交，不设例外。**
（消费方 `multi-dev-next` 同规则，见其 `AGENTS.md` 同名节；两侧各自独立计数。）

- **真值字段**（三平台 `gradle.properties`，**两个字段必须同号同步 +1**，三平台**永远同号**）：
  - `lib_version=2.0.0-SNAPSHOT.<M>`
  - `mod_version=2.0.0-SNAPSHOT.<M>+<neoforge_1.21.1|forge_1.20.1|neoforge_26.1.2>`
- **消费方读取点**：`build.gradle` 的 `version = lib_version`（三平台各一份）与 CI 的
  `VERSION=$(grep '^lib_version=' neoforge-1.21.1/gradle.properties | cut -d'=' -f2)`
  ⇒ **只改上述两个字段，别动其它**；`mod_version` 只进产物名。
- **bump 必须发生在构建 / 发布之前**：否则 `build/libs/*.jar` 与 `~/.m2` 里留的都是旧版本号的件。
  正确顺序 = **改两个字段 → 两份 CHANGELOG 段头同步 → `build` → `publishToMavenLocal` → 提交**。
- **CHANGELOG 段头同批改**：两份 CHANGELOG 顶部的 `## 未发布（<版本号>）` / `## Unreleased (<版本号>)` 必须等于 bump 后的 `lib_version`。
  本约定属**工程口径**，**不写条目**（只改段头标题）。
- ⚠️ **为什么必须 bump**：本库版本**不以 `-SNAPSHOT` 结尾**（`2.0.0-SNAPSHOT.3` 是「已定版」形态）⇒ Gradle **不把它当 changing module**：
  不 bump 时消费方会一直命中 `mavenLocal` 里的旧 jar（还得 `--refresh-dependencies`）⇒ **改库必 bump + `publishToMavenLocal`**。
  同版本号会**就地覆盖**旧件，旧件漏网即隐性 `NoSuchMethodError`。

## 2. 与消费方的契约（改库前必读）

- 消费方三线（`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2`）用 `starengine_version` **硬 pin** 本库版本；
  具体 `versionRange` 写法以**消费方 `AGENTS.md` 与三线 `gradle.properties`** 为准（本文件不重述平台差异）。
- 本库经 **JarJar 内嵌**进消费方产物 ⇒ 改库后消费方**必须重跑构建与部署**，否则留旧内嵌件。
- 库 bump 是**跨仓库同一批义务**：消费方须在同一次提交里同步 ① `starengine_version` pin、
  ② `.github/workflows/build.yml` 的库 checkout `ref`（钉到**携带新版本的本库 commit sha**）、③ 三线重新构建部署。
- 消费方 CI 会先 checkout 本库并 `publishToMavenLocal` 再构建自己 ⇒ 两边版本必须 lockstep；
  **本库未 `push` 时 CI 必然断在该 checkout**（本地 sha 对远端不可达）。

## 3. 变更记录（CHANGELOG）— 必须遵守

- 双语两份：`CHANGELOG_ZH.md` / `CHANGELOG.md`，**纯单一语言**、同一版本号两侧各一次、**条目数与顺序逐条一一对应**，禁止只改一侧。
- 本库是**技术文档**：不收玩家侧「安装要求 / 玩法说明」类内容；条目写清 **API 语义变化**、破坏性变更标注，
  以及**消费方需要同步做什么**（pin / 版本区间 / 重跑构建 / 是否需改代码）。
- 未发布版本内对已记录条目的后续改动**并入原条目**，不追加「再次修改」类条目。

## 4. 构建 / 发布（命令面）

```powershell
# 三平台构建
.\gradlew.bat build --console=plain

# 发布到本地 Maven（消费方解析依赖的唯一来源；改库后必跑）
.\gradlew.bat publishToMavenLocal --console=plain
```

- **验证三重证据**：日志 `BUILD SUCCESSFUL` + `build/libs/*.jar` 时间戳 + **开包核符号**。
  ⚠️ **1.20.1 产物被 SRG 重映射**（`m_XXXXX_`）⇒ 裸 `grep` 方法名会得到**假「缺失」**，必须用 `javap -c`（或先把 `intermediateToNamed.srg` 反混）。
- 发布产物落点：`~/.m2/repository/com/merlinkitsune/starengine/<artifact>/<lib_version>/`。
- `git` 一律 `-C <绝对路径>`（多工作树仓库，别依赖 cwd）；**不执行 `git push`**（由用户决定）。

## 5. 禁止项

- 禁手改 `build/` 内产物；禁把平台分歧塞进 `common`；禁在 `common` 用 Java 21 独有语法。
- 禁「不 bump 就改库」；禁把「`--refresh-dependencies` 能解决」当成不 bump 的替代方案。
- 禁在库内注册注册表条目 / 事件 / 反向依赖消费方。
- 禁把 bump 拆成单独的「版本号提交」——**一次提交 = 一个版本号**。
