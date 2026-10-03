# 中文更新日志

> 本文件仅收录中文更新日志；英文版见 [`CHANGELOG.md`](CHANGELOG.md)。
> 两个文件按版本号一一对应：同一版本号在两边各出现一次，每次改动必须同时更新中英两份，禁止只改一侧。

## 未发布（2.0.0-SNAPSHOT.10）

### 新增

- **新增 Fabric 1.20.1 平台子项目**（2026-10-01）：本库现提供**四平台**产物 ——
  `neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / **`fabric-1.20.1`**
  （artifactId = `starengine_lib-fabric-1.20.1`），四平台**同一版本号**。Fabric 平台提供 9 个平台接线类
  （钱包的附件持久化 / 稀有度扩展 / Trinkets 适配 / 事件基类 / 标签 等），`common` 共享源码**零改动**。
  消费方 `astral_dice` 的 `fabric-1.20.1` 线随之接入本库。

- **新增效果牌专用目标口径 `combat/CreatureTargets` + `TargetType.CREATURE` / `CREATURE_OR_RIVAL`**
  （2026-10-03，自 `main` 线 1.0.9 移植）：敌对 ∪ 中立（宠物除外）∪ 未驯服的可驯服生物，**不含玩家**；
  `SelectorTargets` 新增对应路由。消费方据此收敛符卡-祸 / 活体书页等效果牌的目标面。
- **新增 `TargetType.NON_HOSTILE` + `HostileTargets#isHostileMob(Entity)`**（2026-10-03，自 `main` 线
  1.0.10 与 1.0.11 合并移植）：治疗 / 功能效果牌专用，判据为 `!HostileTargets.isHostileMob(target)`
  —— 即**非敌对生物**（只看原版 `Enemy` 标志）。`HostileTargets` 类头明确**两套并列口径不得互替**：
  战斗口径 `isHostile`（含中立生物）vs 生物类别口径 `isHostileMob`，后者是「原版敌对标志」的唯一入口。
  ⚠️ 与伤害牌的 `CREATURE` 系**方向相反，勿混用**。

### 变更

- **`component/GameplayConstants#CURSED_SWORD_BONUS_MAX`：`16` → `32`**（2026-10-02 用户裁决）。
  诅咒之剑筹码在**单次骰神赐福内**的累计攻击力加成上限**翻倍**。纯数值调整 —— 不改任何 public 类型 /
  方法 / 字段的签名、可见性或语义形状 ⇒ 消费方**无需**改代码（读取方 `CursedSwordChipItem#onKill`
  与 tooltip 渲染均直接读该字段）。本条系**收编**原 `fabric-1.20.1` 独立分支上唯一未并入 `next` 的
  实质改动（该分支已于 2026-10-02 删除；其旧版本线号 `1.0.7` / `1.0.6-alpha.2` 一并作废）。
- **四平台 `lib_version` / `mod_version`：`2.0.0-SNAPSHOT.8` → `2.0.0-SNAPSHOT.9`**（四平台**同号**）。
- **消费方 `astral_dice` 四线**的 `starengine_lib_version` 随本版同步为 `2.0.0-SNAPSHOT.9`。

- **`component/GameplayConstants#ACTIONBAR_FADE_TICKS`：`20` → `10`**（2026-10-03，自 `main` 同步）。
  actionbar 淡出改为 0.5 秒；同时 `ACTIONBAR_DURATION_TICKS`（60 tick）与该项一并**退出配置文件**
  （消费方已改为常量直接注入）⇒ 本库 actionbar 两项不再是玩家可配项。
- 四平台 `lib_version` / `mod_version`：`2.0.0-SNAPSHOT.9` → **`2.0.0-SNAPSHOT.10`**。

### 修复

- **`event/EventTargetCollector` 的 FTB Teams / OPAC 两处反射**目标**根本不存在**（2026-10-01 自 `main` 线 `1.0.6` 移植到本快照线）：
  这两个后端此前**恒为未启用且毫无声响** —— 最外层 `catch (Exception ignored)` 把
  `NoSuchMethodException` / `ClassNotFoundException` 全部吞掉。后果：装了 FTB Teams 或 OPAC 的玩家
  被本库判为「没有任何队伍」⇒ 落进「全服皆友方」兜底 ⇒ **队友判定整体失效**（消费方表现为
  「同队内仍能互相造成伤害 / 电磁炮仍命中友方 / 友方效果扩散到全服」）。按上游实物逐条更正：
  - FTB 的四个管理器访问器声明在**嵌套接口** `FTBTeamsAPI$API` 上 —— 不在外层类上，
    而 `Class#getMethod` **不会**跨到嵌套接口；
  - 服务端取队伍的真实签名是 `TeamManager#getTeamForPlayerID(UUID)`；原代码查的
    `getTeamForPlayer(UUID)` / `getTeamForPlayer(Player)` **一个都不存在**；
  - 客户端取队伍是 `ClientTeamManager#getKnownPlayer(UUID)` + `KnownClientPlayer#teamId()`
    （record 访问器，**没有 get 前缀**）+ `getTeamByID(UUID)`；
  - OPAC 的包路径真实为 **`xaero.pac.*`**（原代码查的 `dev.darkhax.opac.*` **整条不存在**）：
    入口链 = `OpenPACServerAPI.get(MinecraftServer)` → `getPartyManager()` →
    `IPartyManagerAPI#getPartyByMember(UUID)`；成员走 `IServerPartyAPI#getOnlineMemberStream()`
    （回退 `getMemberInfoStream()` + `IPartyMemberAPI#getUUID()`）；
  - `hasAnyTeam` 的 FTB 判据由「Team 对象非空」改为 `isPartyTeam() || isServerTeam()`
    —— FTB 给每个玩家都建了个人队伍 ⇒ 旧判据恒真，把「未组队 ⇒ 作用于全服」的兜底堵死；
  - 解析失败不再静默：新增 `describeBackends()` 打 `AP_LIB_PARTY:` 机器行
    （「没装」记 debug、「装了但签名不符」记 warn）；
  - 客户端路径 / 成员访问器 / 存在性判据按**可选**解析：缺失只降精度；
    客户端分支显式判空返回 `null`，避免一次客户端调用把**服务端**判定一起 disable。

- **版本**：改动落在三平台共用的 `common` 源码 ⇒ 三个 artifact 同批换号 `2.0.0-SNAPSHOT.6` → **`2.0.0-SNAPSHOT.7`**。
  公共 API 只增不减（`collectTeamPlayers` / `hasAnyTeam` 的签名与语义不变，仅新增
  `describeBackends()` 与 `BACKEND_REPORT_PREFIX`）。

### 战斗

- **「会被激怒的可驯服动物」计入敌对目标**（2026-09-27 用户裁决；自 `main` 线 `1.0.5` 移植到本快照线）：
  `combat/HostileTargets` 的口径由「敌对生物 ∪ 中立生物（宠物除外）」扩展为
  「… ∪ 会被激怒的可驯服动物（宠物除外）」。
  - 新增私有判定 `isAngerableTamedMount`：**可驯服**（`getOwner() == null` 且非已驯服 `TamableAnimal`）、
    且**会被激怒**（`Mob#getTarget()` 非空，或 `LivingEntity#getLastHurtByMob()` 非空）的 `Mob` ⇒ 计入敌对目标。
  - 覆盖原版**羊驼 / 行商羊驼** —— 它们会被激怒（受击后吐口水还击）、也可驯服，
    却没有实现 `NeutralMob`，故此前既不落入中立生物分支、也不被 `Enemy` 覆盖 ⇒ 在目标选择器里一直不可选。
  - ⚠️ 判据刻意**不使用类名 / 包名**：26.1.2 已把 `animal/horse/**` 整体改名到 `animal/equine/**`，
    硬编码类路径会让三平台共用的 `common` 源码编译不过。
  - ⚠️ 判据也**不使用 `Mob#isAggressive()`**：其语义按生物而异 —— `Panda` 覆写它表示
    「攻击型基因」（永久性格，并非当前正在发怒），而 `Llama` 不覆写（默认恒 `false`）。

### 工程

- **本库不再对外分发任何 jar —— 只保留源码**（2026-10-01 用户裁决）。CI 的 `Upload StarEngine Lib JARs`
  步骤整体删除：构建照跑（仍会产出 jar，但只留在 runner 上）；Release 步骤改为**不带任何附件**，
  只保留 GitHub 自动生成的源码归档，并新增**自愈清理** —— 发现该 Release 上存在 `*.jar` 附件即
  `gh release delete-asset` 删除（重复执行幂等）。历史存量同步清除：远端 `starengine_lib-jars`
  artifacts **12 个全部删除**（Release 侧逐 tag 交叉核验为 **0** 个）。
  原因与上面 `pushToPack` 那条同源：一份被单独下载的库会**静默盖掉**消费方内嵌的那一份，故障极难定位。
  ⚠️ **消费方需要同步做的事：无** —— 仍按 `starengine_lib_version` 硬 pin + JarJar 内嵌，不受影响。
