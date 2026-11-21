package de.wolfmod.creachreportfix.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * Guard for MrCrayfish's Gun Mod workbench screen ({@code cgm:workbench}).
 *
 * <p>Modpacks with changed/removed recipes (or CGM addons) can leave the screen's
 * {@code displayStack} field unset. The renderer copies it into the local variable
 * {@code currentItem} and then calls {@code ItemStack#getHoverName()} on it, which
 * produces the reported NullPointerException:</p>
 *
 * <pre>
 * java.lang.NullPointerException: Cannot invoke "net.minecraft.world.item.ItemStack.m_41786_()"
 *     because "currentItem" is null
 *   at com.mrcrayfish.guns.client.screen.WorkbenchScreen.m_7286_(WorkbenchScreen.java:407)
 * </pre>
 *
 * <p>The guard repairs the field with {@link ItemStack#EMPTY} before/during rendering.
 * If that is not possible, the screen is closed safely and a short chat message is shown.
 * The two redirects make the null access itself safe as a last line of defence.</p>
 */
@Pseudo
@Mixin(targets = "com.mrcrayfish.guns.client.screen.WorkbenchScreen", remap = false)
public final class CgmWorkbenchScreenMixin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TARGET_CLASS = "com.mrcrayfish.guns.client.screen.WorkbenchScreen";
    private static final String DISPLAY_STACK = "displayStack";
    private static final String MESSAGE =
            "[CreachReportFix] Fehlerhaftes Werkbank-Rezept im Modpack abgefangen – Crash verhindert.";

    private static volatile Field cachedField;
    private static volatile boolean fieldLookedUp;

    @Unique
    private boolean creachfix$warned;

    /** Repair right after the screen has built its widgets. */
    @Inject(method = {"m_7856_", "init"}, at = @At("TAIL"), require = 0, remap = false)
    private void creachfix$afterInit(CallbackInfo ci) {
        creachfix$repair();
    }

    /** {@code Screen.render} - check before the whole screen is drawn. */
    @Inject(method = {"m_6305_", "render"}, at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void creachfix$beforeRender(CallbackInfo ci) {
        if (!creachfix$repair()) {
            ci.cancel();
        }
    }

    /** {@code AbstractContainerScreen.renderBg} - the frame from the crash report. */
    @Inject(method = {"m_7286_", "renderBg"}, at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void creachfix$beforeRenderBg(CallbackInfo ci) {
        if (!creachfix$repair()) {
            ci.cancel();
        }
    }

    /** Turns the local {@code currentItem} into ItemStack.EMPTY at the read itself. */
    @Redirect(
            method = {"m_7286_", "renderBg"},
            at = @At(
                    value = "FIELD",
                    opcode = Opcodes.GETFIELD,
                    target = "Lcom/mrcrayfish/guns/client/screen/WorkbenchScreen;displayStack:Lnet/minecraft/world/item/ItemStack;"
            ),
            require = 0,
            remap = false
    )
    private ItemStack creachfix$safeCurrentItem(Object screen) {
        ItemStack stack = creachfix$readDisplayStack(screen);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    /** Last resort: a null stack must never escape into ItemStack#getHoverName(). */
    @Redirect(
            method = {"m_7286_", "renderBg"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;m_41786_()Lnet/minecraft/network/chat/Component;"
            ),
            require = 0,
            remap = false
    )
    private Component creachfix$safeHoverName(ItemStack stack) {
        return stack == null ? Component.empty() : stack.getHoverName();
    }

    /**
     * @return {@code true} when rendering may continue, {@code false} when the
     *         screen was closed because the field could neither be read nor repaired.
     */
    private boolean creachfix$repair() {
        Field field = creachfix$findDisplayStack();
        if (field == null) {
            // Field renamed by another CGM version - the redirects still keep the
            // actual null access safe, so let the original code run.
            return true;
        }

        ItemStack currentItem;
        try {
            currentItem = (ItemStack) field.get(this);
        } catch (Throwable throwable) {
            LOGGER.debug("[creachreportfix] Could not read WorkbenchScreen.displayStack", throwable);
            return true;
        }
        if (currentItem != null) {
            return true;
        }

        try {
            field.set(this, ItemStack.EMPTY);
            creachfix$warn();
            LOGGER.warn("[creachreportfix] WorkbenchScreen.displayStack war null -> ItemStack.EMPTY eingesetzt.");
            return true;
        } catch (Throwable throwable) {
            LOGGER.debug("[creachreportfix] Could not repair WorkbenchScreen.displayStack", throwable);
        }

        try {
            Minecraft.getInstance().setScreen(null);
            creachfix$warn();
            LOGGER.warn("[creachreportfix] Werkbank-Screen wegen fehlerhaftem Rezept geschlossen.");
        } catch (Throwable throwable) {
            LOGGER.warn("[creachreportfix] Konnte den Werkbank-Screen nicht schließen", throwable);
        }
        return false;
    }

    private static ItemStack creachfix$readDisplayStack(Object screen) {
        Field field = creachfix$findDisplayStack();
        if (field == null || screen == null) {
            return null;
        }
        try {
            return (ItemStack) field.get(screen);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static Field creachfix$findDisplayStack() {
        if (fieldLookedUp) {
            return cachedField;
        }
        synchronized (CgmWorkbenchScreenMixin.class) {
            if (fieldLookedUp) {
                return cachedField;
            }
            Field found = null;
            try {
                Class<?> type = Class.forName(TARGET_CLASS, false, CgmWorkbenchScreenMixin.class.getClassLoader());
                for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                    try {
                        found = current.getDeclaredField(DISPLAY_STACK);
                        found.setAccessible(true);
                        break;
                    } catch (NoSuchFieldException ignored) {
                        // Keep walking up the hierarchy.
                    }
                }
            } catch (Throwable throwable) {
                LOGGER.debug("[creachreportfix] WorkbenchScreen not available for field lookup", throwable);
            }
            cachedField = found;
            fieldLookedUp = true;
            return found;
        }
    }

    @Unique
    private void creachfix$warn() {
        if (this.creachfix$warned) {
            return;
        }
        this.creachfix$warned = true;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal(MESSAGE));
            }
        } catch (Throwable ignored) {
            // A chat hint must never become a new crash.
        }
    }
}
