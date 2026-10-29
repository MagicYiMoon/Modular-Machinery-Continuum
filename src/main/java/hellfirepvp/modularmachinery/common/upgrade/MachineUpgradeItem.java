/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED: it was rewritten for the
 * 1.20.1 API (official/Mojang mappings, Forge 47.3.0, mods.toml metadata, and the 1.20.1
 * vanilla and Forge signatures), and therefore differs substantially from the upstream
 * original named above. Modifications made 2026-10.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.upgrade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The item form of a machine upgrade: an ordinary item whose whole identity is the {@link UpgradeType} it carries.
 *
 * <h2>Rule A0 — how an upgrade got onto an item upstream, and why it is a field here</h2>
 *
 * <p>Upstream has <b>no upgrade item class at all</b>. An upgrade reached an item through a Forge
 * {@code Capability}: {@code UpgradeEventHandler.onAttachCapabilities} (U19-U35) attaches
 * {@code CapabilityUpgradeProvider} to any {@code ItemStack} whose item is in
 * {@code RegistryUpgrade.ITEM_UPGRADES}, and {@code CapabilityUpgrade}'s {@code List<MachineUpgrade>} is what
 * {@code TileUpgradeBus.onUpgradeInventoryChanged} then reads out of the stack
 * ({@code stackInSlot.getCapability(CapabilityUpgrade.MACHINE_UPGRADE_CAPABILITY, null)}, upstream
 * TileUpgradeBus U107). That indirection exists so a PACK AUTHOR can grant an upgrade by attaching it to an
 * EXISTING item — a diamond, a mod's machine part — from a script, without any new item being registered, and
 * {@code RegistryUpgrade.addSupportedItem}/{@code addFixedUpgrade} (U45-U51) are the two shapes of that grant.
 *
 * <p><b>Why this port uses a field instead, and what is lost.</b> Two things are needed for the capability route
 * and neither exists here:
 * <ol>
 *   <li>{@code AttachCapabilitiesEvent<ItemStack>} needs a capability REGISTRY entry for the stack's holder, and on
 *       1.20.1 that means a {@code DeferredRegister<Capability<?>>} plus a capability-aware
 *       {@code ICapabilityProvider} — which is portable, but see the second point;</li>
 *   <li>the route's ONLY producer was {@code RegistryUpgrade.addFixedUpgrade(stack, upgrade)} called from
 *       {@code MachineUpgradeBuilder.buildAndRegister()} — CraftTweaker. <b>This port's
 *       {@code CraftTweakerIntegration} is a stub</b>, so the capability would be attached by nothing, and a
 *       capability attached by nothing is the "abstraction with no producer" this port has already removed twice.
 *       An item carrying its own type is the same information at the same place, with a producer that exists.</li>
 * </ol>
 *
 * <p><b>THE LOSS IS REAL AND IS NAMED:</b> a pack author cannot attach an upgrade to an item this mod did not
 * register. An item that carries exactly one upgrade type cannot express upstream's "this stack is a
 * {@code SimpleMachineUpgrade} with customData", and a single item cannot be several upgrade types at once. What
 * survives is the whole consumption path — slot to registry to bus to ticker — which is the mechanism this unit
 * was asked for.
 */
public class MachineUpgradeItem extends Item {

    private final UpgradeType type;

    public MachineUpgradeItem(final UpgradeType type, final Item.Properties properties) {
        super(properties);
        this.type = type;
    }

    /** @return the upgrade this item installs */
    public UpgradeType getUpgradeType() {
        return type;
    }

    









 
    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.literal(type.getName()).withStyle(ChatFormatting.AQUA));
        List<MachineUpgrade> upgrades = RegistryMachineUpgrade.upgradesFor(stack);
        if (upgrades.isEmpty()) {
            upgrades = List.of(RegistryMachineUpgrade.getPrototype(type.getName()));
        }
        for (MachineUpgrade upgrade : upgrades) {
            if (upgrade == null) continue;
            for (String line : upgrade.getDescriptions()) {
                tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
            }
        }
        if (type.getMaxStackSize() > 1) {
            tooltip.add(Component.literal("Stacks up to " + type.getMaxStackSize())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** @return an {@code ItemStack} of this item, for registrations and checks that need a concrete stack */
    public ItemStack stack() {
        return new ItemStack(this);
    }
}