- 同步更新 `README_ZH` / `README`（§4.3 删去「从本仓 Release 手动取用」、§4.4 表格改为「什么都不上传 /
  Release 附件：无」并补政策顶注；顺带把 CI 的 JDK 计数由「双（21 + 17）」订正为三套 21 / 17 / 25）。
  CHANGELOG 历史条目中关于「Release 附件」的表述**保留**，作为当时事实的记录。
- 三平台 `build.gradle` 的「编译产物部署」注释不再指向「从本仓 Release 手动取用」（改为「本仓不提供
  任何 jar 下载 ⇒ 自行从源码构建」）—— 上一版只改了 `main` / `fabric-1.20.1` 两线，本线（`next`）漏改，
  本次补齐。⚠️ 按本线 AGENTS §1「每提交一版」约定，comment-only 提交同样 bump：`2.0.0-SNAPSHOT.5`
  → **`2.0.0-SNAPSHOT.6`**。

## 未发布（2.0.0-SNAPSHOT.2）

### 战斗

- **`combat` 包（骰战共用公式）**：`2.0.0-SNAPSHOT.1` 把骰战公式从消费方下沉进本库，使三条消费线共用同一份实现：
  `TargetCategory`（生物四分类）、`TargetBattleStats`（初始攻防查表）、`CombatFormula`（纯换算函数）、
  `DiceBattleResolver`（对骰与伤害合成）。`2.0.0-SNAPSHOT.2` 补齐本批其余内容并对数值再校准：
  - **新增 `VanillaMitigation`** —— 原版两条**魔法减伤**通道（**抗性提升**效果 + **保护**附魔）的纯算术，
    在骰战结算**之后**乘算上去。两式均为纯乘法 ⇒ 顺序无关。平台差异（保护点数如何取值、效果常量名）刻意留在消费方，
    本类无状态：不注册任何条目、不依赖任何消费方。
  - **防御曲线再校准**：玩家基数 `2 → 4`、玩家护甲系数 `0.5 → 0.30`、玩家韧性系数 `1.4 → 0.85`；
    生物护甲系数 `0.5 → 0.40`、生物韧性系数 `1.125 → 1.0`；`TargetBattleStats` 敌对初始防御 `2 → 0`、
    敌对初始攻击 `5 → 4`、中立初始攻击 `4 → 3`。意图是**抬基数 + 凹斜率**，让前期难度与后期数值都不再极端。
  - **伤害下限入库**：`DiceBattleResolver.resolve(攻击, 防御, 相对下限比例)` 与 `RELATIVE_FLOOR_RATIO = 0.15F`。
    下限改为 `max(MIN_DAMAGE, 攻击力 × 相对下限比例)`，不再固定 1 点；两参 `resolve` 保留并使用默认比例。
  - **快照线内破坏性变更**：公开常量 **`CombatFormula.ARMOR_DIVISOR` 已删除** ——
    原先两侧共用的「护甲值 ÷ 2」统一除数改为两侧各自的系数（`PLAYER_ARMOR_COEF` / `MOB_ARMOR_COEF`）。

## 未发布

> 下列为**构建脚本 / 文档**改动，不涉及 jar 内容 ⇒ **不 bump 版本号、不重新发布**；
> 等下一次确有产物变化时，再随那一版一起定版本号。

### 工程

- **移除 `pushToPack`（自动推送到整合包）任务**：三平台 `build.gradle` 的整节「编译产物部署」删除，
  连同只服务于它的 `packModsDir` / `packPushBranches` / `forcePackPush` / `dotGitEntry` /
  `jarArchiveProvider`；`build` 不再读写任何整合包目录（原 `finalizedBy tasks.named('pushToPack')` 一并去掉）。
  原因：消费方 `astral_dice` 已改用 **JarJar 内嵌**本库 ⇒ 整合包里再出现独立库 jar 会被 FML 的
  JarInJar 选择器优先采用并盖掉内嵌副本（独立件更旧时表现为 `NoSuchMethodError` / `NoClassDefFoundError`）。
- 同步更新 `README_ZH` / `README`（§4.3 改写为「与整合包的关系」、构建命令注释、消费方式说明）
  与 `.github/workflows/build.yml` 的构建步骤注释。CHANGELOG 自身历史条目（`1.0.0` 等）中关于
  `pushToPack` 的描述**保留**，作为该机制存在时期的记录。

## 1.0.4

> 纯新增（无破坏性变更）⇒ 按补丁位发布；消费方 `starengine_lib_version_range` 下界随之收紧至 `[1.0.4,2.0)`。

### 新功能

- **稀有度等级 API**：新增 `item/Rarity`（等级 / 常量名 / 序列化名 / **颜色码的唯一权威**）与三平台各一份的
  `item/AstralRarities`（把 5 个等级扩展进原版 `net.minecraft.world.item.Rarity` 的接线 + 运行期访问器）。
  - ⚠️ **序列化名的硬性格式**：扩展时交给原版枚举常量的 **name 参数**必须形如 `<modId>:<名字>`（**冒号**分隔），
    即 `astral_dice:rare` / `astral_dice:epic` / `astral_dice:legendary` / `astral_dice:pinnacle` / `astral_dice:bizarre`
    （NeoForge 的 `RuntimeEnumExtender#validateNameParameter` 强制校验，不符则在 `Rarity` 类加载时抛异常、游戏起不来）；
    它与 `enumextensions.json` 里 `name` 字段（注入进枚举的**字段名**，要求以 modId 小写前缀开头、形如 `ASTRAL_DICE_RARE`）
    是**两条不同的规则**，勿混。
  - 5 个等级与颜色：`RARE` 稀有 = **原版 RARE 的配色**（水蓝 `0x55FFFF`，即 `ChatFormatting.AQUA`）、
    `EPIC` 史诗 = **原版 EPIC 的配色**（粉紫 `0xFF55FF`，即 `ChatFormatting.LIGHT_PURPLE`）、
    `LEGENDARY` 传奇 = 金 `0xFFC24B`、`PINNACLE` 巅峰 = 亮红 `0xFF4D4D`、
    **`BIZARRE` 奇特 = 彩虹（流动）**：⚠️ 本档**没有单一颜色**，`rgb()` 只是基准色薄荷绿 `0x6BFFA8`
    （用于物品名那一行等无法逐帧上色的位置）；真正流动的色环由新增 API 提供 ——
    `isRainbow()` 判定是否彩虹档、`rainbowBorderStart(millis)` / `rainbowBorderEnd(millis)` 按时刻取
    **边框起/止色**（ARGB，两者在色环上相差 1/3 圈）、`rainbowHue(millis)` 取相位、`hsvToRgb(...)` 供自算，
    周期常量 `RAINBOW_CYCLE_MILLIS = 3000`，另配 `rainbowSaturation()` / `rainbowBrightness()` 两个访问器，
    消费方自绘时与库保持同一组参数；
  - **提示框边框策略（2026-09-25 用户裁决修订）**：早期「文字与边框严格同色」的约定**作废** ——
    稀有 / 史诗**完全随原版**（消费方不干预边框，原版紫蓝渐变保留，只染物品名那行）；
    传奇 / 巅峰维持自定义：`Rarity#frameColor(long)` = `0xFF000000 | rgb()` 画成单色边框；
    奇特**走不了**平台两色钩子（原版边框是竖直渐变，顺时针彩虹必须由消费方逐像素自绘，
    见消费方的 `TooltipBorderMixin`）。配套 **反查** `AstralRarities#tierOf(原版 Rarity)`：非本库档位
    （原版自带四档）返回 `null`，
    供客户端判断「这件物品是不是我们的档位」，避免把档位映射抄到库外。
  - **染色权威** = `Rarity#apply(Style)` / `Rarity#styleModifier()`：颜色在**扩展时**作为 Style 变换函数交给原版
    枚举常量，此后由原版 tooltip 链路（`ItemStack#getTooltipLines` → `Rarity#getStyleModifier()`）自动套用 ⇒
    改色只需改 `Rarity` 里的常量，消费方没有任何 tooltip 侧代码；
  - **平台差异**：NeoForge 两线（1.21.1 / 26.1.2）用 `EnumProxy` 字段，由消费方的
    `META-INF/enumextensions.json` 按字段名引用；Forge 1.20.1 走 `IExtensibleEnum`（本库静态初始化里
    `Rarity.create(name, styleModifier)`）；
  - 消费方取常量一律走 `AstralRarities.{rare,epic,legendary,pinnacle,bizarre}()`（**懒解析**）；
    ⚠️ 不提供 `Rarity.valueOf(...)` 路径（Forge 侧枚举常量目录可能被提前缓存 ⇒ 会抛异常）。
- ⚠️ **调用契约**：`AstralRarities` 的 5 个 `EnumProxy` 字段名、`enumextensions.json` 的 `name` 字段、以及
  声明该 json 的 mod 的 modId 三者互相约束（FML 要求常量名小写后以声明方 modId 开头）—— 改名须三处同改。

