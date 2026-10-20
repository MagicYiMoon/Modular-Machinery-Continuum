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
    /**
     * Re-binds the components of an ALREADY FORMED machine, using the same walk formation uses.
     *
     * <p><b>THIS EXISTS BECAUSE FORMATION WAS THE ONLY PATH THAT BOUND ANYTHING, AND A WORLD RELOAD IS NOT A
     * FORMATION (PLAN 626-627).</b> The defect is closed by reading the two facts together:</p>
     *
     * <pre>
     * TileController D409  this.formed = tag.getBoolean("formed");     THE FLAG IS PERSISTED AND COMES BACK TRUE
     * TileController T114  private java.util.List&lt;BlockPos&gt; boundPositions    THE POSITIONS ARE NOT PERSISTED
     * MachineFormation T121  bindComponents(...)                        AND ONLY tryForm EVER CALLED IT
     * </pre>
     *
     * <p>SO A RELOADED WORLD SHOWED "已成型（0 个部件）" AND THE MACHINE COULD NOT RUN -- the flag survived and the
     * binding did not -- UNTIL THE PLAYER SHIFT-CLICKED THE CONTROLLER, which happens to run tryForm. UPSTREAM DOES
     * NOT HAVE THIS HOLE: its doStructureCheck (TileMultiblockMachineController U533-U559) calls
     * <b>updateComponents()</b> at U556 on EVERY structure check, so its components are re-bound whenever the
     * structure is re-confirmed, world loads included.</p>
     *
     * <p><b>AND IT DELIBERATELY REUSES bindComponents RATHER THAN REPEATING IT:</b> a second walk would be a second
     * convention for one rule, and the two would drift -- which is exactly what rule B forbids. Saving and
     * unbinding are already handled inside it (it calls unbindStale), so this is a complete re-bind rather than an
     * additive one.</p>
     *
     * @return the component positions now bound, for the caller to store on the controller
     */
    public static List<BlockPos> rebind(ServerLevel level, BlockPos controllerPos, Direction facing,
                                        DynamicMachine machine, MachineLoader loader) {
        // THE POSITIONS ARE PERSISTED AND THE PATTERN'S SHAPE IS NOT, so this measures the sections again rather
        // than trusting a remembered count: a reloaded machine whose line lost a section must re-bind against the
        // line it has NOW. A machine with no dynamic pattern resolves to the definition's own pattern, which is
        // the object this call always used before §750 (no copy, no cost).
        MachinePattern effective = resolveEffectivePattern(machine, level, controllerPos, facing, loader);
        return bindComponents(level, controllerPos, facing, machine,
                effective == null ? machine.getPattern() : effective);
    }
    public static Attempt tryForm(ServerLevel level, BlockPos pos, Direction facing,
                                 DynamicMachine machine, MachineLoader loader) {
        if (machine == null) {
            return new Attempt(false, null, 0, List.of("controller is not bound to a machine definition"));
        }

        // The machine's modifier replacements are passed IN, so their blocks count as alternatives to the
        // pattern at their positions (PLAN section 239). This is upstream's fourth parameter to
        // BlockArray.matches, and without it a machine that declares a modifier can never form with the
        // modifier's block in place -- so the modifier's bonus can never be observed.
        StructureMatcher.Result result = new StructureMatcher().match(
                level, pos, facing, machine.getPattern(), loader.getVariables(), loader.getResolver(),
                machine.getModifierReplacements());

        if (!result.matched()) {
            return new Attempt(false, machine, 0, result.mismatches());
        }

        // THE DYNAMIC PATTERNS MUST BE FOUND BEFORE THE MACHINE IS FORMED (PLAN section 335).
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
        // THE COUNTS ARE RETAINED HERE RATHER THAN DISCARDED (PLAN §750). They are the SIZE ARGUMENT `instantiate`
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
        // and then never visited, so the machine forms with no input or output bus at all (PLAN §750).
        MachinePattern effective = effectivePattern(machine, sectionSizes);

        // The bound POSITIONS, not just the count — the controller needs them so its chunk-unloaded state can
        // cover the whole structure rather than only its own chunk (§136.3, §138). `bindComponents` already
        // built this list for `unbindStale`; returning it instead of its size costs nothing.
        List<BlockPos> bound = bindComponents(level, pos, facing, machine, effective);

        // The pattern the binding walk actually used is remembered on the controller, for the same reason
        // upstream keeps `foundPattern` on the tile: the re-check, the component lookups and the preview must all
        // read the structure the machine was FORMED with rather than the definition's static half (§750).
        if (level.getBlockEntity(pos)
                instanceof hellfirepvp.modularmachinery.common.tiles.TileController controller) {
            controller.setBoundPositions(bound);
            controller.setFormedPattern(effective);
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

    /**
     * {@link #effectivePattern(DynamicMachine, Map)} for a caller that holds no measurements: <b>walks the dynamic
     * patterns in the world</b> to obtain the section counts (PLAN §750).
     *
     * <p>Used by the re-bind-after-a-world-load path, which has a loader but no formation attempt behind it, and by
     * the diagnostic command. The section count cannot be stored and reused — it is a property of the WORLD, and a
     * line that lost a section has a different count — so the walk is run rather than a stale number kept.
     *
     * @return the effective pattern, or {@code null} when a declared pattern does not currently hold
     */
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

    /**
     * Hands one smart interface the machine-declared type it publishes, or takes it away again.
     *
     * <p><b>THE PORT OF UPSTREAM'S WHOLE METHOD, READ LINE BY LINE</b>
     * ({@code TileMultiblockMachineController.checkAndAddSmartInterface}, U902-U931):</p>
     *
     * <pre>
     * if the machine declares no types            -> nothing, ever (upstream U903's own gate)
     * if this interface already publishes a type:
     *     that type is still declared AND not yet taken by an earlier interface -> it keeps it
     *     otherwise (the type is gone, or twice declared)                     -> the datum is REMOVED
     * else:
     *     machine has types left -> take the highest-priority unused one
     *     no types left          -> take the HIGHEST-PRIORITY type and share it
     * and either way: publish it with the declared type's own default value, overriding any old one
     * </pre>
     *
     * <p><b>FOUR THINGS ONLY THE BODY SHOWS, ALL OF THEM KEPT:</b></p>
     *
     * <ul>
     *   <li>The surplus case deliberately does NOT refuse. A machine with one declared type and three
     *       interfaces hands all three the same type (U919-U924), so three physical interfaces read the same
     *       value rather than two of them being dead.</li>
     *   <li>The fresh case takes the SORTED FIRST, i.e. the highest priority — through
     *       {@code SmartInterfaceType.compareTo}, which is descending. Reversing it hands out the lowest.</li>
     *   <li><b>A datum whose type is still declared but ALREADY TAKEN by an earlier interface in this walk
     *       SURVIVES (U914-U923), and that is the SHARE branch's datum rather than a duplicate to clean
     *       up</b> — the surplus case above is the only way such a datum can exist, so it is kept and the
     *       value a player typed into it is kept with it. Only a datum whose type the machine no longer
     *       DECLARES is dropped. <b>PLAN 688 IS THIS LINE:</b> treating the shared datum as a duplicate made
     *       the walk delete it on every other pass and re-create it at the DECLARED DEFAULT on the one after,
     *       so a player's number vanished and reopening the screen showed the default.</li>
     *   <li>Publishing uses {@code override = true} (U922/U927), so re-forming resets the value to the
     *       DECLARED DEFAULT rather than keeping a value the player set for a structure that no longer
     *       exists. <b>AND THAT IS WHY THE SURVIVING DATUM MATTERS: only the <i>fresh</i> branch publishes,
     *       so a walk over an interface that already has its datum touches nothing at all.</b></li>
     * </ul>
     *
     * <p><b>RULE A0 — ONE LINE COULD NOT BE TRANSLATED, AND PLAN 688 FOUND WHAT THE TRANSLATION COST:</b>
     * U915's {@code removeMachineData(realPos)} passes the COMPONENT's world position, while every entry was
     * added under the CONTROLLER's position (U922/U929-U934), so upstream's by-position removal matches
     * nothing and the datum it means to drop survives. <b>THE NO-OP IS LOAD-BEARING:</b> upstream reaches that
     * call for the surplus interface on every walk after the first, so the "the call does nothing" accident is
     * what keeps the shared datum — and the number a player typed into it — alive. This port removes by TYPE
     * NAME (the same key the lookup uses) because its datum carries no position at all
     * ({@code TileSmartInterface}'s class javadoc, branch (a) of §551.4), and that removal, as §663 wrote it,
     * dropped the shared datum too. <b>DONE INSTEAD, and it is the smallest change that restores upstream's
     * observable behaviour:</b> the removal now fires only when the machine no longer declares the type — the
     * case upstream's branch is written for — so it can no longer reach the share case upstream's own
     * {@code notFound.isEmpty()} branch just created.</p>
     *
     * @param machine  the definition being formed
     * @param iface    the interface encountered by the walk
     * @param assigned the type names already handed out during this same walk
     */
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
                // working removal was the defect (PLAN 688): with one declared type and two interfaces, the
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
        // has no definition to read it from (PLAN 674) -- see the provider method's javadoc.
        provider.addMachineData(machine.getRegistryName(), machine.getLocalizedName(), type, true);
        assigned.add(type.getType());
    }

    /**
     * Binds every component block entity in the pattern to the controller, and unbinds any that are no
     * longer part of the structure.
     *
     * <p>Unbinding matters as much as binding: a player who breaks a hatch and replaces it with casing
     * must not leave the old block entity addressable, or the recipe layer would keep moving items
     * through a hatch that is no longer part of the machine.
     *
     * <p><b>THE {@code pattern} PARAMETER IS THE SECTIONS-INCLUDED PATTERN (PLAN §750), NOT the definition's.</b>
     * It is the same object {@link #effectivePattern} produced for the formation that called this, and passing the
     * definition's pattern instead is precisely the shipped defect: the dynamic sections' buses were matched and
     * counted, and this loop never visited them. The parameter is required rather than derived so a caller cannot
     * silently get the static half again.
     */
    private static List<BlockPos> bindComponents(ServerLevel level, BlockPos controllerPos, Direction facing,
                                                 DynamicMachine machine, MachinePattern pattern) {
        List<BlockPos> componentPositions = new ArrayList<>();
        int bound = 0;

        // WHICH DECLARED SMART INTERFACE TYPES THIS WALK HAS ALREADY HANDED OUT (PLAN 663, B2).
        //
        // Upstream keeps this on the controller as `foundSmartInterfaces`, a Map<Provider, String>, because a
        // provider is a live object there and the map is read by two CraftTweaker facades afterwards
        // (TileMultiblockMachineController U1106-U1131 and IMachineController U213/U221 -- verified by grepping
        // every caller). NEITHER READER EXISTS HERE, so a controller field would be a map with no consumer;
        // what the walk actually needs is only "which names are taken", which is the map's VALUES
        // (upstream passes exactly those to getFilteredType). Upstream clears its map at the start of every
        // structure check (U756/U770), so a per-walk set is the same lifetime without the state.
        java.util.Set<String> assignedTypes = new java.util.HashSet<>();

        for (MachinePattern.Part part : pattern.getParts()) {
            BlockPos worldPos = StructureMatcher.toWorldOffset(
                    controllerPos, facing, part.x(), part.y(), part.z());

            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(worldPos);
            if (be instanceof hellfirepvp.modularmachinery.common.tiles.base.MachineComponentTile component) {
                component.setControllerPos(controllerPos);
                componentPositions.add(worldPos);
                bound++;

                // AND THE SAME WALK PUBLISHES A SMART INTERFACE'S VALUE (PLAN 663, B2).
                //
                // This is the producer end of the channel, and it sits HERE rather than in a tick because
                // that is where upstream calls it: TileMultiblockMachineController.checkAndAddComponents
                // U836 calls checkAndAddSmartInterface for every component of the pattern, inside the same
                // per-position loop that binds components. An interface that is not visited publishes
                // nothing, which is why the requirement's producer is this walk and not the block.
                if (be instanceof hellfirepvp.modularmachinery.common.tiles.TileSmartInterface iface) {
                    checkAndAddSmartInterface(machine, iface, assignedTypes);
                }
            }
        }

        // Unbind components that were previously part of this machine but are no longer declared.
        // Scoped to a radius around the controller rather than a global sweep: a global walk on every
        // formation would be O(loaded chunk block entities) and is not worth it.
        //
        // THE REACH COMES FROM THE SAME PATTERN THE WALK USED (§750), because a section is up to `maxSize`
        // blocks out: a reach measured from the definition's static wall cannot see a bus the line placed, so
        // such a bus could never be unbound.
        unbindStale(level, controllerPos, pattern, componentPositions);

        // Record the count on the controller so the GUI can show it. A structure can match while binding
        // nothing (every position holding a decorative casing), and such a machine forms, looks right,
        // and can never run a recipe; the number makes that visible rather than a silent dead end.
        if (level.getBlockEntity(controllerPos)
                instanceof hellfirepvp.modularmachinery.common.tiles.TileController controller) {
            controller.setBoundComponentCount(bound);
        }
        return componentPositions;
    }


    private static void unbindStale(ServerLevel level, BlockPos controllerPos, MachinePattern pattern,
                                    List<BlockPos> stillBound) {
        int reach = reach(pattern);
        for (BlockPos candidate : BlockPos.betweenClosed(
                controllerPos.offset(-reach, -reach, -reach),
                controllerPos.offset(reach, reach, reach))) {
            if (stillBound.contains(candidate)) {
                continue;
            }
            // Only unbind components that point at *this* controller; another machine's parts must be
            // left alone even if they fall inside this bounding box.
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(candidate);
            if (be instanceof hellfirepvp.modularmachinery.common.tiles.base.MachineComponentTile component
                    && controllerPos.equals(component.getControllerPos())) {
                component.setControllerPos(null);
            }
        }
    }

    /** @return the largest absolute offset declared by the pattern, plus one, for the unbind sweep */
    private static int reach(MachinePattern pattern) {
        int max = 0;
        for (MachinePattern.Part part : pattern.getParts()) {
            max = Math.max(max, Math.max(Math.abs(part.x()),
                    Math.max(Math.abs(part.y()), Math.abs(part.z()))));
        }
        return max + 1;
    }
}
