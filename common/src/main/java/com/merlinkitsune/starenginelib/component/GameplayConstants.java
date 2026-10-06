package com.merlinkitsune.starenginelib.component;


/**
 * 全局玩法常量。
 * 「仍从配置文件读取」的少量字段(赠送规则书 / 事件作用队伍 / actionbar 时长)必须保持**非 final**:
 * 它们由消费方在配置加载完成后推入的值快照写入({@link #applyConfig(GameplayConfigValues)}),
 * 改成 final 会让编译期内联导致配置不生效。
 * 其余字段为固定常量(不再写入配置文件);其中一部分历史上按 `static`(无 final)书写并沿用至今
 * (如 `SIGN_ACTIVE_COOLDOWN_SECONDS`/`SKILL_WAIT_SECONDS`/`CURSED_SWORD_BONUS_MAX` 等),它们只在
 * {@link #applyConfig(GameplayConfigValues)} 中派生 tick 值、不读取配置,需要时可逐步改为 `final`。
 *
 * <p>本类**不依赖任何加载器的配置 API**:配置文件与配置读写留在消费方 mod,库只声明
 * 「哪些玩法数值可配置」(见 {@link GameplayConfigValues})以及如何应用。
 */
public final class GameplayConstants {
    // 星光点获取上限(常量:32)
    public static final int MAX_STARLIGHT = 32;
    // 标记层数上限(常量:32)
    public static final int MAX_MARKER = 32;
    // 效果牌公共冷却(单位:秒,常量:30)
    public static final int EFFECT_CARD_COOLDOWN_SECONDS = 30;
    // 功能效果牌叠加层数上限(常量:3 层,伤害效果牌不使用该叠加)
    public static final int MAX_EFFECT_STACKS = 3;
    // 是否在玩家第一次加入世界时给予《恋的规则书》(默认 true)
    public static boolean GIVE_GUIDE_BOOK_ON_FIRST_JOIN = true;

    // === 效果牌出牌数 ===
    // 效果牌单轮(单出牌周期)出牌数上限:固定常量 9,**不写入配置文件**。
    // 实际上限 = min(基础 1 + 固定来源 + 临时来源, MAX_EFFECT_CARD_PLAYS)
    public static final int MAX_EFFECT_CARD_PLAYS = 9;

    // === 充能流派 ===
    // 充能最大层数(写入常量,暂不提供配置文件)
    public static final int CHARGE_MAX_STACKS = 20;
    // 拥有至少 1 层充能时,立牌主动技能冷却的**上限**(单位:秒,2026-09-25 改口径):
    // 基础值超过该上限时封顶为 160 秒;基础值本就更低(如枪匠「精密技巧」的 120 秒)则不受影响。
    // 该上限作用于**基础值**,之后再叠加其它减免(诡异骰子 -50% 等)。
    // 取代旧的按比例减免常量 `CHARGE_COOLDOWN_REDUCTION`(0.2,已删除)。
    public static final int CHARGE_SIGN_COOLDOWN_CAP_SECONDS = 160;
    // 拥有至少 1 层充能时,效果牌公共冷却的**上限**(单位:秒,2026-09-25 改口径):
    // 基础值 30 秒 ⇒ 有充能时 20 秒;同上,作用于基础值。
    public static final int CHARGE_EFFECT_CARD_COOLDOWN_CAP_SECONDS = 20;
    // === 事件系统 ===
    // 注:EVENT_RANGE / EVENT_APPLY_MAID 与其唯一使用方 EventTargetCollector.collectTargets + 女仆收集
    //     已于 1.0.0-SNAPSHOT.5 按期删除(合并后消费方三线零引用)。
    // 事件是否作用于 Minecraft 同队玩家
    public static boolean EVENT_APPLY_MC_TEAM = true;
    // 事件是否作用于 FTB Teams 队友(需安装 FTB Teams,API 不符时自动跳过)
    public static boolean EVENT_APPLY_FTB_TEAM = true;
    // 事件是否作用于 OPAC 队伍(需安装 Open Parties and Claims,API 不符时自动跳过)
    public static boolean EVENT_APPLY_OPAC = true;
    /** 是否允许「枪弹/炮弹类」伤害计入法伤（默认 false = 默认屏蔽枪弹伤害，与既有行为一致）。由消费方公共配置 allow_firearm_damage 驱动。 */
    public static boolean ALLOW_FIREARM_DAMAGE = false;
    // 立牌主动技能触发冷却(单位:秒,默认 180)
    public static int SIGN_ACTIVE_COOLDOWN_SECONDS = 180;
    // 立牌主动技能触发冷却 tick 数(派生值)
    public static int SIGN_ACTIVE_COOLDOWN_TICKS = SIGN_ACTIVE_COOLDOWN_SECONDS * 20;
    // 立牌主动技能等待期(固定常量 30 秒):需要选择目标/等待释放的立牌(占星师、秘密侦探、枪匠)统一使用
    public static int SKILL_WAIT_SECONDS = 30;
    // 扫地机立牌被动:生命值上限/护甲增益各自的最大上限(点)
    public static int JASMINE_MAX_BONUS = 20;
    // 史莱姆立牌主动技能作用范围(格,默认 16)
    public static int LULU_ACTIVE_RANGE = 16;
    // 上班族立牌被动攻防数值刷新间隔(秒,默认 60)
    public static int PADMAN_REFRESH_SECONDS = 60;
    // 经商立牌被动产星光间隔(秒,默认 60)
    public static int PARUNAN_PASSIVE_INTERVAL_SECONDS = 60;
    // 手持风扇-大:主动技能后对周围敌对目标施加标记的范围(格,常量:16)
    public static final int HAND_FAN_BIG_RANGE = 16;
    // 注:忍者立牌的旧「出牌数银行」常量 KOMACHI_EXTRA_PLAYS_CAP 已于 1.0.0-SNAPSHOT.5 按期删除
    //     (主线已把忍者主动改写为「当前出牌轮一次性 +1」并删除出牌银行,合并后消费方三线零引用)。

