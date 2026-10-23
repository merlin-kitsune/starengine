# StarEngine

**English** | [中文](README_ZH.md)

`starengine` is the shared engine library used by the **Astral Dice** mod family.

It was extracted from the 1.20.1 / 1.21.1 dual-version repository of
[Astral Dice](https://github.com/merlin-kitsune/astral_dice), so that
**Astral Dice Extra** and other mods in the family can reuse the same implementations.

---

## 1. Core design constraints

These three constraints explain the repository layout.

### 1.1 The library adds no gameplay registry content

Items, effects, data components and capabilities all stay in the consumer mod. The library adds no gameplay registry content, so a library update does not change the owner of a `ResourceLocation` and cannot corrupt saves or break data pack references.

Fabric is the one platform with a library-owned attachment: `FabricEconomyStorage` registers `starengine:star_coin_wallet` (and a temporary legacy id) as the wallet's persistence backend. An attachment is entity data, not an item/block/effect registry entry. The three FML platforms store the wallet in player NBT (`ForgeData` / `NeoForgeData`).

### 1.2 Across MC versions, share source, not artifacts

1.20.1 Forge (SRG member names / Java 17), 1.20.1 Fabric (Mojmap / Java 17), 1.21.1 NeoForge (Mojmap / Java 21) and 26.1.2 NeoForge (Mojmap / Java 25) cannot share one compiled artifact. The project has no `common` Gradle subproject. Instead:

```
common/src/main/java        <- shared source directory (NOT a Gradle subproject)
neoforge-1.21.1/            <- platform subproject: Java 21 - Mojmap + Parchment - ModDevGradle 2.0.141
forge-1.20.1/               <- platform subproject: Java 17 - reobf(SRG) - ModDevGradle Legacy 2.0.144
neoforge-26.1.2/            <- platform subproject: Java 25 - Mojmap (no Parchment) - ModDevGradle 2.0.147
fabric-1.20.1/              <- platform subproject: Java 17 - Mojmap - Fabric Loom 1.14.10 (Loader 0.19.5, Fabric API 0.92.12+1.20.1)
```

Every platform subproject adds `sourceSets.main.java.srcDir('../../common/src/main/java')`, so the shared source is compiled four times and each platform produces one jar.

### 1.3 Two hard constraints on the shared source

Sources under `common` must compile on every platform:

| Constraint | Reason | Enforced by |
|---|---|---|
| No Java 21-only syntax (record patterns / switch patterns / `SequencedCollection`, ...) | the same source is compiled by a Java 17 toolchain | a `forge-1.20.1` or `fabric-1.20.1` compile failure |
| Only APIs that exist in all supported MC versions with identical signatures | the platform API surfaces differ | a compile failure on any platform |

**Version divergences stay inside the platform subprojects**, with one copy of each diverging class. Current divergences:

| Divergence | neoforge-1.21.1 | forge-1.20.1 | neoforge-26.1.2 | fabric-1.20.1 |
|---|---|---|---|---|
| Client frame delta / render params | `DeltaTracker` + `GuiGraphics` | `float partialTick` + `GuiGraphics` | `DeltaTracker` + `GuiGraphicsExtractor` | `float partialTick` + `GuiGraphics` |
| Effect instance type | `Holder<MobEffect>` | `MobEffect` | `Holder<MobEffect>` | `MobEffect` |
| Custom data keys | `DataComponent` (vanilla component system) | `ItemDataKey` (Forge extension point) | `DataComponent` | `ItemDataKey` (NBT-backed shim) |
| Accessory integration | vanilla/NeoForge capability | `CuriosCompat` adapter (**compile-time only**, not a prerequisite) | vanilla/NeoForge capability | `TrinketsCompat` adapter (**compile-time only**, not a prerequisite) |
| Identifier class | `ResourceLocation` | `ResourceLocation` | `Identifier` (renamed in 26.1) | `ResourceLocation` |
| Entity tag test | `EntityType#is(TagKey)` | `EntityType#is(TagKey)` | `EntityType#builtInRegistryHolder().is(TagKey)` | `EntityType#is(TagKey)` |

The last two rows are absorbed by `platform/LoaderTags#isBoss(Entity)`; the shared source does not see them.

Historical exception (removed in `1.0.0-SNAPSHOT.5`): `common/event/AstralEventType` was the only shared file that could not compile on the then-three lines, because its record component used the old `ResourceLocation` name while 26.1 renamed the type to `Identifier`. It was excluded from the 26.1.2 compile through `sourceSets.main.java.exclude`. The event framework trio (`AstralEventType` / `EventContext` / `EventEffect`) was deleted in `1.0.0-SNAPSHOT.5` after the merge, when the consumer had zero references on all lines, and the `exclude` line was removed in the same batch. Do not add it back.

---

## 2. How loader differences are contained (shims)

The shared source contains no loader imports or calls. Each difference is absorbed by a shim under `platform/`:

| Shim | Purpose |
|---|---|
| `platform/LoaderEvent` | Abstract base class. NeoForge extends `net.neoforged.bus.api.Event`; Forge extends `net.minecraftforge.eventbus.api.Event`. Fabric has no loader-level event base, so its version is an empty abstract class and the consumer dispatches the event. Shared `SignActiveTriggeredEvent` only extends this class. |
| `platform/LoaderTags` | The `c:bosses` common tag. NeoForge uses `net.neoforged.neoforge.common.Tags`; Forge uses `net.minecraftforge.common.Tags`. 26.1.2 and Fabric build the `TagKey` directly: 26.1.2 because of the `Identifier` rename, Fabric because it has no platform-level tag constant. Shared `BossEntityUtil` only calls `isBoss(Entity)`. |

The only loader package name in shared source is a Javadoc link to `net.neoforged.fml.common.asm.enumextension.EnumProxy` in `item/Rarity`; there is no loader import or runtime reference. Adding a platform means adding a subproject with the same shim set: an entry point, `ActionBarManager`, an economy storage, `EffectTimerAccess`, `ModEffectRemoval`, `AstralRarities`, the two platform shims and, where needed, `ItemDataKey` and an accessory adapter. The shared source is not edited; the Fabric 1.20.1 platform added on 2026-10-01 required zero changes under `common`.

---

## 3. Configuration: the config file stays in the consumer; the library exposes a value snapshot

`ModConfigSpec` (NeoForge), `ForgeConfigSpec` (Forge) and Fabric's config handling are not interchangeable, and reading or writing the config file plus the config GUI can only be done by each platform. Configuration therefore does not enter the library. The config file (`config/astral_dice-common.toml`), the config screen and the config entry definitions stay in the consumer mod. The library keeps one platform-agnostic seam:

```
library (shared, common/)                              consumer (per platform)
GameplayConfigValues (plain value-snapshot record)     config/ModCommonConfig (ModConfigSpec / ForgeConfigSpec)
GameplayConstants.applyConfig(GameplayConfigValues)    -> registers the config during @Mod construction and pushes a fresh snapshot on load/reload
```

- The library does not read the config file, hold a config schema or provide a config GUI. It declares which gameplay values are configurable (the record fields) and how to apply them (`applyConfig`).
- After registering its own config, the consumer packs the current values into `GameplayConfigValues` and pushes them to `GameplayConstants`. No restart is needed after a config change: the `GameplayConstants` fields are non-final runtime reads.
- `common` contains no loader import and no third-party config library. On the three FML platforms the only required prerequisite is the loader itself. Fabric additionally requires Fabric API, including `fabric-data-attachment-api-v1`; the library's `fabric.mod.json` declares both. Curios on Forge and Trinkets on Fabric are `modCompileOnly` dependencies of the adapter classes (`item/CuriosCompat`, `item/TrinketsCompat`) and are not prerequisites.
- **The record fields are the contract.** The consumer constructs the record positionally, so adding, removing or reordering fields fails at compile time. Adjusting configurable entries requires updating both sides.

The library once held the config schema in `common` (Cloth Config AutoConfig, writing `config/astral_dice.json`). That approach was withdrawn: the Cloth Config prerequisite and the AutoConfig classes were removed, configuration returned to the consumer's TOML, and the library kept only the seam above.

---

## 4. Building and publishing

```bash
# Build all four platforms (jar only; no automatic modpack push, see §4.3)
./gradlew build

# Build a single platform
./gradlew :neoforge-1.21.1:build
./gradlew :forge-1.20.1:build
./gradlew :neoforge-26.1.2:build
./gradlew :fabric-1.20.1:build

# Publish to the local Maven repository so the consumer's modImplementation can resolve it
./gradlew publishToMavenLocal
```

### 4.1 Publish coordinates

| Platform | Coordinate | Published artifact |
|---|---|---|
| NeoForge 1.21.1 | `com.merlinkitsune.starengine:starengine-neoforge-1.21.1:1.1.0-pre.2` | `jar` (Mojmap; no remapping) |
| Forge 1.20.1 | `com.merlinkitsune.starengine:starengine-forge-1.20.1:1.1.0-pre.2` | `reobfJar` (**production SRG jar**) |
| NeoForge 26.1.2 | `com.merlinkitsune.starengine:starengine-neoforge-26.1.2:1.1.0-pre.2` | `jar` (Mojmap; no remapping) |
| Fabric 1.20.1 | `com.merlinkitsune.starengine:starengine-fabric-1.20.1:1.1.0-pre.2` | `remapJar` (**production intermediary jar**) |

The four platforms share the same `lib_version`. On a version bump all four `gradle.properties` files change together; `mod_version` keeps its platform suffix.

**Forge must publish `reobfJar`, not `jar`.** The consumer's MDG LegacyForge remaps the production SRG jar to dev Mojmap names during resolution. An un-reobfuscated dev jar makes the production environment throw `NoSuchFieldError` because the member names are Mojmap.

**Fabric must publish `remapJar`.** Loom remaps the production intermediary jar to dev names for `modImplementation`; a dev jar does not resolve correctly.

### 4.2 How the consumer integrates

```groovy
// NeoForge 1.21.1
repositories { mavenLocal() }
dependencies {
    implementation "com.merlinkitsune.starengine:starengine-neoforge-1.21.1:1.1.0-pre.2"
}

// Forge 1.20.1
repositories { mavenLocal() }
dependencies {
    modImplementation "com.merlinkitsune.starengine:starengine-forge-1.20.1:1.1.0-pre.2"
}

// NeoForge 26.1.2
repositories { mavenLocal() }
dependencies {
    implementation "com.merlinkitsune.starengine:starengine-neoforge-26.1.2:1.1.0-pre.2"
}

// Fabric 1.20.1
repositories { mavenLocal() }
dependencies {
    modImplementation "com.merlinkitsune.starengine:starengine-fabric-1.20.1:1.1.0-pre.2"
}
```

The library is published to **mavenLocal** for development; there is no remote Maven. A consumer clones this repository and runs `./gradlew publishToMavenLocal` (for CI, see §4.4). Players download release jars from CurseForge (§4.3).

Dependency declarations in the consumer:

| Platform | Declaration |
|---|---|
| NeoForge 1.21.1 / 26.1.2 | `neoforge.mods.toml`: `type="required"`, `ordering="AFTER"`, `side="BOTH"`; `versionRange` is omitted, so the loader checks presence only |
| Forge 1.20.1 | `mods.toml`: `mandatory=true`, `versionRange="*"`, `ordering="AFTER"`, `side="BOTH"`. Omitting `versionRange` makes the mandatory dependency unsatisfiable |
| Fabric 1.20.1 | `fabric.mod.json`: `"starengine": ">=1.1.0-pre.2"` in `depends` |

Example (NeoForge):

```toml
[[dependencies.<mod_id>]]
    modId="starengine"
    type="required"
    ordering="AFTER"
    side="BOTH"
```

For Forge, use `mandatory=true` and `versionRange="*"` instead of `type="required"`.

Historical note: pins used to carry a version range. The table below records how Maven resolved snapshot-era ranges; it is kept only to interpret old pins and is not current practice. Measured with `maven-artifact` 3.8.5; both loaders go through `MavenVersionAdapter.createFromVersionSpec`.

> | Range | `.1` (pre-rename) | `.2` | `.3` | `.4` | `.5` | `.6` | `.7` | `.10` | `1.0.0` | `1.1.0` | `2.0.0` |
> |---|---|---|---|---|---|---|---|---|---|---|---|
> | `[1.0.0,2.0)` (the 1.0.x range) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ |
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
> The `.10` column verifies that numbers compare numerically, not lexicographically. The `.7` column is kept because that number was only published to mavenLocal and never entered git, so its in-flight fix was folded into `.10`; the verdicts remain valid.

> After modifying library code, the consumer's Gradle caches the mavenLocal resolution result. If the consumer does not pick up the new version, bump `lib_version` and republish; `--refresh-dependencies` only clears the local cache.

### 4.3 Distribution and JarJar status

`pushToPack` was removed on 2026-09-24. The library writes nothing into a modpack `mods` folder. The task, its `packPushBranches = ['main']` whitelist, the `-PdeployToPack` switch and the symbols `packModsDir` / `packPushBranches` / `forcePackPush` / `dotGitEntry` / `jarArchiveProvider` are gone.

Distribution channels:

| Channel | Jar download | Notes |
|---|---|---|
| GitHub Releases | no | source archives only; CI deletes any jar asset |
| [CurseForge `1755415`](https://www.curseforge.com/projects/1755415) | yes | the only store; Modrinth publishing was permanently dropped on 2026-10-23 |

JarJar status in the consumer (Astral Dice), from 2026-10-20:

| Consumer line | Library delivery | Player installation |
|---|---|---|
| neoforge-1.21.1 | no JarJar; `implementation` dependency | install `starengine-neoforge-1.21.1-<version>.jar` in `mods/` |
| forge-1.20.1 | no JarJar; `modImplementation` dependency | install `starengine-forge-1.20.1-<version>.jar` (production SRG jar) in `mods/` |
| neoforge-26.1.2 | JarJar embedded (migration pending) | only `astral_dice-*.jar`; do not add a standalone library jar |
| fabric-1.20.1 | Loom `include` embedded (migration pending) | only `astral_dice-*.jar`; do not add a standalone library jar |

For the two embedded lines, FML and Fabric Loader de-duplicate by modId. A standalone library jar takes precedence over the nested copy; when it is older, the result is `NoSuchMethodError` or `NoClassDefFoundError`, and the symptom looks like "the library did not ship with the mod". Keep only the consumer jar in `mods/` for those lines.

For 1.21.1 and 1.20.1 the standalone library jar is required. The consumer no longer embeds a copy, so there is nothing to shadow.

If you need a jar for A/B testing, build it from source (`./gradlew build`, see §4); do not expect one on the GitHub Release page.

### 4.4 CI / automatic releases (GitHub Actions)

GitHub Releases carry source code only. CI builds every platform as a compile check, uploads no build artifact and attaches no jar; any jar asset found on a Release is deleted. Jar downloads are published on CurseForge (§4.3).

The repository is hosted at <https://github.com/merlin-kitsune/StarEngine>. The workflow `.github/workflows/build.yml` follows the Astral Dice release convention:

| Trigger | Behaviour |
|---|---|
| push / PR / manual | triple JDK toolchains (21 / 17 / 25) -> `./gradlew build`: compile check only, nothing is uploaded |
| push to `main` with a final version number | tag automatically (tag = the base version, e.g. `1.0.0`, no `v` prefix, no `+loader` suffix) |
| push a tag matching `/^[0-9]/` | create or update the GitHub Release: no assets (source archives only); existing jar assets are deleted |

The tag rule has one guard compared with the consumer: a version containing `-` (a snapshot or pre-release) is not tagged. Without it, `1.1.0-pre.2` would be truncated by `%%-*` to `1.1.0` and an unfinalised build would be tagged as a release.

**The consumer's CI depends on this library.** Astral Dice's `build.yml` checks this repository out and runs `./gradlew publishToMavenLocal` before building itself, because the library has no remote Maven. Both versions must match (consumer `starengine_version` and library `lib_version`); otherwise the consumer's CI fails at dependency resolution. The checkout is pinned by commit SHA in the consumer's `build.yml` `ref:` line and must point at the commit whose four `gradle.properties` files carry the consumer's pin. Every library bump must update that line. Until the library commit is pushed, the consumer's CI cannot check it out (local builds are unaffected).

---

## 5. Repository contents

```
common/src/main/java/com/merlinkitsune/starengine/  # shared source (53 files)
├── client/       ClientDamageNumbers
├── combat/       CombatFormula, CreatureTargets, DiceBattleResolver, EliteTargets, HostileTargets,
│                 InternalDamageWindows, PartyRelations, PlayerHostilityTracker, TargetBattleStats,
│                 TargetCategory, VanillaMitigation
├── component/    GameplayConfigValues (config value snapshot), GameplayConstants
├── economy/      CommandPermissionGate, EconomyStorage, StarCoinCommand, StarCoinWalletState, StarEngineEconomy
├── effect/       18 MobEffect implementations (ReadyEffect was deleted in 1.0.0-SNAPSHOT.5)
├── event/        AmethystDiceHandler, EffectTimerGuard, EventTargetCollector, SignActiveTriggeredEvent, TimerSource
├── item/         BossEntityUtil, Rarity
├── target/       HoldToSelect, SelectorTargets, SelfTargetable, SignSelectionGate,
│                 TargetSelectionAction, TargetSelectionRegistry, TargetType
└── thirdparty/   ApotheosisMarkers, StarengineThirdParty

neoforge-1.21.1/src/main/java/.../starengine/       # platform-specific (8 files)
├── StarEngineLib          @Mod entry point
├── client/ActionBarManager        (DeltaTracker)
├── economy/NeoForgeEconomyStorage
├── event/EffectTimerAccess        (Holder<MobEffect>)
├── event/ModEffectRemoval         (Holder<MobEffect>)
├── item/AstralRarities
└── platform/LoaderEvent, LoaderTags

forge-1.20.1/src/main/java/.../starengine/          # platform-specific (10 files)
├── StarEngineLib          @Mod entry point
├── client/ActionBarManager        (float partialTick)
├── component/ItemDataKey
├── economy/ForgeEconomyStorage
├── event/EffectTimerAccess        (MobEffect)
├── event/ModEffectRemoval         (MobEffect)
├── item/AstralRarities
├── item/CuriosCompat      (Curios is a compile-time dependency only; the library never calls it)
└── platform/LoaderEvent, LoaderTags

neoforge-26.1.2/src/main/java/.../starengine/       # platform-specific (8 files)
├── StarEngineLib          @Mod entry point
├── client/ActionBarManager        (DeltaTracker + GuiGraphicsExtractor)
├── economy/NeoForgeEconomyStorage
├── event/EffectTimerAccess        (Holder<MobEffect>)
├── event/ModEffectRemoval         (Holder<MobEffect>)
├── item/AstralRarities
└── platform/LoaderEvent, LoaderTags (includes isBoss(Entity), absorbing the 26.1 tag API change)

fabric-1.20.1/src/main/java/.../starengine/         # platform-specific (10 files)
├── StarEngineLib          ModInitializer entry point
├── client/ActionBarManager        (float partialTick)
├── component/ItemDataKey
├── economy/FabricEconomyStorage
├── event/EffectTimerAccess        (MobEffect)
├── event/ModEffectRemoval         (MobEffect)
├── item/AstralRarities
├── item/TrinketsCompat    (Trinkets is a compile-time dependency only; the library never calls it)
└── platform/LoaderEvent, LoaderTags
```

**About `StarEngineLib`**: the entry point makes the library a loadable mod (`@Mod` on NeoForge/Forge, `ModInitializer` on Fabric). It adds no items, blocks, effects or other gameplay registry content. Each platform entry point installs its wallet storage; on Fabric that storage registers the `starengine:star_coin_wallet` attachment and the `/starcoin` command (the FML lines register the command from `RegisterCommandsEvent`). This is the technical basis for "no `ResourceLocation` ownership changes".

---

## 6. Versions and compatibility

- **Compatibility contract (in force since `1.0.0`, applies to all four platforms)**: the library version follows semver, and within one major version (first digit unchanged; currently `1.x`) any breaking change is forbidden. No public type, method, field or constant may be deleted or renamed, and its visibility, signature or existing semantics may not change. Only additions (new types, new members, new optional entry points) and behaviour fixes that do not alter the contract are allowed. When a breaking change is required, the first digit must be incremented (`1.x` -> `2.x`) and the consumer's pin updated in the same release; breaking changes are not allowed in a minor or patch position.
  The contract is not encoded as a loader version range. Current declarations: the NeoForge lines use `type="required"` without `versionRange` (presence check); Forge uses `mandatory=true` with `versionRange="*"`; Fabric uses `depends.starengine = ">=1.1.0-pre.2"`. The installed version is pinned by the consumer's `starengine_version`. `1.1.0-pre.2` is a pre-release and is not a drop-in replacement for `1.0.x`: it carries the breaking rename recorded below. The no-breaking rule covers final `1.x` releases, not pre-release transitions.
- **Current version = `1.1.0-pre.2`** on all four platforms. The four `gradle.properties` files carry it as `lib_version`; `mod_version` appends `+neoforge_1.21.1`, `+forge_1.20.1`, `+neoforge_26.1.2` or `+fabric_1.20.1`. The consumer's four lines pin `starengine_version=1.1.0-pre.2`. `1.0.0` was the first final release, normalised from `1.0.0-SNAPSHOT.16` with zero Java source changes. **There is no `1.0.1`**; see the next bullet.
- **Merged into `1.0.0` (2026-09-22): the Curios prerequisite is removed on the Forge side** (user decision). The change was briefly prepared as a separate `1.0.1` release and then folded back into `1.0.0` before it was pushed or published, so **`1.0.1` has no tag, no Release and no artefact**. The Forge `mods.toml` drops the `modId="curios"` dependency block, and that side's dependency becomes `modCompileOnly`. `item/CuriosCompat` needs Curios at compile time only, and the library itself never calls it. **None of the four platform jars declares Curios as a prerequisite.** A consumer that calls that shim declares Curios itself (`astral_dice`'s 1.20.1 side already declares `mandatory=true [5,6)`). This is a pure relaxation, so it is legal within `1.x`. The only cost: if a consumer forgets to declare Curios yet calls the shim, the failure degrades from a loader-level missing dependency error to a runtime `NoClassDefFoundError`.
- **Merged into `1.0.0` (2026-09-23): the missing platform implementation on the `neoforge-26.1.2` line**. This too was briefly prepared as a separate `1.0.1` release and folded back into `1.0.0`. The first stable release shipped the wallet ledger's platform storage only on `forge-1.20.1` (`ForgeEconomyStorage`) and `neoforge-1.21.1` (`NeoForgeEconomyStorage`), so on 26.1.2 `StarEngineEconomy.isAvailable()` stayed `false` (no `/starcoin`, picked-up coins not absorbed, balance bar always 0). **`1.0.1` has no tag, no Release and no artefact**; the `1.0.0` tag keeps its name and its Release notes are refreshed by this push.
- **The snapshot series (`1.0.0-SNAPSHOT.*`) is terminated and not covered by the contract above.** Snapshots were not binary compatible with each other (`.1` before the package rename, `.2` after it, `.3` missing `ReadyEffect` and with the config module removed, `.4` still carrying the transitional symbols deleted later, `.5` whose wrong `loaderVersion` got the whole jar rejected by FML), which is why consumers in the snapshot era had to pin the range lower bound to the exact number. Otherwise an older library jar was accepted by the loose range and produced `NoClassDefFoundError`, a compile-time `cannot find symbol`, or (`.5`) the loader rejecting the whole library jar. That historical wording and the measurement matrix are kept in §4.2 to interpret old pins; since the final `1.0.0` release, final `1.x` pins no longer need the exact snapshot number, while pre-release pins name the pre-release in the lower bound.
- **Load-gate fix record (2026-09-17)**: `1.0.0-SNAPSHOT.6` fixed the library being rejected by FML. The `loaderVersion` slot in `neoforge-1.21.1` (template `${loader_version_range}`) had been filled with the NeoForge version band `[21.1,21.2)`, while that slot compares against the javafml language-provider version (on NeoForge 21.1.235 it reports `4.0.42`). FML printed `needs language provider javafml:21.1 or above, and below 21.2 to load / We have found 4.0.42` and refused to load the library jar; the consumer `astral_dice` was then judged "`starengine` is not installed" (crash reports: `run/1.21.1/crash-reports/crash-2026-09-17_20.41.28-fml.txt` and `..._20.42.17-fml.txt`). It is now the language range `[1,)` (same value as the consumer's `neoforge-1.21.1/gradle.properties:23`); the NeoForge version band `[21.1,21.2)` is still carried by the `modId="neoforge"` dependency in the template. The same batch aligned the Forge side: `loaderVersion` narrowed from `[47,)` to `[47,48)`, and the `modId="forge"` dependency switched to the exact range derived from `forge_version` in build.gradle, `[47.4.10,48)`. Zero Java source changes; metadata and template comments only.
- **Resource-pack metadata fix record (2026-09-17, shipped with `.10`)**: added `pack.mcmeta` to the `forge-1.20.1` platform (`forge-1.20.1/src/main/resources/pack.mcmeta`, `pack_format` 15, same shape as the consumer's file). Root cause (read from the local Forge 1.20.1 sources): `net.minecraftforge.client.loading.ClientModLoader#clientPackFinder` (lines 154-165) calls `Pack.readMetaAndCreate(..., PackType.CLIENT_RESOURCES, ...)` for every mod file and, when it returns `null`, runs `ModLoader.addWarning(new ModLoadingWarning(mod, ModLoadingStage.ERROR, "fml.modloading.brokenresources", ...))`. That language key reads "File ... failed to load a valid ResourcePackInfo", and `Pack.readMetaAndCreate` returns null when the jar has no `pack.mcmeta` (or the metadata cannot be parsed for that PackType). The server side, `net.minecraftforge.server.ServerLifecycleHooks` (line 214), runs the same check for `SERVER_DATA`. Symptom: the consumer's `run/1.20.1` stopped on Forge's `Warning while loading mods` screen with `latest.log` showing `[net.minecraft.server.packs.repository.Pack/]: Missing metadata in pack mod:starengine`. Beyond the warning, that mod's resource pack is not registered at all. Only Forge 1.20.1 was changed: NeoForge 1.21.1 / 26.1.2 do not go through those two code paths, and `pack_format` differs per MC version, so the values for the other platforms are not guessed. Zero Java source changes. This fix was in flight as `1.0.0-SNAPSHOT.7` (published to mavenLocal only, never entered git) and was folded into `1.0.0-SNAPSHOT.10`.
- **Transitional-symbol cleanup record (end of merge)**: `1.0.0-SNAPSHOT.5` deleted `EventTargetCollector.collectTargets` / `collectTeamTargets` / `collectMaids` / `isMaidOwnedBy`, `GameplayConstants.EVENT_RANGE` / `EVENT_APPLY_MAID` / `KOMACHI_EXTRA_PLAYS_CAP`, `effect/ReadyEffect`, and the event framework trio `event/AstralEventType` / `EventContext` / `EventEffect` (also removing the `sourceSets.main.java.exclude` line kept for `AstralEventType` in `neoforge-26.1.2/build.gradle`). Before deleting, every symbol was grep-proven to have zero references across the consumer's lines at that time (the only `collectTargets` hit was the consumer's own same-named method in `RandomCardHandler`). Deleting the trio was conditional on cleaning up two leftover imports on the consumer side (consumer commit `7dc64cb`), so `.5`'s library bytes were republished once after that prerequisite: same version number, different bytes, hence the consumer had to re-resolve with `--refresh-dependencies`. Details in `temp/t5-cleanup-notes-20260917.md`.
- **Sinking record (Phase 1d)**: `1.0.0-SNAPSHOT.4` moved `common/effect/ReadyEffect` into this library. It was the only remaining self-contained unit among the classes that were byte-identical across the then-three lines (`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2`) and depended only on MC APIs and existing library classes. The exclusion reasons for the other 52 same-source candidates are recorded one by one in `temp/sink-manifest-20260917.md`. The same pass confirmed that the mainline's changes to copies of `GameplayConstants` / `EventTargetCollector` after deleting this batch of classes in dev-next were not lost, and that all differences in `BossEntityUtil` / `SignActiveTriggeredEvent` / `ModEffectRemoval` / `ActionBarManager` are shims and comments.
- **New platform record**: `1.0.0-SNAPSHOT.3` added the `neoforge-26.1.2` platform subproject (MC 26.1.2 / NeoForge 26.1.2.109 / Java 25 / ModDevGradle 2.0.147 / no Parchment), turning `common` from "shared by two versions" into "shared by three at the time". Two 26.1 platform differences were sunk into shims: the identifier class `ResourceLocation` -> `Identifier`, and the entity tag test `EntityType#is(TagKey)` -> `EntityType#builtInRegistryHolder().is(TagKey)` (the latter absorbed by `platform/LoaderTags#isBoss(Entity)`). The only file that could not be shared was `common/event/AstralEventType`; it was excluded from 26.1.2 and deleted later with its family.
- **Fabric platform record (2026-10-01)**: added the `fabric-1.20.1` platform subproject (Fabric Loader 0.19.5 / Fabric API 0.92.12+1.20.1 / Java 17 / Loom 1.14.10). It contributes 9 wiring classes plus the entry point, and `common` required zero changes. `common` is now compiled by four platform subprojects.
- **Change record**: `1.0.0-SNAPSHOT.4` only added a class (`effect/ReadyEffect`); it deleted no class and changed no signature. For a consumer compiled against `.4` it is a load-time required prerequisite: if the runtime still holds the `.3` library jar, it crashes at load with `NoClassDefFoundError: com/merlinkitsune/starenginelib/effect/ReadyEffect`, so the consumer had to raise the range lower bound to `[1.0.0-SNAPSHOT.4,2.0)`. The reverse (compiled against `.3`, running `.4`) stays compatible.
- **Breaking change record**: `1.0.0-SNAPSHOT.3` removed the Cloth Config prerequisite and the library's public config module entirely (`StarEngineCommonConfig` / `StarEngineConfigs` / `LegacyCommonTomlImporter` / `StarEngineConfigScreen`), narrowed `GameplayConfigValues` from 13 fields to 6, and changed `GameplayConstants.refresh()` into `applyConfig(GameplayConfigValues)`. The event framework trio and transitional symbols such as `KOMACHI_EXTRA_PLAYS_CAP` were kept temporarily because the not-yet-merged consumer mainline still referenced them. As of `1.0.0-SNAPSHOT.5` both the cap and the trio had been deleted on schedule. Consumers must return to their own TOML configuration (`ModConfigSpec` / `ForgeConfigSpec`) and push the value snapshot after the config loads (see §3). The `modId` (`starengine_lib` at the time) did not change, so modpack file names were unaffected.
- **Breaking change record**: `1.0.0-SNAPSHOT.2` changed the Java package from `com.merlinkitsune.starengine` to `com.merlinkitsune.starenginelib` (the Maven group followed). Every consumer import had to be rewritten, and the range lower bound had to be raised to `[1.0.0-SNAPSHOT.2,...)`. The `modId` (`starengine_lib` at the time) did not change, so `mods.toml` declarations and modpack file names were unaffected.
- **Rename record (2026-10-19, breaking)**: package and Maven group `com.merlinkitsune.starenginelib` -> `com.merlinkitsune.starengine`; modId `starengine_lib` -> `starengine`; artifact prefix `starengine_lib-` -> `starengine-`. Consumers update imports, `starengine_version` and JarJar coordinates. The current `1.1.0-pre.2` line uses the new names.
- The shared source's use of MC APIs is constrained at compile time on every platform, so breaking changes surface at compile time rather than at runtime.

---

## 7. License

**GPL-3.0-only** (version 3 only, not "any later version") for the source code - see [LICENSE](LICENSE), whose GPL body is followed by a short "Project licensing notes" block.

Every release from `1.0.0` through `1.0.12`, plus the already distributed `1.1.0-pre.1` / `1.1.0-pre.2` builds, remains under the **MIT License**; that grant is irrevocable, and the MIT text is the `LICENSE` file at the corresponding release tag. The working tree no longer ships a separate MIT file.