## 1.0.3

> 版本号说明:本版**合并了原拟的 `1.0.2`** —— 该号已提交但**从未推送、从未发布**(其内容即下方
> 「额外敌对判定 seam」一节),按仓内约定「未发布版本的改动并入原条目」整块并入本版 ⇒ `1.0.2`
> 同样**从未存在**(无 tag、无 Release、无产物)。更早的 `1.0.1` 亦从未发布(内容并入 `1.0.0`)。
> ⚠️ 本版**含一处语义变更**(见「敌对目标口径重写」):按 `1.x` 兼容契约本应升主版本,经用户
> 2026-09-24 裁决作为**玩法口径特例**按补丁位发布。

### 新功能(原拟 `1.0.2`)

- **`combat/HostileTargets` 新增「额外敌对判定」注入 seam**(`ExtraHostileProbe` /
  `installExtraHostileProbe`):消费方可在启动时注入一个「某实体应当视为敌对目标」的判定体,
  使**既非 `Enemy`、也不是中立生物**的实体(典型:第三方测试假人这类靶子)能被
  「需要敌对目标」的效果正确识别。
  - 判定口径扩充为 `既有口径 ∪ 消费方额外声明的实体`;两参重载
    `isHostile(viewer, target)` 经既有委托自动继承该扩充。
  - 之所以做成注入而非库内硬编码:该判定不仅被消费方玩法代码调用,还被**库内**的
    `target/SelectorTargets` 用于「可选中目标」判定(客户端射线 / 客户端半径高亮 / 服务端确认
    三处共用)—— 判定点落在库内,消费方无处插手;而「哪个第三方实体算敌对」属消费方玩法口径,
    库不应知道任何具体模组的实体。范式与既有 `combat/InternalDamageWindows` 完全同构。
  - **未注入时逐字保持既有语义**(不额外认定任何实体为敌对)—— 安全方向:漏注入只退回原有行为,
    不会凭空把中立生物变成敌对目标。

### ⚠️ 语义变更:敌对目标口径重写(2026-09-24 用户裁决)

- **`HostileTargets.isHostile` 的口径由 `敌对生物 ∪ 已被激怒的中立生物` 改为
  `敌对生物 ∪ 中立生物(宠物除外)`**:
  - **去掉「必须已被激怒」的要求**(原 `NeutralMob#isAngry()` = `getRemainingPersistentAngerTime() > 0`,
    被激怒后 20~39 秒)⇒ 未激怒的**狼 / 铁傀儡 / 北极熊 / 蜜蜂**现在也算敌对目标。
  - **新增「宠物除外」**:已驯服的 `TamableAnimal`(狼 / 猫 / 鹦鹉)不计入。**只用
    `TamableAnimal#isTame()` 这一个判据** —— 它在三平台签名一致(common 源码的硬约束);另一个
    「有主人」的家族 `AbstractHorse`(马 / 驴 / 骡 / 骆驼)**本身不是 `NeutralMob`**,根本不在此
    集合里,无需在此排除;且 `OwnableEntity` 在 26.1.2 已改为 `EntityReference` 体系、不再有
    `getOwnerUUID()`。
  - **实测影响面**:唯一「既是 `NeutralMob` 又是 `TamableAnimal`」的原版生物就是**狼** ⇒ 本条实际
    等价于「狼在**未驯服**时算敌对目标、驯服后不算」。
  - **为何算「语义变更」**:本库 `1.x` 兼容契约只允许「新增」与「不改变既有语义的行为修正」,
    而本条改变了 `isHostile` 的既有语义 ⇒ 按契约应升主版本(`1.x` → `2.x`)。经用户 2026-09-24
    裁决,本批作**特例**按补丁位 `1.0.3` 发布(理由:该库的实际消费方仅本仓消费方 mod,且该口径
    由用户直接裁决)。
  - 配套:`target/SelectorTargets` 的类注释不再复述具体口径,改为指向 `HostileTargets`
    (避免同一口径两处维护)。

### 兼容性

- **新增**(seam):新嵌套类型 `ExtraHostileProbe` + 新静态方法 `installExtraHostileProbe` +
  既有 `isHostile(Entity)` 末尾追加一次 probe 调用;未删除 / 改名 / 改签名任何既有 public API。
- ⚠️ **语义变更**(上面那条):按契约本应升主版本,经用户裁决作**特例**按补丁位发布 ⇒ 消费方
  `starengine_lib_version_range` 的下界随本版收紧至 `[1.0.3,2.0)`。

## 1.0.0

> 约定：对当前版本已记录条目的后续改动，直接合并进原条目，仅保留改动后的最终版本，不追加「再次修改」条目。
> 版本号说明：`1.0.0` 是库的**首个正式版** —— 由快照系列的最后一档 `1.0.0-SNAPSHOT.16` 规范化而来，
> **库内 Java 源码零改动**，产物文件名变为 `starengine_lib-<平台>-1.0.0.jar`。`.16` 即**消费方 26.1.2 线接入**
> 那一档（仅随消费方「26.1.2 完整移植」推进版本号；库的历史口径为「每个实质提交进 1 档」）。
> 附注（2026-09-22）：forge 侧「移除 Curios 前置」原拟作 `1.0.1`，现**并入本版本**（见下方「依赖与元数据」）；
> `1.0.1` 从未推送、从未发布。
> 快照系列到此终止；自 `1.x` 起适用下方兼容性政策。
> 附注（2026-09-23）：`neoforge-26.1.2` 侧「补齐钱包账本平台实现」同样原拟作 `1.0.1`，
> 经用户裁决**并入本版本**（见下方「补齐第三条线缺失的平台实现」）；`1.0.1` 从未推送、从未发布。

### 补齐第三条线缺失的平台实现（并入 `1.0.0`，2026-09-23）

- **为 `neoforge-26.1.2` 线补齐 `NeoForgeEconomyStorage`**（钱包账本的平台存储实现）。首个正式版只在
  `forge-1.20.1`（`ForgeEconomyStorage`）与 `neoforge-1.21.1`（`NeoForgeEconomyStorage`）提供了该类，
  导致 `26.1.2` 上 `StarEngineEconomy.isAvailable()` 恒为 `false`：`/starcoin` 命令不注册、拾取星币不会
  被吸收进钱包、余额条永远是 0。现由 26.1.2 的库入口（`StarEngineLib`）注入该实现，与另两条线完全一致。
  - 数据布局、键名、`PlayerEvent.Clone` 的余额搬运与离线 `.dat` 解析与 1.21.1 实现**逐字同构**
    （持久化数据根段 `NeoForgeData`，`starengine_lib/star_coin_wallet`，`balance`/`name`）。
  - 只吸收了两处 26.1.2 的平台 API 变更（均已在代码内就地注明）：`CompoundTag` 的取值/子标签访问改为
    `Optional` / `*Or` 系列（不再有 `contains(String,int)` 与返回裸 `CompoundTag` 的 `getCompound(String)`）；
    命令权限改为**命名权限集**（`CommandSourceStack#hasPermission(int)` 已删除），seam 的数字等级映射到
    `Permissions.COMMANDS_MODERATOR/GAMEMASTER/ADMIN/OWNER`，从而「等级 ≥ 2 才能用 `/starcoin`」与另两条线等价。

### 依赖与元数据（并入 `1.0.0`，2026-09-22）

> 本改动原拟作为独立的 `1.0.1` 发版；推送/发布之前经用户裁决**并入 `1.0.0`** —— 一处未发布的改动不应占用两个
> 版本号。故 `1.0.1` **从未存在**：没有该 tag、没有该 Release、也没有对应产物。既有 `1.0.0` tag 名称不变，
> 其 Release 附件由构建本提交的 CI 同批刷新（工作流复用已存在的 tag，并用 `--clobber` 覆盖附件）。

- **移除 `forge-1.20.1` 侧的 Curios 前置**（2026-09-22 用户裁决「使其与另外两个版本保持一致」）：
  - `forge-1.20.1/src/main/templates/META-INF/mods.toml` 删除 `modId="curios"` 依赖块
    （原为 `mandatory=true versionRange="[5,6)" ordering="AFTER" side="BOTH"`）；
  - `forge-1.20.1/build.gradle` 的依赖由 `modImplementation` 降为 **`modCompileOnly`**。
    必须用 `mod*` 配置而非裸 `compileOnly`：MDG LegacyForge 只对 `mod*` 配置走 `RemappingTransform`
    （Mojmap/Parchment），否则 `CuriosCompat` 的 `LivingEntity` 形参在编译期对不上 Curios 的方法描述符。
  - 依据：库自身**零调用** `item/CuriosCompat` —— 它只是给消费方用的 shim，库内没有任何代码路径会加载它，
    故 Curios 在本侧仅是**编译期**依赖。**调用该 shim 的消费方自己声明 curios 必需**
    （`astral_dice` 的 1.20.1 侧已声明 `mandatory=true [5,6)`）。
  - ⇒ **三个平台的库 jar 都不再声明 Curios 前置**（neo 两线本来就没有），三线的依赖块形态至此一致。
