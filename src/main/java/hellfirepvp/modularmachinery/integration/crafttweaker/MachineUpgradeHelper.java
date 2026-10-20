/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.item.IItemStack;
import hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade;
import hellfirepvp.modularmachinery.common.upgrade.RegistryMachineUpgrade;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.openzen.zencode.java.ZenCodeType;

/** ZenCode helpers corresponding to upstream's capability based MachineUpgradeHelper. */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.MachineUpgradeHelper")
public final class MachineUpgradeHelper {
    private MachineUpgradeHelper() {}

    @ZenCodeType.Method
    public static void registerSupportedItem(String itemId) {
        Item item = resolve(itemId);
        RegistryMachineUpgrade.addSupportedItem(new ItemStack(item));
    }

    @ZenCodeType.Method
    public static void registerSupportedItem(IItemStack stack) {
        if (stack != null && !stack.isEmpty()) RegistryMachineUpgrade.addSupportedItem(stack.getImmutableInternal());
    }

    @ZenCodeType.Method
    public static void addFixedUpgrade(String itemId, String upgradeName) {
        Item item = resolve(itemId);
        MachineUpgrade upgrade = requireUpgrade(upgradeName);
        RegistryMachineUpgrade.addFixedUpgrade(new ItemStack(item), upgrade);
    }

    @ZenCodeType.Method
    public static void addFixedUpgrade(IItemStack stack, String upgradeName) {
        if (stack == null || stack.isEmpty()) return;
        RegistryMachineUpgrade.addFixedUpgrade(stack.getImmutableInternal(), requireUpgrade(upgradeName));
    }

    /** Returns a copy with the named upgrade encoded in stack NBT, replacing upstream's capability attachment. */
    @ZenCodeType.Method
    public static ItemStack addUpgradeToItemStack(ItemStack stack, String upgradeName) {
        if (stack == null || stack.isEmpty()) return stack;
        requireUpgrade(upgradeName);
        if (!RegistryMachineUpgrade.supportsUpgrade(stack)) {
            throw new IllegalArgumentException("item does not support Modular Machinery upgrades: "
                    + ForgeRegistries.ITEMS.getKey(stack.getItem()));
        }
        return RegistryMachineUpgrade.addUpgradeToStack(stack, upgradeName);
    }

    @ZenCodeType.Method
    public static IItemStack addUpgradeToItemStack(IItemStack stack, String upgradeName) {
        if (stack == null || stack.isEmpty()) return stack;
        return IItemStack.of(addUpgradeToItemStack(stack.getImmutableInternal(), upgradeName));
    }

    @ZenCodeType.Method
    public static MachineUpgrade getUpgrade(String upgradeName) {
        return requireUpgrade(upgradeName);
    }

    private static MachineUpgrade requireUpgrade(String name) {
        MachineUpgrade upgrade = RegistryMachineUpgrade.getPrototype(name);
        if (upgrade == null) throw new IllegalArgumentException("unknown Modular Machinery upgrade: " + name);
        return upgrade;
    }

    private static Item resolve(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("item id cannot be blank");
        ResourceLocation id = ResourceLocation.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null) throw new IllegalArgumentException("unknown item: " + raw);
        return item;
    }
}