    // actionbar 消息显示总时长上限(单位: tick,固定 3 秒 = 60 tick;2026-10-03 起不再来自配置文件)
    public static int ACTIONBAR_DURATION_TICKS = 60;
    // actionbar 消息最后淡出时长(单位: tick,默认 0.5 秒 = 10 tick)
    // 2026-10-03 起为固定常量:消费方已把 actionbar 两项移出配置文件(不再开放玩家调整),值由消费方常量直接注入
    public static int ACTIONBAR_FADE_TICKS = 10;

    // 骰神赐福持续时长(单位:秒,默认 60)
    public static int DICE_BLESSING_DURATION_SECONDS = 60;
    // 诅咒之剑:骰神赐福期间每击杀 1 个不少于 20 血的敌对目标攻击力 +1(每个赐福最多一次),最大增加上限。
    // 2026-10-02 用户裁决:上限 **16 → 32**(纯数值调整,不改任何 public 类型/方法/字段的签名、
    //   可见性或语义形状 ⇒ 属 1.x 兼容契约允许的非破坏性修正;消费方读取处无需改代码)。
    public static int CURSED_SWORD_BONUS_MAX = 32;
    // 目标选择器:可指定目标的最大距离(格)。固定常量 16,不写入配置文件(与其它玩法数值一致,服务端确认校验以本值为准)
    public static final int TARGET_SELECT_RADIUS = 16;
    // 骰神赐福持续时长(单位:tick,派生值)
    public static int DICE_BLESSING_DURATION_TICKS = DICE_BLESSING_DURATION_SECONDS * 20;

    // 卡牌槽位基础规则(默认按普通骰子;具体骰子槽位由 DiceTierRegistry 动态提供)
    public static final int CARD_SLOTS_PER_SIDE = 3;
    public static final int CARD_SLOTS_TOTAL = CARD_SLOTS_PER_SIDE * 2;
    public static final int MAX_CARD_COST = 6;

    // 战斗伤害/法伤计算间隔(单位:tick,默认 20t,不写入配置文件)
    public static final int COMBAT_DAMAGE_CALC_INTERVAL_TICKS = 20;
    public static final int SPELL_DAMAGE_CALC_INTERVAL_TICKS = 20;

    // 治愈效果计时器(单位:tick,默认 30 秒,不写入配置文件)
    public static final int HEALING_TIMER_TICKS = 30 * 20;



    // 骰子星级对应的最大费用点数:0星3、1星4、2星5、3星6
    public static int cardCostForStar(int starLevel) {
        return Math.min(MAX_CARD_COST, 3 + Math.max(0, starLevel));
    }
    private GameplayConstants() {
    }

    // 应用配置值快照:仅写入「仍可配置」的少量字段,其余玩法数值固定为上方常量。
    // 调用方是各平台侧的消费方 mod(配置加载/重载后推送),本类不依赖任何加载器的配置 API。
    public static void applyConfig(GameplayConfigValues config) {
        GIVE_GUIDE_BOOK_ON_FIRST_JOIN = config.giveGuideBookOnFirstJoin();

        EVENT_APPLY_MC_TEAM = config.eventApplyMcTeam();
        EVENT_APPLY_FTB_TEAM = config.eventApplyFtbTeam();
        EVENT_APPLY_OPAC = config.eventApplyOpac();

        ALLOW_FIREARM_DAMAGE = config.allowFirearmDamage();

        ACTIONBAR_DURATION_TICKS = config.actionbarDurationTicks();
        ACTIONBAR_FADE_TICKS = config.actionbarFadeTicks();

        // 以下为固定常量对应的派生 tick 值
        SIGN_ACTIVE_COOLDOWN_TICKS = SIGN_ACTIVE_COOLDOWN_SECONDS * 20;
        DICE_BLESSING_DURATION_TICKS = DICE_BLESSING_DURATION_SECONDS * 20;
    }
}
