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
package hellfirepvp.modularmachinery.common.data;

import hellfirepvp.modularmachinery.common.block.prop.EnergyHatchData;
import hellfirepvp.modularmachinery.common.block.prop.FluidHatchSize;
import hellfirepvp.modularmachinery.common.block.prop.ParallelControllerData;
import hellfirepvp.modularmachinery.common.block.prop.UpgradeBusData;
import hellfirepvp.modularmachinery.common.machine.RecipeFailureActions;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The port's configuration.
 *
 * <h2>Why this file exists now, and not earlier</h2>
 *
 * <p>Three separate features were waiting on it, and each had been recorded as a gap in its own javadoc rather than
 * as a decision:
 *
 * <ul>
 *   <li><b>{@code machineColor}</b> — {@code ItemModularium}'s tint. Upstream read it from config as a HEX STRING
 *       with the fallback passed in by the caller, which is why the default could not be recovered from the class
 *       alone (§288). The port used a measured constant and labelled it a placeholder.</li>
 *   <li><b>The TOP progress bar's colours</b> — exposed under display.theoneprobe with upstream defaults.</li>
 *   <li><b>Energy hatch capacities</b> — {@code EnergyHatchData} carries the jar's per-tier values and its javadoc
 *       notes that {@code loadFromConfig} was meant to override them at runtime.</li>
 * </ul>
 *
 * <p>The first was implemented on its own, because it is the one with a loop that can be closed and verified end to
 * end: a config value, read by a registration, observable in a client.
 *
 * <h2>The four tier families are the second (audit row 40)</h2>
 *
 * <p>Upstream's {@code Config.java:63-77} {@code load()} calls {@code FluidHatchSize.loadFromConfig},
 * {@code EnergyHatchData.loadFromConfig}, {@code ParallelControllerData.loadFromConfig} and
 * {@code UpgradeBusData.loadFromConfig} with the just-parsed {@code net.minecraftforge.common.config.Configuration},
 * and each of the four writes the numbers into its own <b>non-final instance fields</b>. So on 1.12.2 a server owner
 * changes hatch capacity, hatch transfer, the parallelism ceiling and the upgrade-slot counts by editing the config
 * file. The port could not change any of them: its enums held {@code private final} fields and its {@code Config}
 * knew only {@code machineColor}.
 *
 * <p><b>THE MECHANISM DIFFERS BECAUSE FORGE'S CONFIG API DIFFERS, AND THE DIFFERENCE IS RECORDED RATHER THAN
 * HIDDEN.</b> 1.20.1 has no {@code Configuration}. {@code ForgeConfigSpec} builds a spec once; the file is parsed
 * against it; each declared value is then read through {@code ConfigValue#get()}, which resolves the entry from the
 * parsed file and caches it. So the port declares <b>upstream's own section names and key names</b> — a config file
 * written for upstream reads correctly here — and the four enums read them <b>at the point of use</b> through the
 * typed accessors below, instead of having a number assigned into a field once at startup.
 *
 * <p>The observable result is upstream's exactly: the number a hatch, a controller or a bus reports is the number in
 * the config file. It is also one step further than upstream in one respect, stated because it is a real difference:
 * upstream's values were frozen at startup, while a value re-read after a reload takes effect without a restart. That
 * is a consequence of where the read happens, not a feature bolted on — {@code ConfigValue#get()} is what
 * {@code loadFromConfig} did with its own {@code getInt}, just moved to the reader.
 *
 * <p><b>An unloaded spec falls back to the tier's own default rather than throwing.</b> A dedicated server parses its
 * configs before a world exists, but a client colour handler — and, in this port, {@code ItemModularium}'s tint a
 * registration time — can reach an enum before the spec is loaded. {@code ConfigValue#get()} throws
 * {@code IllegalStateException} in that window and a tooltip may not be the thing that crashes a client, so every
 * accessor tests {@link ForgeConfigSpec#isLoaded()} first. <b>The value this class reports is therefore never a
 * value nobody chose</b>: it is the config file's, or it is the tier table's own default.
 *
 * <h2>COMMON, not CLIENT</h2>
 *
 * <p>{@code machineColor} is read by a client colour handler, but it is not client STATE — it describes the mod's
 * appearance and belongs in a file the player can edit without needing a client to exist. A CLIENT config would also
 * make the value unavailable to a dedicated server that might want to describe the same colour in a chat message
 * later. COMMON is the honest scope, and it is the scope the tier numbers need too: hatch capacity is decided by the
 * server and must be knowable to it.
 */
public final class Config {

    /** The spec, built once in the static initialiser. */
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue FACTORY_CONTROLLERS_DEFAULT;

    private static final ForgeConfigSpec.ConfigValue<String> MACHINE_COLOR;
    private static final ForgeConfigSpec.BooleanValue MACHINE_PARALLEL_DEFAULT;
    private static final ForgeConfigSpec.BooleanValue RECIPE_PARALLEL_DEFAULT;
    private static final ForgeConfigSpec.IntValue MAX_MACHINE_PARALLELISM;
    private static final ForgeConfigSpec.IntValue FACTORY_MAX_THREADS;
    private static final ForgeConfigSpec.IntValue MANA_PROVIDER_CAPACITY;
    private static final ForgeConfigSpec.BooleanValue ENABLE_SECURITY_SYSTEM;
    private static final ForgeConfigSpec.BooleanValue ENABLE_FLUX_NETWORKS;
    private static final ForgeConfigSpec.BooleanValue CLEAN_CUSTOM_DATA_ON_STRUCTURE_FAILURE;
    private static final ForgeConfigSpec.ConfigValue<String> DEFAULT_FAILURE_ACTION;

    // MMCE's survival assembler defaults to a vanilla stick.  Keep the item configurable so packs can
    // use a binding tool without changing the machine definitions.
    private static final ForgeConfigSpec.ConfigValue<String> AUTO_ASSEMBLY_ITEM;
    private static final ForgeConfigSpec.IntValue AUTO_ASSEMBLY_INTERVAL;
    private static final ForgeConfigSpec.BooleanValue AUTO_ASSEMBLY_NEED_ALL;
    private static final ForgeConfigSpec.BooleanValue AUTO_ASSEMBLY_SKIP_NBT;

    /**
     * The default, and it is NOT invented: measured from the mod's own art, {@code blockcasing_plain.png}'s mean
     * opaque colour, which is (215, 215, 215).
     *
     * <p>Upstream's own default is not recoverable — {@code Config.machineColor} is assigned from a method PARAMETER,
     * so the class alone does not contain it (§288). A measured value from the mod's texture is the closest thing to
     * it that exists in the port's hands, and it is stated as measured rather than presented as upstream's.
     */
    private static final String DEFAULT_MACHINE_COLOR = "D7D7D7";

    // =============================================================================================
    // Audit row 41 — the energy unit switch. Upstream's defaults, from the loadFromConfig bodies at
    // U31/U32/U33/U35: three booleans true and the type "FE". They are constants here because they are
    // needed TWICE — once as the spec's declared default and once as the fallback for the window before
    // the spec is loaded — and a default written in two places is a default that can disagree with
    // itself (the defect §65/§78/§99 recorded three times over on the enum tables).
    // =============================================================================================
    private static final boolean DEFAULT_DISPLAY_FE_TOOLTIP = true;
    private static final boolean DEFAULT_DISPLAY_IC2_TOOLTIP = true;
    private static final boolean DEFAULT_DISPLAY_GT_TOOLTIP = true;
    private static final String DEFAULT_DISPLAY_ENERGY_TYPE = "FE";

    private static final ForgeConfigSpec.BooleanValue ENERGY_DISPLAY_FE;
    private static final ForgeConfigSpec.BooleanValue ENERGY_DISPLAY_IC2;
    private static final ForgeConfigSpec.BooleanValue ENERGY_DISPLAY_GT;
    private static final ForgeConfigSpec.ConfigValue<String> ENERGY_DISPLAY_TYPE;

    // The One Probe display settings are kept in common config, matching the 1.12 integration.
    private static final ForgeConfigSpec.ConfigValue<String> TOP_RECIPE_FILLED;
    private static final ForgeConfigSpec.ConfigValue<String> TOP_RECIPE_ALTERNATE;
    private static final ForgeConfigSpec.ConfigValue<String> TOP_RECIPE_BORDER;
    private static final ForgeConfigSpec.ConfigValue<String> TOP_RECIPE_BACKGROUND;
    private static final ForgeConfigSpec.ConfigValue<String> TOP_FAILURE_FILLED;
    private static final ForgeConfigSpec.ConfigValue<String> TOP_FAILURE_ALTERNATE;
    private static final ForgeConfigSpec.ConfigValue<String> TOP_FAILURE_BORDER;
    private static final ForgeConfigSpec.BooleanValue TOP_DECIMAL;
    private static final ForgeConfigSpec.BooleanValue TOP_PARALLEL;

    // =============================================================================================
    // Audit row 40 — the four tier families, declared with upstream's own keys.
    //
    // Upstream call sites (all four are lines of Config.load): U64, U65, U66, U67.
    // Upstream key strings, read out of each loadFromConfig BODY rather than from its signature:
    //   FluidHatchSize          U45  getInt("size",             "fluidhatch." + UPPERCASE, def, 1, Integer.MAX_VALUE)
    //   EnergyHatchData         U69  get   ("energyhatch.size",  UPPERCASE, def, ...).getLong()
    //                           U71  get   ("energyhatch.limit", UPPERCASE, def, ...).getLong()
    //   ParallelControllerData  U25  getInt("max-parallelism",  "parallel-controller." + lowercase, def, 1, MAX)
    //   UpgradeBusData          U25  getInt("max-upgrade_slot", "upgrade-bus." + lowercase, def, 1, 18)
    //
    // Note the underscore in "max-upgrade_slot" -- it is upstream's, not a typo, and it is kept so a config
    // written for upstream keeps working. Note also the CASE RULE, which differs per family: the two hatch
    // families uppercase the constant name, the other two lowercase it. Reproducing that is not cosmetic;
    // it decides which key a value is read from.
    //
    // The per-tier entries are declared by ITERATING THE ENUM, so the tier list exists in exactly one place
    // (the enum's constants). Writing eight defines per family here would be a second copy of a table that
    // already exists -- the shape §65 found in the invented MachineComponentTier, which had a value set that
    // matched neither of the two components it served.
    //
    // LOAD ORDER IS SAFE IN BOTH DIRECTIONS, and that was checked rather than assumed: the four enums read
    // Config only inside method bodies (never from a constructor), and this initialiser only calls
    // `values()`/`name()`/a getter on them, so no class can be initialised while the other is mid-clinit.
    // =============================================================================================

    private static final Map<FluidHatchSize, ForgeConfigSpec.ConfigValue<Integer>> FLUID_HATCH_SIZE =
            new IdentityHashMap<>();
    private static final Map<EnergyHatchData, ForgeConfigSpec.ConfigValue<Long>> ENERGY_HATCH_SIZE =
            new IdentityHashMap<>();
    private static final Map<EnergyHatchData, ForgeConfigSpec.ConfigValue<Long>> ENERGY_HATCH_LIMIT =
            new IdentityHashMap<>();
    private static final Map<ParallelControllerData, ForgeConfigSpec.ConfigValue<Integer>> PARALLEL_CONTROLLER_MAX =
            new IdentityHashMap<>();
    private static final Map<UpgradeBusData, ForgeConfigSpec.ConfigValue<Integer>> UPGRADE_BUS_SLOTS =
            new IdentityHashMap<>();

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("How Modular Machinery looks.").push("appearance");

        MACHINE_COLOR = builder
                .comment("The machine colour, as a hex string without a leading #.",
                        "Tints the modularium, whose texture is authored in pure greyscale for this purpose.",
                        "Default D7D7D7 is the measured mean colour of the mod's own machine casing texture.")
                .define("machineColor", DEFAULT_MACHINE_COLOR);

        builder.pop();

        builder.comment("Controller ownership protection.",
                        "When enabled, a controller may only be broken or re-placed by its owner.",
                        "Creative players bypass this check.")
                .push("general");
        ENABLE_SECURITY_SYSTEM = builder.define("enable-security-system", false);
        ENABLE_FLUX_NETWORKS = builder
                .comment("Expose the Flux Networks energy capability on ordinary FE hatches.",
                        "Shares the existing FE buffer and respects its direction, capacity and transfer limit.",
                        "Disabling this does not disable ordinary Forge Energy connections.")
                .define("enable-fluxnetworks-integration", true);
        CLEAN_CUSTOM_DATA_ON_STRUCTURE_FAILURE = builder
                .comment("Clear controller customData and custom modifiers when its structure becomes invalid.")
                .define("clean-custom-data-on-structure-check-failed", false);
        DEFAULT_FAILURE_ACTION = builder
                .comment("Default progress policy when a machine does not declare failure-action.",
                        "Allowed values: still, decrease, reset.")
                .define("default-failure-actions", "still");
        AUTO_ASSEMBLY_ITEM = builder
                .comment("Item that starts survival machine assembly (MMCE default: minecraft:stick).")
                .define("auto-assembly-item", "minecraft:stick");
        AUTO_ASSEMBLY_INTERVAL = builder
                .comment("Ticks between blocks placed by survival auto-assembly (MMCE default: 5).")
                .defineInRange("auto-assembly-interval", 5, 1, 1000);
        AUTO_ASSEMBLY_NEED_ALL = builder
                .comment("Require every assembly material before starting the queue.")
                .define("auto-assembly-need-all", true);
        AUTO_ASSEMBLY_SKIP_NBT = builder
                .comment("Skip structure parts that declare block-entity NBT during auto-assembly.",
                        "MMCE source setting: skipBlockContainNBT (default: false).")
                .define("auto-assembly-skip-block-containing-nbt", false);
        builder.pop();

        // --- fluid hatch capacity, from FluidHatchSize.loadFromConfig (upstream U45) ---
        builder.comment("Fluid hatch tank sizes, in millibuckets (mB).",
                        "One section per tier under the tier's own name; the key inside it is `size`.",
                        "Upstream: Config.java:64 -> FluidHatchSize.loadFromConfig (FluidHatchSize.java:45),",
                        "section \"fluidhatch.<TIER>\", key \"size\", range 1 .. 2147483647.")
                .push("fluidhatch");
        for (FluidHatchSize tier : FluidHatchSize.values()) {
            // THE DEFAULT IS THE `default` ACCESSOR, NOT THE LIVE GETTER -- see the warning further down. The live
            // getter reads THIS spec, so using it here would make the spec's own default a function of the spec.
            FLUID_HATCH_SIZE.put(tier, builder
                    .comment("Tank capacity for the " + tier.getSerializedName() + " fluid hatch, in mB.",
                            "Default " + tier.getDefaultCapacity() + " is the jar's own value for this tier.",
                            "Range 1 .. 2147483647, matching upstream's lower bound of 1.")
                    .defineInRange(key(tier.name()), tier.getDefaultCapacity(), 1, Integer.MAX_VALUE));
        }
        builder.pop();

        // --- energy hatch storage and transfer, from EnergyHatchData.loadFromConfig (upstream U69/U71) ---
        builder.comment("Energy hatch storage and transfer, in Forge Energy (FE).",
                        "Upstream: Config.java:65 -> EnergyHatchData.loadFromConfig",
                        "(EnergyHatchData.java:69 and :71), sections \"energyhatch.size\" /",
                        "\"energyhatch.limit\"; this port uses lowercase tier keys.",
                        "UPSTREAM DECLARES BOTH OVER 0 .. Long.MAX_VALUE; this port caps them at",
                        "Integer.MAX_VALUE, and that is a recorded difference rather than a preference:",
                        "Forge Energy on 1.20.1 is int-based (IEnergyStorage#getMaxEnergyStored returns int),",
                        "so a hatch above 2.1 GE cannot be represented through the capability at all and would",
                        "clamp silently. See EnergyHatchData#getMaxEnergy, which says the same thing.")
                .push("energyhatch");
        for (EnergyHatchData tier : EnergyHatchData.values()) {
            ENERGY_HATCH_SIZE.put(tier, builder
                    .comment("Energy buffer for the " + tier.getSerializedName() + " energy hatch, in FE.",
                            "Default " + tier.getDefaultMaxEnergy() + " is the Continuum capacity for this tier; existing settings are preserved.")
                    .defineInRange(key("size." + tier.name()), (long) tier.getDefaultMaxEnergy(), 1L,
                            (long) Integer.MAX_VALUE));
            ENERGY_HATCH_LIMIT.put(tier, builder
                    .comment("Maximum FE moved per tick by the " + tier.getSerializedName() + " energy hatch.",
                            "Default " + tier.getDefaultMaxTransfer() + " is the jar's own value for this tier.")
                    .defineInRange(key("limit." + tier.name()), (long) tier.getDefaultMaxTransfer(), 1L,
                            (long) Integer.MAX_VALUE));
        }
        builder.pop();

        // --- parallel controller ceiling, from ParallelControllerData.loadFromConfig (upstream U25) ---
        builder.comment("Parallel controller ceilings: the most recipe copies one controller may run at once.",
                        "Upstream: Config.java:66 -> ParallelControllerData.loadFromConfig",
                        "(ParallelControllerData.java:25), section \"parallel-controller.<tier>\",",
                        "key \"max-parallelism\", range 1 .. 2147483647.")
                .push("parallel-controller");
        MACHINE_PARALLEL_DEFAULT = builder
                .comment("Default machine batching policy. Explicit machine parallelizable/script settings override it.")
                .define("machine-parallelize-enabled-bydefault", true);
        RECIPE_PARALLEL_DEFAULT = builder
                .comment("Default recipe batching policy. Explicit recipe parallelized/script settings override it.")
                .define("recipe-parallelize-enabled-bydefault", true);
        MAX_MACHINE_PARALLELISM = builder
                .comment("Default machine batch ceiling, independent of factory thread count.")
                .defineInRange("max-machine-parallelism", 2048, 1, Integer.MAX_VALUE);
        for (ParallelControllerData tier : ParallelControllerData.values()) {
            PARALLEL_CONTROLLER_MAX.put(tier, builder
                    .comment("Maximum parallelism for the " + tier.getSerializedName() + " parallel controller.",
                            "Default " + tier.getDefaultMaxParallelism() + " is the jar's own value for this tier.")
                    .defineInRange(key(tier.name()), tier.getDefaultMaxParallelism(), 1, Integer.MAX_VALUE));
        }
        builder.pop();

        builder.push("mods").push("botania");
        MANA_PROVIDER_CAPACITY = builder.comment("ModularMagic mana buffer capacity, matching the source default.")
                .defineInRange("Mana Capacity", 100000, 0, Integer.MAX_VALUE);
        builder.pop(2);

        builder.push("factory-system");
        FACTORY_CONTROLLERS_DEFAULT = builder.comment("Register a dedicated factory controller for every machine at startup. Requires restart.")
                .worldRestart().define("enable-factory-controller-bydefault", false);
        FACTORY_MAX_THREADS = builder
                .comment("Default factory thread ceiling. MMCE's loaded default is 10 (its field initializer was 20).",
                        "Changes apply when machine definitions are reloaded; existing paid runs retain their batch.")
                .defineInRange("default-factory-max-thread", 10, 1, 100);
        builder.pop();

        // --- upgrade bus slots, from UpgradeBusData.loadFromConfig (upstream U25) ---
        builder.comment("Upgrade bus slot counts: how many machine upgrades a bus of a tier holds.",
                        "Upstream: Config.java:67 -> UpgradeBusData.loadFromConfig (UpgradeBusData.java:25),",
                        "section \"upgrade-bus.<tier>\", key \"max-upgrade_slot\" -- WITH AN UNDERSCORE, which",
                        "is upstream's spelling and is kept so an upstream config file reads correctly here.")
                .push("upgrade-bus");
        for (UpgradeBusData tier : UpgradeBusData.values()) {
            UPGRADE_BUS_SLOTS.put(tier, builder
                    .comment("Upgrade slots for the " + tier.getSerializedName() + " upgrade bus.",
                            "Default " + tier.getDefaultUpgradeSlots() + " is the jar's own value for this tier.",
                            "Upstream bounds this at 1 .. 18 and 18 is a GEOMETRY fact, not taste:",
                            "ContainerUpgradeBus wraps every 3 slots from y=17 and the panel is 213",
                            "pixels tall, so a larger count would be drawn off the bottom of the screen.")
                    .defineInRange(key(tier.name()), tier.getDefaultUpgradeSlots(), 1, 18));
        }
        builder.pop();

        // --- the energy unit switch, from EnergyDisplayUtil.loadFromConfig (upstream U68) ---
        //
        // Audit row 41. Upstream's section is "display.energy" and the four key names below are read out of that
        // method's BODY (U31/U32/U33/U35), not inferred from its signature. The type key is a FREE STRING with
        // upstream's own fallback: EnergyDisplayUtil.EnergyType.getType answers FE for anything it cannot parse,
        // which is what makes a config typo cost the player their unit and not their game.
        builder.comment("Which unit every energy figure in this mod is displayed in.",
                        "Upstream: Config.java:68 -> EnergyDisplayUtil.loadFromConfig",
                        "(EnergyDisplayUtil.java:31-35), section \"display.energy\".")
                .push("display").push("energy");
        ENERGY_DISPLAY_FE = builder
                .comment("Show the FE/RF storage and transfer lines on an energy hatch tooltip.",
                        "Upstream key FE_RF_Tooltip. Both units have multiplier 1, so this gates the two lines",
                        "rather than converting them: with it false a hatch reports no energy figures at all.")
                .define("FE_RF_Tooltip", DEFAULT_DISPLAY_FE_TOOLTIP);
        ENERGY_DISPLAY_IC2 = builder
                .comment("Show IC2 EU section on an energy hatch tooltip.",
                        "Upstream key IC2_EU_Tooltip; upstream notes it only has an effect when IC2 is installed,",
                        "and the port implements no IC2 integration, so nothing emits that section yet -- the",
                        "switch is declared and readable rather than missing.")
                .define("IC2_EU_Tooltip", DEFAULT_DISPLAY_IC2_TOOLTIP);
        ENERGY_DISPLAY_GT = builder
                .comment("Show GregTech EU section on an energy hatch tooltip.",
                        "Upstream key GT_EU_Tooltip; the IC2_EU_Tooltip note above applies for the same reason.")
                .define("GT_EU_Tooltip", DEFAULT_DISPLAY_GT_TOOLTIP);
        ENERGY_DISPLAY_TYPE = builder
                .comment("The unit every energy figure is shown in.",
                        "Available options: 'FE', 'RF', 'IC2_EU', 'GT_EU' - Default: FE.",
                        "Upstream's own wording. Any other value falls back to FE (EnergyType.getType).")
                .define("Display_Energy_Type", DEFAULT_DISPLAY_ENERGY_TYPE);
        builder.pop().pop();

        builder.comment("The One Probe machine overlay.",
                "Colours are ARGB hexadecimal values, without a leading #.")
                .push("display").push("theoneprobe");
        TOP_RECIPE_FILLED = builder.define("RECIPE_PROGRESSBAR_FILLED_COLOR", "CC54FF9F");
        TOP_RECIPE_ALTERNATE = builder.define("RECIPE_PROGRESSBAR_ALTERNATE_FILLED_COLOR", "CC4EEE94");
        TOP_RECIPE_BORDER = builder.define("RECIPE_PROGRESSBAR_BORDER_COLOR", "CC43CD80");
        TOP_RECIPE_BACKGROUND = builder.define("RECIPE_PROGRESSBAR_BACKGROUND_COLOR", "FF000000");
        TOP_FAILURE_FILLED = builder.define("FAILURE_PROGRESSBAR_FILLED_COLOR", "CCFF6347");
        TOP_FAILURE_ALTERNATE = builder.define("FAILURE_PROGRESSBAR_ALTERNATE_FILLED_COLOR", "CCFF4500");
        TOP_FAILURE_BORDER = builder.define("FAILURE_PROGRESSBAR_BORDER_COLOR", "CCFF6347");
        TOP_DECIMAL = builder.define("SHOW_RECIPE_PROGRESSBAR_DECIMAL_POINTS", true);
        TOP_PARALLEL = builder.define("SHOW_PARALLEL_CONTROLLER_INFO", true);
        builder.pop().pop();

        SPEC = builder.build();
    }

    private Config() {
    }

    private static int topColor(ForgeConfigSpec.ConfigValue<String> value, int fallback) {
        if (!SPEC.isLoaded()) return fallback;
        try {
            String hex = value.get().replace("#", "");
            return hex.matches("[0-9a-fA-F]{8}") ? Integer.parseUnsignedInt(hex, 16) : fallback;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    public static int topRecipeFilledColor() { return topColor(TOP_RECIPE_FILLED, 0xCC54FF9F); }
    public static int topRecipeAlternateColor() { return topColor(TOP_RECIPE_ALTERNATE, 0xCC4EEE94); }
    public static int topRecipeBorderColor() { return topColor(TOP_RECIPE_BORDER, 0xCC43CD80); }
    public static int topRecipeBackgroundColor() { return topColor(TOP_RECIPE_BACKGROUND, 0xFF000000); }
    public static int topFailureFilledColor() { return topColor(TOP_FAILURE_FILLED, 0xCCFF6347); }
    public static int topFailureAlternateColor() { return topColor(TOP_FAILURE_ALTERNATE, 0xCCFF4500); }
    public static int topFailureBorderColor() { return topColor(TOP_FAILURE_BORDER, 0xCCFF6347); }
    public static boolean topShowDecimal() { return !SPEC.isLoaded() || TOP_DECIMAL.get(); }
    public static boolean topShowParallel() { return !SPEC.isLoaded() || TOP_PARALLEL.get(); }

    /**
     * @return the enum's constant name in the case upstream's own key expression uses for that family
     *
     * <p>{@code UpgradeBusData.getName()} is {@code name().toLowerCase()} (upstream U34) and
     * {@code ParallelControllerData.getName()} likewise (U34), while the two hatch families call
     * {@code name().toUpperCase()} inline (U45 / U69). Locale.ROOT is used because a Turkish default locale would
     * otherwise map {@code i} to a dotless {@code ı} and produce a key no other machine can read.
     */
    private static String key(String constantName) {
        return constantName.toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------------------------------------
    // The spec entries themselves, exposed so a CHECK can change a value and put it back.
    //
    // The audit's requirement for this half is "assert that changing a config value changes the enum's reported
    // number", and the only way to do that from a running server is to reach the same
    // ForgeConfigSpec.ConfigValue the enum reads through: ConfigValue#set writes into the loaded config and get()
    // then returns what was written. A check that edited the config FILE would need a restart, and a check that
    // hard-coded a number would pass on a mechanism that does not work at all.
    //
    // Reading these is NOT a supported way to obtain a tier value -- the enums' own getters are, and they apply the
    // unloaded-spec fallback that these accessors do not. They are here for callers that must WRITE.
    // ---------------------------------------------------------------------------------------------

    /** @return the spec entry for a fluid hatch tier, or {@code null} when the family declares none */
    public static ForgeConfigSpec.ConfigValue<Integer> entryFor(FluidHatchSize tier) {
        return FLUID_HATCH_SIZE.get(tier);
    }

    /** @return the spec entry for an energy hatch tier's buffer */
    public static ForgeConfigSpec.ConfigValue<Long> entryForEnergyCapacity(EnergyHatchData tier) {
        return ENERGY_HATCH_SIZE.get(tier);
    }

    /** @return the spec entry for an energy hatch tier's transfer limit */
    public static ForgeConfigSpec.ConfigValue<Long> entryForEnergyTransfer(EnergyHatchData tier) {
        return ENERGY_HATCH_LIMIT.get(tier);
    }

    /** @return the spec entry for a parallel controller tier's ceiling */
    public static ForgeConfigSpec.ConfigValue<Integer> entryFor(ParallelControllerData tier) {
        return PARALLEL_CONTROLLER_MAX.get(tier);
    }

    /** @return the spec entry for an upgrade bus tier's slot count */
    public static ForgeConfigSpec.ConfigValue<Integer> entryFor(UpgradeBusData tier) {
        return UPGRADE_BUS_SLOTS.get(tier);
    }

    // ---------------------------------------------------------------------------------------------
    // The typed accessors the four enums call.
    //
    // ⚠️ THE FALLBACK IS THE TIER'S `default` ACCESSOR, NEVER ITS LIVE GETTER, AND THAT IS LOAD-BEARING.
    // The live getter is what CALLS these methods, so passing it here is infinite recursion:
    //
    //     FluidHatchSize.getCapacity()          -> Config.fluidHatchCapacity(tier)
    //     Config.fluidHatchCapacity(tier)       -> read(entry, tier.getCapacity())   <-- re-enters
    //     FluidHatchSize.getCapacity()          -> Config.fluidHatchCapacity(tier)   ...
    //
    // which is a StackOverflowError during mod construction, before a single machine exists -- and it happened,
    // on the first run of this unit: `Failed to create mod instance. ModID: modularmachinery`,
    // `java.lang.StackOverflowError` through `Config.fluidHatchCapacity(Config.java:343)` /
    // `FluidHatchSize.getCapacity(FluidHatchSize.java:82)`. The default accessors exist for exactly this reason:
    // they read the frozen table and call nothing.
    // ---------------------------------------------------------------------------------------------

    /**
     * @param tier the fluid hatch tier being asked about
     * @return the configured tank capacity in mB, or the tier's own default when the spec is not loaded
     */
    public static int fluidHatchCapacity(FluidHatchSize tier) {
        return read(FLUID_HATCH_SIZE.get(tier), tier.getDefaultCapacity());
    }

    /**
     * @param tier the energy hatch tier being asked about
     * @return the configured energy buffer in FE, or the tier's own default when the spec is not loaded
     *
     * <p>The {@code Long} is narrowed deliberately: upstream's key is declared over a {@code long} range and this
     * port's field is an {@code int}, and the spec's own {@code defineInRange(..., 1, Integer.MAX_VALUE)} is what
     * makes that narrowing total rather than a truncation. Doing it at the accessor keeps the range rule in one
     * place instead of spreading {@code (int)} casts that would each be a place to get it wrong.
     */
    public static int energyHatchCapacity(EnergyHatchData tier) {
        Long configured = read(ENERGY_HATCH_SIZE.get(tier), (long) tier.getDefaultMaxEnergy());
        return configured.intValue();
    }

    /**
     * @param tier the energy hatch tier being asked about
     * @return the configured transfer limit in FE/t, or the tier's own default when the spec is not loaded
     */
    public static int energyHatchTransfer(EnergyHatchData tier) {
        Long configured = read(ENERGY_HATCH_LIMIT.get(tier), (long) tier.getDefaultMaxTransfer());
        return configured.intValue();
    }

    /**
     * @param tier the parallel controller tier being asked about
     * @return the configured parallelism ceiling, or the tier's own default when the spec is not loaded
     */
    public static int parallelControllerMaxParallelism(ParallelControllerData tier) {
        return read(PARALLEL_CONTROLLER_MAX.get(tier), tier.getDefaultMaxParallelism());
    }

    /** Defaults are captured by definitions at load time; explicit JSON/script values take precedence. */
    public static boolean machineParallelizeEnabledByDefault() { return read(MACHINE_PARALLEL_DEFAULT, true); }
    public static boolean recipeParallelizeEnabledByDefault() { return read(RECIPE_PARALLEL_DEFAULT, true); }
    public static int maxMachineParallelism() { return read(MAX_MACHINE_PARALLELISM, 2048); }
    public static int manaProviderCapacity() { return read(MANA_PROVIDER_CAPACITY, 100000); }
    public static int defaultFactoryMaxThreads() { return read(FACTORY_MAX_THREADS, 10); }

    /**
     * @param tier the upgrade bus tier being asked about
     * @return the configured slot count, or the tier's own default when the spec is not loaded
     */
    public static int upgradeBusSlots(UpgradeBusData tier) {
        return read(UPGRADE_BUS_SLOTS.get(tier), tier.getDefaultUpgradeSlots());
    }

    /**
     * @return the configured value, or {@code fallback} when the spec has not been loaded yet
     *
     * <p>The fallback is not a safety net for a broken file — {@code defineInRange} makes that impossible — it is
     * the answer for the window before Forge has parsed anything, and for a caller that has no business forcing a
     * config load. It is also what makes the four enums usable from a unit-test context where no config file exists.
     */
    private static <T> T read(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        if (!SPEC.isLoaded()) {
            return fallback;
        }
        T loaded = value.get();
        return loaded == null ? fallback : loaded;
    }

    /**
     * @return the machine colour as an opaque ARGB int, or the default when the configured string is not a hex
     *         colour
     *
     * <p><b>The parse cannot throw.</b> A config file is player data and can be edited by hand; a typo in it must
     * cost the player their colour and not their game. A malformed value falls back to the default and says so in the
     * log once — upstream logged "Machine-Casing color defined in the config is not a hex color:" followed by "Using
     * default color instead...", and both of those messages are kept in spirit.
     */
    public static int machineColor() {
        String raw;
        if (!SPEC.isLoaded()) {
            // The config is not available yet -- a colour handler can be asked for a colour before the config
            // file has been read. The default is correct in that window, not an error.
            return parseOrDefault(DEFAULT_MACHINE_COLOR, false);
        }
        try {
            raw = MACHINE_COLOR.get();
        } catch (IllegalStateException notLoaded) {
            return parseOrDefault(DEFAULT_MACHINE_COLOR, false);
        }
        return parseOrDefault(raw, true);
    }

    /** Whether controller owner checks are enabled. */
    public static boolean enableSecuritySystem() {
        return read(ENABLE_SECURITY_SYSTEM, false);
    }

    public static boolean enableFluxNetworksIntegration() {
        return read(ENABLE_FLUX_NETWORKS, true);
    }

    public static ForgeConfigSpec.BooleanValue entryForFluxNetworksIntegration() {
        return ENABLE_FLUX_NETWORKS;
    }

    /** Mirrors upstream's optional cleanup on a failed structure check. */
    public static boolean cleanCustomDataOnStructureCheckFailed() {
        return read(CLEAN_CUSTOM_DATA_ON_STRUCTURE_FAILURE, false);
    }

    /** Upstream's general/default-failure-actions setting, with safe fallback for malformed config values. */
    public static RecipeFailureActions defaultFailureAction() {
        String value;
        try {
            value = SPEC.isLoaded() ? DEFAULT_FAILURE_ACTION.get() : "still";
        } catch (IllegalStateException ignored) {
            value = "still";
        }
        return RecipeFailureActions.byNameWithoutConfig(value);
    }

    /** Item used by the source-compatible survival assembler. */
    public static Item autoAssemblyItem() {
        if (!SPEC.isLoaded()) return Items.STICK;
        try {
            ResourceLocation id = ResourceLocation.tryParse(AUTO_ASSEMBLY_ITEM.get());
            Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            return item == null ? Items.STICK : item;
        } catch (RuntimeException ignored) {
            return Items.STICK;
        }
    }

    public static int autoAssemblyInterval() {
        return read(AUTO_ASSEMBLY_INTERVAL, 5);
    }

    public static boolean autoAssemblyNeedAll() {
        return read(AUTO_ASSEMBLY_NEED_ALL, true);
    }

    /** Mirrors MMCE's {@code skipBlockContainNBT} assembler option. */
    public static boolean autoAssemblySkipBlockContainingNbt() {
        return read(AUTO_ASSEMBLY_SKIP_NBT, false);
    }

    /** @return the ARGB int for a 6-digit hex string, or {@code fallback} when it is not one */
    private static int parseOrDefault(String raw, boolean logFailure) {
        int rgb = parseHex(raw);
        if (rgb != -1) {
            return 0xFF000000 | rgb;
        }
        if (logFailure) {
            hellfirepvp.modularmachinery.Modularmachinery.LOG.warn(
                    "[MMCE] machineColor '{}' is not a 6-digit hex colour; using the default {} instead",
                    raw, DEFAULT_MACHINE_COLOR);
        }
        return 0xFF000000 | parseHex(DEFAULT_MACHINE_COLOR);
    }

    /** @return the 24-bit value of a 6-digit hex string, or -1 when the string is not one */
    private static int parseHex(String raw) {
        if (raw == null) {
            return -1;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.length() != 6) {
            return -1;
        }
        try {
            return Integer.parseInt(trimmed, 16);
        } catch (NumberFormatException notHex) {
            return -1;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Audit row 41 — the four readers EnergyDisplayUtil (and nothing else) calls. Each returns the spec's
    // value, or upstream's own default when the spec has not been parsed yet: a tooltip can be asked for
    // its lines before any config file exists, and that is exactly the window `read` below answers, the
    // same one the four tier families use.
    // ---------------------------------------------------------------------------------------------

    /** @return upstream's {@code FE_RF_Tooltip} */
    public static boolean displayEnergyFETooltip() {
        return read(ENERGY_DISPLAY_FE, DEFAULT_DISPLAY_FE_TOOLTIP);
    }

    /** @return upstream's {@code IC2_EU_Tooltip} */
    public static boolean displayEnergyIC2Tooltip() {
        return read(ENERGY_DISPLAY_IC2, DEFAULT_DISPLAY_IC2_TOOLTIP);
    }

    /** @return upstream's {@code GT_EU_Tooltip} */
    public static boolean displayEnergyGTTooltip() {
        return read(ENERGY_DISPLAY_GT, DEFAULT_DISPLAY_GT_TOOLTIP);
    }

    /**
     * @return upstream's {@code Display_Energy_Type} as written, or {@code "FE"} before the spec is loaded
     *
     * <p>The value is NOT validated here: upstream parses it with {@code EnergyType.getType}, which answers FE
     * for anything unrecognised, and doing the validation in two places would be two answers to one question.</p>
     */
    public static String energyDisplayType() {
        return read(ENERGY_DISPLAY_TYPE, DEFAULT_DISPLAY_ENERGY_TYPE);
    }
}
