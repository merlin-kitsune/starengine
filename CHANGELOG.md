# Changelog (English)

> This file contains the English changelog only. Chinese version: [`CHANGELOG_ZH.md`](CHANGELOG_ZH.md).
> The two files correspond one-to-one by version number: each version appears once in both files, and every change must update both together — never only one side.

## Unreleased (2.0.0-SNAPSHOT.15)

### Changed

- **Atomic rename `StarEngine Lib` → `StarEngine` (2026-10-19, breaking)**: modId `starengine_lib` → `starengine`,
  group/package root `com.merlinkitsune.starenginelib` → `com.merlinkitsune.starengine`, artifactId prefix
  `starengine_lib-` → `starengine-`; the version stays `2.0.0-SNAPSHOT.15`. The wallet NBT root key is now `starengine`,
  with an on-read migration from the legacy `starengine_lib` key logging
  `AP_WALLET_NBT_MIGRATED:key=starengine_lib->starengine` (removed in 2.0.1); the Fabric attachment id follows the same rule.
  Consumers must update imports, the `starengine_lib_version` pin and JarJar coordinates.

### New

- **New `event/EffectTimerGuard` plus the timer-table seam `event/TimerSource` (sunk from the `astral_dice`
  consumer)**: the public API and semantics are unchanged verbatim (`INFINITE_THRESHOLD`, the nested record
  `TimerEntry` + `CODEC`, `isForcedRemoval` / `apply` / `record` / `tick` / `forget` / `clear`); the 20 tps
  enforcement, the "reapply with 1 tick when an expired instance lingers", the 20-tick clamp tolerance and the
  max-end-tick merge are kept as-is. The timer table no longer reads the consumer's AttachmentType directly: it is
  a static, injectable `TimerSource` (`getTimers` / `setTimers` / `now`) with the new `setTimerSource(TimerSource)`;
  without injection a degenerate implementation keeps the guard inert but never throws. The `MobEffect` /
  `Holder<MobEffect>` / `Identifier` divergences are absorbed by one `event/EffectTimerAccess` per platform
  (shared source only deals in registered names). Consumers must call `EffectTimerGuard.setTimerSource(...)`
  during init and delete their own platform copies (pin follows this version).

- **New `target/HoldToSelect` and `target/SelfTargetable` (two pure extension interfaces, sunk from the `astral_dice`
  consumer)**: `HoldToSelect#stillHeld(Player)` and `SelfTargetable#allowSelf()` (default `false`) keep their
  signatures verbatim; package is `com.merlinkitsune.starengine.target` and neither file references the consumer.
  `TargetSelectionAction` is untouched (no new method) and `TargetSelectionManager` stays in the consumer (it has
  consumer-only dependencies). `SelectorTargets#matches(type, selector, target, allowSelf)` already consumed this flag
  and is unchanged. Consumers must switch to the library interfaces and delete their own copies (pin follows this version).

- **New `combat/EliteTargets` (single entry point for elite/boss checks) and the `thirdparty/StarengineThirdParty`
  facade** (2026-10-19, sunk from the `astral_dice` consumer): `EliteTargets#isEliteOrBoss` keeps its signature and
  criteria verbatim (health > 40 or armor > 20; `c:bosses` / boss bar; third-party markers). The Apotheosis check that
  used to live in the consumer (the persisted NBT keys `apoth.boss` / `apoth.miniboss`) is now a **built-in probe
  registered by default** in the library and read via reflection, so all four platforms compile (Fabric 1.20.1 has no
  `Entity#getPersistentData()`, where the probe always returns `false`); consumers call only the library's semantic
  method and no longer touch those keys. Consumers must switch to the library `EliteTargets` and delete their own
  per-platform copies (pin follows this version).

- **New Fabric 1.20.1 platform subproject** (2026-10-01): the library now ships for **four platforms** —
  `neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / **`fabric-1.20.1`**
  (artifactId `starengine-fabric-1.20.1`), all sharing **one version number**. The Fabric platform
  contributes 9 platform wiring classes (wallet attachment persistence / rarity extension / Trinkets adapter /
  event base classes / tags, etc.) with **zero changes** to the shared `common` sources.
  The consumer `astral_dice`'s `fabric-1.20.1` line now depends on this library.

- **New effect-card targeting surface `combat/CreatureTargets` + `TargetType.CREATURE` / `CREATURE_OR_RIVAL`**
  (2026-10-03, ported from the `main` line 1.0.9): hostile union neutral (pets excluded) union untamed
  tamable mobs, **excluding players**; `SelectorTargets` gains the matching routing. Consumers use it to
  narrow the target surface of effect cards such as the Misfortune Card and the Living Page.
- **New `TargetType.NON_HOSTILE` + `HostileTargets#isHostileMob(Entity)`** (2026-10-03, ported from the
  `main` line 1.0.10 and 1.0.11): for healing / utility effect cards, defined as
  `!HostileTargets.isHostileMob(target)` - i.e. **non-hostile mobs** (vanilla `Enemy` flag only). The
  `HostileTargets` class javadoc now states that the **two parallel criteria must not substitute for each
  other**: combat criterion `isHostile` (includes neutral mobs) vs mob-category criterion `isHostileMob`,
  the latter being the sole entry point for the vanilla hostile flag. ⚠️ Opposite in direction to the
  damage-card `CREATURE` family - do not mix them up.

### Changes

- Four-platform `lib_version` / `mod_version`: `2.0.0-SNAPSHOT.10` -> **`2.0.0-SNAPSHOT.11`** (same number on
  all four); the consumer `astral_dice` four lines follow with `starengine_version = 2.0.0-SNAPSHOT.11`.

- Four-platform `lib_version` / `mod_version`: `2.0.0-SNAPSHOT.11` -> **`2.0.0-SNAPSHOT.12`** (same number on all four);
  the consumer `astral_dice` four lines follow with `starengine_version = 2.0.0-SNAPSHOT.12` (the fabric line's
  version-range lower bound moves with it).

- **`component/GameplayConstants#CURSED_SWORD_BONUS_MAX`: `16` -> `32`** (user ruling, 2026-10-02).
  The Cursed Sword chip's stacking attack-bonus cap **within a single Dice Blessing cycle** is **doubled**.
  A pure value change - no public type / method / field signature, visibility or semantic shape is altered,
  so consumers need **no** code change (both `CursedSwordChipItem#onKill` and the tooltip renderer read the
  field directly). This entry **absorbs** the only substantive change of the former standalone
  `fabric-1.20.1` branch that had not been merged into `next` (that branch was deleted on 2026-10-02; its
  old line numbers `1.0.7` / `1.0.6-alpha.2` are retired with it).
- **All four platforms' `lib_version` / `mod_version`: `2.0.0-SNAPSHOT.8` -> `2.0.0-SNAPSHOT.9`** (one number for all four).
- **The consumer `astral_dice`'s four lines** move their `starengine_version` to `2.0.0-SNAPSHOT.9` in the same batch.

- **`component/GameplayConstants#ACTIONBAR_FADE_TICKS`: `20` -> `10`** (2026-10-03, synced from `main`).
  The actionbar fade is now 0.5 s; together with `ACTIONBAR_DURATION_TICKS` (60 ticks) it has left the
  config file (consumers now inject constants directly), so the two actionbar values are no longer
  player-configurable.
- All four platforms' `lib_version` / `mod_version`: `2.0.0-SNAPSHOT.9` -> **`2.0.0-SNAPSHOT.10`**.

### Fixes

- **Dynamic `yShift` in `client/ActionBarManager` (the two NeoForge lines)** (ported from the `main` line
  `1.0.12`, 2026-10-05/06): `neoforge-1.21.1` / `neoforge-26.1.2` now **replicate** the vanilla
  `Gui#renderOverlayMessage` shift (`yShift = max(Gui.leftHeight, Gui.rightHeight) + (68 - 59)`,
  `y = guiHeight - max(yShift, 68)`; both fields are patched `public` by NeoForge). Fixes "actionbar
  overlapping the item-name tooltip once absorption hearts / armour / mount health push the bars
  higher" (the old code hard-coded `68`, which only covered `max(lh, rh) <= 59`).
  ⚠️ **`forge-1.20.1` / `fabric-1.20.1` keep the fixed `guiHeight - 68`**: vanilla 1.20.1 has no such
  mechanism, so following it would misplace the text ⇒ **a registered platform difference**, noted
  in-place in both platforms.

