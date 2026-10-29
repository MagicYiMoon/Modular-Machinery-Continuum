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
package hellfirepvp.modularmachinery.common.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Attempts to form the machine belonging to a controller.
 *
 * <p>This is the layer between "here is a pattern and a world" ({@link StructureMatcher}) and "the
 * controller now believes it is assembled". It owns the decisions the matcher deliberately does not:
 * <b>which</b> definition a controller is bound to, and what to do about the machine's parts once a
 * match succeeds.
 *
 * <h2>Definition selection</h2>
 *
 * <p>1.12.2 bound a controller to a machine through an item ("machine projector") or a command, and
 * stored the machine's name on the tile. That model is kept: {@code TileController#getMachineName()}
 * names the definition, and formation is attempted against it. An unbound controller (no name) does
 * not form — silently picking "the first definition that happens to fit" would make an unbound
 * controller assemble into an arbitrary machine, which is far worse than doing nothing.
 *
 * <h2>Component binding</h2>
 *
 * <p>On success every matched part that owns a {@code BlockEntity} implementing
 * {@link hellfirepvp.modularmachinery.common.tiles.base.MachineComponentTile} is bound to the
 * controller position. Without that binding a hatch is just a box: the recipe layer has no way to
 * find the machine's inventories, tanks and energy buffers.
 */
public final class MachineFormation {

    /**
     * Outcome of one formation attempt.
     *
     * @param formed     whether the controller is now assembled
     * @param machine    the definition used, or {@code null} when none was bound
     * @param boundCount how many machine components were bound to the controller
     * @param report     human-readable reasons when {@code formed} is false
     */
    public record Attempt(boolean formed, DynamicMachine machine, int boundCount, List<String> report) {
    }

    private MachineFormation() {
    }