- 本改动是**纯放宽**：不删除/不改名任何 public 类型、方法、字段、常量，不改可见性/签名/语义
  ⇒ 符合下方兼容性政策，**消费方 `starengine_lib_version_range=[1.0.0,2.0)` 无需收紧**，也无需改消费方代码。
  唯一代价：若某消费方漏声明 curios 却调用该 shim，失败形态会从加载器的「缺必需前置」明确报错
  退化为运行期 `NoClassDefFoundError`。
- 三平台**恒同号发布** ⇒ 产物文件名保持 `starengine_lib-<平台>-1.0.0.jar`，**不产生 `1.0.1` 产物**。

### 工程（并入 `1.0.0`）

- 验证：三平台 `./gradlew build publishToMavenLocal`；`~/.m2` 三坐标均为 `1.0.0`（中途短命的 `1.0.1`
  坐标已从本地仓库移出）；三平台 `pushToPack` 把 `1.0.0` 的 jar 推入各自整合包 `mods`
  （每包**只留一个**库 jar ⇒ 不会出现同 modId 重复 jar）。
- 开 jar 复核：三平台 `mods.toml` 的依赖块只剩「加载器 + minecraft」
  （1.21.1 = `neoforge [21.1,21.2)`；1.20.1 = `forge [47.4.10,48)`；26.1.2 = `neoforge [26.1.0.0,26.2)`），
  已无 `modId="curios"`。
- **回归判据（关键）**：`forge-1.20.1` 的 `CuriosCompat.class` 与改动前的 `1.0.0` **逐字节相同**
  （3352 B，md5 前缀 `d56a57b0`），且新旧 jar 的 **55 个类无一字节差异**
  ⇒ 改用 `modCompileOnly` 后 reobf/重映射行为未变，本改动确属纯元数据变更。

### 兼容性政策（新增，自 1.0.0 起生效）

- **同一主版本号内（第一位数字不变，当前 = `1.x`）禁止任何破坏性更新。** 不得删除或改名任何 public 类型、
  方法、字段、常量，也不得改变其可见性、签名或既有语义；只允许**新增**（新类型、新成员、新可选入口）
  与不改变契约的行为修正。
- **破坏性变更必须升第一位（`1.x` → `2.x`）**，并在同一次发布里收紧消费方 `starengine_lib_version_range` 的下界。
  破坏性变更**不允许**藏在次版本/补丁位里。
- ⇒ 消费方区间 `[1.0.0,2.0)` 即本政策的机器可读表达：任何 `1.x` 版本都可原位替换，无需改动消费方代码。
- 本条**取代**此前 `gradle.properties` 里「SNAPSHOT 系列的 API 不作语义化兼容承诺」的口径 —— 该豁免仅覆盖
  快照系列，自 `1.0.0` 起不再适用。

### 工程

- 三平台版本号由 `1.0.0-SNAPSHOT.16` 规范化为 **`1.0.0`** 并已 `publishToMavenLocal`
  （`./gradlew publishToMavenLocal`）；三个 jar 为
  `starengine_lib-{neoforge-1.21.1,forge-1.20.1,neoforge-26.1.2}-1.0.0.jar`。
- 消费方 `astral_dice` 的 **26.1.2 线**首次接入本版本（即原 `.16` 代码）：此前该线 pin 在 `.11`，且**本地仍留有**
  `combat/HostileTargets`、`combat/PlayerHostilityTracker`、`target/SelectorTargets`、`target/SignSelectionGate`
  四个已被 `.15` 下沉取代的副本（冻结期未同步）——本次移植一并删除，引用改指 `com.merlinkitsune.starenginelib.*`，
  并补齐平台挂点 `combat/PlayerHostilityTrackerEvents` 与 `InternalDamageWindows.install(...)` 注入
  （与 1.21.1 / 1.20.1 两侧同构）。
- **库内 Java 源码零改动**；必须发布新版本号的原因同前几版：该版本号不以 `-SNAPSHOT` 结尾（按 Maven 语义属普通版本），
  Gradle 不把它当作 changing module —— 不发布新版本号则消费方会继续解析 `mavenLocal` 里的旧 jar。

## 未发布（1.0.0-SNAPSHOT.15）

> 约定：对当前版本已记录条目的后续改动，直接合并进原条目，仅保留改动后的最终版本，不追加「再次修改」条目。
> 版本号说明：`.15` 是**公共判定逻辑下沉**版本 —— 把消费方 `astral_dice` 两侧逐字节一致的敌对/选择器/钱包显示判定搬进库。

### 新功能

- **新增 `combat` 包：敌对目标判定的**唯一入口**下沉**。`HostileTargets`（`isHostile(Entity)` / `isHostile(viewer, target)`，
  口径 = 敌对生物 ∪ 已被激怒的中立生物 ∪ 「非同队伍且曾主动攻击过观察者的玩家」）、
  `PlayerHostilityTracker`（受害者 UUID → 攻击者 UUID 集合的内存表 + `hasAttacked` / `forget`）。
  ⚠️ **表与事件分离**：库内**不注册任何事件**（库红线），四个平台挂点留在消费方 ——
  记进攻（`LivingDamageEvent`）、死亡清（`LivingDeathEvent`）、死亡重生克隆清（`PlayerEvent.Clone`）、
  登出清（`PlayerLoggedOutEvent`）由消费方 `combat.PlayerHostilityTrackerEvents` 翻译成库的
  `recordAttack` / `recordAttackIfExternal` / `forget` 调用。
- **新增平台 seam `InternalDamageWindows`**：`PlayerHostilityTracker` 需区分「主动攻击」与「模组内部波及伤害」
  （溅射 / AOE / 反击注入），而这两个窗口开关属消费方的玩法实现（不下沉）⇒ 库内只留判定接口，
  由消费方启动时 `install(aoeProbe, counterProbe)` 注入；未注入时一律返回 `false`（安全方向：
  漏判只多记一条立场，误判会让真实攻击不计入）。
- **新增 `target.SelectorTargets`**：把「敌对」族并到 `HostileTargets` 上，修补 `TargetType#matches`
  对 `ENEMY` / `ENEMY_OR_RIVAL` 只做裸 `instanceof Enemy`、漏掉被激怒的狼/铁傀儡/北极熊/蜜蜂的缺陷；
  另含「允许对自身使用」的四参重载。**该缺陷在库内被就地适配而非改写 `TargetType`** ——
  既有语义保持逐字不变，调用点一律走 `SelectorTargets`。
- **新增 `target.SignSelectionGate`**：立牌主动技能前置门控的待执行记录（纯内存瞬态，`arm` / `isArmed` / `take` / `clear`）。
- **新增 `economy.StarCoinWalletState`**：星币钱包余额条的客户端显示缓存（只有基本类型，
  **刻意不含任何客户端专有类型** ⇒ 服务端也会加载它）。与库既有 `StarEngineEconomy` 同域。

### 工程

- 三平台版本号 `1.0.0-SNAPSHOT.14 → 1.0.0-SNAPSHOT.15` 并已 `publishToMavenLocal`；
  三平台 `compileJava` 通过（含共享源码在 **26.1.2** 的编译验证 —— 正是它保证了「只用三版本都存在的 API」这条红线）。
  开包核对：三平台 jar 内均含上述 7 个新 class（含 `InternalDamageWindows$Probe`、`SignSelectionGate$Pending`）。
- 消费方 `astral_dice` 同步解除对本地类的依赖：删掉两侧的 `combat/HostileTargets`、`combat/PlayerHostilityTracker`、
  `target/SelectorTargets`、`target/SignSelectionGate`、`economy/StarCoinWalletState`，
  新增 `combat/PlayerHostilityTrackerEvents`（平台挂点）。

## 未发布（1.0.0-SNAPSHOT.14）

> 约定：对当前版本已记录条目的后续改动，直接合并进原条目，仅保留改动后的最终版本，不追加「再次修改」条目。
> 版本号说明：`.14` 是**新增玩家余额账本底层**版本（星币钱包功能的库侧实现）。

### 新功能

- **新增玩家余额账本（星币钱包的库侧底层）**：`common` 新增 `economy` 包 ——
  `EconomyStorage`（平台存储 seam：读 / 写 / 离线读）、`StarEngineEconomy`（对外 API：`getBalance / setBalance / hasBalance /
  deposit / withdraw / transfer` + 离线读；**存储缺失时一律返回 0 / false 且不抛异常**，便于第三方模组（如 FTB）安全联动）、
  `StarCoinCommand`（`/starcoin add|set|remove|get|rank`，其中 `rank` 含**离线玩家**）。
  余额以**玩家持久化 NBT** 承载 —— 库的既有不变量是「不注册任何注册表条目」，故**不使用** `AttachmentType`；
  并在 `PlayerEvent.Clone` 时显式复制 ⇒ **余额不因死亡丢失**。两平台各一份实现
  （`NeoForgeEconomyStorage` / `ForgeEconomyStorage`，差异仅事件包名与玩家 `.dat` 里持久化数据的根段
  `NeoForgeData` / `ForgeData`）；26.1.2 侧**未接入**（API 安全降级为不可用）。库入口构造时注入存储实现。
