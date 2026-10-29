/* SPDX-License-Identifier: GPL-3.0-only
 * Source MMCE RecipeThread query contract adapted for Forge 1.20.1, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.machine;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import hellfirepvp.modularmachinery.common.crafting.ActiveMachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import hellfirepvp.modularmachinery.common.tiles.TileFactoryController;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import org.openzen.zencode.java.ZenCodeType;

/** Live logical-thread handle, separate from the FactoryRecipeThread preset builder. */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.RecipeThread")
public final class RecipeThread {
    private final TileController controller;
    private final FactoryRecipeThread factoryThread;
    private final String machineName;

    public RecipeThread(TileController controller, FactoryRecipeThread factoryThread) {
        this.controller = controller;
        this.factoryThread = factoryThread;
        this.machineName = controller == null ? null : controller.getMachineName();
    }

    @ZenCodeType.Getter("valid")
    public boolean isValid() {
        if (controller == null || controller.isRemoved()
                || !java.util.Objects.equals(machineName, controller.getMachineName())) return false;
        if (controller.getLevel() != null && (!controller.getLevel().isLoaded(controller.getBlockPos())
                || controller.getLevel().getBlockEntity(controller.getBlockPos()) != controller)) return false;
        return factoryThread == null || controller instanceof TileFactoryController factory
                && factory.getFactoryRecipeThreadList().contains(factoryThread);
    }

    @ZenCodeType.Getter("threadName")
    public String getThreadName() { return factoryThread == null ? "" : factoryThread.getThreadName(); }

    @ZenCodeType.Getter("isWorking")
    public boolean isWorking() {
        return isValid() && controller.isFormed() && !controller.isRedstoneStopped()
                && (factoryThread == null ? controller.isCrafting() : factoryThread.isWorking());
    }

    @ZenCodeType.Getter("activeRecipe") @ZenCodeType.Nullable
    public ActiveMachineRecipe getActiveRecipe() {
        if (!isValid()) return null;
        if (factoryThread != null) {
            if (!factoryThread.hasActiveRecipe()) return null;
            return new ActiveMachineRecipe(factoryThread.getRecipeName(), factoryThread.getRecipeTicks(),
                    factoryThread.getRecipeDuration(), factoryThread.getParallelism(), factoryThread.getMaxParallelism());
        }
        if (!controller.hasActiveRecipe()) return null;
        return new ActiveMachineRecipe(controller.getRecipeName(), controller.getRecipeTicks(), controller.getRecipeDuration(),
                controller.getParallelism(), controller.getMaxParallelism());
    }

    @ZenCodeType.Getter("context") @ZenCodeType.Nullable
    public RecipeCraftingContext getContext() {
        return !isValid() || !controller.isFormed() ? null : factoryThread == null
                ? controller.getRuntimeRecipeContext() : factoryThread.getRuntimeRecipeContext();
    }

    private boolean canWrite() {
        return controller != null && controller.getLevel() instanceof net.minecraft.server.level.ServerLevel
                && controller.canChangeRuntimeModifiers() && isValid();
    }
    @ZenCodeType.Method public void addModifier(String key, RecipeModifier modifier) {
        if (!canWrite() || modifier == null) return;
        if (factoryThread == null) controller.changeRecipeThreadModifier(false, key, modifier);
        else factoryThread.addSemiPermanentModifier(key, modifier);
    }
    @ZenCodeType.Method public void removeModifier(String key) {
        if (!canWrite()) return;
        if (factoryThread == null) controller.changeRecipeThreadModifier(false, key, null);
        else factoryThread.removeSemiPermanentModifier(key);
    }
    @ZenCodeType.Method public boolean hasModifier(String key) {
        return isValid() && (factoryThread == null ? controller.getRecipeThreadModifiers(false)
                : factoryThread.getSemiPermanentModifiers()).containsKey(key);
    }
    @ZenCodeType.Method public void addPermanentModifier(String key, RecipeModifier modifier) {
        if (!canWrite() || modifier == null) return;
        if (factoryThread == null) controller.changeRecipeThreadModifier(true, key, modifier);
        else factoryThread.addPermanentModifier(key, modifier);
    }
    @ZenCodeType.Method public void removePermanentModifier(String key) {
        if (!canWrite()) return;
        if (factoryThread == null) controller.changeRecipeThreadModifier(true, key, null);
        else factoryThread.removePermanentModifier(key);
    }
    @ZenCodeType.Method public boolean hasPermanentModifier(String key) {
        return isValid() && (factoryThread == null ? controller.getRecipeThreadModifiers(true)
                : factoryThread.getPermanentModifiers()).containsKey(key);
    }
}