    /**
     * Attempts to form {@code machine} at the controller's position.
     *
     * @param level   the server level; a client level is not authoritative and must not be used to
     *                decide machine state
     * @param pos     the controller's position
     * @param facing  the controller's facing, taken from its blockstate
     * @param machine the bound definition, or {@code null} when the controller is unbound
     * @param loader  the loader supplying the variable table and the resolver
     * @return the attempt result; never {@code null}
     */
    























 
    public static List<BlockPos> rebind(ServerLevel level, BlockPos controllerPos, Direction facing,
                                        DynamicMachine machine, MachineLoader loader) {
        // THE POSITIONS ARE PERSISTED AND THE PATTERN'S SHAPE IS NOT, so this measures the sections again rather
        // than trusting a remembered count: a reloaded machine whose line lost a section must re-bind against the
        // line it has NOW. A machine with no dynamic pattern resolves to the definition's own pattern, which is
        // the object this call always used before §750 (no copy, no cost).
        Map<String, Integer> sizes = resolveSizes(machine, level, controllerPos, facing, loader);
        MachinePattern effective = sizes == null ? null : effectivePattern(machine, sizes);
        MachinePattern pattern = effective == null ? machine.getPattern() : effective;
        List<BlockPos> bound = bindComponents(level, controllerPos, facing, machine, pattern);
        if (level.getBlockEntity(controllerPos) instanceof hellfirepvp.modularmachinery.common.tiles.TileController controller) {
            if (sizes != null) controller.recordFormedStructure(machine, pattern, sizes, facing);
            else controller.setFormedPattern(pattern);
        }
        return bound;
    }
    public static Attempt tryForm(ServerLevel level, BlockPos pos, Direction facing,
                                 DynamicMachine machine, MachineLoader loader) {
        if (machine == null) {
            return new Attempt(false, null, 0, List.of("controller is not bound to a machine definition"));
        }

        String fixed = hellfirepvp.modularmachinery.common.registry.DedicatedControllers.binding(level.getBlockState(pos).getBlock());
        if (fixed != null) {
            if (!fixed.equals(machine.getRegistryName()))
                return new Attempt(false, machine, 0, List.of("This controller belongs to machine " + fixed));
            if (machine.isRequiresBlueprint() && level.getBlockEntity(pos) instanceof hellfirepvp.modularmachinery.common.tiles.TileController controller
                    && hellfirepvp.modularmachinery.common.item.ItemBlueprint.getAssociatedMachine(controller.getBlueprintInventory().getStackInSlot(0)) != machine)
                return new Attempt(false, machine, 0, List.of("This machine requires its matching blueprint"));
        }

        // The machine's modifier replacements are passed IN, so their blocks count as alternatives to the
         
        // BlockArray.matches, and without it a machine that declares a modifier can never form with the
        // modifier's block in place -- so the modifier's bonus can never be observed.
        StructureMatcher.Result result = new StructureMatcher().match(
                level, pos, facing, machine.getPattern(), loader.getVariables(), loader.getResolver(),
                machine.getModifierReplacements());

        if (!result.matched()) {
            return new Attempt(false, machine, 0, result.mismatches());
        }

         
        //
        // Upstream's formation gate is `pattern.matches(...) && matchesDynamicPatternRotation(machine)` at
        // TileMultiblockMachineController:434, and this is the port's half of it. The port had NONE of it, so the
        // shipped assembly_line formed from its wall alone while the mod refuses to form it without its line --
        // the defect the audit found and section 320.4 split into three steps.
        //
        // A machine that declares no dynamic pattern passes through untouched, which is upstream's own shortcut
        // at T460 and why 4 of the 5 shipped machines are unaffected by any of this.
        //
        // `matchSize` returns a COUNT rather than a boolean, so the comparison is against the pattern's declared
        // bounds: a line of 3 sections where 5 is the minimum is a REFUSAL, not a smaller machine. That is what
        // minSize is for, and it is why the walk counts instead of answering yes or no.
        //
         
        // takes, and re-walking to obtain them again below would be two derivations of one number -- upstream
        // walks once inside `matchesDynamicPatternRotation` (U466) and hands the recorded size to
        // `addDynamicPatternToBlockArray` from `foundDynamicPatterns` (U660-U662).
        Map<String, Integer> sectionSizes = new LinkedHashMap<>();
        for (DynamicPattern pattern : machine.getDynamicPatterns().values()) {
            int size = pattern.matchSize(level, pos, facing, loader.getVariables(), loader.getResolver());
            if (!pattern.accepts(size)) {
                return new Attempt(false, machine, 0,
                        List.of("dynamic pattern '" + pattern.getName() + "' found " + size
                                + " section(s), needs " + pattern.getMinSize() + ".." + pattern.getMaxSize()));
            }
            sectionSizes.put(pattern.getName(), size);
        }

        // AND THE SECTIONS BECOME PART OF THE PATTERN THE COMPONENTS ARE BOUND FROM. This is upstream's
        // `foundPattern = new TaggedPositionBlockArray(foundPattern)` + the addPatternToBlockArray loop (U659-U664)
        // in the port's terms. Without it the shipped `assembly_line`'s 5 bus positions -- `casings_item_input`
        // and the fluid/ME hatches in EVERY section, plus the cap's `casings_item_output` -- are matched, counted,
         
        MachinePattern effective = effectivePattern(machine, sectionSizes);

        // The bound POSITIONS, not just the count — the controller needs them so its chunk-unloaded state can
        // cover the whole structure rather than only its own chunk (§136.3, §138). `bindComponents` already
        // built this list for binding reconciliation; returning it instead of its size costs nothing.
        List<BlockPos> bound = bindComponents(level, pos, facing, machine, effective);

        // The pattern the binding walk actually used is remembered on the controller, for the same reason
        // upstream keeps `foundPattern` on the tile: the re-check, the component lookups and the preview must all
        // read the structure the machine was FORMED with rather than the definition's static half (§750).
        if (level.getBlockEntity(pos)
                instanceof hellfirepvp.modularmachinery.common.tiles.TileController controller) {
            controller.setBoundPositions(bound);
            controller.recordFormedStructure(machine, effective, sectionSizes, facing);
            if (controller.isFormed()) {
                controller.setBoundComponentCount(bound.size());
                // Source doStructureCheck posts an update after each successful component refresh,
                // including an already formed structure. Scripts must see the newly measured segment sizes.
                new hellfirepvp.modularmachinery.common.event.machine.MachineStructureUpdateEvent(controller).postEvent();
            }
        }

        // A machine that matches but binds no components is not usable, but it *is* formed: the player
        // built the structure correctly and a subsequent edit (adding a hatch) should not require
        // re-forming. So the two facts are reported separately rather than conflated into "failed".
        return new Attempt(true, machine, bound.size(), List.of());
    }

