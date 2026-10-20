/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED. Modifications made 2026-10.
 * Upstream attribution is retained as GPL-3.0 requires.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.tiles.me;

import net.minecraft.nbt.CompoundTag;

/**
 * A block entity whose CONFIGURATION can be written to, and read back out of, a memory card (audit row 43).
 *
 * <h2>Upstream's interface, and the one thing the port had to decide about it</h2>
 *
 * <pre>
 *   U K/tile/SettingsTransfer.java (9 lines, the whole file)
 *       public interface SettingsTransfer {
 *           NBTTagCompound downloadSettings();
 *           void uploadSettings(NBTTagCompound settings);
 *       }
 * </pre>
 *
 * <p><b>The type changed and the shape did not.</b> {@code NBTTagCompound} is 1.12.2's name for
 * {@link net.minecraft.nbt.CompoundTag}; the two methods, their order and their arity are upstream's. Nothing else
 * in upstream's file needed porting — it has no body and no other member.</p>
 *
 * <p><b>It names no Applied Energistics type, and that is deliberate rather than incidental.</b> The card that
 * drives it is AE2's own Memory Card, so the interface is what lets the card step live behind the AE2 guard while
 * the tiles themselves stay pure settings holders: a tile implements THIS (an interface with no AE2 in it) and
 * never mentions the card. See {@code MEBusTiles.handleSettingsTransfer} for the half that does name AE2 and is
 * therefore only ever loaded with AE2 present.</p>
 *
 * <p><b>Upstream's implementors, for the record — this is the set the port had to compare its own models
 * against</b> ({@code K/tile/*}, each {@code downloadSettings}/{@code uploadSettings} pair read from the body):</p>
 *
 * <pre>
 *   MEItemInputBus   U300-U314   {@code configInventory} under key "configInventory"
 *   MEItemOutputBus  U182-U199   the int field {@code configuredStackSize}
 *   MEFluidInputBus  U217-U232   the fluid {@code config}, written with config.writeToNBT(tag, CONFIG_TAG_KEY)
 *   MEGasInputBus    U225-U239   the Mekanism GAS config
 * </pre>
 *
 * <p>The port now has the first three: item input configuration, item output stack cap, and fluid input slot
 * configuration. Gas remains absent because the 1.20.1 chemical API is a separate adapter surface.</p>
 */
public interface SettingsTransfer {

    /**
     * @return this component's configuration, as a tag the memory card can hold
     */
    CompoundTag downloadSettings();

    /**
     * Replaces this component's configuration with the card's contents.
     *
     * <p>Upstream's callers pass the tag straight through, so an implementation must tolerate a tag that lacks its
     * own key: a card written by a DIFFERENT block is refused by name before this is called (see the card step),
     * but a card written by the same block on an older version of this mod is not.</p>
     *
     * @param settings the tag {@link #downloadSettings()} produced, possibly from an older save
     */
    void uploadSettings(CompoundTag settings);
}
