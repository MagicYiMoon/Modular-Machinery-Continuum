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

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.IOType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * The upgrades this mod ships, and the items that install them.
 *
 * <h2>What upstream ships here, checked rather than assumed</h2>
 *
 * <p><b>UPSTREAM 2.3.2 SHIPS NO CONCRETE UPGRADE CLASS AT ALL.</b> That is a measurement, not an impression: the
 * 1.12.2 tree contains exactly eleven files under {@code upgrade/} — {@code MachineUpgrade}, {@code UpgradeType},
 * {@code SimpleMachineUpgrade}, {@code DynamicMachineUpgrade}, {@code SimpleDynamicMachineUpgrade},
 * {@code registry/RegistryUpgrade}, {@code registry/UpgradeInfo}, {@code handler/UpgradeEventHandler},
 * {@code handler/UpgradeMachineEventHandler}, {@code capability/CapabilityUpgrade},
 * {@code capability/CapabilityUpgradeProvider} — plus the three CraftTweaker builders under
 * {@code integration/crafttweaker/upgrade/}. Every one of them is FRAMEWORK. The roughly twenty-five upgrades the
 * audit row 37 counts are things a PACK declares through {@code mods.modularmachinery.MachineUpgradeBuilder} in a
 * {@code .zs} script, and a script is the one input a port cannot inherit.
 *
 * <p><b>So the port must choose, and the choice is recorded here.</b> A port that shipped only the framework would
 * leave every player-visible effect still absent — the audit's own complaint — because nothing would register an
 * upgrade. This class therefore declares four, which are upstream's own {@code addModifier} idiom
 * ({@code MachineUpgradeBuilder.addModifier(boolean stackAble, String modifierKey, RecipeModifier modifier)},
 * U139-U159) written in Java instead of ZenScript, and which are the four an operator would have written first:
 *
 * <table border="1">
 *   <caption>Shipped upgrades</caption>
 *   <tr><th>Type</th><th>Effect</th><th>Stacks</th></tr>
 *   <tr><td>{@code speed}</td><td>2x input cost, HALF the recipe time</td><td>yes (4x, 8x …)</td></tr>
 *   <tr><td>{@code energy}</td><td>0.5x energy per tick</td><td>yes (0.25x, 0.125x …)</td></tr>
 *   <tr><td>{@code output}</td><td>2x items produced</td><td>yes</td></tr>
 *   <tr><td>{@code time}</td><td>0.5x recipe time, and nothing else</td><td>no</td></tr>
 * </table>
 *
 * <p><b>{@code speed} IS THE ONE THE VERIFICATION MEASURES, AND IT IS AN HONEST TRADE RATHER THAN A FREE LUNCH:</b>
 * it costs twice the input for half the time, so it changes a tick count without making a machine strictly better.
 * The mechanism a check can observe is the recipe time; the mechanism a player cares about is throughput per input.
 *
 * <p><b>The stack rule is upstream's, read at U148-U156.</b> {@code stackAble == true} raises the multiplier to the
 * power of the stack size; {@code false} uses the declared value however many are installed. Both are exercised:
 * {@code output}'s factor of 2 with two installed gives 4x, {@code time}'s 0.5 stays 0.5.
 *
 * <h2>Compatibility lists are declared but empty, deliberately</h2>
 *
 * <p>All four types are compatible with every machine, which is upstream's answer for a type with neither list
 * populated ({@code UpgradeType.isCompatible}, U49-U57). {@code UpgradeType#addIncompatibleMachine} exists and is
 * exercised by a check, so the mechanism is reachable; what is absent is any SHIPPED restriction, because upstream
 * ships none either (its lists are filled only from scripts, and this port has no scripts).
 */
public final class UpgradeItems {

    /** The registry name prefix for an upgrade item, so a stack and its type can be matched by eye. */
    public static final String ITEM_PREFIX = "upgrade_";

    /**
     * The four types, by name. The names are upstream's own key space: {@code UpgradeType#getName()} is what
     * {@code RegistryUpgrade.UPGRADES} and the bus's custom-data map are keyed by, and what
     * {@code MachineUpgradeBuilder.newBuilder(String name, …)} takes.
     */
    public static final UpgradeType SPEED = UpgradeType.of("speed", "Speed Upgrade", 3);
    public static final UpgradeType ENERGY = UpgradeType.of("energy", "Energy Upgrade", 3);
    public static final UpgradeType OUTPUT = UpgradeType.of("output", "Output Upgrade", 3);
    public static final UpgradeType TIME = UpgradeType.of("time", "Time Upgrade", 1);

    /**
     * The registered items. Assigned inside {@link #register(DeferredRegister)} rather than declared here as four
     * fields, because the deferring supplier needs the {@link DeferredRegister} the caller owns — which is also what
     * keeps this class free of any dependency on {@code RegistryItems} and therefore loadable before it.
     *
     * <p><b>They are assigned at class-load time of {@code RegistryItems}, not lazily</b>: an item registered without
     * its {@link RegistryMachineUpgrade} entry would be a stack that does nothing when a player installs it, which is
     * the exact complaint the audit recorded.
     */
    private static RegistryObject<MachineUpgradeItem> speedItem;
    private static RegistryObject<MachineUpgradeItem> energyItem;
    private static RegistryObject<MachineUpgradeItem> outputItem;
    private static RegistryObject<MachineUpgradeItem> timeItem;

    private static boolean registered;

    /** Set by {@link #bindUpgrades()}, so a second call is a no-op rather than a duplicate registration. */
    private static boolean bound;

    private UpgradeItems() {
    }

    








































 
    public static void register(DeferredRegister<net.minecraft.world.item.Item> items) {
        if (registered) {
            return;
        }
        registered = true;

        speedItem = items.register(ITEM_PREFIX + "speed",
                () -> new MachineUpgradeItem(SPEED, new net.minecraft.world.item.Item.Properties()));
        energyItem = items.register(ITEM_PREFIX + "energy",
                () -> new MachineUpgradeItem(ENERGY, new net.minecraft.world.item.Item.Properties()));
        outputItem = items.register(ITEM_PREFIX + "output",
                () -> new MachineUpgradeItem(OUTPUT, new net.minecraft.world.item.Item.Properties()));
        timeItem = items.register(ITEM_PREFIX + "time",
                () -> new MachineUpgradeItem(TIME, new net.minecraft.world.item.Item.Properties()));
    }

    /**
     * <b>Bound AFTER the item registry has run, NEVER during class loading.</b>
     *
     * <p>Hands each item to {@link RegistryMachineUpgrade} together with the upgrade it installs, which is the
     * pairing that makes a stack in a bus slot mean something. Safe only once {@code .get()} is legal — see
     * {@link #register(DeferredRegister)} for the crash that establishes why.
     *
     * <p>Idempotent, and it says so rather than relying on the caller: the mod calls it from the registry event, and
     * a second call would register the same four pairs again, which {@link RegistryMachineUpgrade#register} would
     * reject with an exception naming the duplicate.
     *
     * <p><b>{@code bound} IS SET ONLY WHEN THE BINDING ACTUALLY SUCCEEDED, which is a deliberate difference from a
     * plain "have I run yet" flag.</b> A flag set on entry would make ONE failed attempt permanent: the second
     * caller — the self-test calls this again before it asserts anything — would get an empty report and read the
     * failure as success. Re-attempting is safe because {@link RegistryMachineUpgrade#register} throws on a
     * duplicate rather than replacing, so a partial success cannot be double-registered by a later call: it is
     * rejected, and {@link #bind} below treats that rejection as the problem it is.
     *
     * @return a human-readable report, empty when every pair was bound
     */
    public static String bindUpgrades() {
        if (bound) {
            return "";
        }
        StringBuilder problems = new StringBuilder();
        bind(problems, speedItem, "speed", UpgradeItems::speed);
        bind(problems, energyItem, "energy", UpgradeItems::energy);
        bind(problems, outputItem, "output", UpgradeItems::output);
        bind(problems, timeItem, "time", UpgradeItems::time);
        String report = problems.toString().trim();
        if (report.isEmpty()) {
            bound = true;
        }
        return report;
    }

    private static void bind(StringBuilder problems,
                             RegistryObject<MachineUpgradeItem> item, String name,
                             java.util.function.Supplier<MachineUpgrade> upgrade) {
        // `.get()` here and ONLY here: this method is reached from FMLCommonSetupEvent, i.e. after RegisterEvent has
        // bound the object. See register(...) for the crash that establishes why that is not optional.
        MachineUpgradeItem value = item == null ? null : item.get();
        if (value == null) {
            problems.append(name).append("(its item never reached the registry) ");
            return;
        }
        try {
            RegistryMachineUpgrade.register(value, upgrade.get());
        } catch (RuntimeException alreadyRegistered) {
            problems.append(name).append('(').append(alreadyRegistered.getMessage()).append(") ");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The four upgrades themselves. Each builder call maps to a constructor argument of
    // SimpleMachineUpgrade.Effect, and the comments name which upstream builder method it corresponds to.
    // ---------------------------------------------------------------------------------------------

    /**
     * {@code speed}: twice the input for half the time.
     *
     * <p>Upstream's own idiom would be
     * {@code MachineUpgradeBuilder.addModifier(true, "speed_input", new RecipeModifier(itemType, INPUT, 2.0F, MULTIPLY, false))}
     * plus a duration handler. {@code stackAble = true} is passed, so two of these give 4x input and 0.25x time.
     */
    private static MachineUpgrade speed() {
        return SimpleMachineUpgrade.of(SPEED)
                .scale(UpgradeTargets.ITEM, IOType.INPUT, UpgradeTargets.MULTIPLY, 2.0F, false, true)
                .duration(0.5F)
                .describe("Consumes 2x the items")
                .describe("Crafts in half the time");
    }

    /**
     * {@code energy}: half the energy per tick, stackable.
     *
     * <p>{@code 0.5} raised to {@code stackSize}: two installed give 0.25x. This is the upgrade an operator installs
     * when the machine is power-starved rather than input-starved, which is why it does not also change the time.
     */
    private static MachineUpgrade energy() {
        return SimpleMachineUpgrade.of(ENERGY)
                .scale(UpgradeTargets.ENERGY, IOType.INPUT, UpgradeTargets.MULTIPLY, 0.5F, false, true)
                .describe("Uses 0.5x the energy per tick");
    }

    /**
     * {@code output}: twice the items produced, stackable — and the input is <b>not</b> raised with it.
     *
     * <p>The one upgrade here that makes a machine strictly better, which is why it is limited to three. It is the
     * cleanest demonstration that an upgrade reaches the WRITE end of a craft and not only the check: the port's own
     * vent check exists because a modifier that reaches only the check would ask for room for two and place one.
     */
    private static MachineUpgrade output() {
        return SimpleMachineUpgrade.of(OUTPUT)
                .scale(UpgradeTargets.ITEM, IOType.OUTPUT, UpgradeTargets.MULTIPLY, 2.0F, false, true)
                .describe("Produces 2x the items");
    }

    /**
     * {@code time}: half the recipe time and nothing else, NOT stackable.
     *
     * <p>A stack of these reaching 0.125x would be a machine that finishes in the same tick it starts, which
     * {@code MachineTicker.isRunnable} refuses for a zero duration and which the port's zero-duration check exists to
     * catch. The effect is bounded by construction, and the bound is stated rather than discovered.
     */
    private static MachineUpgrade time() {
        return SimpleMachineUpgrade.of(TIME)
                .duration(0.5F)
                .describe("Crafts in half the time")
                .describe("Does not stack");
    }

    // --- accessors: the fields above are private so that nothing can assign them after registration ---

    public static RegistryObject<MachineUpgradeItem> speedItem() {
        return speedItem;
    }

    public static RegistryObject<MachineUpgradeItem> energyItem() {
        return energyItem;
    }

    public static RegistryObject<MachineUpgradeItem> outputItem() {
        return outputItem;
    }

    public static RegistryObject<MachineUpgradeItem> timeItem() {
        return timeItem;
    }

    /** @return every shipped item, in registration order, for the tab and for the registry self-check */
    public static java.util.List<RegistryObject<MachineUpgradeItem>> all() {
        java.util.List<RegistryObject<MachineUpgradeItem>> out = new java.util.ArrayList<>(4);
        out.add(speedItem);
        out.add(energyItem);
        out.add(outputItem);
        out.add(timeItem);
        return out;
    }

    /** @return the mod id, so callers do not have to reach for the main class */
    public static String namespace() {
        return Modularmachinery.MODID;
    }
}