    /**
     * The pattern a formed machine owns: <b>the machine's declared parts with its dynamic sections merged in</b>.
     *
     * <p><b>THE ONE RULE FOR "WHAT IS THE STRUCTURE", READ BY THE BINDING, THE RE-CHECK, THE COMPONENT LOOKUP AND
     * THE PREVIEW.</b> It is upstream's {@code foundPattern} after {@code addDynamicPatternToBlockArray}
     * ({@code github.kasuminova.mmce.common.util.DynamicPattern} L165-L211, called from
     * {@code TileMultiblockMachineController} U612-U613), and every consumer of the public overloads below is one
     * of those four readers. A machine that declares no dynamic pattern gets its own pattern object back
     * <b>unchanged</b> — not a copy — so the four readers and the matcher cannot end up holding two spellings of
     * the same structure for the 4 of 5 shipped machines that have no sections at all.
     *
     * @param machine the definition
     * @param sizes   each declared pattern's section count, as {@link #resolveSizes} measured it
     * @return the pattern, never {@code null}
     */
    static MachinePattern effectivePattern(DynamicMachine machine, Map<String, Integer> sizes) {
        MachinePattern declared = machine.getPattern();
        if (machine.getDynamicPatterns().isEmpty()) {
            return declared;
        }

        // A COPY, because the merge REPLACES cells (upstream's `new TaggedPositionBlockArray(foundPattern)` at
        // U659 exists for exactly this reason: the definition's own array is cached and shared by every controller
        // in the world, so writing sections into it would give one machine's line to all of them).
        MachinePattern merged = new MachinePattern();
        for (MachinePattern.Part part : declared.getParts()) {
            merged.addPart(part);
        }
        for (DynamicPattern pattern : machine.getDynamicPatterns().values()) {
            Integer size = sizes.get(pattern.getName());
            if (size != null) {
                pattern.mergeSectionsInto(merged, size);
            }
        }
        return merged;
    }

    








 
    public static MachinePattern resolveEffectivePattern(DynamicMachine machine, ServerLevel level, BlockPos pos,
                                                         Direction facing, MachineLoader loader) {
        Map<String, Integer> sizes = resolveSizes(machine, level, pos, facing, loader);
        return sizes == null ? null : effectivePattern(machine, sizes);
    }

