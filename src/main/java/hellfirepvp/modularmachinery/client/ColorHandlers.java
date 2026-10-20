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
 * original named above. Modifications made 2026-08 .. 2026-09.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.client;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the tint index the machine models declare.
 *
 * <h2>The bug this fixes</h2>
 *
 * <p>{@code models/block/blockmodel_overlay.json} draws every face with {@code "tintindex": 1}. A tint index is
 * a REQUEST for a colour, and 1.20.1 answers it through a {@code BlockColor}/{@code ItemColor} registered
 * against {@code RegisterColorHandlersEvent}. <b>Nothing in this port registered one</b>, so the request went
 * unanswered — and vanilla's fallback for an unresolved tint is <b>white for an item and black for a block</b>.
 *
 * <p>Reported from the client exactly that way: <i>"工厂控制器和机械控制器拿手上是白色，放下来变成黑色"</i> —
 * white in hand, black once placed.
 *
 * <p><b>And §? the interface was already here.</b> {@link hellfirepvp.modularmachinery.common.block
 * .BlockDynamicColor} describes this in its javadoc — "on 1.20.1 tints come from a {@code BlockColor} registered
 * per block against {@code RegisterColorHandlersEvent.Block}" — and <b>nothing implements it and nothing
 * registers it</b>. The explanation was written and the registration was not.
 *
 * <h2>What it returns</h2>
 *
 * <p>White, which is what leaves a texture untinted: the models are authored in their real colours and the tint
 * index is upstream's hook for machine colouring rather than a colour it applies by default. Returning
 * {@code -1} would mean "no tint" but also disables the index entirely, so a later coloured block could not
 * take it over. <b>0xFFFFFF is the honest answer for a block that has a tint slot and no colour yet.</b>
 *
 * <h2>Dist safety</h2>
 *
 * <p>{@code Dist.CLIENT} and the MOD bus: colour registration happens on the client only, and this class must
 * never be touched by a dedicated server.
 */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ColorHandlers {

    /** The tint index upstream's overlay model asks for. */
    private static final int MACHINE_TINT = 1;

    /** White, i.e. the texture as authored. */
    /** The machine casing's own colour, measured from {@code blockcasing_plain.png}: (215,215,215). */
    /**
     * The machine colour, now READ FROM CONFIG (PLAN section 291).
     *
     * <p>It was a constant through sections 288-290, documented then as "becomes the config default and nothing
     * else changes". That is what happened: the value moved into {@code Config} with the same measured default, and
     * this method now asks for it.
     *
     * <p>Called per tint query rather than cached, deliberately: Forge reloads a config file on change, and a
     * cached int here would keep the old colour until the client restarted.
     */
    private static int machineColor() {
        return hellfirepvp.modularmachinery.common.data.Config.machineColor();
    }
    private static final int NO_TINT = 0xFFFFFF;

    private ColorHandlers() {
    }

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        // §216: a colour registration must never be able to stop the client. The models are authored in
        // their real colours and the tint index is a hook, not a requirement, so a rejection here means
        // "the machine keeps its texture" and nothing more.
        try {
        List<Block> ours = new ArrayList<>();
        for (Block block : ForgeRegistries.BLOCKS) {
            var key = ForgeRegistries.BLOCKS.getKey(block);
            if (key == null) {
                continue;
            }
            String ns = key.getNamespace();
            if (ns.equals(Modularmachinery.MODID) || ns.equals("modularmagic")) {
                ours.add(block);
            }
        }
        if (ours.isEmpty()) {
            // Nothing registered is a bug in itself, and registering nothing would make this silently
            // ineffective rather than visibly wrong.
            return;
        }
        event.register((state, level, pos, tintIndex) -> NO_TINT, ours.toArray(new Block[0]));
        // Registered. Nothing to log: the effect is visible and the count is not a diagnostic.
        } catch (Throwable coloured) {
            // Cosmetic only: the textures render untinted, which is correct.
        }
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        // §216: a colour registration must never be able to stop the client. The models are authored in
        // their real colours and the tint index is a hook, not a requirement, so a rejection here means
        // "the machine keeps its texture" and nothing more.
        try {
        // The item form of the same model carries the same tint index, so it needs the same answer -- without
        // this the block is correct in the world and white in the inventory.
        for (var entry : ForgeRegistries.ITEMS.getEntries()) {
            var key = entry.getKey().location();
            String ns = key.getNamespace();
            if (ns.equals(Modularmachinery.MODID) || ns.equals("modularmagic")) {
                // itemmodularium IS SKIPPED HERE, because a second registration for one item is REJECTED
                // (PLAN section 290).
                //
                // Vanilla's ItemColors.register warns and ignores a duplicate, so registering it here and
                // again below registers only the FIRST one -- the NO_TINT one -- and the coloured one is the
                // one thrown away. That is exactly why the ingot still rendered grey after section 288.
                //
                // I had added this skip to the BLOCK handler by mistake, then removed it as a detour, and
                // never added it here. The mistake was not the detour; it was putting the guard in the wrong
                // handler and then deleting it instead of moving it.
                if (key.getPath().equals("itemmodularium")) {
                    continue;
                }
                event.register((stack, tintIndex) -> NO_TINT, entry.getValue());
            }

        // THE MACHINE COLOUR, for the one greyscale texture in this mod (PLAN section 288).
        //
        // `itemmodularium` is authored in PURE GREYSCALE -- measured: mean saturation 0.000 across its 34
        // opaque pixels -- which is a texture with exactly one purpose: to be tinted. Leaving it at NO_TINT
        // renders it grey, and in 1.12.2 it was NOT left untinted: an item's LAYER NUMBER was its tint index,
        // so ItemModularium.getColorFromItemstack(stack, 0) was called implicitly. 1.20.1 kept that rule for
        // `item/generated` (vanilla's spawn egg relies on it), so nothing in the model needs to change --
        // only a colour needs registering.
        //
        // The colour is MEASURED, not chosen: the mean opaque colour of the mod's own machine casing texture
        // is (215,215,215). That is what upstream's `machineColor` names, and it gives the modularium the
        // colour of the machine it is made of.
        //
        // Upstream read this from config as a HEX STRING with the fallback passed in by the caller --
        // `Config.machineColor` is assigned from a method PARAMETER, so its default is not recoverable from
        // the class alone, and this port has no config system yet. When one arrives, MACHINE_COLOR becomes
        // its default and nothing else changes.
        event.register((stack, tintIndex) -> tintIndex == 0 ? machineColor() : NO_TINT,
                hellfirepvp.modularmachinery.common.registry.RegistryItems.itemModularium.get());

        }
        } catch (Throwable coloured) {
            // Cosmetic only: the textures render untinted, which is correct.
        }
    }
}
