package com.merlinkitsune.starenginelib.platform;

/**
 * 加载器事件基类 shim(Fabric 1.20.1)。
 *
 * <p>共享源码里的事件类不能直接 {@code extends Event} —— NeoForge 是
 * {@code net.neoforged.bus.api.Event},Forge 是 {@code net.minecraftforge.eventbus.api.Event},
 * 两者包名不同;Fabric **完全没有**加载器级事件基类(只有 FAPI 的回调注册)。
 *
 * <p>因此本平台把它做成**空抽象类**:共享源码的 {@code SignActiveTriggeredEvent} 只继承它,
 * 事件的派发由消费方自己直接调用订阅点(见消费方 {@code event/AstralEventSystem} 与立牌实现)。
 *
 * <p>声明为 abstract 是刻意的:无论平台侧 Event 是抽象类还是具体类,继承都合法。
 */
public abstract class LoaderEvent {
}
