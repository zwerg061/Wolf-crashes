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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guard for MrCrayfish's Gun Mod workbench screen ({@code cgm:workbench}).
 *
 * <p>Decompiled fields of {@code WorkbenchScreen} (cgm-forge-1.19.2-1.3.7.jar):</p>
 * <ul>
 *   <li>{@code tabs} - {@code List<WorkbenchScreen$Tab>}</li>
 *   <li>{@code WorkbenchScreen$Tab.items} - {@code List<WorkbenchRecipe>}</li>
 *   <li>{@code displayStack} - the source of the local {@code currentItem} in renderBg</li>
 * </ul>
 *
 * <p>There is no {@code filteredRecipes} and no {@code currentItem} field in this CGM
 * version. Modpacks with removed/changed recipes leave recipes whose {@code getItem()}
 * is {@code null}; {@code loadItem(int)} then copies null into {@code displayStack} and
 * the renderer calls {@code ItemStack#getHoverName()} (m_41786_) on the resulting local
 * variable {@code currentItem}.</p>
 *
 * <p>Since 1.3.1 the screen is <em>kept open</em>: broken recipes are pruned from the
 * tab lists, {@code displayStack} is replaced with the first valid recipe item (or
 * {@link ItemStack#EMPTY}), and the null access is additionally guarded by redirects.
 * {@code setScreen(null)} is now a last resort that only runs when an unexpected
 * exception occurs while applying the repair.</p>
 */
@Pseudo
@Mixin(targets = "com.mrcrayfish.guns.client.screen.WorkbenchScreen", remap = false)
public final class CgmWorkbenchScreenMixin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MESSAGE =
            "[CreachReportFix] Fehlerhaftes Werkbank-Rezept im Modpack abgefangen – Crash verhindert.";
    private static final Object UNKNOWN = new Object();
    private static final Map<String, Optional<Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    @Unique
    private boolean creachfix$pruned;

    @Unique
    private boolean creachfix$warned;

    /** Screen has built its widgets and called loadItem - repair before the first draw. */
    @Inject(method = {"m_7856_", "init"}, at = @At("TAIL"), require = 0, remap = false)
    private void creachfix$afterInit(CallbackInfo ci) {
        creachfix$guard();
    }

    /** Screen.render - repair at the very beginning of every frame. */
    @Inject(method = {"m_6305_", "render"}, at = @At("HEAD"), require = 0, remap = false)
    private void creachfix$beforeRender(CallbackInfo ci) {
        creachfix$guard();
    }

    /** AbstractContainerScreen.renderBg - the frame from the original crash report. */
    @Inject(method = {"m_7286_", "renderBg"}, at = @At("HEAD"), require = 0, remap = false)
    private void creachfix$beforeRenderBg(CallbackInfo ci) {
        creachfix$guard();
    }

    /**
     * CGM's own {@code loadItem(int)} is the place where {@code displayStack} is set from
     * a recipe. A broken recipe (or an out-of-range index after pruning) is replaced here
     * instead of letting the original code throw.
     */
    @Inject(method = "loadItem", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void creachfix$beforeLoadItem(int index, CallbackInfo ci) {
        try {
            creachfix$pruneRecipeLists();

            Object tab = creachfix$readField(this, "currentTab");
            Object rawItems = tab == null ? null : creachfix$readField(tab, "items");
            if (!(rawItems instanceof List)) {
                return;
            }
            List<?> items = (List<?>) rawItems;

            boolean broken = index < 0 || index >= items.size();
            if (!broken) {
                broken = creachfix$inspectItem(items.get(index)) == null;
            }
            if (!broken) {
                return;
            }

            ItemStack replacement = creachfix$firstValidRecipeItem();
            creachfix$writeField(this, "displayStack",
                    replacement == null ? ItemStack.EMPTY : replacement);
            if (tab != null) {
                creachfix$setCurrentIndex(tab, 0);
            }
            creachfix$warn();
            ci.cancel();
        } catch (Throwable throwable) {
            creachfix$lastResort(throwable, "loadItem der Werkbank-GUI");
        }
    }

    /** Turns the local {@code currentItem} into ItemStack.EMPTY right at the read. */
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
        Object value = creachfix$readField(screen, "displayStack");
        return value instanceof ItemStack ? (ItemStack) value : ItemStack.EMPTY;
    }

    /** Null-safe {@code currentItem.m_41786_()} - never closes the screen. */
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
        try {
            return stack == null ? Component.empty() : stack.getHoverName();
        } catch (Throwable throwable) {
            return Component.empty();
        }
    }

    /** Prune defective recipes once per screen instance; keep the GUI usable. */
    private void creachfix$guard() {
        try {
            creachfix$pruneRecipeLists();
            if (creachfix$readField(this, "displayStack") == null) {
                ItemStack replacement = creachfix$firstValidRecipeItem();
                creachfix$writeField(this, "displayStack",
                        replacement == null ? ItemStack.EMPTY : replacement);
                creachfix$warn();
                LOGGER.warn("[creachreportfix] WorkbenchScreen.displayStack war null -> "
                        + "gueltiges Rezept-Item/ItemStack.EMPTY gesetzt, GUI bleibt offen.");
            }
        } catch (Throwable throwable) {
            creachfix$lastResort(throwable, "Reparatur der Werkbank-GUI");
        }
    }

    private void creachfix$pruneRecipeLists() {
        if (this.creachfix$pruned) {
            return;
        }
        this.creachfix$pruned = true;

        Object rawTabs = creachfix$readField(this, "tabs");
        if (!(rawTabs instanceof List)) {
            return;
        }
        List<?> tabs = (List<?>) rawTabs;
        Object currentTab = creachfix$readField(this, "currentTab");

        for (Object tab : new ArrayList<>(tabs)) {
            Object rawItems = creachfix$readField(tab, "items");
            if (!(rawItems instanceof List)) {
                continue;
            }
            List<?> items = (List<?>) rawItems;
            try {
                items.removeIf(recipe -> creachfix$inspectItem(recipe) == null);
            } catch (UnsupportedOperationException ex) {
                LOGGER.debug("[creachreportfix] CGM-Rezeptliste ist unveraenderlich", ex);
            }
            //Tabs ohne jedes gueltige Rezept entfernen - aber niemals den aktiven Tab.
            if (items.isEmpty() && tab != currentTab) {
                tabs.remove(tab);
            }
        }
    }

    private ItemStack creachfix$firstValidRecipeItem() {
        ItemStack fallback = null;
        Object rawTabs = creachfix$readField(this, "tabs");
        if (!(rawTabs instanceof List)) {
            return null;
        }
        for (Object tab : (List<?>) rawTabs) {
            Object rawItems = creachfix$readField(tab, "items");
            if (!(rawItems instanceof List)) {
                continue;
            }
            for (Object recipe : (List<?>) rawItems) {
                Object inspected = creachfix$inspectItem(recipe);
                if (inspected instanceof ItemStack) {
                    ItemStack stack = (ItemStack) inspected;
                    if (!stack.isEmpty()) {
                        return stack;
                    }
                    if (fallback == null) {
                        fallback = stack;
                    }
                }
            }
        }
        return fallback;
    }

    /**
     * @return the recipe's ItemStack, {@code null} for a defective recipe and
     *         {@link #UNKNOWN} when the recipe could not be inspected at all.
     */
    private static Object creachfix$inspectItem(Object recipe) {
        if (recipe == null) {
            return null;
        }
        try {
            Method method = recipe.getClass().getMethod("getItem");
            Object value = method.invoke(recipe);
            return value instanceof ItemStack ? value : null;
        } catch (Throwable throwable) {
            return UNKNOWN;
        }
    }

    private static void creachfix$setCurrentIndex(Object tab, int index) {
        try {
            Method method = tab.getClass().getMethod("setCurrentIndex", int.class);
            method.setAccessible(true);
            method.invoke(tab, index);
        } catch (Throwable ignored) {
            // The guard already supplied a valid displayStack; an unusable tab
            // is not worth crashing the whole screen.
        }
    }

    private static Object creachfix$readField(Object instance, String name) {
        Field field = creachfix$findField(instance, name);
        if (field == null) {
            return null;
        }
        try {
            return field.get(instance);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static void creachfix$writeField(Object instance, String name, Object value) {
        Field field = creachfix$findField(instance, name);
        if (field == null) {
            throw new IllegalStateException("Feld " + name + " nicht gefunden");
        }
        try {
            field.set(instance, value);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Feld " + name + " nicht setzbar", ex);
        }
    }

    /**
     * Field lookup on the <em>actual runtime class</em> of the screen. Using
     * Class.forName with our own classloader caused the 1.3.0 fallback to fail and
     * close the GUI; instance.getClass() always resolves the real CGM class.
     */
    private static Field creachfix$findField(Object instance, String name) {
        if (instance == null) {
            return null;
        }
        Class<?> type = instance.getClass();
        return FIELD_CACHE.computeIfAbsent(type.getName() + '#' + name, key -> {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return Optional.of(field);
                } catch (NoSuchFieldException ignored) {
                    // Keep walking up the hierarchy.
                } catch (RuntimeException ex) {
                    return Optional.empty();
                }
            }
            return Optional.empty();
        }).orElse(null);
    }

    /** Only used when an unexpected exception defeats all repairs. */
    private void creachfix$lastResort(Throwable throwable, String action) {
        LOGGER.warn("[creachreportfix] Unerwartete Exception bei {} - Notanker schliesst den Screen.",
                action, throwable);
        try {
            Minecraft.getInstance().setScreen(null);
            creachfix$warn();
        } catch (Throwable ignored) {
            // Nothing left to do.
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