- **新增命令权限 seam `CommandPermissionGate`**：`CommandSourceStack#hasPermission(int)` 在 **26.1.2** 上签名不一致
  （1.20.1 / 1.21.1 一致），故共享源码不直接写权限谓词，改由平台侧注入 `(source, level) -> source.hasPermission(level)`。

### 工程

- 三平台版本号 `1.0.0-SNAPSHOT.13 → 1.0.0-SNAPSHOT.14` 并已 `publishToMavenLocal`；
  三平台 `build` 通过（含共享源码在 26.1.2 的编译验证，正是它保证了 keep-common-version-agnostic 这条红线）。

## 1.0.0-SNAPSHOT.13

> 约定：对当前版本已记录条目的后续改动，直接合并进原条目，仅保留改动后的最终版本，不追加“再次修改”条目。
> 版本号说明：`.13` 是**资源包元数据修复 + 版本线统一**版本 —— 给 `forge-1.20.1` 补上缺失的 `pack.mcmeta`（Forge 把每个模组 jar 当作资源包校验，缺该文件时在加载界面报一条警告，且该模组的资源包**不会被注册**），并沿用 `.6` 的加载门槛修复。按用户 2026-09-17 指令，库版本号统一为 `1.0.0-SNAPSHOT.10`（与消费方 `2.0.0-SNAPSHOT.10` 的 patch 段同号）；该修复原先以 `.7` 为号在途（只发布过 mavenLocal、从未进入 git），按上面的合并约定并入本节，不单列 `.7` 小节。必须 bump 的原因同前几版：该版本号不以 `-SNAPSHOT` 结尾（按 Maven 语义属普通版本），Gradle 不把它当作 changing module —— 不 bump 则消费方会继续解析 `mavenLocal` 里那份没有 `pack.mcmeta`（且需 `--refresh-dependencies` 才能重取）的旧 jar，警告依旧。

- **新增模组图标，并把描述文本统一到三平台。** 仓库根目录的 `icon.png` 现作为各 jar 的模组图标发布：同一份字节（2671 字节，PNG 签名 `89 50 4E 47 0D 0A 1A 0A`）被复制到 `neoforge-1.21.1/src/main/resources/icon.png`、`forge-1.20.1/src/main/resources/icon.png` 与 `neoforge-26.1.2/src/main/resources/icon.png`，即 **jar 根目录** —— 正是 FML 解析 `logoFile` 的位置（与消费方 `astral_dice` 各 jar 用根级 `icon.png` 的做法同源）。三份元数据模板均新增图标键，且 `description` 统一为 1.21.1 侧文本：`A common library for the Astral Dice mod. It includes cross-platform implementations and supports functional integration with other mods.` 原先的中文多段描述随之从 `forge-1.20.1` 与 `neoforge-26.1.2` 侧移除，模组列表条目三平台一致。注：仓库根目录的 `icon.png` 不是任何子项目的资源根，真正随 jar 发布的是 `src/main/resources` 下这三份副本。本次仅元数据与资源改动，**库内 Java 源码零改动**。三平台版本号 `1.0.0-SNAPSHOT.12 → 1.0.0-SNAPSHOT.13`，必须 bump 的原因同前几版：该版本号不以 `-SNAPSHOT` 结尾（按 Maven 语义属普通版本），Gradle 不把它当作 changing module —— 不 bump 则消费方会继续解析 `mavenLocal` 里那份既没有图标、描述也仍旧的 jar。
  - ⚠️ **更正（2026-09-22）：该键原先拼作 `logofile`（小写 `f`），因此从未生效。** TOML 键名**大小写敏感**，文档规定的键是 `logoFile`（大写 `F`），所以解析器把无法识别的 `logofile` **静默忽略、图标从未被加载** —— 图标文件本身一直是对的（位于 jar 根、合法 64×64 RGBA PNG、边长为 2 的幂、字节与仓库根一致），失效**纯属键名大小写**。三平台一并改为 `logoFile="icon.png"`（开包核对：`.14` jar 内为 `logoFile="icon.png"`，且 `icon.png` 的 md5 等于仓库根的 `09762024a7672b5bad645fd41786ce27`）。**无需 bump 版本号** —— `.14` 尚未被消费方取用，修正后的元数据按惯例并入在途的 `.14` jar。

### 工程

- **修复：Forge 1.20.1 加载界面报「File … failed to load a valid ResourcePackInfo」**。现象：消费方 `run/1.20.1` 客户端进入 Forge 的 `Warning while loading mods` 界面，列出 1 条警告并指向库 jar（用户截图与 `latest.log` 均指向 `starengine_lib-forge-1.20.1-1.0.0-SNAPSHOT.5.jar`；`.6` 同样缺该文件，故该警告与快照序号无关），日志对应行 `[net.minecraft.server.packs.repository.Pack/]: Missing metadata in pack mod:starengine_lib`。根因（**读本机 Forge 1.20.1 源码取证**，非推测）：`net.minecraftforge.client.loading.ClientModLoader#clientPackFinder`（行 154-165）对**每一个**模组文件调用 `Pack.readMetaAndCreate(name, …, PackType.CLIENT_RESOURCES, Pack.Position.BOTTOM, …)`，返回 `null` 时执行 `ModLoader.get().addWarning(new ModLoadingWarning(mod, ModLoadingStage.ERROR, "fml.modloading.brokenresources", e.getKey()))` —— `assets/forge/lang/*.json` 里该键的文案即 `File {2} failed to load a valid ResourcePackInfo`；`Pack.readMetaAndCreate` 在 jar 内**没有 `pack.mcmeta`**（或元数据无法按该 PackType 解析）时返回 null。服务端同源：`net.minecraftforge.server.ServerLifecycleHooks`（行 214）在 `PackType.SERVER_DATA` 上做同一检查。⇒ 凡缺 `pack.mcmeta` 的 Forge 1.20.1 模组 jar 都会各报一条警告，且其资源包**不被注册**（库当前无资源 ⇒ 无功能损失，但属静默失效面）。处置：新增 `forge-1.20.1/src/main/resources/pack.mcmeta`（`pack_format` **15**、`description` = `starengine_lib resources`，与消费方 `forge-1.20.1/src/main/resources/pack.mcmeta` 同形）。**范围仅 Forge 1.20.1**：NeoForge 1.21.1 / 26.1.2 不走这两段代码（两侧运行日志无该警告，消费方三线 jar 也只在 1.20.1 侧带 `pack.mcmeta`），且各版本 `pack_format` 不同，故**不猜测**其余平台的取值（需要时按各自版本客户端源码取值再补）。三平台 `lib_version` / `mod_version` 由 `1.0.0-SNAPSHOT.6` 统一到 **`1.0.0-SNAPSHOT.10`**（后缀各自 `+neoforge_1.21.1` / `+forge_1.20.1` / `+neoforge_26.1.2`，与消费方 `2.0.0-SNAPSHOT.10` 的 patch 段同号）并已 `publishToMavenLocal`；**库内 Java 源码零改动**。
- **新增配置驱动常量 `ALLOW_FIREARM_DAMAGE`（默认 `false`），供消费方公共配置 `allow_firearm_damage` 推入**。`component/GameplayConstants` 新增**非 final** 的 `public static boolean ALLOW_FIREARM_DAMAGE = false`（默认**仍屏蔽**枪弹/炮弹类伤害，与既有行为完全一致；配置为 `true` 时才允许这类伤害计入法伤），并放在既有的「仍可配置」布尔量一组里（`EVENT_APPLY_OPAC` 之后）；`component/GameplayConfigValues` 在**末尾追加**第 7 个分量 `boolean allowFirearmDamage`（前 6 个分量的名称与顺序**逐字未变**，消费方仍按位置构造，属**源码兼容**的追加）；`applyConfig(GameplayConfigValues)` 在「仅写入仍可配置的少量字段」那一段内写入该值，固定常量派生 tick 值的部分未动。库内仍**不读配置文件、不持有配置 schema**，配置文件与配置项定义照旧留在消费方。三平台 `lib_version` / `mod_version` 由 `1.0.0-SNAPSHOT.11` 统一为 **`1.0.0-SNAPSHOT.12`**（后缀各自 `+neoforge_1.21.1` / `+forge_1.20.1` / `+neoforge_26.1.2`）；必须 bump 的原因同前几版：该版本号不以 `-SNAPSHOT` 结尾（按 Maven 语义属普通版本），Gradle 不把它当作 changing module —— 不 bump 则消费方会继续解析 `mavenLocal` 里那份既没有该常量、也没有第 7 个 record 分量的旧 jar，构造快照时编译期即报错。

