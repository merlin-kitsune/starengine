package com.merlinkitsune.starenginelib.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * 「敌对目标」判定的**唯一入口**(2026-09-14 用户裁决,必须遵守)。
 * (2026-09-22 自主模组 {@code combat.HostileTargets} 下沉;口径逐字未变。)
 *
 * <p><b>口径(2026-09-27 用户裁决扩展)</b>:
 * {@code 敌对目标 = 敌对生物 ∪ 中立生物(宠物除外) ∪ 会被激怒的可驯服动物(宠物除外) ∪ 消费方额外声明的实体}
 * <ul>
 *   <li><b>敌对生物</b>:{@link Enemy} 实例 —— 含 {@code Monster} 全部子类,以及
 *       {@code Ghast}/{@code Phantom}/{@code EnderDragon}/{@code Slime}(含岩浆怪)/
 *       {@code Shulker}/{@code Zoglin}/{@code Hoglin}。**平静的敌对类中立生物
 *       (末影人/僵尸猪灵/猪灵/猪灵蛮兵/疣猪兽)同样计入** —— 它们本身就是 {@link Enemy}。</li>
 *   <li><b>中立生物(宠物除外)</b>:{@link NeutralMob} 实例,且**不是已被驯服的宠物**
 *       (见 {@link #isTamedPet})。全原版 {@code NeutralMob} 直接实现者仅 6 个:
 *       {@code EnderMan}/{@code ZombifiedPiglin}(二者即 {@link Enemy},由上一条覆盖)
 *       与 <b>狼 / 铁傀儡 / 北极熊 / 蜜蜂</b>。
 *       ⚠️ <b>与旧口径的唯一差别</b>:旧版要求中立生物**已被激怒**({@code isAngry()})才计入,
 *       自本条起**一律计入** —— 于是「未被激怒的狼/铁傀儡/北极熊/蜜蜂」现在也算敌对目标。</li>
 *   <li><b>会被激怒的可驯服动物(宠物除外)</b>(2026-09-27 新增):见下方
 *       {@link #isAngerableTamedMount} —— 覆盖「可驯服、会被激怒,却**没有**实现
 *       {@link NeutralMob}」的动物(原版唯一实例 = **羊驼 / 行商羊驼**)。</li>
 *   <li><b>消费方额外声明的实体</b>:见下方「额外敌对判定 seam」。</li>
 * </ul>
 *
 * <p><b>「宠物除外」的判据与其范围</b>:宠物 = <b>已被驯服的 {@link TamableAnimal}</b>
 * (狼 / 猫 / 鹦鹉)。之所以只用这一个判据:① 它在三平台(1.20.1 / 1.21.1 / 26.1.2)签名一致,
 * 而本类的宿主是三平台**共用**的 common 源码;
 * ② 语义上也无遗漏 —— 另一个「有主人」的家族 {@code AbstractHorse}(马/驴/骡/骆驼/羊驼,
 * 旧版经 {@code OwnableEntity#getOwnerUUID} 判定)**本身不是 {@code NeutralMob}**,
 * 本来就不在第 2 条的集合里、也无从被排除。
 * (注:{@code OwnableEntity} 在 26.1.2 已改为 {@code EntityReference} 体系、不再有
 * {@code getOwnerUUID()},进一步说明不宜跨平台使用它。)
 * <p>实测影响面:唯一「既是 {@code NeutralMob} 又是 {@code TamableAnimal}」的原版生物就是**狼**,
 * 故第 2 条实际等价于「狼在**未驯服**时算敌对目标」。
 *
 * <p>⚠️ 但**已在驯服的羊驼**(第 3 条的动物)由 {@link #isAngerableTamedMount} 内独立排除
 * (它走 {@link OwnableEntity#getOwner()} 判据,与本条的 {@code TamableAnimal#isTame()} 是两套)，
 * 故「已驯服 ⇒ 不是敌对目标」这条不变式在两条通道上都成立。
 *
 * <p><b>马科(含羊驼)为何长期缺席,以及 2026-09-27 的补法</b>:羊驼 / 行商羊驼**会被激怒**
 * (有 {@code HurtByTargetGoal},被打后吐口水还击)、也**可驯服**,但原版**没有**把它们标成
 * {@link NeutralMob},故既不落入上一条、也不被 {@link Enemy} 覆盖 ⇒ 在选择器里一直不可选。
 * 补法见 {@link #isAngerableTamedMount}:不能用类名/包名判定(26.1.2 把
 * {@code animal/horse/**} 整体改名到 {@code animal/equine/**}、且把 {@code wolf}/{@code panda}
 * 等拆进子包 ⇒ 任何硬编码类路径都会让 common 源码编译不过),故改用**行为/接口**判据。
 * <p>⚠️ 同样**不得**用 {@code Mob#isAggressive()} 来代替本条 —— 该方法的语义按生物而异:
 * {@code Panda} 覆写它表示「**攻击型基因**(永久性格)」而非「当前正在发怒」,而
 * {@code Llama} 根本不覆写(默认读 synched flag、恒为 false)⇒ 用它既漏羊驼又会把
 * 「攻击型熊猫」在任何时候都误判为敌对。
 * <p>(熊猫/骆驼/山羊/海豚/狐狸等仍**不在**本口径内 —— 它们既不是 {@link NeutralMob},
 * 也不满足 {@link #isAngerableTamedMount} 的「可驯服」前提;若将来需要把它们也算作敌对目标,
 * 须另行裁决并在此扩展。)
 *
 * <p><b>禁止</b>在玩法代码里再写裸的 {@code instanceof Enemy} 来判定敌对目标 —— 那会漏掉
 * 中立生物。新增判定一律调用本类。
 *
 * <p><b>敌对玩家(用户裁决,全局规则)</b>:带「视谁为敌」上下文的
 * {@link #isHostile(Entity, Entity)} 额外把「非同队伍、且曾主动攻击过观察者的玩家」计入敌对目标
 * (记录见 {@link PlayerHostilityTracker});能提供上下文的调用点都应使用两参重载。
 * 无上下文的重载 {@link #isHostile(Entity)} 保持既有语义(玩家不计入)——
 * "曾主动攻击过谁"必须由上下文决定,不能由被判定实体自身推出。
 *
 * <h2>额外敌对判定 seam(2026-09-24 新增)</h2>
 * <b>为什么需要它</b>:本类不仅被消费方的玩法代码调用,还被库内的
 * {@link com.merlinkitsune.starenginelib.target.SelectorTargets} 用于「可选中目标」判定
 * (客户端射线 / 客户端半径高亮 / 服务端确认三处共用)。而「某个第三方实体应当算敌对」属于
 * **消费方玩法口径**(库不该知道任何具体模组的实体),判定点却有一部分落在库内 ——
 * 消费方无处插手。故库内只留判定接口,由消费方在启动时把实现注入进来(与
 * {@link InternalDamageWindows} 同构,同一套「seam + 消费方 static 注入」范式)。
 *
 * <p><b>未注入时的默认语义</b>:不额外认定任何实体为敌对。这是安全方向 ——
 * 漏注入只会退回原有行为,不会凭空把中立生物变成敌对目标。
 *
 * <h2>⚠️ 兼容性:本轮口径重写不属于「新增」</h2>
 * 库自 {@code 1.0.0} 起的 1.x 契约只允许「新增」与「不改变既有语义的行为修正」,而本条**改变了
 * {@code isHostile} 的既有语义**(中立生物不再需要被激怒)。⇒ 按契约,这部分改动**正式发版时
 * 必须升主版本**(1.x → 2.x)并同批收紧消费方 {@code starengine_lib_version_range} 的下界。
 * (2026-09-24 用户裁决:本批**先只改代码、不 bump 不发布**,与后续改动一起发版。)
 */
public final class HostileTargets {
    private HostileTargets() {
    }

    /** 额外敌对判定:消费方实现的「某实体应当视为敌对目标」判定体。 */
    @FunctionalInterface
    public interface ExtraHostileProbe {
        /**
         * @param target 待判定实体(可能为 {@code null};实现方应容忍并返回 {@code false})
         * @return 该实体是否应当被额外视为敌对目标
         */
        boolean isHostile(Entity target);
    }

    /** 未注入时的默认语义:不额外认定任何实体为敌对。 */
    private static volatile ExtraHostileProbe extraHostileProbe = target -> false;

    /**
     * 注入「额外敌对判定」(消费方启动时调用一次)。
     *
     * <p>传 {@code null} 等价于恢复默认(不额外认定任何实体)。重复调用以最后一次为准。
     */
    public static void installExtraHostileProbe(ExtraHostileProbe probe) {
        extraHostileProbe = (probe != null) ? probe : target -> false;
    }

    /**
     * 是否为「已被驯服的宠物」—— 本口径下**唯一**会被排除出敌对集合的中立生物类别。
     *
     * <p>只用 {@link TamableAnimal#isTame()}:该判据在三平台签名一致(common 源码的硬约束);详见类注释。
     */
    private static boolean isTamedPet(Entity entity) {
        return entity instanceof TamableAnimal tamable && tamable.isTame();
    }

    /**
     * 「会被激怒的可驯服动物」判定(2026-09-27 新增;覆盖原版**羊驼 / 行商羊驼**)。
     *
     * <p><b>为什么不用类名判据</b>:26.1.2 把 {@code animal/horse/**} 整体改名到
     * {@code animal/equine/**},并把 {@code wolf}/{@code panda}/{@code bee} 等拆进各自子包 ⇒
     * 任何硬编码类路径(或 {@code Class.forName})都会让三平台共用的本 common 源码编译/运行不一致。
     * 故本判定只用三平台签名一致的**接口 + 行为**。
     *
     * <p><b>判据(三个条件同时成立,全部跨平台安全)</b>:
     * <ol>
     *   <li><b>可驯服(尚未被驯服)</b> —— {@link OwnableEntity#getOwner()} 为 {@code null},
     *       且不是已驯服的 {@link TamableAnimal}。{@code getOwner()} 是三平台一致的
     *       {@code default} 方法(⚠️ 另一条更"直白"的 {@code OwnableEntity#getOwnerUUID()} 在
     *       26.1.2 已被删除,故**不得**使用)。
     *       ⚠️ 反过来也成立:已驯服的羊驼 {@code getOwner() != null} ⇒ 归为「宠物」,不计入(符合
     *       「未驯服」的字面要求)。</li>
     *   <li><b>会被激怒</b> —— 该生物是 {@link Mob},且**当前持有目标**
     *       ({@link Mob#getTarget()} 非空:羊驼的 {@code LlamaHurtByTargetGoal} 在被打后即设目标)
     *       **或**近期被攻击过({@link LivingEntity#getLastHurtByMob()} 非空)。
     *       ⚠️ 这两条在跨平台语义一致,且恰好命中「会被激怒」的原意。</li>
     *   <li><b>不是消费方声明为"宠物除外"以外的普通动物</b> —— 由调用点保证:本方法**只**作为
     *       {@link #isHostile(Entity)} 的**附加**通道(在原两条之后),不改变它们的语义。</li>
     * </ol>
     *
     * <p>⚠️ <b>为什么不用 {@link Mob#isAggressive()}</b>:其语义按生物而异 ——
     * {@code Panda} 覆写它表示「**攻击型基因**(永久性格)」而非「当前正在发怒」,而 {@code Llama}
     * 不覆写(默认读 synched flag、恒 false)。用它既漏羊驼、又会把「攻击型熊猫」在任何时候误判为敌对。
     *
     * <p>⚠️ {@code Mob#getTarget()} 在 26.1.2 加了 {@code getTargetUnchecked()} 与
     * {@code asValidTarget(...)} 两个兄弟方法,但**原方法名与签名未变**,故此处可安全跨平台调用。
     */
    private static boolean isAngerableTamedMount(Entity entity) {
        if (!(entity instanceof Mob mob)) return false;
        // 可驯服尚未被驯服:有"主人"概念但当前无主(且未通过 TamableAnimal 被驯服)。
        if (!(entity instanceof OwnableEntity ownable) || ownable.getOwner() != null) return false;
        if (isTamedPet(entity)) return false;
        // 会被激怒:持有目标,或近期被攻击过(羊驼被打后 LlamaHurtByTargetGoal 立即设目标)。
        return mob.getTarget() != null || mob.getLastHurtByMob() != null;
    }

    /** 该实体是否为「敌对目标」(敌对生物,或中立生物且非已驯服宠物,或会被激怒的可驯服动物,或消费方额外声明的实体)。 */
    public static boolean isHostile(Entity entity) {
        if (entity == null) return false;
        if (entity instanceof Enemy) return true;
        // 中立生物:一律计入(不再要求已被激怒),但排除已被驯服的宠物。
        if (entity instanceof NeutralMob && !isTamedPet(entity)) return true;
        // 会被激怒的可驯服动物(原版唯一实例 = 羊驼 / 行商羊驼,2026-09-27 用户裁决):这类生物
        // 可驯服、被打后会还击,却没有实现 NeutralMob ⇒ 既不落入上一条、也不被 Enemy 覆盖。
        if (isAngerableTamedMount(entity)) return true;
        // 以上是原版口径的早退路径(覆盖绝大多数目标);仅未命中者才落到消费方注入的判定。
        return extraHostileProbe.isHostile(entity);
    }

    /**
     * 该实体是否为**原版标志的敌对生物**（vanilla {@link Enemy} 标记接口）—— **生物类别口径**。
     *
     * <p>⚠️ 本库有**两套并列口径，不得互相替代**：
     * <ul>
     *   <li>{@link #isHostile(Entity)} = **战斗口径** —— 敌对生物 ∪ 中立生物（宠物除外）∪
     *       会被激怒的可驯服动物 ∪ 消费方额外声明的实体；供**伤害 / 攻击 / 立牌选敌**使用
     *       （未驯服的狼 / 铁傀儡 / 北极熊 / 蜜蜂在此**算敌对**）；</li>
     *   <li>{@code isHostileMob} = **生物类别口径** —— 只看原版 {@link Enemy} 标志（僵尸 / 骷髅 /
     *       掠夺者 / 末影人 / 猪灵 / 恶魂 / 潜影贝 …），**不含**上述中立生物；供「**治疗 / 增益类
     *       效果牌可否对目标施放**」使用（2026-10-03 用户裁决）。</li>
     * </ul>
     *
     * <p>本方法是「原版敌对标志」的**唯一入口** —— 玩法代码**不得**再写裸的
     * {@code instanceof Enemy}（与 {@link #isHostile} 同一纪律，见类注释末尾）。
     */
    public static boolean isHostileMob(Entity entity) {
        return entity instanceof Enemy;
    }

    /**
     * 带「视谁为敌」上下文的敌对判定(全局规则,见类注释):
     * {@code 敌对目标 = 敌对生物 ∪ 中立生物(宠物除外) ∪ 消费方额外声明的实体
     * ∪ 「非同队伍,且曾主动攻击过 viewer 的玩家」}。
     *
     * <p>{@code viewer} 是本次判定的观察者(如溅射施放者、被攻击方);viewer 不是玩家时无法确定
     * "曾主动攻击过谁",玩家一律不计入敌对。
     */
    public static boolean isHostile(Entity viewer, Entity target) {
        if (target == null) return false;
        // 原有语义:敌对生物 ∪ 中立生物(宠物除外) ∪ 消费方额外声明的实体
        if (isHostile(target)) return true;
        if (!(viewer instanceof Player viewerPlayer)) return false;
        if (!(target instanceof Player targetPlayer)) return false;
        if (viewerPlayer == targetPlayer) return false;
        // 同队豁免("非同队伍"才可能为敌对):任何一方未加入队伍时不算同队。
        // 注意:EventTargetCollector「未加入队伍视为全服玩家」的既有约定只适用于发奖,不适用于此处。
        if (viewerPlayer.getTeam() != null && viewerPlayer.isAlliedTo(targetPlayer)) return false;
        return PlayerHostilityTracker.hasAttacked(viewerPlayer, targetPlayer);
    }
}
