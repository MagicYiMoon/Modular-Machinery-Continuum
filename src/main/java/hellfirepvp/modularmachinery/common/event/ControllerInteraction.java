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
package hellfirepvp.modularmachinery.common.event;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.MachineFormation;
import hellfirepvp.modularmachinery.common.machine.MachineLoader;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forms a machine when a player right-clicks its controller.
 *
 * <p><b>1.12.2 → 1.20.1.</b> Upstream handled this in {@code BlockController#onBlockActivated}
 * (SRG {@code func_180639_a}). The interaction callback still exists on 1.20.1 as
 * {@code BlockBehaviour#use}, but it is implemented here as a Forge event instead, for two reasons:
 *
 * <ul>
 *   <li>the formation logic needs the machine registry and the pattern matcher, and pushing that into
 *       the block class couples a block to the whole engine;</li>
 *   <li>a {@code use} override cannot cleanly report mismatches to the player without also owning the
 *       messaging, which is better kept in one place.</li>
 * </ul>
 *
 * <p>Server-side only. Formation state must never be decided on the client: {@code StructureMatcher}
 * reads the world, and a client's world is neither complete nor authoritative.
 */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ControllerInteraction {

    private ControllerInteraction() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // Guard on the level rather than on the event's side: the event fires on both logical sides and
        // only the server's view may decide machine state.
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockController)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof TileController controller)) {
            return;
        }
        // Only a SNEAKING bare-hand click is the formation gesture (§191).
        //
        // The guard used to test only for an empty hand, and the handler then cancelled the event
        // unconditionally -- so EVERY bare-hand right-click was swallowed, `BlockController.use` never ran,
        // and the controller's GUI opened only when the player happened to be holding an item. Reported from
        // the client as "空手右击提示机器未成形，必须手持物品才能打开".
        //
        // `BlockController.use` already documents the split: it returns PASS on a sneaking click "left to the
        // formation handler", and opens the menu otherwise. This is the other half of that contract, which was
        // missing.
        // MAIN HAND ONLY, or the message appears TWICE (PLAN section 304).
        //
        // Forge fires RightClickBlock once for EACH hand on the server, so a sneak-click produced two
        // identical chat lines. The client reported exactly that, with a screenshot showing the same
        // sentence stacked twice.
        //
        // The off-hand copy is not a second gesture: a player has one intention per click, and the
        // formation path would run twice as well if the structure happened to match.
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        if (!event.getEntity().getMainHandItem().isEmpty() || !event.getEntity().isShiftKeyDown()) {
            return;
        }

        MachineLoader loader = MachineReloadListener.getLoader();
        DynamicMachine machine = loader.findByName(controller.getMachineName());

        Direction facing = state.hasProperty(BlockController.FACING)
                ? state.getValue(BlockController.FACING)
                : Direction.NORTH;


        //
        // The binding is written with setMachineName, the same call CommandModularMachinery bind uses, so
        // a detected machine and a bound one are indistinguishable afterwards -- which is what makes this
        // a shortcut rather than a second kind of binding.
        if (machine == null) {
            // FIRST MATCHING MACHINE WINS, which is what upstream does (PLAN section 309).
            //
            // Upstream's TileMultiblockMachineController.checkAllPatterns walks every machine, SKIPS the ones
            // that require a blueprint, and tests each remaining one against the blocks actually built:
            //
            //     for (DynamicMachine machine : MachineRegistry.getRegistry()) {
            //         if (machine.isRequiresBlueprint()) continue;
            //         if (matchesRotation(pattern, machine, controllerRotation)) {
            //             onStructureFormed();
            //             break;
            //         }
            //     }
            //
            // The port's first version COUNTED the eligible machines and refused when there were several,
            // which was wrong twice over: "several eligible" is not ambiguous, because only one of them can
            // match the structure a player built; and refusing meant the feature did nothing the moment a
            // second machine opted in.
            for (DynamicMachine candidate : loader.all()) {
                if (candidate.isRequiresBlueprint()) {
                    continue;
                }
                // The probe is tryForm itself, so "matches" means exactly what the formation path means by
                // it -- there is no second notion of a match to keep in step with the first.
                if (MachineFormation.tryForm(level, pos, facing, candidate, loader).formed()) {
                    machine = candidate;
                    controller.setMachineName(machine.getRegistryName());
                    event.getEntity().displayClientMessage(Component.translatable(
                            "message.modularmachinery.structure_detected", machine.getLocalizedName()), false);
                    break;
                }
            }
        }

        MachineFormation.Attempt attempt = MachineFormation.tryForm(level, pos, facing, machine, loader);

        if (attempt.formed()) {
            controller.setFormed(true);
            // LOCALISED (PLAN section 305). This was a hard-coded English sentence, and the screenshot that
            // confirmed structure detection showed it directly beneath the CHINESE detection message --
            // one message in each language, about a single click.
            //
            // getLocalizedName, not getRegistryName: the registry name is an identifier and belongs in
            // commands and logs, while a player-facing sentence should use the name the machine shows
            // everywhere else.
            event.getEntity().displayClientMessage(Component.translatable(
                    "message.modularmachinery.machine_formed",
                    attempt.machine().getLocalizedName(), attempt.boundCount()), false);
        } else {
            controller.setFormed(false);
            event.getEntity().displayClientMessage(Component.translatable("gui.modularmachinery.controller.did_not_form"), false);
            for (String line : attempt.report()) {
                event.getEntity().displayClientMessage(Component.literal("  " + line), false);
            }
        }
        // Cancel only when we did something. §191: this comment was here while the code cancelled
        // unconditionally, which is the defect above -- the prose described the intent and the statement did
        // not implement it. The formation gesture always "does something" (it reports either formed or why
        // not), so reaching here means cancelling, and the condition is written out to keep the claim and the
        // code in step.
        if (attempt.formed() || event.getEntity().isShiftKeyDown()) {
            event.setCanceled(true);
        }
    }
}