## 1.0.0-SNAPSHOT.6

> 约定：对当前版本已记录条目的后续改动，直接合并进原条目，仅保留改动后的最终版本，不追加“再次修改”条目。
> 版本号说明：`.6` 是**加载门槛修复**版本 —— 修掉「库自身被 FML 拒载」的根因，并把 forge 侧门槛对齐到已文档化口径。必须 bump 的原因同前几版：该版本号不以 `-SNAPSHOT` 结尾（按 Maven 语义属普通版本），Gradle 不把它当作 changing module —— 不 bump 则消费方会继续解析 `mavenLocal` 里那份**带错 `loaderVersion`、会被 FML 拒载**的 `.5` jar。

### 工程

- **修复：库自身被 FML 拒载（`loaderVersion` 误用 NeoForge 版本带）**。现象：消费方 `run/1.21.1` 连续两次启动在 FML 阶段崩溃（`crash-2026-09-17_20.41.28-fml.txt` / `…_20.42.17-fml.txt`），报 `Mod File …\starengine_lib-neoforge-1.21.1-1.0.0-SNAPSHOT.5.jar needs language provider javafml:21.1 or above, and below 21.2 to load / We have found 4.0.42`，随后消费方 `astral_dice` 被判 `requires starengine_lib 1.0.0-SNAPSHOT.5 or above … Currently, starengine_lib is not installed`。根因：`neoforge-1.21.1/gradle.properties` 的 `loader_version_range` 曾被写成 **NeoForge 版本带** `[21.1,21.2)`，而模板 `neoforge.mods.toml:2 loaderVersion="${loader_version_range}"` 比的是 **javafml 语言提供者版本**（本机 NeoForge 21.1.235 报 `4.0.42`）。处置：该属性改为语言区间 **`[1,)`**（与消费方 `astral_dice_multiloader-next/neoforge-1.21.1/gradle.properties:23` 同值）；NeoForge 版本带 `[21.1,21.2)` 仍由模板里 `modId="neoforge"` 依赖承担（**区间未变**）。`neoforge-26.1.2` 侧本来就是 `[1,)`（未触发拒载），本次不改其值。
- **同批：`forge-1.20.1` 门槛对齐已文档化口径 + 三平台版本同步**。`loaderVersion` 由 `[47,)` 收敛为 **`[47,48)`**（javafml 语言提供者的版本号就是 Forge 主系列号 `47`，写完整版本号区间会被 FML 判 `fml.language.missingversion`），`modId="forge"` 依赖改用 `build.gradle` 从 `forge_version` **派生**的精确区间 **`[47.4.10,48)`**（模板占位由 `${loader_version_range}` 改为 `${forge_version_range}`），与消费方 `forge-1.20.1/build.gradle:139-152` 的口径逐条一致。三平台 `lib_version` / `mod_version` 由 `1.0.0-SNAPSHOT.5` → **`1.0.0-SNAPSHOT.6`**（后缀各自 `+neoforge_1.21.1` / `+forge_1.20.1` / `+neoforge_26.1.2`）并已 `publishToMavenLocal`（产物落在 `~/.m2/repository/com/merlinkitsune/starenginelib/<artifact>/1.0.0-SNAPSHOT.6/`）；开包断言三个 jar 的 `loaderVersion` 分别为 `[1,)` / `[47,48)` / `[1,)`、forge 依赖 `versionRange="[47.4.10,48)"`，`neoforge` / `minecraft` 依赖区间未变。**库内 Java 源码零改动**（只改元数据、模板注释与文档）；消费方引脚与 CI ref 由消费方侧同步，不在本次范围。

## 1.0.0-SNAPSHOT.5

> 约定：对当前版本已记录条目的后续改动，直接合并进原条目，仅保留改动后的最终版本，不追加“再次修改”条目。
> 版本号说明：`.5` 是**合并收尾**版本 —— 主线（`multi-1.20.1-1.21.1` @ `8f68482`）已并入消费方 `multi-dev-next`，库侧据此按期删除「合并后即删」的过渡符号。必须 bump 的原因同 `.4`：该版本号不以 `-SNAPSHOT` 结尾（属普通版本），Gradle 不把它当作 changing module，不 bump 则消费方会继续解析 `mavenLocal` 里的 `.4` 旧 jar。

### 工程

- **过渡符号按期删除（已删部分）**：删除前**逐符号 grep 举证**合并后消费方三线（`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2`）零引用，然后删除 ①`EventTargetCollector` 的 `collectTargets` / `collectTeamTargets` / `collectMaids` / `isMaidOwnedBy`（含其 `GameplayConstants.EVENT_RANGE` / `EVENT_APPLY_MAID` 分支）、②`GameplayConstants.KOMACHI_EXTRA_PLAYS_CAP`、③`effect/ReadyEffect`（`.4` 才下沉、合并后三线零引用）。判定要点：`collectTargets` 的 6 处 grep 命中全部是消费方 `RandomCardHandler` 自己的同名方法（不同类、不同签名），库侧方法确为 0 引用；`EVENT_APPLY_MC_TEAM` / `EVENT_APPLY_FTB_TEAM` / `EVENT_APPLY_OPAC` 与 `EventTargetCollector.collectTeamPlayers` 是**在用**能力（消费方多处调用），原样保留。命令与输出见 `temp/t5-cleanup-notes-20260917.md`。
- **过渡符号按期删除（同批完成）——事件框架三件套**：`event/AstralEventType` / `EventContext` / `EventEffect` 三个文件整体删除（三者互相引用：`AstralEventType(ResourceLocation, EventEffect)` 与 `EventEffect.apply(EventContext)`、`AstralEventType.trigger(EventContext)`，必须同批删），并移除 `neoforge-26.1.2/build.gradle` 里专为 `AstralEventType` 留的 `sourceSets.main.java.exclude` 行（文件已删，该行只会变成无效残留）。**前提工序**：合并后的消费方 `event/AstralEventSystem.java` 曾在 import 段留下 4 处指向本库的 `import`（方法体已不用，但未解析的 import 就是编译错误），先由消费方侧提交 `7dc64cb` 清掉，库侧才可删除；此后 `git grep -E 'AstralEventType|EventContext|EventEffect' -- '*.java'` 在消费方三线为 **0 命中**。⚠️ 由于这一前提在 `.5` 首次发布之后才满足，`.5` 的库字节**重新发布过**（同版本号、不同字节）——消费方必须以 `--refresh-dependencies`（或清 mavenLocal 缓存）重新解析，否则会继续用带三件套的旧 `.5` 字节。
- **版本与消费方同步**：库版本 `1.0.0-SNAPSHOT.4 → 1.0.0-SNAPSHOT.5`（三个平台 `gradle.properties` 同号）；dev-next 两侧 `starengine_lib_version` 与 `starengine_lib_version_range`（两个 `mods.toml` 经 `${starengine_lib_version_range}` 展开）同步到 `[1.0.0-SNAPSHOT.5,2.0)`；消费方 CI 钉住的库检出 `ref` 换成 `.5` 删除三件套后的库提交 sha，并在注释里写明「库版本 bump 必须与本 ref 同一次提交更新」。⚠️ 库仓库目前**尚未 push**，该 sha 仅存在于本机，CI 检出会失败直到库被推送且该 sha 可达。

## 1.0.0-SNAPSHOT.4

> 约定：对当前版本已记录条目的后续改动，直接合并进原条目，仅保留改动后的最终版本，不追加“再次修改”条目。
> 版本号说明：`.4` 是 `.3` 的**续接**而非重做 —— 库内容只**新增**了 `common/effect/ReadyEffect`。必须 bump 的原因与 `.3` 相同：该版本号不以 `-SNAPSHOT` 结尾（按 Maven 语义属普通版本），Gradle 不会把它当作 changing module，不 bump 则消费方会继续解析 `mavenLocal` 里的 `.3` 旧 jar（缺该类 → 编译期报「找不到符号」）。`.3` 的条目原样保留在下一节。

### 新内容

- **`effect/ReadyEffect` 下沉进库**：该类在三线（`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2`）各有一份且**字节完全一致**，只 import 原版 `MobEffect` / `MobEffectCategory`，不引用任何消费方专有类 —— 是本轮按「三线字节一致 + 自包含」口径筛出的 53 个候选里**唯一尚未进库**者（其余 52 个已在前序 Phase 下沉或按原因排除，见「工程」）。下沉后位于 `common/src/main/java/com/merlinkitsune/starenginelib/effect/ReadyEffect.java`（14 行 / 540 字节），与原文件逐字节相同、仅 `package` 一行改为库包名，行尾保持 CRLF 不变。

### 工程

