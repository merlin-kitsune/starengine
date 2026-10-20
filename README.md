# StarEngine

**English** | [中文](README_ZH.md)

`starengine` — the shared engine library used by the **Astral Dice** mod family.

It was extracted from the 1.20.1 / 1.21.1 dual-version single repository of
[Astral Dice](https://github.com/), with the goal of giving the upcoming
**Astral Dice Extra** and other mods in the same family directly reusable shared implementations.

---

## 1. Core design constraints

Understanding these three is enough to understand every structural decision in this repository.

### 1.1 The library registers no registry entries

Items, effects, attachments, data components and capabilities **all stay in the consumer mod**.

That constraint is exactly where the value lies: introducing or upgrading the library
**never changes the ownership of any `ResourceLocation`**, so it can neither corrupt saves
(lost item / block state) nor break data packs (tag / recipe / loot-table references).

### 1.2 Across MC versions, share source — not artifacts

1.20.1 (SRG member names / Java 17), 1.21.1 (Mojmap / Java 21) and 26.1.2 (Mojmap / Java 25)
**cannot share one compiled artifact**. This project therefore has no single `common` Gradle
subproject; instead:

```
common/src/main/java        <- shared source directory (NOT a Gradle subproject)
neoforge-1.21.1/            <- platform subproject: Java 21 - Mojmap + Parchment - ModDevGradle 2.0.141
forge-1.20.1/               <- platform subproject: Java 17 - reobf(SRG) - ModDevGradle Legacy 2.0.144
neoforge-26.1.2/            <- platform subproject: Java 25 - Mojmap (no Parchment) - ModDevGradle 2.0.147
```

Every platform subproject adds `sourceSets.main.java.srcDir('../../common/src/main/java')`, so
**the same shared source is compiled three times**, producing one jar per platform.

### 1.3 Two hard constraints on the shared source

Sources under `common` must compile on all platforms at the same time, therefore:

| Constraint | Reason | Enforced by |
|---|---|---|
| No Java 21-only syntax (record patterns / switch patterns / `SequencedCollection`, …) | the same source has to be compiled by a Java 17 toolchain | a `forge-1.20.1` compile failure |
| Only APIs that exist in all three MC versions **with identical signatures** | the three API surfaces differ widely | a compile failure on any platform |

**Version divergences must stay inside the platform subprojects**, writing one copy of each
diverging class. Divergences implemented so far:

| Divergence | neoforge-1.21.1 | forge-1.20.1 | neoforge-26.1.2 |
|---|---|---|---|
| Client frame delta / render params | `DeltaTracker` + `GuiGraphics` | `float partialTick` | `DeltaTracker` + `GuiGraphicsExtractor` |
| Effect instance type | `Holder<MobEffect>` | `MobEffect` | `Holder<MobEffect>` |
| Custom data keys | `DataComponent` (vanilla component system) | `ItemDataKey` (Forge extension point) | `DataComponent` |
| Curios integration | uses the vanilla/NeoForge capability directly | `CuriosCompat` adapter (**compile-time only**, not a prerequisite) | uses the vanilla/NeoForge capability directly |
| Identifier class | `ResourceLocation` | `ResourceLocation` | `Identifier` (renamed in 26.1) |
| Entity tag test | `EntityType#is(TagKey)` | `EntityType#is(TagKey)` | `EntityType#builtInRegistryHolder().is(TagKey)` |

> The last two rows are absorbed by `platform/LoaderTags`'s `isBoss(Entity)`; the shared source is unaware of them.
> **Historical exception (eliminated in `1.0.0-SNAPSHOT.5`)**: `common/event/AstralEventType` used to be the only
> shared file that "could not be shared across all three lines" (from 26.1 the type is named `Identifier`, while that
> record's component type used the old name directly — Java has no type aliases), so it was excluded from the 26.1.2
> compile through `sourceSets.main.java.exclude`. That event framework trio
> (`AstralEventType` / `EventContext` / `EventEffect`) was deleted as a whole in `1.0.0-SNAPSHOT.5` at the end of the
> merge (after the merge the consumer had zero references on all three lines), and the `exclude` line in
> `neoforge-26.1.2/build.gradle` was removed in the same batch — **do not add it back**.

---

## 2. How loader differences are contained (shims)

Loader-specific namespaces are not allowed in the shared source; every difference is absorbed by a
shim under `platform/`:

| Shim | Purpose |
|---|---|
| `platform/LoaderEvent` | an abstract base class; each side does `extends net.neoforged.bus.api.Event` / `net.minecraftforge.eventbus.api.Event`. The shared `SignActiveTriggeredEvent` only refers to this class. |
| `platform/LoaderTags` | the `c:bosses` common tag. NeoForge takes it from `net.neoforged.neoforge.common.Tags`, Forge from `net.minecraftforge.common.Tags`. The shared `BossEntityUtil` only refers to this class. |

That way the shared code contains **no `net.neoforged.*` / `net.minecraftforge.*` literal** at all,
and adding a platform only requires one more shim.

---

## 3. Configuration: the config file stays in the consumer, the library only provides a value-snapshot seam

`ModConfigSpec` (NeoForge) and `ForgeConfigSpec` (Forge) are not interchangeable, and reading/writing
the config file plus the config GUI **can only be done by each platform itself**. Configuration therefore
does **not** enter the library: the config file (consumer-side `config/astral_dice-common.toml`), the
config screen and the config entry definitions all stay in the consumer mod, and the library keeps only
one platform-agnostic seam:

```
library (shared, common/)                              consumer (per platform)
GameplayConfigValues (plain value-snapshot record)     config/ModCommonConfig (ModConfigSpec / ForgeConfigSpec)
GameplayConstants.applyConfig(GameplayConfigValues)    -> registers the config during @Mod construction and pushes a fresh snapshot on load/reload
```

- The library **does not read the config file, hold a config schema or provide a config GUI**: it only declares
  "which gameplay values are configurable" (the record fields) and how to apply them (`applyConfig`).
- After registering its own config, the consumer packs the current values into `GameplayConfigValues`
  and pushes them to `GameplayConstants`; **no restart is needed after changing a config** — the
  `GameplayConstants` fields are non-final runtime reads, so one more push takes effect.
- Consequently `common` contains neither `net.neoforged.*` / `net.minecraftforge.*` literals nor any
  third-party config library dependency, and **all three platforms' only required prerequisite is the loader
  itself** — on the Forge side Curios is merely a **compile-time** dependency of `item/CuriosCompat`
  (`modCompileOnly`); it does not appear in `mods.toml`, so it is not a prerequisite.
- **The record fields are the contract**: the consumer constructs that record positionally, so
  adding/removing/reordering fields makes the consumer fail **at compile time** (which is precisely why a
  record was chosen over a loose set of getters). Adjusting configurable entries always requires updating both sides.

> The library once sank the config schema into `common` (Cloth Config AutoConfig, rewriting the config
> file to `config/astral_dice.json`). That approach has been withdrawn entirely: the Cloth Config
> prerequisite and all AutoConfig-related classes were removed, configuration went back to the consumer's own
> **TOML** (`ModConfigSpec` / `ForgeConfigSpec`), and the library kept only the seam described above.

---

## 4. Building and publishing

```bash
# Build all three platforms (jar only; no longer auto-pushed to the modpacks, see §4.3)
./gradlew build

# Build a single platform
./gradlew :neoforge-1.21.1:build
./gradlew :forge-1.20.1:build
./gradlew :neoforge-26.1.2:build

# Publish to the local Maven repository so the consumer's modImplementation can resolve it
./gradlew publishToMavenLocal
```

### 4.1 Publish coordinates

| Platform | Coordinate | Published artifact |
|---|---|---|
| NeoForge 1.21.1 | `com.merlinkitsune.starengine:starengine-neoforge-1.21.1:1.0.0` | `jar` (NeoForge compiles and runs on Mojmap, no remapping needed) |
| Forge 1.20.1 | `com.merlinkitsune.starengine:starengine-forge-1.20.1:1.0.0` | `reobfJar` (**production SRG jar**) |
| NeoForge 26.1.2 | `com.merlinkitsune.starengine:starengine-neoforge-26.1.2:1.0.0` | `jar` (same as 1.21.1: Mojmap, no remapping needed) |

> The three platforms share **the same version number**; on a version bump all three `gradle.properties` must change together.

**The Forge side must publish `reobfJar`, not `jar`**: the consumer's MDG LegacyForge remaps the production
SRG jar to dev Mojmap names during resolution; publishing an un-reobfuscated dev jar makes the production
environment throw `NoSuchFieldError` because the member names are Mojmap.

### 4.2 How the consumer integrates

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

This library is currently published to **mavenLocal** only (there is no remote Maven), so a consumer either
clones this repository locally and runs `./gradlew publishToMavenLocal` (for CI, see §4.4), or embeds this library
via **JarJar** in its own `build.gradle` — which is what `astral_dice` now does. Therefore a modpack should
**not** carry a standalone library jar any more (see §4.3).

The `mods.toml` on both sides must declare it as a required dependency:

```toml
[[dependencies.<mod_id>]]
    modId="starengine"
    type="required"        # 1.20.1 Forge uses mandatory=true
    versionRange="[1.0.0,2.0)"
    ordering="AFTER"
    side="BOTH"
```

> ⚠️ **From `1.0.0` on, the lower bound of the range is simply `[1.0.0,2.0)`** (consistent with the compatibility
> contract in §6).
> Note: in the snapshot era this had to read `[1.0.0-SNAPSHOT.<n>,2.0)` — under Maven's `ComparableVersion`
> semantics `1.0.0-SNAPSHOT.x < 1.0.0` (the pre-release qualifier sorts before the final version), so
> `[1.0.0,2.0)` used to contain **no** snapshot version at all, and the game refused to load with a
> "missing / unsatisfied required dependency" error. The snapshot series is terminated; that wording now only
> serves to interpret old pins.
>
> ⚠️ **(Snapshot-era wording, no longer applicable since `1.0.0`) the lower bound once had to be precise down to the snapshot
> number (e.g. `.10`)** rather than stopping at `[1.0.0-SNAPSHOT,2.0)`: a loose range also accepted the **pre-rename old
> library jar** — same `modId`, possibly even the same version number, but carrying the old package name
> `com.merlinkitsune.starengine`, which inevitably crashed with `NoClassDefFoundError` once loaded; tightening the range
> turned that mismatch into an explicit loader-level "missing required dependency" error.
>
> Measured (`maven-artifact` 3.8.5; both loaders go through `MavenVersionAdapter.createFromVersionSpec`):
>
> | Range | `.1` (pre-rename) | `.2` | `.3` | `.4` | `.5` | `.6` | `.7` | `.10` | `1.0.0` | `1.1.0` | `2.0.0` |
> |---|---|---|---|---|---|---|---|---|---|---|---|
> | `[1.0.0,2.0)` ← **used from 1.0.0 on** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ |
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
> (Note: the `.10` column verifies that numbers compare numerically rather than lexicographically; the `.7`
> column is kept because that number was only ever published to mavenLocal and never entered git — its in-flight
> fix was folded into `.10` — but the verdicts for that range are still a valid reference.)

> After modifying library code, the consumer's Gradle caches the mavenLocal resolution result.
> If the consumer does not pick up the new version, use `--refresh-dependencies` or bump `lib_version`.

### 4.3 Relationship with the modpacks (`pushToPack` removed on 2026-09-24)

This library **no longer** writes anything into any modpack `mods` folder. The former `pushToPack` task (hooked at the
end of `build`, whitelisted by `packPushBranches = ['main']`, forceable with `-PdeployToPack`) has been removed
entirely, along with the symbols that served it only: `packModsDir` / `packPushBranches` / `forcePackPush` /
`dotGitEntry` / `jarArchiveProvider`.

Reason: since 2026-09-24 the consumer `astral_dice` **embeds** this library via **JarJar**. FML's JarInJar selector
identifies same-origin artefacts by **modId** — if a mod with the same id is already present at the top level, the
nested copy is discarded (the selector only logs one WARN: `JarJarSelector … which was passed in as source`).
⇒ A standalone library jar left in a modpack therefore takes precedence; when it is older than the embedded copy the
symptom is a plain `NoSuchMethodError` / `NoClassDefFoundError` — and it is **hard to diagnose**, because it looks
exactly like "the library did not ship with the mod".

Correct practice: **keep only `astral_dice-*.jar` in the modpack `mods` folder**; the library rides inside it.

⚠️ **This repository publishes no jars at all** (since 2026-10-01). There is nothing to download here: the Releases page
carries **only the automatically generated source archives**, and CI actively deletes any jar asset it finds. If you
believe you need a standalone jar for A/B testing, build it yourself from source (`./gradlew build`, see §4).

### 4.4 CI / automatic releases (GitHub Actions)

⚠️ **This repository distributes no jars — source code only** (policy of 2026-10-01; rationale in §4.3).
CI still builds every platform as a compile check, but it uploads **no** build artifact and attaches
**no** jar to the Release; any jar asset found on a Release is deleted. (The reason is not stinginess: an
independently downloaded copy of the library silently shadows the copy the consumer mod embeds, and the
resulting failure is very hard to diagnose — see §4.3.)

The repository is hosted at <https://github.com/merlin-kitsune/starengine>, and the workflow
`.github/workflows/build.yml` follows the consumer Astral Dice release convention:

| Trigger | Behaviour |
|---|---|
| push / PR / manual | triple JDK toolchains (21 / 17 / 25) → `./gradlew build` — **compile check only: nothing is uploaded** (the jars stay on the runner) |
| push to `main` with a **final** version number | tag automatically (tag = the base version, e.g. `1.0.0`, no `v` prefix, no `+loader` suffix) |
| push a tag matching `/^[0-9]/` | create/update the GitHub Release — **assets: none** (source archives only); existing jar assets are deleted |

> The tag rule has one extra guard compared with the consumer: when the version contains `-` (i.e. a snapshot)
> it does **not** tag. Otherwise `1.0.0-SNAPSHOT.2` would be truncated by `%%-*` to `1.0.0` and an unfinalised
> snapshot would be mistagged as a release.

**The consumer's CI depends on this library**: Astral Dice's `build.yml` first checks this repository out and runs
`./gradlew publishToMavenLocal`, then builds itself — because this library only publishes to mavenLocal and cannot be
resolved directly on CI. Both version numbers must line up (consumer `starengine_version` ↔ library `lib_version`),
otherwise the consumer's CI fails at dependency resolution.
⚠️ That checkout is **pinned by commit SHA** (the consumer's `build.yml` `ref:` line) and must point at the commit whose
three `gradle.properties` version numbers equal the consumer's `starengine_version` — every library bump must update
that line in the consumer. While the library commit has not been pushed to the remote, the consumer's CI cannot check it
out (local builds are unaffected).

---

## 5. Repository contents

```
common/src/main/java/com/merlinkitsune/starengine/  # shared source (28 files)
├── client/       ClientDamageNumbers
├── component/    GameplayConfigValues (config value snapshot), GameplayConstants
├── effect/       18 MobEffect implementations (ReadyEffect was deleted on schedule in 1.0.0-SNAPSHOT.5)
├── event/        EventTargetCollector (only team collection left since 1.0.0-SNAPSHOT.5)
│                 AmethystDiceHandler / SignActiveTriggeredEvent
├── item/         BossEntityUtil
└── target/       TargetSelectionAction / TargetSelectionRegistry / TargetType

neoforge-1.21.1/src/main/java/.../starengine/       # platform-specific (5 files)
├── StarEngineLib          @Mod entry point
├── client/ActionBarManager        (DeltaTracker)
├── event/ModEffectRemoval         (Holder<MobEffect>)
└── platform/LoaderEvent, LoaderTags

forge-1.20.1/src/main/java/.../starengine/          # platform-specific (7 files)
├── StarEngineLib          @Mod entry point
├── client/ActionBarManager        (float partialTick)
├── component/ItemDataKey
├── event/ModEffectRemoval         (MobEffect)
├── item/CuriosCompat      (Curios is a compile-time dependency only; the library never calls it)
└── platform/LoaderEvent, LoaderTags

neoforge-26.1.2/src/main/java/.../starengine/       # platform-specific (5 files)
├── StarEngineLib          @Mod entry point
├── client/ActionBarManager        (DeltaTracker + GuiGraphicsExtractor)
├── event/ModEffectRemoval         (Holder<MobEffect>)
└── platform/LoaderEvent, LoaderTags (includes isBoss(Entity), absorbing the 26.1 tag API change)
```

**About `StarEngineLib`**: the entry point class exists for one reason only — making this library a loadable mod
(`@Mod`). It **registers nothing**. That is precisely the technical precondition for "changing no ResourceLocation ownership".

---

## 6. Versions and compatibility

- **Compatibility contract (in force since `1.0.0`, applies to all three platforms alike)**: the library version follows
  semver, and **within one major version (first digit unchanged; currently `1.x`) any breaking change is forbidden** —
  no public type, method, field or constant may be deleted or renamed, and its visibility, signature or existing
  semantics may not change; only **additions** (new types, new members, new optional entry points) and behaviour fixes
  that do not alter the contract are allowed. When a breaking change is genuinely required, the first digit **must** be
  incremented (`1.x` → `2.x`) and the consumer's range lower bound tightened in the same release — breaking changes are
  **not** allowed to hide in a minor or patch position.
  ⇒ The consumer's `mods.toml` declares `versionRange="[1.0.0,2.0)"`, the machine-readable expression of this contract:
  any `1.x` version can be swapped in place.
- **Current version = `1.0.0`**. It was the first final release, normalised from the last snapshot of the series,
  `1.0.0-SNAPSHOT.16` (**zero Java source changes in the library**; only the `-SNAPSHOT` qualifier was dropped from the
  version number). The consumer's three lines match with `starengine_version` and
  `starengine_version_range=[1.0.0,2.0)`. **There is no `1.0.1`** — see the next bullet.
- **Merged into `1.0.0` (2026-09-22): the Curios prerequisite is removed on the Forge side** (user decision,
  "make it consistent with the other two versions"). The change was briefly prepared as a separate `1.0.1` release and
  then folded back into `1.0.0` before it was ever pushed or published, so **`1.0.1` has no tag, no Release and no
  artefact**; the existing `1.0.0` tag keeps its name and its Release assets are refreshed by CI. The Forge `mods.toml`
  drops the `modId="curios"` dependency block, and that side's dependency becomes **`modCompileOnly`** —
  `item/CuriosCompat` needs Curios at **compile time only**, and the library itself never calls it (it is a shim for
  consumers). ⇒ **None of the three platform jars declares Curios as a prerequisite any more** (the two NeoForge lines
  never did); **a consumer that calls that shim declares Curios itself** (`astral_dice`'s 1.20.1 side already declares
  `mandatory=true [5,6)`). This is a **pure relaxation** — no public type, method, field, visibility, signature or
  semantics is removed or changed — so it is legal within `1.x`. The only cost: if a consumer forgets to declare Curios
  yet calls the shim, the failure degrades from a clear loader-level "missing required dependency" error to a runtime
  `NoClassDefFoundError`.
- **Merged into `1.0.0` (2026-09-23): the missing platform implementation on the `neoforge-26.1.2` line** — this
  too was briefly prepared as a separate `1.0.1` release and folded back into `1.0.0` before it was ever pushed.
  The first stable release shipped the wallet ledger's platform storage only on `forge-1.20.1`
  (`ForgeEconomyStorage`) and `neoforge-1.21.1` (`NeoForgeEconomyStorage`), so on 26.1.2
  `StarEngineEconomy.isAvailable()` stayed `false` (no `/starcoin`, picked-up coins not absorbed, balance bar
  always 0). ⇒ As above, **`1.0.1` has no tag, no Release and no artefact**; the `1.0.0` tag keeps its name
  and its Release assets are refreshed by this push.

- **The snapshot series (`1.0.0-SNAPSHOT.*`) is terminated and not covered by the contract above** — snapshots were not
  binary compatible with each other (`.1` before the rename, `.2` with a deleted config class, `.3` missing
  `ReadyEffect`, `.4` still carrying deleted transitional symbols, `.5` whose **wrong `loaderVersion` got the whole
  jar rejected by FML**), which is why consumers in the snapshot era had to pin the range lower bound **to the exact
  number**; otherwise an older library jar was accepted by the loose range and produced `NoClassDefFoundError`, a
  compile-time `cannot find symbol`, or (`.5`) the loader rejecting the whole library jar. That historical wording and
  the measurement matrix are kept in §4.2 purely to interpret old pins; **since `1.0.0` the exact number is no longer
  needed**.
- **Load-gate fix record (2026-09-17)**: `1.0.0-SNAPSHOT.6` fixed "the library itself being rejected by FML" — the
  `loaderVersion` slot in `neoforge-1.21.1` (template `${loader_version_range}`) had been filled with the NeoForge
  version band `[21.1,21.2)`, while that slot compares against the **javafml language-provider version** (on NeoForge
  21.1.235 that reports `4.0.42`), so FML printed `needs language provider javafml:21.1 or above, and below 21.2 to
  load / We have found 4.0.42` and refused to load the library jar; the consumer `astral_dice` was then judged
  "`starengine` is not installed" (crash reports: `run/1.21.1/crash-reports/crash-2026-09-17_20.41.28-fml.txt` and
  `…_20.42.17-fml.txt`). It is now the language range `[1,)` (same value as the consumer's
  `neoforge-1.21.1/gradle.properties:23`); the NeoForge version band `[21.1,21.2)` is still carried by the
  `modId="neoforge"` dependency in the template (range unchanged). The same batch aligned the already documented Forge
  side: `loaderVersion` narrowed from `[47,)` to `[47,48)` (the language major range), and the `modId="forge"`
  dependency switched to the exact range derived from `forge_version` in build.gradle, `[47.4.10,48)`. Zero Java source
  changes in the library — metadata and template comments only.
- **Resource-pack metadata fix record (2026-09-17, shipped with `.10`)**: added **`pack.mcmeta`** to the
  `forge-1.20.1` platform (`forge-1.20.1/src/main/resources/pack.mcmeta`, `pack_format` 15, same shape as the
  consumer's file of that name). Root cause (read from the local Forge 1.20.1 sources):
  `net.minecraftforge.client.loading.ClientModLoader#clientPackFinder` (lines 154-165) calls
  `Pack.readMetaAndCreate(…, PackType.CLIENT_RESOURCES, …)` for **every** mod file and, when it returns `null`, runs
  `ModLoader.addWarning(new ModLoadingWarning(mod, ModLoadingStage.ERROR, "fml.modloading.brokenresources", …))` —
  the wording of that language key is "File … failed to load a valid ResourcePackInfo", and
  `Pack.readMetaAndCreate` returns null when the jar has **no `pack.mcmeta`** (or the metadata cannot be parsed for
  that PackType); the server side, `net.minecraftforge.server.ServerLifecycleHooks` (line 214), runs the same check for
  `SERVER_DATA`. Symptom: the consumer's `run/1.20.1` stopped on Forge's `Warning while loading mods` screen (one
  warning pointing at the library jar), with `latest.log` showing
  `[net.minecraft.server.packs.repository.Pack/]: Missing metadata in pack mod:starengine`. Beyond the warning, that
  mod's resource pack is **not registered** at all (the library currently has no resources, so nothing is lost
  functionally, but it is a silent-failure surface). **Only Forge 1.20.1 was changed**: NeoForge 1.21.1 / 26.1.2 do not
  go through those two code paths (their run logs show no such warning), and `pack_format` differs per MC version, so
  the values for the other platforms are **not guessed** (add them by reading that version's client sources when
  needed). Zero Java source changes in the library. This fix was originally in flight as
  `1.0.0-SNAPSHOT.7` (published to mavenLocal only, never entered git) and, per the repository convention that "later
  changes to an unreleased version are merged into the original entry", was folded into **`1.0.0-SNAPSHOT.10`** (same
  number as the consumer's `2.0.0-SNAPSHOT.10` patch section).
- **Transitional-symbol cleanup record (end of merge)**: after the mainline merge completed, `1.0.0-SNAPSHOT.5` deleted,
  as planned, `EventTargetCollector.collectTargets` / `collectTeamTargets` / `collectMaids` / `isMaidOwnedBy`,
  `GameplayConstants.EVENT_RANGE` / `EVENT_APPLY_MAID` / `KOMACHI_EXTRA_PLAYS_CAP`, `effect/ReadyEffect`, and the event
  framework trio `event/AstralEventType` / `EventContext` / `EventEffect` (also removing the
  `sourceSets.main.java.exclude` line kept for `AstralEventType` in `neoforge-26.1.2/build.gradle`).
  **Before deleting, every symbol was grep-proven** to have zero references across the consumer's three lines after the
  merge (the only `collectTargets` hit was the consumer's own same-named method in `RandomCardHandler`); deleting the
  trio was conditional on cleaning up the two leftover imports on the consumer side (consumer commit `7dc64cb`), so
  `.5`'s library bytes were **republished** once after that prerequisite — same version number, different bytes, hence
  the consumer had to re-resolve with `--refresh-dependencies`. Details in `temp/t5-cleanup-notes-20260917.md`.
- **Sinking record (Phase 1d)**: `1.0.0-SNAPSHOT.4` moved `common/effect/ReadyEffect` into this library — it was the
  **only remaining** self-contained unit among the classes that were byte-identical across all three lines
  (`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2`) and depended only on MC APIs and existing library classes;
  the exclusion reasons for the other 52 same-source candidates (not identical across three lines / depends on
  consumer-specific classes / mixin) are recorded one by one in `temp/sink-manifest-20260917.md`. The same pass
  confirmed that the mainline's changes to copies of `GameplayConstants` / `EventTargetCollector` etc. after deleting
  this batch of classes in dev-next were **not lost** (the library copies already carry the mainline constant values,
  and the transitional symbols were kept for now per the existing convention), and that all differences in
  `BossEntityUtil` / `SignActiveTriggeredEvent` / `ModEffectRemoval` / `ActionBarManager` are shims and comments — a
  deliberate design.
- **New platform record**: `1.0.0-SNAPSHOT.3` added the **`neoforge-26.1.2`** platform subproject (MC 26.1.2 / NeoForge
  26.1.2.109 / Java 25 / ModDevGradle 2.0.147 / no Parchment), turning `common` from "shared by two versions" into
  "**shared by three**". To that end two 26.1 platform differences were sunk into shims: the identifier class
  `ResourceLocation` → `Identifier`, and the entity tag test `EntityType#is(TagKey)` →
  `EntityType#builtInRegistryHolder().is(TagKey)` (the latter absorbed by `platform/LoaderTags#isBoss(Entity)`; the
  shared source does not know about it). The only file that could not be shared was `common/event/AstralEventType`
  (its record component type used the old name directly, and Java has no type aliases), so that file was excluded from
  26.1.2 via `sourceSets.main.java.exclude` — it and its family were already deleted on the consumer mainline and were
  deleted in this library right after the merge.
- **Change record**: `1.0.0-SNAPSHOT.4` only **added** a class (`effect/ReadyEffect`); it deleted no class and changed no
  signature — but for "a consumer compiled against `.4`" it is a **load-time required prerequisite**: if the runtime
  still holds the `.3` library jar, it crashes at load with
  `NoClassDefFoundError: com/merlinkitsune/starengine/effect/ReadyEffect`, so the consumer had to raise the range
  lower bound to `[1.0.0-SNAPSHOT.4,2.0)`; the reverse (compiled against `.3`, running `.4`) stays compatible.
- **Breaking change record**: `1.0.0-SNAPSHOT.3` removed the Cloth Config prerequisite and the library's public config
  module entirely (`StarEngineCommonConfig` / `StarEngineConfigs` / `LegacyCommonTomlImporter` /
  `StarEngineConfigScreen`), narrowed `GameplayConfigValues` from 13 fields to 6, and changed
  `GameplayConstants.refresh()` into `applyConfig(GameplayConfigValues)`; the event framework trio (`AstralEventType` /
  `EventContext` / `EventEffect`) and "delete-after-merge" transitional symbols such as `KOMACHI_EXTRA_PLAYS_CAP` were
  **kept temporarily** (the not-yet-merged consumer mainline still referenced them, and deleting them would have made
  that round of commits unbuildable). As of `1.0.0-SNAPSHOT.5` both `KOMACHI_EXTRA_PLAYS_CAP` and the trio had been
  deleted on schedule (the trio conditional on the consumer's leftover imports being cleaned up, see §1.3). Consumers
  must return to their own TOML configuration (`ModConfigSpec` / `ForgeConfigSpec`) and push the value snapshot after
  the config loads (see §3). Note that the `modId` (`starengine`) did **not** change, so modpack file names are
  unaffected.
- **Breaking change record**: `1.0.0-SNAPSHOT.2` changed the Java package name from `com.merlinkitsune.starengine` to
  `com.merlinkitsune.starengine` (the Maven group likewise became `com.merlinkitsune.starengine`). Every consumer
  `import` had to be rewritten, and the range lower bound had to be raised to `[1.0.0-SNAPSHOT.2,...)`. Note that the
  `modId` (`starengine`) did **not** change, so the `mods.toml` dependency declaration and modpack file names are
  unaffected.
- The shared source's use of MC APIs is constrained at compile time on both sides, so breaking changes surface **at
  compile time** rather than at runtime.

---

## 7. License

MIT License, see [LICENSE](LICENSE).
