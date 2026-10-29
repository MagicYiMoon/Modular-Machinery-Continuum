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

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The registry that answers "what upgrade does this item carry, and may it act on this machine".
 *
 * <h2>Rule A0 — upstream's two maps, and the field this port had to replace</h2>
 *
 * <p>Upstream: {@code github.kasuminova.mmce.common.upgrade.registry.RegistryUpgrade} (60 lines) holds
 * {@code HashMap<String, MachineUpgrade> UPGRADES} (the prototype per type name — this is what
 * {@code CapabilityUpgrade.readNBT} uses to reconstruct an installed upgrade from NBT, U50) and
 * {@code Map<Item, UpgradeInfo> ITEM_UPGRADES} (which items grant which upgrades, with {@code UpgradeInfo} carrying
 * the match list and the upgrade list, 48 lines). Its API is {@code getItemUpgradeList},
 * {@code supportsUpgrade}, {@code addFixedUpgrade}, {@code addSupportedItem}, {@code registerUpgrade},
 * {@code getUpgrade}, {@code clearAll}.
 *
 * <p><b>{@code ITEM_UPGRADES} IS A ONE-TO-ONE MAP HERE, AND THAT IS THE A0 DIFFERENCE.</b> Upstream's
 * {@code UpgradeInfo} exists so ONE ITEM can grant SEVERAL upgrades, or grant one only when an NBT-matched stack
 * is present ({@code UpgradeInfo.matches} compares {@code ItemStack.areItemsEqual} against a declared list).
 * Every producer of that list was CraftTweaker's {@code MachineUpgradeBuilder} / {@code DynamicMachineUpgradeBuilder}
 * — {@code addSupportedItem} and {@code addFixedUpgrade} have no other callers — so in a port with no script engine
 * the multi-upgrade case can be neither produced nor tested. One item to one upgrade type is what
 * {@link MachineUpgradeItem} declares, and {@code supportsUpgrade} is therefore an {@code instanceof} test rather
 * than an NBT comparison.
 *
 * <p><b>{@code UPGRADES} (the type-name map) IS KEPT, because it has a live reader even here:</b> the bus's stored
 * custom data is keyed by type name upstream, and {@link #getPrototype(String)} is the lookup a future NBT restore
 * path needs. {@link #descriptionsOf(UpgradeType)} is this port's substitute for reading them off a capability.
 *
 * <h2>Registration ORDER, and why the built-ins are not registered from a static initialiser of this class</h2>
 *
 * <p>{@link #ITEMS} is filled by {@link UpgradeItems}, which {@code RegistryItems} calls at its own class-load time
 * — i.e. while the Forge registries are still open and before any {@code DeferredRegister} callback runs. That is
 * deliberate: {@code ITEMS} must be complete before an item's {@code Supplier} is invoked, or an item could exist
 * in the registry with no upgrade behind it.
 */
public final class RegistryMachineUpgrade {

    /** The prototypes, by type name — upstream's {@code UPGRADES}. */
    private static final Map<String, MachineUpgrade> PROTOTYPES = new LinkedHashMap<>();

    /** Which items grant which upgrades.  A list is required by the upstream API: one item may carry several. */
    private static final Map<Item, List<Binding>> ITEMS = new LinkedHashMap<>();

    /** The items, by upgrade type, for reports and for the creative tab. */
    private static final Map<UpgradeType, MachineUpgradeItem> ITEM_BY_TYPE = new LinkedHashMap<>();

    private static final String STACK_UPGRADES = "modularmachinery_upgrades";
    private static final String DYNAMIC_BINDINGS = "modularmachinery_dynamic_upgrade_bindings";

    private record Binding(ItemStack match, MachineUpgrade prototype) {
        boolean matches(ItemStack stack) {
            // 1.12's ItemStack.areItemsEqual, used by UpgradeInfo.matches, ignores arbitrary NBT.  The closest
            // 1.20.1 equivalent is item identity plus damage value; comparing all tags would reject a supported
            // item merely because a pack added an unrelated custom tag to it.
            return match == null || (match.getItem() == stack.getItem()
                    && match.getDamageValue() == stack.getDamageValue());
        }
    }

    private RegistryMachineUpgrade() {
    }

    /**
     * Registers an upgrade type and the item that installs it.
     *
     * @param item  the item players put in an upgrade bus slot
     * @param prototype the upgrade it installs, which must be the item's own type's upgrade
     * @throws IllegalArgumentException when the type or the item is already registered, because either would make
     *         {@link #upgradeFor} ambiguous and the second registration would silently win over the first
     */
    public static void register(MachineUpgradeItem item, MachineUpgrade prototype) {
        if (prototype == null || item == null) {
            throw new IllegalArgumentException("an upgrade registration needs an item and an upgrade");
        }
        String name = prototype.getType().getName();
        registerPrototype(prototype);
        List<Binding> bindings = ITEMS.computeIfAbsent(item, ignored -> new ArrayList<>());
        if (bindings.stream().anyMatch(binding -> binding.match() == null)) {
            throw new IllegalArgumentException("item " + item + " already has a wildcard upgrade binding");
        }
        bindings.add(new Binding(null, prototype));
        ITEM_BY_TYPE.put(prototype.getType(), item);
    }

    /** Registers a script-defined upgrade that has no dedicated item of its own. */
    public static void registerPrototype(MachineUpgrade prototype) {
        if (prototype == null) {
            throw new IllegalArgumentException("an upgrade registration needs an upgrade");
        }
        String name = prototype.getType().getName();
        MachineUpgrade previous = PROTOTYPES.putIfAbsent(name, prototype);
        if (previous != null) {
            throw new IllegalArgumentException("upgrade type '" + name + "' is already registered by "
                    + previous + "; a second registration would replace the first silently");
        }
    }

    /** Adds a fixed upgrade to an arbitrary existing item, matching the exact item/NBT pair. */
    public static void addFixedUpgrade(ItemStack stack, MachineUpgrade upgrade) {
        if (stack == null || stack.isEmpty() || upgrade == null) return;
        registerPrototypeIfAbsent(upgrade);
        ITEMS.computeIfAbsent(stack.getItem(), ignored -> new ArrayList<>())
                .add(new Binding(stack.copyWithCount(1), upgrade));
    }

    /** Marks an arbitrary item as upgrade-capable; a later tagged stack may carry one or more upgrades. */
    public static void addSupportedItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ITEMS.computeIfAbsent(stack.getItem(), ignored -> new ArrayList<>())
                .add(new Binding(stack.copyWithCount(1), null));
    }

    private static void registerPrototypeIfAbsent(MachineUpgrade upgrade) {
        PROTOTYPES.putIfAbsent(upgrade.getType().getName(), upgrade);
    }

    /**
     * @param stack the stack in an upgrade bus slot
     * @return a NEW upgrade instance ready to be installed, or {@code null} when the stack is not an upgrade
     *
     * <p>The {@code copy()} is upstream's own behaviour at the same call: {@code CapabilityUpgrade.readNBT} does
     * {@code upgrade = upgrade.copy(ItemStack.EMPTY)} before installing (U55), precisely so the prototype in the
     * registry is never the object a machine mutates. A bus that held the prototype would make two buses share one
     * {@code stackSize}.
     */
    public static MachineUpgrade upgradeFor(ItemStack stack) {
        List<MachineUpgrade> upgrades = upgradesFor(stack);
        return upgrades.isEmpty() ? null : upgrades.get(0);
    }

    /** Returns every upgrade carried by a stack, including upgrades encoded by the helper's returned stack. */
    public static List<MachineUpgrade> upgradesFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Collections.emptyList();
        List<Binding> bindings = ITEMS.get(stack.getItem());
        if (bindings == null) {
            // The source attaches the upgrade capability only to an item that has a matching UpgradeInfo.  A
            // forged modularmachinery_upgrades tag on an unrelated item therefore cannot install an upgrade.
            return Collections.emptyList();
        }
        boolean matchesRegistration = bindings.stream().anyMatch(binding -> binding.matches(stack));
        List<MachineUpgrade> result = new ArrayList<>();
        java.util.Set<String> added = new java.util.HashSet<>();
        if (stack.hasTag()) {
            ListTag encoded = stack.getTag().getList(STACK_UPGRADES, Tag.TAG_STRING);
            for (int i = 0; i < encoded.size(); i++) {
                MachineUpgrade prototype = PROTOTYPES.get(encoded.getString(i));
                if (prototype != null && (matchesRegistration || persistedUpgrade(stack, bindings, prototype.getType().getName()))
                        && added.add(prototype.getType().getName())) result.add(copyForStack(prototype, stack));
            }
        }
        for (Binding binding : bindings) {
            if (binding.prototype() != null && binding.matches(stack) && added.add(binding.prototype().getType().getName())) {
                result.add(copyForStack(binding.prototype(), stack));
            }
        }
        return result;
    }

    private static MachineUpgrade copyForStack(MachineUpgrade prototype, ItemStack owner) {
        if (prototype instanceof DynamicMachineUpgrade dynamic) {
            DynamicMachineUpgrade dynamicCopy = dynamic.copy(owner);
            CompoundTag tag = owner.getTag();
            dynamicCopy.readItemNBT(tag == null ? new CompoundTag()
                    : tag.contains(DynamicMachineUpgrade.ITEM_DATA_BY_TYPE_TAG, Tag.TAG_COMPOUND)
                    ? tag.getCompound(DynamicMachineUpgrade.ITEM_DATA_BY_TYPE_TAG).getCompound(prototype.getType().getName())
                    : tag.getCompound(DynamicMachineUpgrade.ITEM_DATA_TAG));
            return dynamicCopy;
        } else {
            return prototype.copy();
        }
    }

    /** @return whether this stack carries an upgrade — upstream's {@code supportsUpgrade} (U37-U43) */
    public static boolean supportsUpgrade(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        List<Binding> bindings = ITEMS.get(stack.getItem());
        if (bindings == null) return false;
        if (bindings.stream().anyMatch(binding -> binding.matches(stack))) return true;
        if (!stack.hasTag()) return false;
        for (String type : stack.getTag().getCompound(DYNAMIC_BINDINGS).getAllKeys())
            if (persistedUpgrade(stack, bindings, type)) return true;
        return false;
    }

    /** Source capability identity survives wear; a saved identity still requires a matching item registration. */
    private static boolean persistedUpgrade(ItemStack stack, List<Binding> bindings, String type) {
        if (!PROTOTYPES.containsKey(type) || !stack.hasTag()) return false;
        CompoundTag saved = stack.getTag().getCompound(DYNAMIC_BINDINGS);
        if (!saved.contains(type, Tag.TAG_INT)) return false;
        int originalDamage = saved.getInt(type);
        return bindings.stream().anyMatch(binding -> (binding.prototype() == null || type.equals(binding.prototype().getType().getName()))
                && (binding.match() == null || binding.match().getDamageValue() == originalDamage));
    }

    /** Called on authoritative dynamic-item writes, before changing damage. Ordinary registry reads stay pure. */
    public static ItemStack preserveDynamicIdentity(ItemStack input, String type) {
        ItemStack result = addUpgradeToStack(input, type);
        CompoundTag tag = result.getOrCreateTag(); var saved = tag.getCompound(DYNAMIC_BINDINGS);
        if (!saved.contains(type, Tag.TAG_INT)) saved.putInt(type, input.getDamageValue());
        tag.put(DYNAMIC_BINDINGS, saved);
        return result;
    }

    /** Preserve every attached upgrade before wear changes fixed item/damage matching, as capability NBT does. */
    public static ItemStack preserveUpgradeIdentities(ItemStack input) {
        ItemStack result = input.copy();
        for (MachineUpgrade installed : upgradesFor(input))
            result = preserveDynamicIdentity(result, installed.getType().getName());
        return result;
    }

    /** @return the registered prototype for a type name, or {@code null} — upstream's {@code getUpgrade} (U57-U59) */
    public static MachineUpgrade getPrototype(String typeName) {
        return PROTOTYPES.get(typeName);
    }

    /** @return the item that installs an upgrade type, or {@code null} when the type has no item */
    public static MachineUpgradeItem itemFor(UpgradeType type) {
        return ITEM_BY_TYPE.get(type);
    }

    /** @return every registered type, in registration order */
    public static List<UpgradeType> registeredTypes() {
        List<UpgradeType> types = new ArrayList<>(PROTOTYPES.size());
        for (MachineUpgrade prototype : PROTOTYPES.values()) {
            types.add(prototype.getType());
        }
        return Collections.unmodifiableList(types);
    }

    /** @return the description lines declared for a type, or an empty list when the type is unknown */
    public static List<String> descriptionsOf(UpgradeType type) {
        MachineUpgrade prototype = type == null ? null : PROTOTYPES.get(type.getName());
        return prototype == null ? Collections.emptyList() : prototype.getDescriptions();
    }

    /**
     * <b>THE REGISTRY'S OWN FAILURE MODE IS A DUPLICATE OR A MISSING ITEM, AND NEITHER CAN BE SEEN FROM A COUNT.</b>
     * A check that asserted "the registry has entries" would pass on a registry whose items were never registered
     * with the item registry at all — the type map and the item map would both be full. So this asks the two
     * questions that can actually be wrong: every type has an item, and every item is really in
     * {@code ForgeRegistries.ITEMS} under its own name.
     *
     * @return a human-readable report, empty when every registration round-trips
     */
    public static String validate() {
        StringBuilder problems = new StringBuilder();
        for (Map.Entry<UpgradeType, MachineUpgradeItem> entry : ITEM_BY_TYPE.entrySet()) {
            UpgradeType type = entry.getKey();
            Item item = entry.getValue();
            net.minecraft.resources.ResourceLocation key =
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
            if (key == null) {
                problems.append(type.getName()).append("(item not in the item registry) ");
                continue;
            }
            if (!hellfirepvp.modularmachinery.Modularmachinery.MODID.equals(key.getNamespace())) {
                problems.append(type.getName()).append("(item registered as ").append(key).append(") ");
            }
            // THE REVERSE DIRECTION, which is the one a "has entries" check cannot ask: the item the registry
            // hands back for this type must be the very item in the item registry, i.e. the stack a player holds
            // must resolve back to this type.
            MachineUpgrade roundTrip = upgradeFor(new ItemStack(item));
            if (roundTrip == null || !roundTrip.getType().equals(type)) {
                problems.append(type.getName()).append("(its own item does not resolve back to it) ");
            }
        }
        return problems.toString().trim();
    }

    /** For a check that must start from a clean state; the built-ins re-register through {@link UpgradeItems}. */
    public static void clearAll() {
        PROTOTYPES.clear();
        ITEMS.clear();
        ITEM_BY_TYPE.clear();
    }

    /** Clears CraftTweaker-owned registrations while retaining the four built-in item upgrades. */
    public static void clearScriptRegistrations() {
        java.util.Set<String> builtIns = new java.util.HashSet<>();
        java.util.Set<Item> builtInItems = new java.util.HashSet<>();
        for (Map.Entry<UpgradeType, MachineUpgradeItem> entry : ITEM_BY_TYPE.entrySet()) {
            builtIns.add(entry.getKey().getName());
            builtInItems.add(entry.getValue());
        }
        PROTOTYPES.entrySet().removeIf(entry -> !builtIns.contains(entry.getKey()));
        ITEMS.forEach((item, bindings) -> bindings.removeIf(binding ->
                !builtInItems.contains(item) || binding.match() != null
                        || binding.prototype() == null
                        || !builtIns.contains(binding.prototype().getType().getName())));
        ITEMS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /** Encodes an upgrade name on a returned stack, which is the 1.20.1 replacement for upstream's capability NBT. */
    public static ItemStack addUpgradeToStack(ItemStack input, String upgradeName) {
        if (input == null || input.isEmpty() || !PROTOTYPES.containsKey(upgradeName)) return input;
        ItemStack result = input.copy();
        CompoundTag tag = result.getOrCreateTag();
        ListTag names = tag.getList(STACK_UPGRADES, Tag.TAG_STRING);
        boolean present = false;
        for (int i = 0; i < names.size(); i++) present |= upgradeName.equals(names.getString(i));
        if (!present) names.add(StringTag.valueOf(upgradeName));
        tag.put(STACK_UPGRADES, names);
        return result;
    }
}