- **Aligned `client/ActionBarManager` to the vanilla actionbar position on all four platforms**
  (ported from the `main` line `1.0.8`, 2026-10-03): the render `y` moved from `guiHeight()-58` to
  **`guiHeight()-68`**. Vanilla pins the hotbar item-name tooltip at `guiHeight-max(yShift,59)`, so the old
  `58` sat **1 px away on the same row and the two overlapped** (user-reported "ActionBar text sometimes
  overlaps the item name"). The previous sync (`1c0d128`) only carried the `ACTIONBAR_FADE_TICKS` **value**
  and **missed this position fix**; this release completes it and also corrects the class javadoc from
  "default 5 s, driven by config" to "default 3 s / 60 ticks" (`ACTIONBAR_DURATION_TICKS` is 60).
  The four platforms' `ActionBarManager` is now **byte-for-byte identical** to the `main` line.

- **`event/EventTargetCollector`: both the FTB Teams and the OPAC reflection targets did not exist at all**
  (ported to this snapshot line from the `main` line's `1.0.6` on 2026-10-01): the two backends were
  **permanently disabled and completely silent** - the outermost `catch (Exception ignored)` swallowed every
  `NoSuchMethodException` / `ClassNotFoundException`. Consequence: players running FTB Teams or OPAC were
  reported as "in no team at all", fell through to the "no team => everyone is friendly" fallback, and
  **party detection failed across the board** (consumers saw "same-team players can still hurt each other /
  the railgun still hits allies / friendly-only effects spread to the whole server"). Corrected one by one
  against the real upstream artifacts:
  - FTB's four manager accessors live on the **nested interface** `FTBTeamsAPI$API`, not on the outer class -
    and `Class#getMethod` **does not** cross into nested interfaces;
  - the real server-side signature is `TeamManager#getTeamForPlayerID(UUID)`; the previously probed
    `getTeamForPlayer(UUID)` / `getTeamForPlayer(Player)` **do not exist**;
  - the client-side entry point is `ClientTeamManager#getKnownPlayer(UUID)` + `KnownClientPlayer#teamId()`
    (a record accessor, **no get prefix**) + `getTeamByID(UUID)`;
  - OPAC's real package path is **`xaero.pac.*`** (the previously probed `dev.darkhax.opac.*` **does not exist
    at all**): entry chain = `OpenPACServerAPI.get(MinecraftServer)` -> `getPartyManager()` ->
    `IPartyManagerAPI#getPartyByMember(UUID)`; members come from `IServerPartyAPI#getOnlineMemberStream()`
    (falling back to `getMemberInfoStream()` + `IPartyMemberAPI#getUUID()`);
  - `hasAnyTeam`'s FTB criterion changed from "a Team object is present" to `isPartyTeam() || isServerTeam()`
    - FTB gives every player a personal team, so the old criterion was always true and blocked the
    "no team => no restriction" fallback;
  - resolution failures are no longer silent: a new `describeBackends()` prints an `AP_LIB_PARTY:` machine line
    (debug when simply not installed, warn when installed but with mismatched signatures);
  - client path / member accessors / existence checks are resolved **optionally**: a missing one only lowers
    precision; the client branch explicitly returns `null` so that one client-side call cannot disable the
    **server-side** decision as well.

- **Version**: the change lands in the `common` sources shared by all three platforms, so all three artifacts
  move together: `2.0.0-SNAPSHOT.6` -> **`2.0.0-SNAPSHOT.7`**. The public API only grows
  (`collectTeamPlayers` / `hasAnyTeam` keep their signatures and semantics; only `describeBackends()` and
  `BACKEND_REPORT_PREFIX` are new).

### Combat

- **"Angerable tamable animals" now count as hostile targets** (2026-09-27 user ruling; ported to this snapshot
  line from the `main` line's `1.0.5`): the criterion in `combat/HostileTargets` is extended from
  "hostile mobs + neutral mobs (pets excluded)" to "... + angerable tamable animals (pets excluded)".
  - New private predicate `isAngerableTamedMount`: a `Mob` that is **tamable**
    (`getOwner() == null` and not a tamed `TamableAnimal`) and **angerable**
    (`Mob#getTarget() != null`, or `LivingEntity#getLastHurtByMob() != null`) now counts as a hostile target.
  - Covers vanilla **llamas / trader llamas** -- they retaliate when hit and are tamable, yet they do not
    implement `NeutralMob`, so they were covered neither by the neutral branch nor by `Enemy`
    => they used to be unselectable in the target selector.
  - Note: the predicate deliberately avoids class/package names: 26.1.2 renamed `animal/horse/**`
    to `animal/equine/**`, so hard-coded class paths would break the shared `common` sources.
  - Note: it also avoids `Mob#isAggressive()`: its meaning is mob-specific -- `Panda` overrides it to mean
    the "aggressive gene" (a permanent personality, not current anger), while `Llama` does not override it
    (always `false`).

### Engineering

- **This repository no longer distributes any jar - source code only** (user ruling 2026-10-01). The CI step
  `Upload StarEngine JARs` is gone entirely: the build still runs (jars are produced, but they stay on the
  runner), the Release step now attaches **no assets at all** and keeps only GitHub's automatically generated
  source archives, and it gained a **self-healing cleanup** - any `*.jar` asset found on that Release is removed
  via `gh release delete-asset` (idempotent when re-run). The existing stock was purged too: all **12** remote
  `starengine-jars` artifacts were deleted (the Release side was cross-checked tag by tag and held **0**).
  The reason is the same as the `pushToPack` item above: an independently downloaded copy of the library silently
  shadows the copy the consumer embeds, and the resulting failure is very hard to diagnose.
  ⚠️ **Nothing changes for the consumer** - it still pins `starengine_version` and embeds the library via JarJar.
- Updated `README_ZH` / `README` accordingly (§4.3 no longer suggests fetching a jar from this repository's
  Releases; the §4.4 table now reads "nothing is uploaded / Release assets: none", plus a policy note at the top of
  that section; the CI JDK count was corrected from "dual (21 + 17)" to the three toolchains 21 / 17 / 25).
  Historical "Release asset" mentions in older CHANGELOG sections are **kept** as a record of the facts then.
- The three platforms' `build.gradle` "build artifact deployment" comment no longer points at "fetch a jar
  from this repository's Releases" (it now says: no jar downloads here, build from source instead) - the
  previous version only covered the `main` / `fabric-1.20.1` lines and missed this one. Per this line's
  AGENTS §1 ("one bump per commit", no exception) a comment-only commit still bumps: `2.0.0-SNAPSHOT.5`
  -> **`2.0.0-SNAPSHOT.6`**.

## Unreleased (2.0.0-SNAPSHOT.2)

### Combat

- **The `combat` package (shared dice-combat formulas).** `2.0.0-SNAPSHOT.1` pushed the dice-combat formulas down from
  the consumer into this library so that all three consumer lines share one implementation: `TargetCategory` (four-way
  mob classification), `TargetBattleStats` (initial attack / defense table), `CombatFormula` (pure conversion
  functions) and `DiceBattleResolver` (opposed rolls and damage synthesis). `2.0.0-SNAPSHOT.2` adds the rest of the
  batch and re-calibrates the numbers:
  - **New `VanillaMitigation`** - pure arithmetic for the two vanilla **magic-reduction** channels (the **Resistance**
    effect and the **Protection** enchantment), to be multiplied onto the dice-combat result **after** resolution.
    Both formulas are pure multiplication, so they compose order-independently. The class deliberately keeps the
    platform differences out (how protection points are obtained, and the effect constant name, stay on the consumer
    side) and stays stateless: it registers nothing and depends on no consumer.
  - **Re-calibrated defense curve**: player base defense `2 -> 4`, player armor coefficient `0.5 -> 0.30`, player
    toughness coefficient `1.4 -> 0.85`; mob armor coefficient `0.5 -> 0.40`, mob toughness coefficient
    `1.125 -> 1.0`. `TargetBattleStats`: hostile base defense `2 -> 0`, hostile base attack `5 -> 4`, neutral base
    attack `4 -> 3`. The intent is to raise the base and flatten the slope, so early-game difficulty and late-game
    output stop being extreme.
  - **Damage floor moved into the library**: `DiceBattleResolver.resolve(attack, defense, relativeFloorRatio)` plus
    `RELATIVE_FLOOR_RATIO = 0.15F`. The floor is now `max(MIN_DAMAGE, attack power x relativeFloorRatio)` instead of a
    flat 1 point. The two-argument `resolve` keeps working and delegates with the default ratio.
  - **Breaking change inside the snapshot line**: the public constant **`CombatFormula.ARMOR_DIVISOR` was removed** -
    the single shared `armor / 2` divisor is replaced by per-side coefficients (`PLAYER_ARMOR_COEF` /
    `MOB_ARMOR_COEF`).

## Unreleased

> The entries below are **build-script / documentation** changes with no effect on jar contents, so
> **no version bump and no re-release**; a version number will be assigned together with the next release
> that actually changes an artefact.

### Engineering

- **Removed the `pushToPack` task (automatic deployment into the modpacks).** The whole "build artifact
  deployment" section was deleted from all three `build.gradle` files, together with the symbols that served it
  only: `packModsDir` / `packPushBranches` / `forcePackPush` / `dotGitEntry` / `jarArchiveProvider`;
  `build` no longer reads or writes any modpack folder (the former `finalizedBy tasks.named('pushToPack')`
  is gone as well).
  Reason: the consumer `astral_dice` now **embeds** this library via **JarJar** ⇒ a standalone library jar left in
  a modpack is picked up first by FML's JarInJar selector and shadows the embedded copy (when the standalone one is
  older this shows up as `NoSuchMethodError` / `NoClassDefFoundError`).
- Updated `README_ZH` / `README` accordingly (§4.3 rewritten as "Relationship with the modpacks", the build-command
  comment, and the consumer notes) plus the build-step comment in `.github/workflows/build.yml`. Historical
  `pushToPack` mentions in older CHANGELOG sections (e.g. `1.0.0`) are **kept** as a record of the period when that
  mechanism existed.

## 1.0.4

> Purely additive (no breaking change), so it ships in the patch slot; the consumer's
> `starengine_version_range` lower bound moves to `[1.0.4,2.0)`.

### New features

- **Rarity tier API**: new `item/Rarity` (the single source of truth for tiers / constant names / serialized names /
  **colour codes**) plus a per-platform `item/AstralRarities` that extends the five tiers onto the vanilla
  `net.minecraft.world.item.Rarity` and hands the values back at runtime.
  - ⚠️ **Hard format rule for the serialized name**: the *name parameter* handed to the vanilla enum constant must be
    `<modId>:<name>` (**colon separated**), i.e. `astral_dice:rare` / `astral_dice:epic` / `astral_dice:legendary` /
    `astral_dice:pinnacle` / `astral_dice:bizarre` (NeoForge's `RuntimeEnumExtender#validateNameParameter` enforces it; otherwise `Rarity`'s class
    initialiser throws and the game will not start). This is a **different rule** from the `name` field in
    `enumextensions.json` (the **field name** injected into the enum, which must start with the lowercased modId, e.g.
    `ASTRAL_DICE_RARE`) - do not conflate them.
  - Five tiers and colours: `RARE` = **the vanilla RARE colour** (aqua `0x55FFFF`, i.e. `ChatFormatting.AQUA`),
    `EPIC` = **the vanilla EPIC colour** (light purple `0xFF55FF`, i.e. `ChatFormatting.LIGHT_PURPLE`),
    `LEGENDARY` = gold `0xFFC24B`, `PINNACLE` = bright red `0xFF4D4D`, and
    **`BIZARRE` = rainbow (flowing)**: ⚠️ this tier has **no single colour**; `rgb()` is only its base colour
    (mint `0x6BFFA8`, used for the item-name line and anywhere that cannot be coloured per frame). The moving
    wheel comes from the new API - `isRainbow()`, `rainbowBorderStart(millis)` / `rainbowBorderEnd(millis)`
    (frame start/end colours as ARGB, one third of the wheel apart), `rainbowHue(millis)` for the phase and
    `hsvToRgb(...)` for your own maths, with `RAINBOW_CYCLE_MILLIS = 3000` plus the pair
    `rainbowSaturation()` / `rainbowBrightness()` so consumer-side rendering stays in sync with the library;
  - **Tooltip frame policy (revised 2026-09-25, user ruling)**: the earlier "text and border strictly share
    one colour" rule is **withdrawn** - the vanilla look wins for RARE / EPIC (the consumer leaves the frame
    alone, vanilla's purple gradient stays, only the name line is tinted). LEGENDARY / PINNACLE stay custom:
    `Rarity#frameColor(long)` = `0xFF000000 | rgb()` painted as a solid frame. BIZARRE cannot go through the
    two-colour platform hook at all (the vanilla frame is a vertical gradient; a clockwise rainbow must be
    drawn per pixel by the consumer - see the consumer's `TooltipBorderMixin`). Paired with the reverse lookup
    `AstralRarities#tierOf(vanilla Rarity)`,
    which returns `null` for tiers that are not ours (the four vanilla ones) so consumers never have to copy
    the tier mapping outside the library.
  - **Colour authority** = `Rarity#apply(Style)` / `Rarity#styleModifier()`: the colour is handed to the vanilla enum
    constant **at extension time** as a style modifier, after which vanilla's own tooltip path
    (`ItemStack#getTooltipLines` -> `Rarity#getStyleModifier()`) applies it - so changing a colour is a one-line edit
    in `Rarity` with no tooltip code on the consumer side;
  - **Platform split**: the NeoForge lines (1.21.1 / 26.1.2) use `EnumProxy` fields referenced by name from the
    consumer's `META-INF/enumextensions.json`; Forge 1.20.1 uses `IExtensibleEnum` (the library registers via
    `Rarity.create(name, styleModifier)` in its static initialiser);
  - Consumers must fetch values through `AstralRarities.{rare,epic,legendary,pinnacle,bizarre}()` (**lazy**);
    there is deliberately no `Rarity.valueOf(...)` path (Forge's enum-constant directory may be cached before
    registration, which would throw).
- ⚠️ **Call contract**: the five `EnumProxy` field names, the `name` entries in `enumextensions.json`, and the modId of
  the mod declaring that json are mutually constrained (FML requires the lowercased constant name to start with the
  declaring mod's modId) - renaming means changing all three together.

## 1.0.3

> Version note: this release **absorbs the planned `1.0.2`** — that number was committed but **never
> pushed and never published** (its content is the "extra hostile seam" section below), and per this
> repo's convention ("changes to an unreleased version are merged into the original entry") it is
> folded into this release in full ⇒ `1.0.2` therefore **never existed** (no tag, no Release, no
> artefact). The earlier `1.0.1` was likewise never published (merged into `1.0.0`).
> ⚠️ This release contains **one semantic change** (see "Hostile-target criterion rewritten"): under
> the `1.x` compatibility contract it should have raised the major version; by user ruling of
> 2026-09-24 it ships as a **gameplay-ruling exception** in a patch slot.

### New features (planned `1.0.2`)

- **`combat/HostileTargets` gains an "extra hostile" injection seam** (`ExtraHostileProbe` /
  `installExtraHostileProbe`): a consumer can install a predicate declaring which entities should
  additionally count as hostile targets, so entities that are **neither `Enemy` nor a neutral mob**
  (typically third-party training dummies) are correctly recognised by effects that require a
  hostile target.
  - The criterion becomes `the previous criterion ∪ entities declared by the consumer`; the
    two-argument overload `isHostile(viewer, target)` inherits the extension through its existing
    delegation.
  - Why a seam instead of a hard-coded rule: this criterion is called not only by consumer gameplay
    code but also by the **library's own** `target/SelectorTargets` for selectable-target checks
    (client raycast / client radius highlight / server-side confirmation) — the decision point lives
    inside the library, where a consumer cannot reach; and "which third-party entity counts as
    hostile" is consumer gameplay policy that the library must not know about. The pattern mirrors
    the existing `combat/InternalDamageWindows`.
  - **With nothing installed the previous semantics are preserved verbatim** (no entity is
    additionally treated as hostile) — the safe direction: a missing injection only falls back to the
    existing behaviour and can never turn a neutral mob hostile by accident.

### ⚠️ Semantic change: hostile-target criterion rewritten (user ruling 2026-09-24)

- **`HostileTargets.isHostile` changes from `Enemy ∪ angered neutral mob` to
  `Enemy ∪ neutral mob (pets excluded)`**:
  - **The "must be angered" requirement is dropped** (previously `NeutralMob#isAngry()` =
    `getRemainingPersistentAngerTime() > 0`, 20–39 s after being provoked) ⇒ unangered
    **wolf / iron golem / polar bear / bee** now also count as hostile targets.
  - **"Pets excluded" is added**: tamed `TamableAnimal` (wolf / cat / parrot) no longer count. **A
    single predicate `TamableAnimal#isTame()` is used** — it has an identical signature across all
    three platforms (a hard constraint for the shared common sources); the other "owned" family
    `AbstractHorse` (horse / donkey / mule / camel) **is not a `NeutralMob`** and therefore never was
    in this set; and `OwnableEntity` was changed in 26.1.2 to the `EntityReference` system, which no
    longer has `getOwnerUUID()`.
  - **Measured impact**: the only vanilla mob that is **both** a `NeutralMob` and a `TamableAnimal`
    is the **wolf** ⇒ this entry is effectively "a wolf counts as hostile while **untamed**, and
    stops counting once tamed".
  - **Why this is a "semantic change"**: the `1.x` compatibility contract only permits "additions"
    and "behaviour fixes that do not change existing semantics", while this changes the existing
    semantics of `isHostile` ⇒ it should raise the major version (`1.x` → `2.x`). By user ruling of
    2026-09-24 it ships as an **exception** in the patch slot `1.0.3` (rationale: this library's only
    real consumer is the consumer mod in this repo, and the criterion was ruled directly by the
    user).
  - Supporting change: the class comment of `target/SelectorTargets` no longer restates the
    criterion and now points at `HostileTargets` (avoiding two places to maintain).

### Compatibility

- **Addition** (seam): a new nested type `ExtraHostileProbe`, a new static method
  `installExtraHostileProbe`, and one extra probe call at the end of the existing `isHostile(Entity)`.
  No existing public API is removed, renamed or re-signatured.
- ⚠️ **Semantic change** (the entry above): under the contract it should raise the major version;
  by user ruling it ships as an **exception** in a patch slot ⇒ the lower bound of the consumer's
  `starengine_version_range` moves to `[1.0.3,2.0)` with this release.

## 1.0.0

> Convention: follow-up changes to entries already recorded for the current version are merged into the original entry;
> only the final wording is kept.
> Version note: `1.0.0` is the library's **first stable release** — the version number was normalised from the snapshot
> series' last notch (`1.0.0-SNAPSHOT.16`) with **zero Java source changes**; the artefact files are now
> `starengine-<platform>-1.0.0.jar`. `.16` was the **26.1.2 consumer-adoption** notch — the version simply advanced
> alongside the consumer's "26.1.2 full port" (the library's convention is one notch per substantive commit).
> Note (2026-09-22): the Forge-side Curios-prerequisite removal was briefly prepared as `1.0.1`; it is **folded
> into this version** instead (see "Dependencies and metadata" below). `1.0.1` was never pushed or released.
> The snapshot series ends here; from `1.x` on, the compatibility policy below governs.
> Note (2026-09-23): the 26.1.2-side "missing platform implementation" was likewise briefly prepared as
> `1.0.1`; it is **folded into this version** instead (see "Missing platform implementation on the third
> line" below). `1.0.1` was never pushed or released.

### Missing platform implementation on the third line (merged into `1.0.0`, 2026-09-23)

- **`NeoForgeEconomyStorage` for the `neoforge-26.1.2` line** — the platform storage implementation of the wallet
  ledger. The first stable release shipped this class only on `forge-1.20.1` (`ForgeEconomyStorage`) and
  `neoforge-1.21.1` (`NeoForgeEconomyStorage`), so on `26.1.2` `StarEngineEconomy.isAvailable()` stayed `false`:
  the `/starcoin` command was never registered, picked-up star coins were not absorbed into the wallet and the
  balance bar always read 0. The 26.1.2 entry point (`StarEngineLib`) now injects the implementation, exactly like
  the other two lines.
  - Data layout, key names, `PlayerEvent.Clone` carry-over and offline `.dat` parsing are **identical** to the
    1.21.1 implementation (`NeoForgeData` persistent-data root, `starengine/star_coin_wallet`, `balance`/`name`).
  - Only two 26.1.2 platform API changes had to be absorbed, both documented inline: `CompoundTag` value / sub-tag
    accessors are now `Optional` / `*Or` flavoured (no `contains(String,int)`, no bare `getCompound(String)`), and
    command permission became a **named permission set** (`CommandSourceStack#hasPermission(int)` is gone) — the
    seam's numeric level is mapped onto `Permissions.COMMANDS_MODERATOR/GAMEMASTER/ADMIN/OWNER`, so level ≥ 2 still
    means "may use `/starcoin`" exactly as on the other two lines.

### Dependencies and metadata (merged into `1.0.0`, 2026-09-22)

> This change was originally prepared as a separate `1.0.1` release. Before it was ever pushed or published the user
> decided to **fold it into `1.0.0`** — one unreleased change must not occupy two version numbers. `1.0.1` therefore
> **never existed**: there is no such tag, no such Release and no such artefact. The existing `1.0.0` tag keeps its
> name, and its Release assets are refreshed by the same CI run that builds this commit (the workflow reuses an
> existing tag and re-uploads the jars with `--clobber`).

- **The Curios prerequisite is removed on the `forge-1.20.1` side** (user decision, 2026-09-22:
  "make it consistent with the other two versions"):
  - `forge-1.20.1/src/main/templates/META-INF/mods.toml` drops the `modId="curios"` dependency block
    (previously `mandatory=true versionRange="[5,6)" ordering="AFTER" side="BOTH"`);
  - in `forge-1.20.1/build.gradle` the dependency is downgraded from `modImplementation` to **`modCompileOnly`**.
    A `mod*` configuration (not plain `compileOnly`) is required: MDG LegacyForge only routes `mod*`
    configurations through `RemappingTransform` (Mojmap/Parchment); otherwise `CuriosCompat`'s
    `LivingEntity` parameter would not match Curios' method descriptor at compile time.
  - Rationale: the library **never calls** `item/CuriosCompat` — it is a shim for consumers, and no code path
    inside the library loads it, so Curios is a **compile-time** dependency on this side only.
    **A consumer that calls that shim declares Curios itself** (`astral_dice`'s 1.20.1 side already declares
    `mandatory=true [5,6)`).
  - ⇒ **None of the three platform jars declares Curios as a prerequisite any more** (the two NeoForge lines
    never did), so the dependency blocks are now uniform across all three.
- This change is a **pure relaxation**: no public type, method, field or constant is deleted or renamed, and no
  visibility, signature or semantics changes. It therefore complies with the compatibility policy below, and the
  consumer's `starengine_version_range=[1.0.0,2.0)` **needs no tightening** — no consumer code changes either.
  The only cost: if a consumer forgets to declare Curios yet calls the shim, the failure degrades from a clear
  loader-level "missing required dependency" error to a runtime `NoClassDefFoundError`.
- The three platforms always share one version number ⇒ artefact file names stay
  `starengine-<platform>-1.0.0.jar` — **no `1.0.1` artefact is produced**.

### Engineering (merged into `1.0.0`)

- Verified: three-platform `./gradlew build publishToMavenLocal`; `~/.m2` holds `1.0.0` for all three coordinates
  (the short-lived aborted-release `1.0.1` coordinates were moved out of the local repository); each platform's
  `pushToPack` pushed its `1.0.0` jar into the corresponding modpack's `mods` folder (**exactly one library jar per
  pack**, so there is no duplicate-`modId` clash).
- Jar inspection: the `mods.toml` dependency block on all three platforms is now just "loader + minecraft"
  (1.21.1 = `neoforge [21.1,21.2)`; 1.20.1 = `forge [47.4.10,48)`; 26.1.2 = `neoforge [26.1.0.0,26.2)`),
  with no `modId="curios"` left.
- **Regression criterion (the important one)**: `forge-1.20.1`'s `CuriosCompat.class` is **byte-identical** to the
  one in the previous `1.0.0` build (3352 B, md5 prefix `d56a57b0`), and **not a single byte differs across all 55
  classes** ⇒ switching to `modCompileOnly` did not change reobf/remapping behaviour; this really is a
  metadata-only change.

### Compatibility policy (new, in force from 1.0.0)

- **Within one major version (first number unchanged — currently `1.x`) breaking changes are forbidden.** The library
  must not delete or rename any public type, method, field or constant, and must not change its visibility, signature
  or established semantics. Only **additions** (new types, new members, new optional entry points) and behaviour fixes
  that preserve the contract are allowed.
- **A breaking change must bump the first number (`1.x` → `2.x`)**, and must tighten the consumers'
  `starengine_version_range` lower bound in the same release. Breaking changes must never hide in a minor/patch slot.
- ⇒ The consumer range `[1.0.0,2.0)` is the machine-readable form of this contract: any `1.x` release can replace
  another in place, without touching consumer code.
- This **supersedes** the earlier `gradle.properties` note that "the SNAPSHOT series' API carries no
  semantic-compatibility promise" — that exemption covered the snapshot series only, and no longer applies from
  `1.0.0` on.

### Engineering

- All three platform versions normalised from `1.0.0-SNAPSHOT.16` to **`1.0.0`** and published to `mavenLocal`
  (`./gradlew publishToMavenLocal`); the three jars are
  `starengine-{neoforge-1.21.1,forge-1.20.1,neoforge-26.1.2}-1.0.0.jar`.
- The consumer `astral_dice`'s **26.1.2 line** adopts this release (the `.16` code) for the first time: that line was pinned to `.11`
  and still carried local copies of `combat/HostileTargets`, `combat/PlayerHostilityTracker`, `target/SelectorTargets`
  and `target/SignSelectionGate` — all four superseded by the `.15` sinking but never synchronised during the freeze.
  They are removed by this port, their references now point at `com.merlinkitsune.starengine.*`, and the platform
  hook `combat/PlayerHostilityTrackerEvents` plus the `InternalDamageWindows.install(...)` injection are added
  (structurally identical to the 1.21.1 / 1.20.1 lines).
- **Zero Java source changes in the library.** Publishing a new version number is required for the usual reason: the
  version does not end in `-SNAPSHOT` (a plain version per Maven semantics), so Gradle does not treat it as a changing
  module — without a bump the consumer would keep resolving the stale jar from `mavenLocal`.

## Unreleased (1.0.0-SNAPSHOT.15)

> Convention: follow-up changes to entries already recorded for the current version are merged into the original entry;
> only the final wording is kept.
> Version note: `.15` is the **shared-judgement sinking** release — it moves the byte-identical hostility / selector /
> wallet-display judgements out of the consumer `astral_dice` and into the library.

### New features

- **New `combat` package: the single entry point for hostile-target judgement, sunk from the consumer.**
  `HostileTargets` (`isHostile(Entity)` / `isHostile(viewer, target)`; semantics = hostile mobs ∪ angered neutral mobs ∪
  "players neither on the same team nor who have ever attacked the viewer") and `PlayerHostilityTracker`
  (an in-memory map of victim UUID → attacker UUIDs, with `hasAttacked` / `forget`).
  ⚠️ **Table and events are separated**: the library **registers no events at all** (a library red line), so the four
  platform hooks stay on the consumer side — record an attack (`LivingDamageEvent`), clear on death
  (`LivingDeathEvent`), clear on death-respawn clone (`PlayerEvent.Clone`) and clear on log-out
  (`PlayerLoggedOutEvent`) are translated by the consumer's `combat.PlayerHostilityTrackerEvents` into the library's
  `recordAttack` / `recordAttackIfExternal` / `forget` calls.
- **New platform seam `InternalDamageWindows`**: `PlayerHostilityTracker` must distinguish a "deliberate attack" from the
  mod's own splash / AOE / counter-injection damage. Those two window flags belong to the consumer's gameplay
  implementation (not sunk), so the library only exposes the predicate and the consumer injects it at startup via
  `install(aoeProbe, counterProbe)`. When nothing is installed both predicates return `false` — the safe direction,
  since a missed match only records one extra hostility entry while a false match would drop a real attack.
- **New `target.SelectorTargets`**: routes the "hostile" families through `HostileTargets`, fixing the defect where
  `TargetType#matches` tests `ENEMY` / `ENEMY_OR_RIVAL` with a bare `instanceof Enemy` and therefore misses angered
  wolves / iron golems / polar bears / bees. It also carries the four-argument overload that permits self-targeting.
  The defect is **adapted around in place rather than rewriting `TargetType`**, so the existing semantics stay
  byte-for-byte identical and all call sites go through `SelectorTargets`.
- **New `target.SignSelectionGate`**: the pending record for sign active-skill pre-gating (pure in-memory and
  transient: `arm` / `isArmed` / `take` / `clear`).
- **New `economy.StarCoinWalletState`**: the client-side display cache behind the star coin wallet balance bar
  (primitives only, **deliberately free of any client-only type** so the dedicated server can load it too).
  It shares a domain with the library's existing `StarEngineEconomy`.

### Engineering

- All three platforms bumped `1.0.0-SNAPSHOT.14 → 1.0.0-SNAPSHOT.15` and were `publishToMavenLocal`'d; `compileJava`
  passes on all three (including the shared source compiling against **26.1.2**, which is exactly what enforces the
  "only APIs present in all three MC versions" red line). Jar inspection confirms all seven new classes are present on
  every platform (including `InternalDamageWindows$Probe` and `SignSelectionGate$Pending`).
- The consumer `astral_dice` dropped its local copies on both sides: `combat/HostileTargets`,
  `combat/PlayerHostilityTracker`, `target/SelectorTargets`, `target/SignSelectionGate` and
  `economy/StarCoinWalletState` were removed, and `combat/PlayerHostilityTrackerEvents` was added as the platform hook.

## Unreleased (1.0.0-SNAPSHOT.14)

> Convention: follow-up changes to entries already recorded for the current version are merged into the original entry;
> only the final wording is kept.
> Version note: `.14` adds the **player balance ledger backend** (the library side of the star coin wallet feature).

### New features

- **New player balance ledger (library-side backend of the star coin wallet)**: a new `economy` package in `common` —
  `EconomyStorage` (the platform storage seam: read / write / offline read), `StarEngineEconomy` (the public API:
  `getBalance / setBalance / hasBalance / deposit / withdraw / transfer` plus offline reads; **returns 0 / false and never
  throws when no storage is installed**, so third-party mods such as FTB can integrate safely) and `StarCoinCommand`
  (`/starcoin add|set|remove|get|rank`, where `rank` includes **offline players**).
  The balance lives in **player persistent NBT** — the library's standing invariant is that it never registers registry
  entries, so `AttachmentType` is deliberately not used — and is explicitly copied on `PlayerEvent.Clone`, so the
  **balance survives death**. One implementation per platform (`NeoForgeEconomyStorage` / `ForgeEconomyStorage`; the only
  differences are the event package names and the persistent-data root segment in the player `.dat`, i.e.
  `NeoForgeData` / `ForgeData`); 26.1.2 is **not wired up** (the API degrades to unavailable). The platform entry point
  installs the storage implementation.
- **New command permission seam `CommandPermissionGate`**: `CommandSourceStack#hasPermission(int)` has a different
  signature on **26.1.2** (identical on 1.20.1 / 1.21.1), so shared code does not write the permission predicate directly;
  the platform supplies `(source, level) -> source.hasPermission(level)`.

### Engineering

- Version bumped on all three platforms from `1.0.0-SNAPSHOT.13` to `1.0.0-SNAPSHOT.14` and published with
  `publishToMavenLocal`; `build` passes on all three (including compiling the shared sources on 26.1.2, which is exactly
  what enforces the keep-common-version-agnostic rule).

## 1.0.0-SNAPSHOT.13

> Convention: later edits to an entry already recorded for this version are merged into that entry — only the final version is kept, no "updated again" follow-ups.
> Note on the version number: `.13` is the **resource-pack metadata fix + version-line unification** release — it adds the missing `pack.mcmeta` to `forge-1.20.1` (Forge validates every mod jar as a resource pack; without that file the loading screen shows a warning and the mod's resource pack is **not registered**) and keeps the `.6` loader-gate fix. Per the user's ruling of 2026-09-17 the library version is unified as `1.0.0-SNAPSHOT.10` (same patch segment as the consumer's `2.0.0-SNAPSHOT.10`); the fix was originally in flight as `.7` (published to `mavenLocal` only, never committed), so it is merged into this section per the convention above and no separate `.7` section is created. The bump is mandatory for the same reason as before: the version does not end in `-SNAPSHOT` (a normal version under Maven semantics), so Gradle does not treat it as a changing module — without a bump a consumer keeps resolving the old jar from `mavenLocal`, which has no `pack.mcmeta` (and needs `--refresh-dependencies` to be re-resolved anyway).

- **New mod icon plus a description synced across all three platforms.** The repository-root `icon.png` is now shipped as each jar's mod icon: the same bytes (2671 bytes, PNG signature `89 50 4E 47 0D 0A 1A 0A`) were copied to `neoforge-1.21.1/src/main/resources/icon.png`, `forge-1.20.1/src/main/resources/icon.png` and `neoforge-26.1.2/src/main/resources/icon.png` — the **jar root**, which is where FML resolves `logoFile` from (same mechanism the consumer `astral_dice` jars use for their own root-level `icon.png`). All three metadata templates gained the logo key, and their `description` was replaced with the 1.21.1-side text: `A common library for the Astral Dice mod. It includes cross-platform implementations and supports functional integration with other mods.` The former Chinese multi-paragraph description is therefore gone from `forge-1.20.1` and `neoforge-26.1.2` too, so the mod-list entry is now identical on all three platforms. Note: the repository-root `icon.png` is not a resource root of any subproject — the three copies under `src/main/resources` are the shipped ones. This is a metadata/resource-only change; **no Java source changed**. Version bumped `1.0.0-SNAPSHOT.12 → 1.0.0-SNAPSHOT.13` on all three platforms for the usual reason: the number does not end in `-SNAPSHOT` (a normal version under Maven semantics), so Gradle does not treat it as a changing module — without a bump a consumer keeps resolving the old `mavenLocal` jar, which has no icon and still carries the old description.
  - ⚠️ **Correction (2026-09-22): the key was originally spelled `logofile` (lowercase `f`) and therefore never took effect.** TOML keys are case-sensitive and the documented key is `logoFile` (capital `F`), so the unrecognized `logofile` key was **silently ignored by the metadata parser and no logo was ever loaded** — the icon file itself was shipped correctly all along (present at the jar root, valid 64×64 RGBA PNG, 2-to-the-power-of-2, bytes identical to the repository root), so the failure was purely the key's casing. Fixed on all three platforms as `logoFile="icon.png"` (verified by opening the built jars: the `.14` jars carry `logoFile="icon.png"` and an `icon.png` whose md5 equals the repository root's `09762024a7672b5bad645fd41786ce27`). No version bump was needed — `.14` had not been consumed yet, so the corrected metadata was folded into the in-flight `.14` jars as usual.

### Project

- **Fix: the Forge 1.20.1 loading screen reported "File … failed to load a valid ResourcePackInfo".** Symptom: the consumer's `run/1.20.1` client stopped on Forge's `Warning while loading mods` screen with one warning pointing at the library jar (both the user's screenshot and `latest.log` name `starengine-forge-1.20.1-1.0.0-SNAPSHOT.5.jar`; `.6` lacks the same file, so the warning is independent of the snapshot number), matching the log line `[net.minecraft.server.packs.repository.Pack/]: Missing metadata in pack mod:starengine`. Root cause (read from the local Forge 1.20.1 sources, not guessed): `net.minecraftforge.client.loading.ClientModLoader#clientPackFinder` (lines 154-165) calls `Pack.readMetaAndCreate(name, …, PackType.CLIENT_RESOURCES, Pack.Position.BOTTOM, …)` for **every** mod file and, when it returns `null`, runs `ModLoader.get().addWarning(new ModLoadingWarning(mod, ModLoadingStage.ERROR, "fml.modloading.brokenresources", e.getKey()))` — that key resolves to `File {2} failed to load a valid ResourcePackInfo` in `assets/forge/lang/*.json`; `Pack.readMetaAndCreate` returns null when the jar has **no `pack.mcmeta`** (or its metadata cannot be parsed for that PackType). The server path is identical: `net.minecraftforge.server.ServerLifecycleHooks` (line 214) performs the same check for `PackType.SERVER_DATA`. ⇒ Any Forge 1.20.1 mod jar without `pack.mcmeta` produces one warning, and its resource pack is **not registered** (the library ships no resources today, so there is no functional loss — but it is a silent-failure surface). Fix: added `forge-1.20.1/src/main/resources/pack.mcmeta` (`pack_format` **15**, `description` = `starengine resources`, same shape as the consumer's `forge-1.20.1/src/main/resources/pack.mcmeta`). **Scope: Forge 1.20.1 only** — NeoForge 1.21.1 / 26.1.2 never run those code paths (no such warning in their run logs, and the consumer's three jars only carry `pack.mcmeta` on 1.20.1), and `pack_format` differs per MC version, so the other platforms' values are **not guessed** (derive them from each version's client sources if ever needed). All three platforms' `lib_version` / `mod_version` were unified from `1.0.0-SNAPSHOT.6` to **`1.0.0-SNAPSHOT.10`** (suffixes `+neoforge_1.21.1` / `+forge_1.20.1` / `+neoforge_26.1.2`, same patch segment as the consumer's `2.0.0-SNAPSHOT.10`) and were republished with `publishToMavenLocal`; **no Java source changed**.
- **New config-driven constant `ALLOW_FIREARM_DAMAGE` (default `false`), carrying the consumer's public `allow_firearm_damage` option.** `component/GameplayConstants` gains a **non-final** `public static boolean ALLOW_FIREARM_DAMAGE = false` (the default **still blocks** firearm/artillery damage, exactly matching the existing behaviour; only when the option is `true` may such damage count towards spell damage), placed with the existing group of "still configurable" booleans (right after `EVENT_APPLY_OPAC`); `component/GameplayConfigValues` **appends** a 7th component `boolean allowFirearmDamage` at the end (the first six keep their names and order **verbatim**, so consumers still construct it positionally — a **source-compatible** addition); and `applyConfig(GameplayConfigValues)` writes the value inside its "only the few still-configurable fields" block, leaving the fixed-constant tick derivation untouched. The library still **reads no config file and holds no config schema** — the config file and option definitions stay on the consumer side as before. All three platforms' `lib_version` / `mod_version` move from `1.0.0-SNAPSHOT.11` to **`1.0.0-SNAPSHOT.12`** (suffixes `+neoforge_1.21.1` / `+forge_1.20.1` / `+neoforge_26.1.2`); the bump is mandatory for the same reason as before: the version does not end in `-SNAPSHOT` (a normal version under Maven semantics), so Gradle does not treat it as a changing module — without a bump a consumer keeps resolving the old jar from `mavenLocal`, which has neither this constant nor the 7th record component, and fails to compile when it builds the snapshot.

## 1.0.0-SNAPSHOT.6

> Convention: later edits to an entry already recorded for this version are merged into that entry — only the final version is kept, no "updated again" follow-ups.
> Note on the version number: `.6` is the **loader-gate fix** release — it fixes the root cause of the library itself being rejected by FML and aligns the Forge-side gate with the documented convention. The bump is mandatory for the same reason as before: the version does not end in `-SNAPSHOT` (a normal version under Maven semantics), so Gradle does not treat it as a changing module — without a bump a consumer keeps resolving the `.5` jar from `mavenLocal`, which carries the broken `loaderVersion` and is rejected by FML.

### Project

- **Fix: the library itself was rejected by FML (`loaderVersion` wrongly set to a NeoForge version band).** Symptom: two consecutive launches crashed in the FML phase on the consumer's `run/1.21.1` (`crash-2026-09-17_20.41.28-fml.txt` / `…_20.42.17-fml.txt`) with `Mod File …\starengine-neoforge-1.21.1-1.0.0-SNAPSHOT.5.jar needs language provider javafml:21.1 or above, and below 21.2 to load / We have found 4.0.42`, after which the consumer `astral_dice` was reported as `requires starengine 1.0.0-SNAPSHOT.5 or above … Currently, starengine is not installed`. Root cause: `neoforge-1.21.1/gradle.properties` set `loader_version_range` to the **NeoForge version band** `[21.1,21.2)`, while the template's `neoforge.mods.toml:2 loaderVersion="${loader_version_range}"` compares against the **javafml language provider version** (NeoForge 21.1.235 reports `4.0.42`). Fix: the property is now the language range **`[1,)`** — the same value as the consumer's `astral_dice_multiloader-next/neoforge-1.21.1/gradle.properties:23`; the NeoForge band `[21.1,21.2)` still lives on the template's `modId="neoforge"` dependency (**unchanged**). The `neoforge-26.1.2` module already used `[1,)` (no rejection there) and keeps that value.
- **Same batch: Forge 1.20.1 gate aligned with the documented convention + version sync across all three platforms.** `loaderVersion` narrowed from `[47,)` to **`[47,48)`** (the javafml language provider version *is* the Forge major series `47`; a full-version range there makes FML fail with `fml.language.missingversion`), and the `modId="forge"` dependency now uses the exact range **`[47.4.10,48)`** derived in `build.gradle` from `forge_version` (the template placeholder changed from `${loader_version_range}` to `${forge_version_range}`), matching the consumer's `forge-1.20.1/build.gradle:139-152` convention line by line. All three platforms' `lib_version` / `mod_version` moved from `1.0.0-SNAPSHOT.5` to **`1.0.0-SNAPSHOT.6`** (suffixes `+neoforge_1.21.1` / `+forge_1.20.1` / `+neoforge_26.1.2`) and were published with `publishToMavenLocal` (`~/.m2/repository/com/merlinkitsune/starengine/<artifact>/1.0.0-SNAPSHOT.6/`); opening the jars asserts `loaderVersion` = `[1,)` / `[47,48)` / `[1,)` and the Forge dependency `versionRange="[47.4.10,48)"`, with the `neoforge` / `minecraft` ranges unchanged. **No Java source in the library was touched** (metadata, template comments and docs only); consumer pins and the CI ref are synced on the consumer side and are out of scope here.

## 1.0.0-SNAPSHOT.5

> Convention: later edits to an entry already recorded for this version are merged into that entry — only the final version is kept, no "updated again" follow-ups.
> Note on the version number: `.5` is the **merge-cleanup** release — the mainline (`multi-1.20.1-1.21.1` @ `8f68482`) has been merged into the consumer's `multi-dev-next`, so the library now deletes the "delete once merged" transition symbols on schedule. The bump is mandatory for the same reason as `.4`: the version does not end in `-SNAPSHOT` (a normal version under Maven semantics), so Gradle does not treat it as a changing module — without a bump a consumer keeps resolving the stale `.4` jar from `mavenLocal`.

### Project

- **Transition symbols deleted on schedule (the deleted part).** Before deleting anything, every symbol was proven to have **zero references** across the three merged consumer lines (`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2`) by grep, and then removed: (1) `EventTargetCollector`'s `collectTargets` / `collectTeamTargets` / `collectMaids` / `isMaidOwnedBy` (together with the `GameplayConstants.EVENT_RANGE` / `EVENT_APPLY_MAID` branch they used); (2) `GameplayConstants.KOMACHI_EXTRA_PLAYS_CAP`; (3) `effect/ReadyEffect` (sunk in `.4`, zero references after the merge). Decision notes: all 6 `collectTargets` grep hits belong to the consumer's **own** `RandomCardHandler.collectTargets` (a different class and signature), so the library method really is unreferenced; `EVENT_APPLY_MC_TEAM` / `EVENT_APPLY_FTB_TEAM` / `EVENT_APPLY_OPAC` and `EventTargetCollector.collectTeamPlayers` are **live** (called from several consumer sites) and were kept verbatim. Commands and raw output: `temp/t5-cleanup-notes-20260917.md`.
- **Transition symbols deleted on schedule (completed in the same batch) — the event-framework triad.** The three files `event/AstralEventType` / `EventContext` / `EventEffect` were removed together (they reference each other: `AstralEventType(ResourceLocation, EventEffect)`, `EventEffect.apply(EventContext)`, `AstralEventType.trigger(EventContext)` — they must go as one), and the `sourceSets.main.java.exclude` line that `neoforge-26.1.2/build.gradle` kept for `AstralEventType` was removed with them (with the file gone the line could only be dead residue). **Precondition:** the merged consumer's `event/AstralEventSystem.java` had left 4 library `import`s in its import block (unused by the body, but an unresolved `import` is itself a compile error), so the consumer-side commit `7dc64cb` had to remove them first; after that, `git grep -E 'AstralEventType|EventContext|EventEffect' -- '*.java'` reports **0 hits** across the consumer's three lines. ⚠️ Because that precondition was only met after `.5` had already been published once, the `.5` bytes were **republished** (same version number, different bytes) — consumers must re-resolve with `--refresh-dependencies` (or clear the mavenLocal cache), otherwise they keep the old `.5` bytes that still carry the triad.
- **Version and consumer sync.** Library version `1.0.0-SNAPSHOT.4 → 1.0.0-SNAPSHOT.5` (all three platform `gradle.properties` share the number); the dev-next `starengine_version` and `starengine_version_range` on both sides (expanded from `${starengine_version_range}` in the two `mods.toml` files) now read `[1.0.0-SNAPSHOT.5,2.0)`; and the consumer CI's pinned library checkout `ref` was moved to the library commit that carries `.5` **after** the triad removal, with a comment stating that "a library version bump must update this ref in the same commit". ⚠️ The library repository has **not been pushed** yet, so that sha exists only locally and a CI checkout will fail until the repository is pushed and the sha is reachable.

## 1.0.0-SNAPSHOT.4

> Convention: later edits to an entry already recorded for this version are merged into that entry — only the final version is kept, no "updated again" follow-ups.
> Note on the version number: `.4` **continues** `.3` rather than redoing it — the library only **adds** `common/effect/ReadyEffect`. The bump is mandatory for the same reason as `.3`: the version does not end in `-SNAPSHOT` (a normal version under Maven semantics), so Gradle does not treat it as a changing module — without a bump a consumer keeps resolving the stale `.3` jar from `mavenLocal` and then fails to compile with "cannot find symbol" for the missing class. The `.3` entries are kept unchanged in the next section.

### New Content

- **`effect/ReadyEffect` was sunk into the library.** The class exists in all three lines (`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2`) with **byte-identical** content and imports only vanilla `MobEffect` / `MobEffectCategory` — no consumer-specific class — making it the **only** one of the 53 candidates selected by the "identical across all three lines + self-contained" rule that was not yet in the library (the other 52 were either already sunk in earlier phases or excluded with a recorded reason, see Project). It now lives at `common/src/main/java/com/merlinkitsune/starengine/effect/ReadyEffect.java` (14 lines / 540 bytes), byte-for-byte the original with only the `package` line rewritten to the library package, line endings (CRLF) preserved.

### Project

- **The sink criteria for three-line shared classes are now recorded** (manifest: `temp/sink-manifest-20260917.md`). Candidate gate = "the same relative java file has an identical SHA-256 on all three lines" (229 java files across the three lines → 53 candidates); self-containment gate = "the reference closure stays inside the candidate set ∪ classes already in the library ∪ Minecraft/loader APIs". Every exclusion is listed with its reason: not identical across the three lines (176), depends on a consumer-specific class (`ModItems`, `ModAttachments`, `BaseChipItem`, `DiceCombatModifiers`, `SpellDamageRegistry`, …), or one of the 3 mixins (`mixin/trade/Merchant{Container,Menu,ResultSlot}Mixin`, which need a mixin config and a refmap the library has no infrastructure for).
- **Pre-merge drift audit (the reason these classes were left untouched).** For every copy that dev-next had deleted and the mainline had later modified, the library counterpart was compared line by line: `component/GameplayConstants` already **carries the mainline constant values** (`MAX_MARKER` 16→32, `HAND_FAN_BIG_RANGE` made `final`, `MAX_STARLIGHT` / `EFFECT_CARD_COOLDOWN_SECONDS` / `MAX_EFFECT_STACKS` made `final`, …), and the only remaining differences are the deliberate "value-snapshot seam vs `ModCommonConfig`" split plus the transition symbols kept by prior convention (`EVENT_RANGE` / `EVENT_APPLY_MAID` / `KOMACHI_EXTRA_PLAYS_CAP` / `TARGET_SELECT_RADIUS`); `event/EventTargetCollector` keeps its **full implementation** in the library (the pre-merge consumer still calls `collectTargets(...)`); and the differences in `item/BossEntityUtil`, `event/SignActiveTriggeredEvent`, `event/ModEffectRemoval` and `client/ActionBarManager` (one copy per platform) are shim base classes and comments only. ⇒ None of the mainline's changes in this set can be lost by the merge.
- **Two leftover consumer-package references in `common` were removed.** The javadoc of `effect/HealingEffect` and `event/AmethystDiceHandler` still carried `{@link com.merlinkitsune.astral_dice.item.HealingManager}` and `{@link com.merlinkitsune.astral_dice.combat.SpellDamageRegistry}` — the earlier package rename only rewrote the `starengine` segment, so the consumer package survived verbatim; it never broke the build, but it violated the acceptance rule "`common` must contain no reference to a consumer-specific class". Both are now plain text, with zero change to behaviour or signatures; after the cleanup the library (including all three platform subprojects) references `com.merlinkitsune.astral_dice` **0** times.
- **Consumers must raise their range lower bound to `1.0.0-SNAPSHOT.4`.** Both `gradle.properties` files in the dev-next worktree now carry the new `starengine_version` / `starengine_version_range`, and both `mods.toml` files expand to the same range through `${starengine_version_range}`. The semantics are the same "exact lower bound" as `.3`: `[1.0.0-SNAPSHOT.4,2.0)` rejects `.1` / `.2` / `.3`, accepts `.4` / `.10` / `1.0.0` / `1.1.0`, and still rejects `2.0.0`.

## 1.0.0-SNAPSHOT.3

> Convention: later edits to an entry already recorded for this version are merged into that entry — only the final version is kept, no "updated again" follow-ups.
> Note on the version number: the library was previously built locally as `1.1.0` (never published anywhere outside `mavenLocal`) and was renumbered to `1.0.0-SNAPSHOT.1` for its first public release. It then became **`1.0.0-SNAPSHOT.2`**, because the Java package was renamed while `mod_version` stayed `1.0.0` — the patch segment alone carries the rename, so a dependency range can tell the two jars apart. It is now **`1.0.0-SNAPSHOT.3`**, because Cloth Config and the in-library config module were removed entirely, `GameplayConfigValues` was narrowed from 13 fields to 6 and the dead event-framework classes were deleted — again binary-incompatible, so it needs its own patch segment before a consumer can stop resolving the stale `.2` jar from `mavenLocal`. Since `1.0.0-SNAPSHOT.1` and `.2` never reached any repository other than `mavenLocal`, all three versions' entries are merged here as one `1.0.0-SNAPSHOT.3` changelog.

### Breaking Changes

- **Java package renamed `com.merlinkitsune.starengine` → `com.merlinkitsune.starengine`; the Maven `groupId` moved with it.** The package segment `starengine` was replaced by `starengine` at every place it is a *package*: `package` / `import` statements, javadoc `{@link}` targets, the three source directories (`common`, `neoforge-1.21.1`, `forge-1.20.1`) and `mod_group_id` in both `gradle.properties` — 50 files, 69 occurrences, plus the publication `groupId` (`com.merlinkitsune.starengine` → `com.merlinkitsune.starengine`). Deliberately **unchanged**: the mod id `starengine`, the display name `StarEngine`, every `StarEngine*` class name, and all historical records (older `CHANGELOG` entries, `docs/starengine-lib/`, the one-off `tools/migrate_to_starengine.py`). The rename is **binary-incompatible at the same version number** — the pre-rename `.1` jar and this `.2` jar expose identical `modId` and identical MC-version suffixes but different class names — so consumers must move their range lower bound too (see Project below). Replacement was literal and byte-level (`starengine(?![A-Za-z0-9_])`, case-sensitive), so `starengine` (mod id), `starengine` (new package) and `StarEngine` (brand/class prefix) could never be hit; line endings were preserved, so the diff is exactly one line per occurrence and contains no CRLF/LF noise. Verified after the rename by opening both platform jars: 45 (neoforge) / 47 (forge) classes, **0 occurrences** of the old package in class paths or the constant pool, and a full byte scan of the repository (binaries included) likewise finds 0 real references. Any consumer artifact that pairs the library's fully-qualified class names with a *string* (a Mixin config targeting `com.merlinkitsune.starengine.*`, an obfuscation mapping, reflection or codegen) had to be updated as well; this repository has none left. Client-visible impact: none.

### New Content

- First extraction of **StarEngine** (`starengine`) from the Astral Dice 1.20.1 / 1.21.1 multiloader monorepo: 34 shared units that both MC versions can reference (18 `MobEffect` implementations, the `AstralEventType`/`EventContext`/`EventEffect`/`EventTargetCollector` event framework, the target-selection framework, `BossEntityUtil`, `ClientDamageNumbers`, `GameplayConstants`, `SignActiveTriggeredEvent`, `AmethystDiceHandler`) were moved down into this library to be reused by the upcoming **Astral Dice Extra** mod and its siblings.
- The library registers **no** registry entries (items / effects / attachments / data components / capabilities all remain in the consuming mod). Introducing or upgrading the library therefore changes no `ResourceLocation` ownership, so it can neither corrupt saves nor break datapacks.
- Provides a **platform-neutral config seam**: `GameplayConfigValues` (a plain snapshot record) and `GameplayConstants.applyConfig(...)`. Config files and config IO **do not** move into the library — `ModConfigSpec` (NeoForge) and `ForgeConfigSpec` (Forge) are not interchangeable, so the option definitions, the TOML IO and the config screen all stay in the consuming mod, while the library only declares which gameplay values are configurable and how they are applied. A consumer builds the snapshot in its own config load/reload callback and pushes it; **a config change needs no restart** (`GameplayConstants` fields are non-final runtime reads).

- Added the **`neoforge-26.1.2` platform**: the library goes from "two versions share one source" to "**three versions share one source**" — the same `common` sources are now compiled by Java 17 (forge-1.20.1), Java 21 (neoforge-1.21.1) and Java 25 (neoforge-26.1.2; MC 26.1.2 / NeoForge 26.1.2.109 / ModDevGradle 2.0.147 / **no Parchment**, since Parchment's official maven 404s for every 26.1.x / 26.2 build), one jar per platform, all sharing one version number.
### Project

- **Multiloader single-repo shape.** `common/src/main/java` is a shared *source directory* (not a Gradle subproject); the `neoforge-1.21.1` (Java 21, Mojmap + Parchment, ModDevGradle 2.0.141) and `forge-1.20.1` (Java 17, reobf SRG, ModDevGradle Legacy 2.0.144) subprojects each add it as an extra `sourceSet` and compile the same sources once, producing one jar per MC version.
- **Two hard constraints on shared sources**, both enforced at compile time rather than by convention: `common` may not use Java 21-only syntax (record patterns, switch patterns, `SequencedCollection`, …) — enforced by the Java 17 toolchain compiling the same sources — and may only use MC APIs that exist with **identical signatures** in both versions.
- **Loader divergence lives in the platform subprojects**, one file per side: `ActionBarManager` (`DeltaTracker` vs `float partialTick`), `ModEffectRemoval` (`Holder<MobEffect>` vs `MobEffect`), the custom data key (`DataComponent` vs Forge's `ItemDataKey`), and the Curios integration (`CuriosCompat`, Forge only).
- **Loader divergence folded into shims.** `platform/LoaderEvent` is an abstract base that each side extends from its own event bus type (NeoForge / Forge), and `platform/LoaderTags` exposes the `c:bosses` common tag from the platform's own `Tags` class. Shared files such as `SignActiveTriggeredEvent` and `BossEntityUtil` reference only the shims, so no `net.neoforged.*` / `net.minecraftforge.*` literal remains in shared code.
- **Config stays out of the library (a value-snapshot seam).** `ModConfigSpec` (NeoForge) and `ForgeConfigSpec` (Forge) are not interchangeable, and config IO plus the config GUI can only be done per platform, so the library holds **no config schema, reads no config file and ships no config screen**; it keeps only the platform-neutral `GameplayConfigValues` (a plain snapshot record) and `GameplayConstants.applyConfig(...)`. A consumer registers its own config and pushes the current values as a snapshot; changes take effect without a restart. The record's fields are the contract — consumers construct it **positionally**, so adding, removing or reordering a field breaks them at **compile time** (exactly why a record was chosen over a loose set of getters). As a result `common` contains no loader literals and no third-party config library dependency, and the library's only required prerequisites are the loader itself (plus Curios on the Forge side).
- **The version was bumped to `1.0.0-SNAPSHOT.3` (mandatory).** This change deletes the in-library config module and the event-framework classes and narrows `GameplayConfigValues`, all binary-incompatible; because the version does not end in `-SNAPSHOT` (a normal version under Maven semantics), Gradle does not treat it as a changing module — without a bump a consumer keeps resolving the stale `.2` jar from `mavenLocal` and then fails to compile with "cannot find symbol".
- **Maven publication.** NeoForge publishes `from components.java` (a plain Mojmap jar — NeoForge compiles and ships Mojmap from 1.20.5 on, so no remapping is needed); Forge 1.20.1 publishes `reobfJar`, i.e. the **production SRG jar**, because the consuming MDG LegacyForge remaps SRG jars to dev Mojmap names at resolution time. Publishing the non-reobfuscated dev jar instead would cause `NoSuchFieldError` in production. Both publications set an explicit `artifactId` so the coordinates match `archivesName`.
- **Consumers must raise their range lower bound to `1.0.0-SNAPSHOT.3`.** Adjacent snapshots are **binary-incompatible** (`.1` → `.2` renamed the package; `.2` → `.3` removed the in-library config module, narrowed `GameplayConfigValues` and deleted the event-framework classes), while the older jars share the `modId` and the version *shape*, differing only in patch segment — so a loose range such as `[1.0.0-SNAPSHOT,2.0)` matches them too: a stale `.1` fails at runtime with `NoClassDefFoundError: com/merlinkitsune/starengine/...`, and a stale `.2` fails at compile time because classes such as `StarEngineConfigs` no longer exist. `[1.0.0-SNAPSHOT.3,2.0)` rejects `.1` / `.2` while accepting `.3` / `.10` / `1.0.0` / `1.1.0`, and still rejects `2.0.0`. Measured against `maven-artifact` 3.8.5 across three ranges × seven versions (`[1.0.0-SNAPSHOT.2,2.0)` rejects `.1`, accepts `.2`/`.3`/`.10`/`1.0.0`/`1.1.0` and rejects `2.0.0`; all three are "exact lower bound" semantics, so moving the bound to `.3` behaves identically); note `.10` compares **numerically** (not lexicographically), so a two-digit patch segment does not fall out of range.
- **Auto-deploy on build.** `pushToPack` copies the library jar into both modpack `mods` directories after `build` (the SRG jar into the Forge test pack, the plain jar into the NeoForge pack), matching the consumer's existing "build means deploy" convention. This step is not optional: the consumer declares `starengine` as a required dependency, so a missing library jar makes the pack refuse to start. ⚠️ **Since 2026-09-12 this stops on development branches — `main` is exempt.** The library and the consumer must enter a pack as a **matched pair** (the consumer pins the library version at compile time *and* declares a range in `mods.toml`), so as soon as one side advances alone the pack holds an unmatched combination and dies at startup with `NoClassDefFoundError` or "missing required prerequisite". The consumer already stopped pushing on its development branch `multi-dev-next`, so this library stops in lockstep on `main`, freezing the packs on the last matched pair instead of letting one side overwrite them. The guard reads the current git branch from `.git/HEAD` at **execution time** (configuration-cache friendly; a branch switch takes effect immediately) and handles both layouts — a normal repository's `.git` directory and a worktree's `.git` *file* holding `gitdir: <path>`; an undeterminable branch (detached HEAD, no `.git`, i.e. CI) is skipped as well, which is the safe side. `-PdeployToPack` / `-PpackPush` still forces a push for manual releases — when you use it, push the consumer jar as well so the pair stays matched.
- **Hosted on GitHub with its own CI and releases.** The repository now lives at <https://github.com/merlin-kitsune/starengine> and carries `.github/workflows/build.yml`, following the Astral Dice release convention: every push / PR / manual run builds both targets with JDK 21 + JDK 17 and uploads a `starengine-jars` artifact; a push to `main` auto-creates the tag (tag = base version, no `v` prefix, no `+loader` suffix); a tag push creates or updates a GitHub Release whose assets are both platform jars. One guard was added on top of the consumer's rule — **a version containing `-` (a snapshot) is not auto-tagged**, otherwise `1.0.0-SNAPSHOT.1` would be truncated to `1.0.0` and silently published as a release tag. The `gradlew` executable bit was also restored in the git index (`100644` → `100755`): committed from Windows, the repository shipped a non-executable wrapper, so `./gradlew` on the runner died with `Permission denied` (exit 126) before Gradle ever started. Because the consumer's CI checks this repository out first, that single defect reddened **both** repositories' Actions.
- **The library version range must start at `1.0.0-SNAPSHOT`, not `1.0`.** Under Maven `ComparableVersion` semantics `1.0.0-SNAPSHOT.1 < 1.0`, so a consumer declaring `versionRange="[1.0,2.0)"` would reject every `1.0.0-SNAPSHOT.x` build and refuse to start. Verified empirically against `maven-artifact` 3.8.5 — both loaders route through `MavenVersionAdapter.createFromVersionSpec` → `VersionRange.createFromVersionSpec`: `[1.0,2.0)` excludes `1.0.0-SNAPSHOT.1`, while `[1.0.0-SNAPSHOT,2.0)` includes it together with `1.0.0` / `1.1.0`. The bound then has to be precise down to the snapshot index (currently `.2`, see the next entry), otherwise it is too loose to protect against an older jar at the same coordinate.
- **The consumer's CI now builds the library first.** Because the library is published only to `mavenLocal`, Astral Dice's `build.yml` checks out this repository and runs `./gradlew publishToMavenLocal` before building itself. The two repositories' versions must therefore stay in lockstep (consumer's `starengine_version` ↔ this library's `lib_version`), or the consumer CI fails at dependency resolution.
- **Two 26.1 platform differences were sunk into shims.** (1) The identifier class was renamed `net.minecraft.resources.ResourceLocation` → `Identifier` (1.20.1 / 1.21.1 still use the old name). (2) `EntityType#is(TagKey)` is gone and must go through `builtInRegistryHolder()`. (2) is absorbed by `platform/LoaderTags#isBoss(Entity)`, so the shared `BossEntityUtil` is unaware of the difference, and the 26.1.2 `LoaderTags` builds `c:bosses` with `TagKey.create` instead of a platform Tags constant that drifts between versions. (1) has no type-alias equivalent in Java, so the only file using the old name, `common/event/AstralEventType`, is `sourceSets.main.java.exclude`d from the 26.1.2 compile (it and its siblings are already gone from the consumer's mainline; the library only keeps them for now).
