package com.merlinkitsune.starengine.platform;

/**
 * 加载器事件基类 shim(NeoForge 1.21.1)。
 *
 * <p>共享源码里的事件类不能直接 {@code extends Event}——NeoForge 是
 * {@code net.neoforged.bus.api.Event},Forge 是 {@code net.minecraftforge.eventbus.api.Event},
 * 两者包名不同。共享源码统一 {@code extends LoaderEvent},由本平台类接上真正的事件基类。
 *
 * <p>声明为 abstract 是刻意的:无论平台侧 Event 是抽象类还是具体类,继承都合法。
 */
public abstract class LoaderEvent extends net.neoforged.bus.api.Event {
}
