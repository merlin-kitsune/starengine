package com.merlinkitsune.starengine.economy;

import net.minecraft.commands.CommandSourceStack;

/**
 * 命令权限判定的**平台 seam**。
 *
 * <p>为什么需要它:{@code CommandSourceStack} 的权限判定方法在三个 MC 版本上**签名不一致**
 * （实测 26.1.2 侧对 {@code hasPermission(int)} 编译失败,而 1.20.1 / 1.21.1 一致),
 * 因此共享源码不能直接写权限谓词,改为由平台侧传入。
 */
@FunctionalInterface
public interface CommandPermissionGate {

    /** 判定该命令源是否具备给定权限等级。 */
    boolean test(CommandSourceStack source, int level);
}