- **三线共享类的下沉判定口径固化**（清单：`temp/sink-manifest-20260917.md`）：候选闸门 = 「三个子项目同相对路径的 java 文件 Sha256 完全相同」（三线共 229 个 java 文件 → 候选 53 个）；自包含闸门 = 「引用闭包只落在候选集 ∪ 库内已有类 ∪ Minecraft/加载器 API」。排除项逐条列明并给出原因：非三线一致（176 个）、依赖消费方专有类（如 `ModItems` / `ModAttachments` / `BaseChipItem` / `DiceCombatModifiers` / `SpellDamageRegistry`）、以及 3 个 mixin（`mixin/trade/Merchant{Container,Menu,ResultSlot}Mixin`，需要 mixin 配置与 refmap，库内无此基建）。
- **合并前漂移核对（本次据此判定「不需再改这些类」）**：dev-next 已删除、而主线在删除之后又改过的库同源副本逐条比对 —— `component/GameplayConstants` 的常量改动（`MAX_MARKER` 16→32、`HAND_FAN_BIG_RANGE` 转 `final`、`MAX_STARLIGHT`/`EFFECT_CARD_COOLDOWN_SECONDS`/`MAX_EFFECT_STACKS` 转 `final` 等）库内**已取主线值**，两侧差异只剩「库用值快照 seam、主线用 `ModCommonConfig`」与按既有约定暂留的过渡符号（`EVENT_RANGE` / `EVENT_APPLY_MAID` / `KOMACHI_EXTRA_PLAYS_CAP` / `TARGET_SELECT_RADIUS`）；`event/EventTargetCollector` 库内**暂留全量实现**（合并前消费方仍在调用 `collectTargets(...)`）；`item/BossEntityUtil`、`event/SignActiveTriggeredEvent`、`event/ModEffectRemoval`、`client/ActionBarManager`（三个平台各一份）的差异全部是 shim 基类与注释。⇒ 主线这批改动不会在合并中丢失。
- **`common` 内两处消费方包名残留已清除**：`effect/HealingEffect` 与 `event/AmethystDiceHandler` 的 javadoc 里还留着 `{@link com.merlinkitsune.astral_dice.item.HealingManager}` 与 `{@link com.merlinkitsune.astral_dice.combat.SpellDamageRegistry}` —— 上一轮的包名重构只把 `starengine` 段换成 `starenginelib`，消费方包名被原样留下，不影响编译但违反「`common` 不得出现消费方专有类引用」的验收口径。改为纯文本描述，功能与签名零改动；清理后**全库（含三个平台子项目）对 `com.merlinkitsune.astral_dice` 的引用为 0**。
- **消费方区间下界抬到 `1.0.0-SNAPSHOT.4`**：dev-next worktree 两个 `gradle.properties` 的 `starengine_lib_version` 与 `starengine_lib_version_range` 已同步，两侧 `mods.toml` 经 `${starengine_lib_version_range}` 展开后同为新区间。语义与 `.3` 同构（同为「精确下界」）：`[1.0.0-SNAPSHOT.4,2.0)` 拒绝 `.1` / `.2` / `.3`，接受 `.4` / `.10` / `1.0.0` / `1.1.0`，仍拒绝 `2.0.0`。

## 1.0.0-SNAPSHOT.3

> 约定：对当前版本已记录条目的后续改动，直接合并进原条目，仅保留改动后的最终版本，不追加“再次修改”条目。
> 版本号说明：本库此前以 `1.1.0` 在本地构建（除 `mavenLocal` 外未发布到任何地方），为首次对外发布改号为 `1.0.0-SNAPSHOT.1`；随后改为 **`1.0.0-SNAPSHOT.2`** —— Java 包名发生重命名，而 `mod_version` 仍停在 `1.0.0`，只能靠 patch 段承载这次改名，否则依赖区间无从区分新旧 jar；现又改为 **`1.0.0-SNAPSHOT.3`** —— 完全移除 Cloth Config 前置与库内公共配置模块、把 `GameplayConfigValues` 由 13 字段收敛为 6 字段并删除事件框架死代码，同样是二进制不兼容变更，必须再占一个 patch 段才能让消费方摆脱 `mavenLocal` 里的 `.2` 旧 jar。又因 `1.0.0-SNAPSHOT.1` 与 `.2` 除 `mavenLocal` 外未进入任何仓库，三个版本号的条目在此合并为一份 `1.0.0-SNAPSHOT.3` 更新日志。

### 破坏性变更

- **Java 包名 `com.merlinkitsune.starengine` → `com.merlinkitsune.starenginelib`，Maven `groupId` 随之迁移。** 只在「这是包名」的位置把 `starengine` 段替换为 `starenginelib`：`package` / `import` 语句、javadoc `{@link}` 目标、三个源码目录（`common`、`neoforge-1.21.1`、`forge-1.20.1`）与两侧 `gradle.properties` 的 `mod_group_id` —— 共 50 个文件、69 处，另加发布用 `groupId`（`com.merlinkitsune.starengine` → `com.merlinkitsune.starenginelib`）。**有意保持不变**的：mod id `starengine_lib`、显示名 `StarEngine Lib`、全部 `StarEngine*` 类名，以及所有历史记录（旧 CHANGELOG 条目、`docs/starengine-lib/`、一次性脚本 `tools/migrate_to_starengine_lib.py`）。本次改名**在同一版本号上二进制不兼容** —— 改名前的 `.1` jar 与本 `.2` jar 拥有完全相同的 `modId` 与相同的 MC 版本后缀，但类名不同 —— 因此消费方必须同时抬高区间下界（见下方「工程」）。替换为字面量、字节级（`starengine(?![A-Za-z0-9_])`，大小写敏感），故 `starengine_lib`（mod id）、`starenginelib`（新包名）、`StarEngine`（品牌/类名前缀）均不可能被命中；且保留原行尾，diff 严格「一处一行」，无 CRLF/LF 噪音。改名后以开包方式核对：两个平台 jar 分别 45（neoforge）/ 47（forge）个 class，类路径与常量池中**旧包名 0 处**；对仓库（含二进制）的全字节扫描同样 0 处真引用。若某消费方产物把库的全限定类名以**字符串**形式配对（Mixin 配置指向 `com.merlinkitsune.starengine.*`、混淆映射、反射或代码生成），也必须同步改写；本仓库已无此类引用。客户端可见影响：无。

### 新内容

- 首次从 Astral Dice 1.20.1 / 1.21.1 多加载器单仓中提取出 **StarEngine Lib**（`starengine_lib`）：将两个 MC 版本可共同引用的 34 个共享单元（18 个 `MobEffect` 实现、`AstralEventType`/`EventContext`/`EventEffect`/`EventTargetCollector` 事件框架、目标选择框架、`BossEntityUtil`、`ClientDamageNumbers`、`GameplayConstants`、`SignActiveTriggeredEvent`、`AmethystDiceHandler`）下沉至本库，供后续 **Astral Dice Extra（星之骰戏：扩展）** 及同系列模组复用。
- 本库**不注册任何**注册表条目（物品/效果/附件/数据组件/能力全部留在消费方 mod）。因此引入或升级本库都不会改变任何 `ResourceLocation` 归属，既不损坏存档，也不破坏数据包。
- 提供**平台无关的配置值 seam**：`GameplayConfigValues`（纯值快照 record）与 `GameplayConstants.applyConfig(...)`。配置文件与配置读写**不进库** —— `ModConfigSpec`（NeoForge）与 `ForgeConfigSpec`（Forge）不通用，故配置项定义、TOML 读写与配置屏幕全部留在消费方 mod，库只声明「哪些玩法数值可配置」以及如何应用。消费方在自己的配置加载/重载回调里构造快照并推送，**改完配置不需重启**（`GameplayConstants` 字段是非 final 的运行时读取）。

- 新增 **`neoforge-26.1.2` 平台**：本库由「两版同源」变为「**三版同源**」—— `common` 同一份共享源码现由 Java 17（forge-1.20.1）、Java 21（neoforge-1.21.1）与 Java 25（neoforge-26.1.2；MC 26.1.2 / NeoForge 26.1.2.109 / ModDevGradle 2.0.147 / **无 Parchment**，Parchment 官方 maven 对 26.1.x / 26.2 全部 404）各编译一次，三个平台各产出一个 jar 并共用同一版本号。
### 工程

