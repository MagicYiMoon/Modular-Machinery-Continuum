/* SPDX-License-Identifier: GPL-3.0-only
 * Source MMCE controller query contract, adapted for Forge 1.20.1, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.helper;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import hellfirepvp.modularmachinery.common.crafting.ActiveMachineRecipe;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.util.SmartInterfaceData;
import hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.openzen.zencode.java.ZenCodeType;

/** Source controller queries and modifier scopes. All mutation is authoritative on the server. */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.IMachineController")
public interface IMachineController {
    /** Uses CT14's native Level API; detached observations have no world. */
    @ZenCodeType.Getter("world") @ZenCodeType.Nullable
    Level getIWorld();

    @ZenCodeType.Getter("ownerUUID") @ZenCodeType.Nullable
    String getOwnerUUIDString();

    @ZenCodeType.Getter("ownerName") @ZenCodeType.Nullable
    String getOwnerName();

    @ZenCodeType.Getter("ownerPlayer") @ZenCodeType.Nullable
    Player getOwnerIPlayer();

    @ZenCodeType.Getter("ticksExisted")
    int getTicksExisted();

    @ZenCodeType.Getter("extraThreadCount")
    int getExtraThreadCount();

    @ZenCodeType.Setter("extraThreadCount")
    void setExtraThreadCount(int extraThreadCount);

    @ZenCodeType.Getter("pos")
    net.minecraft.core.BlockPos getIPos();

    @ZenCodeType.Getter("facing")
    net.minecraft.core.Direction getFacing();

    @ZenCodeType.Getter("blockState")
    net.minecraft.world.level.block.state.BlockState getIBlockState();

    /** Rotates an offset from the source NORTH frame; does not add the controller position. */
    @ZenCodeType.Method
    net.minecraft.core.BlockPos rotateWithControllerFacing(net.minecraft.core.BlockPos offset);

    @ZenCodeType.Getter("customData")
    com.blamejared.crafttweaker.api.data.IData getCustomData();

    @ZenCodeType.Setter("customData")
    void setCustomData(com.blamejared.crafttweaker.api.data.IData data);

    @ZenCodeType.Method @ZenCodeType.Nullable
    SmartInterfaceData getSmartInterfaceData(String type);

    @ZenCodeType.Getter("smartInterfaceDataList")
    SmartInterfaceData[] getSmartInterfaceDataList();

    @ZenCodeType.Getter("foundModifiers") String[] getFoundModifierReplacements();
    @ZenCodeType.Method boolean hasModifierReplacement(String modifierName);
    @ZenCodeType.Method boolean hasMachineUpgrade(String upgradeName);
    @ZenCodeType.Method MachineUpgrade[] getMachineUpgrade(String upgradeName);

    @ZenCodeType.Method @ZenCodeType.Nullable
    IDynamicPatternInfo getDynamicPattern(String patternName);

    @ZenCodeType.Method int getBlocksInPattern(String blockName);
    @ZenCodeType.Method int getBlocksInPattern(net.minecraft.world.level.block.state.BlockState state);
    @ZenCodeType.Method int getBlocksInPattern(com.blamejared.crafttweaker.api.item.IItemStack stack);
    @ZenCodeType.Method int getBlocksInPattern(IBlockStatePredicate predicate);

    /** Returned offsets are already rotated, as in the source; add pos without rotating again. */
    @ZenCodeType.Method net.minecraft.core.BlockPos[] getBlockPosInPattern(String blockName);
    @ZenCodeType.Method net.minecraft.core.BlockPos[] getBlockPosInPattern(net.minecraft.world.level.block.state.BlockState state);
    @ZenCodeType.Method net.minecraft.core.BlockPos[] getBlockPosInPattern(com.blamejared.crafttweaker.api.item.IItemStack stack);
    @ZenCodeType.Method net.minecraft.core.BlockPos[] getBlockPosInPattern(IBlockStatePredicate predicate);

    @ZenCodeType.Getter("isWorking")
    boolean isWorking();

    @ZenCodeType.Getter("formedMachineName")
    @ZenCodeType.Nullable
    String getFormedMachineName();

    @ZenCodeType.Getter("activeRecipe") @ZenCodeType.Nullable
    ActiveMachineRecipe getActiveRecipe();

    @ZenCodeType.Getter("activeRecipeList")
    hellfirepvp.modularmachinery.common.crafting.ActiveMachineRecipe[] getActiveRecipeList();

    @ZenCodeType.Getter("recipeThreadList")
    hellfirepvp.modularmachinery.common.machine.RecipeThread[] getRecipeThreadList();

    @ZenCodeType.Method void addModifier(String key, RecipeModifier modifier);
    @ZenCodeType.Method void removeModifier(String key);
    @ZenCodeType.Method boolean hasModifier(String key);
    @ZenCodeType.Method void addPermanentModifier(String key, RecipeModifier modifier);
    @ZenCodeType.Method void removePermanentModifier(String key);
}
