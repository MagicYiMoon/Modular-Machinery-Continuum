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
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.machine.factory;

import hellfirepvp.modularmachinery.common.tiles.TileFactoryController;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The factory controllers bound to ONE machine definition -- the family.
 *
 * <p>MMC 1.1 registers dedicated factory blocks during startup, following upstream's has-factory
 * setting. This runtime family additionally tracks live block entities by machine name, including
 * legacy generic controllers, so reloads do not invalidate capacity or HUD statistics.</p>
 *
 * <h2>Consumers, named because a registry nothing reads is this project's most repeated defect</h2>
 *
 * <ul>
 *   <li>{@code TileFactoryController.syncFamily} / {@code setRemoved} -- the writes, on the tile's own
 *       lifecycle.</li>
 *   <li>{@code MachineProbeProvider} -- the reads: the HUD shows how many controllers serve the machine a
 *       player is looking at, and the family's thread totals (upstream MMInfoProvider L131-U138 shows the
 *       thread numbers on the same overlay).</li>
 *   <li>{@code MachineSelfTest} -- asserts a formed, bound controller appears in its machine's family.</li>
 * </ul>
 *
 * <p>Server-side only by construction: only a placed, bound controller is registered, and a client copy of
 * one skips the registration (see {@link TileFactoryController#syncFamily()}).</p>
 */
public final class FactoryControllerFamily {

    private FactoryControllerFamily() {
    }

    /**
     * Machine name -> the live factory controllers serving it.
     *
     * <p>INSERTION-ORDERED, so anything listing a family reports a stable order between ticks rather than
     * HashMap's. Equality is identity, which is what a block entity wants: two controllers at different
     * positions are never "the same controller".</p>
     */
    private static final Map<String, List<TileFactoryController>> FAMILIES = new LinkedHashMap<>();

    /**
     * Registers a controller under its bound machine, or does nothing when it has none.
     *
     * <p>IDEMPOTENT rather than assert-and-throw: its call sites are lifecycle hooks ({@code setFormed},
     * {@code setMachineName}, {@code load}) that legitimately fire more than once for one controller, and a
     * second registration must not put the controller in the list twice -- a duplicate would double every
     * family count a player sees.</p>
     */
    public static void register(TileFactoryController controller) {
        if (controller == null) {
            return;
        }
        String machine = controller.getMachineName();
        if (machine == null || machine.isEmpty()) {
            return;
        }
        List<TileFactoryController> family = FAMILIES.computeIfAbsent(machine, name -> new ArrayList<>());
        if (!family.contains(controller)) {
            family.add(controller);
        }
    }

    /**
     * Removes a controller from its machine's family; the last removal drops the key.
     *
     * <p>Keyed by the controller's CURRENT machine name, and called before the binding is cleared where both
     * happen together -- a controller that unregistered under a machine it no longer names would stay in the
     * old family for as long as the server runs.</p>
     */
    public static void unregister(TileFactoryController controller) {
        if (controller == null) {
            return;
        }
        String machine = controller.getMachineName();
        if (machine == null) {
            return;
        }
        List<TileFactoryController> family = FAMILIES.get(machine);
        if (family == null) {
            return;
        }
        family.remove(controller);
        if (family.isEmpty()) {
            FAMILIES.remove(machine);
        }
    }

    /**
     * The controller list for one machine definition.
     *
     * @param machineName the machine's {@code registryname}, as {@code DynamicMachine.getRegistryName()} holds it
     * @return a copy, so a caller iterating it cannot be broken by a controller that unloads mid-iteration
     *
     * <p>DEAD ENTRIES ARE DROPPED HERE RATHER THAN TRUSTED TO A LIFECYCLE CALL, and that is deliberate: a
     * block entity that is removed without {@code setRemoved()} -- or a chunk that unloads by a path this
     * port has not read -- would otherwise leave a strong reference to a dead level in a static map.</p>
     */
    public static List<TileFactoryController> withMachine(String machineName) {
        if (machineName == null) {
            return List.of();
        }
        List<TileFactoryController> family = FAMILIES.get(machineName);
        if (family == null) {
            return List.of();
        }
        pruneDead(family);
        if (family.isEmpty()) {
            FAMILIES.remove(machineName);
            return List.of();
        }
        return List.copyOf(family);
    }

    /**
     * The family's factory capacity, read the way the HUD needs it.
     *
     * @return {@code [threadsRunning, threadsMax]}, summed over the controllers bound to this machine;
     *         {@code {0, 0}} when the machine has no factory controller. A controller that is not formed
     *         contributes nothing running but still contributes its ceiling, because its block is still
     *         standing and will run again once it forms.
     */
    public static int[] threads(String machineName) {
        long running = 0;
        long max = 0;
        for (TileFactoryController controller : withMachine(machineName)) {
            running += controller.getRunningThreadCount();
            max += controller.getMaxThreads();
        }
        return new int[]{(int) Math.min(Integer.MAX_VALUE, running), (int) Math.min(Integer.MAX_VALUE, max)};
    }

    private static void pruneDead(List<TileFactoryController> family) {
        Iterator<TileFactoryController> it = family.iterator();
        while (it.hasNext()) {
            TileFactoryController controller = it.next();
            if (controller.isRemoved() || controller.getLevel() == null) {
                it.remove();
            }
        }
    }
}