    /** Walks every declared dynamic pattern and measures it; {@code null} when one does not currently hold. */
    private static Map<String, Integer> resolveSizes(DynamicMachine machine, ServerLevel level, BlockPos pos,
                                                     Direction facing, MachineLoader loader) {
        Map<String, Integer> sizes = new LinkedHashMap<>();
        for (DynamicPattern pattern : machine.getDynamicPatterns().values()) {
            int size = pattern.matchSize(level, pos, facing, loader.getVariables(), loader.getResolver());
            if (!pattern.accepts(size)) {
                return null;
            }
            sizes.put(pattern.getName(), size);
        }
        return sizes;
    }

    





















































 
    static void checkAndAddSmartInterface(DynamicMachine machine,
                                          hellfirepvp.modularmachinery.common.tiles.TileSmartInterface iface,
                                          java.util.Set<String> assigned) {
        if (machine.smartInterfaceTypesIsEmpty()) {
            return;
        }
        hellfirepvp.modularmachinery.common.tiles.TileSmartInterface.SmartInterfaceProvider provider =
                iface.provideComponent();

        // Upstream reads "the datum bound to this controller" by the controller's position
        // (getMachineData(getPos()), U906). One controller per interface makes that "the datum this
        // interface publishes", which is the by-index accessor upstream also provides.
        hellfirepvp.modularmachinery.common.tiles.TileSmartInterface.Data data = provider.getMachineData(0);
        java.util.Map<String, hellfirepvp.modularmachinery.common.util.SmartInterfaceType> notFound =
                machine.getFilteredType(assigned);

        if (data != null) {
            String type = data.getKey();
            if (notFound.containsKey(type)) {
                // STILL DECLARED AND NOT YET CLAIMED BY AN EARLIER INTERFACE IN THIS WALK: it keeps it.
                assigned.add(type);
            } else if (machine.hasSmartInterfaceType(type)) {
                // DECLARED BUT ALREADY CLAIMED -- WHICH IS THE SHARE BRANCH'S OWN DATUM (the `notFound.isEmpty()`
                // branch below hands one type to EVERY interface past the declared list, upstream U925-U931).
                // UPSTREAM REACHES ITS REMOVE CALL HERE TOO AND THE CALL IS A NO-OP -- it is keyed by the
                // COMPONENT's position while every entry was added under the CONTROLLER's (U922/U929-U934), so
                // the datum SURVIVES on every walk. That no-op is therefore LOAD-BEARING, and this port's
                 
                // surplus interface's datum was DELETED on every other walk and re-created at the DECLARED
                // DEFAULT on the one after -- so the number a player typed into it vanished, and reopening the
                // screen showed the default (or "Not Found Bound Machine" while it was deleted).
                assigned.add(type);
            } else {
                // NO LONGER DECLARED: THE STALE DATUM THIS REMOVAL EXISTS FOR. Upstream's branch at U919-U923
                // means to drop exactly this one and cannot (its by-position removal matches nothing); this
                // port removes by the type name the lookup uses, and that intent is the only thing this
                // deviation keeps -- narrowed here so it can no longer fire on the share case above.
                provider.removeMachineData(type);
            }
            return;
        }

        hellfirepvp.modularmachinery.common.util.SmartInterfaceType type;
        if (notFound.isEmpty()) {
            type = machine.getFirstSmartInterfaceType().orElse(null);
        } else {
            type = notFound.values().stream().sorted().findFirst().orElse(null);
        }
        if (type == null) {
            return;
        }
        // UPSTREAM'S OWN ARGUMENTS (U922/U927): the declaring machine and the declared type. The port passes
        // the machine's own display name alongside its registry name because the SCREEN draws that name and
         
        provider.addMachineData(machine.getRegistryName(), machine.getLocalizedName(), type, true);
        assigned.add(type.getType());
    }

    












 
    private static List<BlockPos> bindComponents(ServerLevel level, BlockPos controllerPos, Direction facing,
                                                 DynamicMachine machine, MachinePattern pattern) {
        List<BlockPos> componentPositions = new ArrayList<>();
        int bound = 0;

         
        //
        // Upstream keeps this on the controller as `foundSmartInterfaces`, a Map<Provider, String>, because a
        // provider is a live object there and the map is read by two CraftTweaker facades afterwards
        // (TileMultiblockMachineController U1106-U1131 and IMachineController U213/U221 -- verified by grepping
        // every caller). The port's runtime getters now resolve the existing recorded bound positions, so
        // another controller map would duplicate those bindings;
        // what the walk actually needs is only "which names are taken", which is the map's VALUES
        // (upstream passes exactly those to getFilteredType). Upstream clears its map at the start of every
        // structure check (U756/U770), so a per-walk set is the same lifetime without the state.
        java.util.Set<String> assignedTypes = new java.util.HashSet<>();

        for (MachinePattern.Part part : pattern.getParts()) {
            BlockPos worldPos = StructureMatcher.toWorldOffset(
                    controllerPos, facing, part.x(), part.y(), part.z());

            if (!level.hasChunkAt(worldPos)) continue;
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(worldPos);
            if (be instanceof hellfirepvp.modularmachinery.common.tiles.base.MachineComponentTile component) {
                component.setControllerPos(controllerPos);
                componentPositions.add(worldPos);
                bound++;

                 
                //
                // This is the producer end of the channel, and it sits HERE rather than in a tick because
                // that is where upstream calls it: TileMultiblockMachineController.checkAndAddComponents
                // U836 calls checkAndAddSmartInterface for every component of the pattern, inside the same
                // per-position loop that binds components. An interface that is not visited publishes
                // nothing, which is why the requirement's producer is this walk and not the block.
                if (be instanceof hellfirepvp.modularmachinery.common.tiles.TileSmartInterface iface) {
                    checkAndAddSmartInterface(machine, iface, assignedTypes);
                }
            } else if (hellfirepvp.modularmachinery.common.machine.component.MachineComponentProxyRegistry.INSTANCE.proxy(be) != null) {
                // External views are recorded, but never receive native ownership data or foreign NBT writes.
                componentPositions.add(worldPos);
                bound++;
            }
        }

        // Record the count on the controller so the GUI can show it. A structure can match while binding
        // nothing (every position holding a decorative casing), and such a machine forms, looks right,
        // and can never run a recipe; the number makes that visible rather than a silent dead end.
        if (level.getBlockEntity(controllerPos)
                instanceof hellfirepvp.modularmachinery.common.tiles.TileController controller) {
            controller.reconcileComponentBindings(componentPositions);
            controller.setBoundComponentCount(bound);
        }
        return componentPositions;
    }


}