- **多加载器单仓结构**：`common/src/main/java` 是共享**源码目录**（不是 Gradle 子项目）；`neoforge-1.21.1`（Java 21 · Mojmap + Parchment · ModDevGradle 2.0.141）与 `forge-1.20.1`（Java 17 · reobf SRG · ModDevGradle Legacy 2.0.144）两个子项目各自把它加入 `sourceSet` 并编译同一份源码一次，每个 MC 版本各产出一个 jar。
- **共享源码的两条硬约束**，均由编译期而非约定强制：`common` 不得使用 Java 21 独有语法（record pattern / switch pattern / `SequencedCollection` 等，由 Java 17 工具链编译同一份源码强制），且只能使用两版 MC 中都存在且**签名一致**的 API。
- **加载器差异留在平台子项目中**，每个差异各写一份：`ActionBarManager`（`DeltaTracker` / `float partialTick`）、`ModEffectRemoval`（`Holder<MobEffect>` / `MobEffect`）、自定义数据键（原版 `DataComponent` / Forge 的 `ItemDataKey`）、饰品集成（`CuriosCompat`，仅 Forge）。
- **加载器差异以 shim 收敛**：`platform/LoaderEvent` 是抽象基类，两侧分别继承各自的 Event Bus 事件类型（NeoForge / Forge）；`platform/LoaderTags` 暴露 `c:bosses` 通用 tag，取自平台自身的 `Tags` 类。共享文件（如 `SignActiveTriggeredEvent`、`BossEntityUtil`）只引用 shim，因此共享代码中不再残留任何 `net.neoforged.*` / `net.minecraftforge.*` 字面量。
- **配置不进库（值快照 seam）**：`ModConfigSpec`（NeoForge）与 `ForgeConfigSpec`（Forge）不通用，配置文件的读写与配置 GUI 只能各平台自己做，因此库内**不持有配置 schema、不读配置文件、不提供配置屏幕**，只保留平台无关的 `GameplayConfigValues`（纯 record 值快照）与 `GameplayConstants.applyConfig(...)`；消费方注册自己的配置后，把当前值装进快照推送即可热生效。record 字段即契约 —— 消费方按**位置**构造该 record，增删/改序字段会让消费方在**编译期**失败（这正是刻意选择 record 而非松散 getter 集合的原因）。由此 `common` 中既无加载器字面量、也无任何第三方配置库依赖，库的必需前置只剩加载器本身（Forge 侧另有 Curios）。
- **版本号 bump 到 `1.0.0-SNAPSHOT.3`（必须）**：本次删除库内公共配置模块与事件框架类、并收敛 `GameplayConfigValues` 字段，属二进制不兼容变更；而该版本号不以 `-SNAPSHOT` 结尾（按 Maven 语义属普通版本），Gradle 不会把它当作 changing module —— 不 bump 则消费方会继续解析 `mavenLocal` 里的 `.2` 旧 jar，随后在编译期报「找不到符号」。
- **Maven 发布**：NeoForge 侧 `from components.java` 发布普通 Mojmap jar（NeoForge 自 1.20.5 起编译与生产同为 Mojmap，无需重映射）；Forge 1.20.1 侧发布 `reobfJar`，即**生产 SRG jar** —— 消费方 MDG LegacyForge 会在解析期把 SRG jar 重映射为 dev Mojmap 命名，若发布未重混淆的 dev jar，生产环境会因成员名为 Mojmap 而 `NoSuchFieldError`。两侧均显式指定 `artifactId`，使坐标与 `archivesName` 一致。
- **build 自动部署**：`build` 结束后 `pushToPack` 会把库 jar 复制进两个整合包的 `mods` 目录（Forge 测试包推 SRG jar、NeoForge 包推普通 jar），与消费方既有的「build 即部署」约定一致。此步不可省略：消费方把 `starengine_lib` 声明为必需前置，库 jar 不在包内会导致整合包直接拒绝启动。⚠️ **自 2026-09-12 起，开发分支上不再推送 —— `main` 已在豁免名单中。** 本库与消费方必须以**成对匹配**的版本一起进整合包（消费方既在编译期锁定库版本，又在 `mods.toml` 声明库版本区间），只要一侧单独前进，整合包就会带着不匹配的组合在启动期以 `NoClassDefFoundError` 或「缺失必需前置」崩溃。消费方已在其开发分支 `multi-dev-next` 上停推，故本库同步在 `main` 上停推，让整合包冻结在最后一个匹配的组合上，而不是被单侧覆盖。守卫在**执行期**读 `.git/HEAD` 判定当前分支（对 configuration-cache 友好、切分支立即生效），并兼容两种形态 —— 普通仓库的 `.git` 目录，以及 worktree 下作为**文件**、内容为 `gitdir: <path>` 的 `.git`；无法确定分支时（detached HEAD、无 `.git`，如 CI）同样跳过，取安全侧。`-PdeployToPack` / `-PpackPush` 仍可强制推送（手动出包用）—— 使用时请把消费方 jar 一并推送，保持成对。
- **托管到 GitHub，并配上独立 CI 与 Release**：仓库现位于 <https://github.com/merlin-kitsune/starengine_lib>，新增 `.github/workflows/build.yml`，沿用 Astral Dice 的发布规范：每次 push / PR / 手动触发均以 JDK 21 + JDK 17 构建双平台，并上传 `starengine_lib-jars` 构建产物；push 到 `main` 自动打 tag（tag = 基础版本号，无 `v` 前缀、无 `+加载器` 后缀）；push tag 则创建/更新 GitHub Release，附件为两个平台的 jar。相较消费方规则**多加一道守卫**：版本号含 `-`（即快照）时**不打 tag**，否则 `1.0.0-SNAPSHOT.1` 会被截成 `1.0.0` 并被当成正式发布自动打出，且 tag 难以回收。另修复了 `gradlew` 在 git 索引中丢失的可执行位（`100644` → `100755`）：仓库自 Windows 提交，wrapper 不带执行权限，Runner 上 `./gradlew` 在 Gradle 启动前就因 `Permission denied`（exit 126）退出；又因消费方 CI 需先 checkout 本库，这一个缺陷会同时打红**两个**仓库的 Action。
- **前置版本区间下界必须是 `1.0.0-SNAPSHOT` 而非 `1.0`**：按 Maven `ComparableVersion` 语义 `1.0.0-SNAPSHOT.1 < 1.0`，消费方若写 `versionRange="[1.0,2.0)"` 会拒绝所有 `1.0.0-SNAPSHOT.x`，游戏直接以「不满足必需前置」拒绝加载。已用 `maven-artifact` 3.8.5 实测（两侧加载器均经 `MavenVersionAdapter.createFromVersionSpec` → `VersionRange.createFromVersionSpec`）：`[1.0,2.0)` 不含 `1.0.0-SNAPSHOT.1`，而 `[1.0.0-SNAPSHOT,2.0)` 连同 `1.0.0` / `1.1.0` 一并包含。下界还须精确到快照序号（当前为 `.2`，见下一条），否则区间过宽、挡不住同坐标的旧 jar。
- **消费方必须把区间下界抬到 `1.0.0-SNAPSHOT.3`**：相邻快照之间是**二进制不兼容**的（`.1` → `.2` 改包名；`.2` → `.3` 删除库内公共配置模块、收敛 `GameplayConfigValues` 字段并删除事件框架类），而旧 jar 的 `modId` 与版本号形态完全相同、只差 patch 段，故宽松区间（如 `[1.0.0-SNAPSHOT,2.0)`）会把残留的旧 jar 一并放行 —— `.1` 会在运行期以 `NoClassDefFoundError: com/merlinkitsune/starengine/...` 崩溃，`.2` 会在编译期因找不到 `StarEngineConfigs` 等已删除的类而失败。`[1.0.0-SNAPSHOT.3,2.0)` 拒绝 `.1` / `.2`，同时接受 `.3` / `.10` / `1.0.0` / `1.1.0`，并照旧拒绝 `2.0.0`。已用 `maven-artifact` 3.8.5 按三区间 × 七版本实测（`[1.0.0-SNAPSHOT.2,2.0)` 拒绝 `.1`、接受 `.2`/`.3`/`.10`/`1.0.0`/`1.1.0`、拒绝 `2.0.0`；三者同为「精确下界」语义，故把下界换成 `.3` 的行为与之完全同构）；注意 `.10` 是按**数值**而非字典序比较，故两位数的 patch 段不会掉出区间。
- **消费方 CI 改为先构建本库**：本库仅发布到 `mavenLocal`，故 Astral Dice 的 `build.yml` 会先 checkout 本仓并执行 `./gradlew publishToMavenLocal`，再构建自身。因此两个仓库的版本号必须严格对齐（消费方的 `starengine_lib_version` ↔ 本库的 `lib_version`），否则消费方 CI 会断在依赖解析。
- **26.1 的两处平台差异下沉进 shim**：① 标识符类由 `net.minecraft.resources.ResourceLocation` 改名为 `Identifier`（1.20.1 / 1.21.1 仍用旧名）；② `EntityType#is(TagKey)` 被移除，须经 `builtInRegistryHolder()`。② 由 `platform/LoaderTags#isBoss(Entity)` 吸收（共享源码 `BossEntityUtil` 只调用该 shim，不感知版本差异），26.1.2 侧的 `LoaderTags` 另改用 `TagKey.create` 直接构造 `c:bosses`，不依赖随版本漂移的平台 Tags 常量；① 因 Java 无类型别名，唯一直接用旧名的 `common/event/AstralEventType` 被 `sourceSets.main.java.exclude` 排除在 26.1.2 编译之外（它与其同族在消费方主线已删除，本库也只是暂留）。
