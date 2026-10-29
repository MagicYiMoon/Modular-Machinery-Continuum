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
package hellfirepvp.modularmachinery.common.crafting.requirement;

import hellfirepvp.modularmachinery.common.util.nbt.NbtPattern;

import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.MachineContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Requires (input) or produces (output) a quantity of an item.
 *
 * <h2>1.12.2 → 1.20.1: three legacy id forms to resolve</h2>
 *
 * <p>Upstream resolved an item with {@code Item.getByNameOrId} and compared stacks by
 * <b>item + metadata</b>. 1.20.1 has neither the single lookup nor metadata, and the shipped content
 * exercises <b>three different</b> legacy addressing schemes. Resolution is attempted in this order,
 * and each step is a table rather than a transform because the flattening was not mechanical:
 *
 * <ol>
 *   <li><b>a plain resource location</b> — {@code minecraft:diamond}. Resolved directly.</li>
 *   <li><b>a metadata-carrying id</b> — {@code minecraft:dye@3}. Via {@link ItemMetadataShim}; the
 *       variant metadata selected became a <i>distinct item</i>, and the numbering changed, so this
 *       cannot be computed.</li>
 *   <li><b>an ore-dictionary name</b> — {@code ore:ingotIron}. Via {@link ItemTagShim}, to an item
 *       <b>tag</b>: resolving it to {@code minecraft:iron_ingot} would work for vanilla and break every
 *       pack that adds another iron ingot, which is precisely what the ore dictionary existed to
 *       avoid.</li>
 *   <li><b>a renamed id</b> — {@code minecraft:wool} → {@code minecraft:white_wool}. Via
 *       {@link #ITEM_ALIASES}.</li>
 * </ol>
 *
 * <p>A requirement is satisfied by <b>exactly one</b> of an item, a tag, or nothing. The three cases
 * are reported distinctly so a load-time report says which scheme applied rather than just "ok".
 */
public final class RequirementItem extends hellfirepvp.modularmachinery.common.crafting.SelectorRequirement implements Requirement.Parallelizable {

    @Override public int getSortingWeight() { return PRIORITY_WEIGHT_ITEM; }

    /** 1.12.2 item ids that were split or renamed by the flattening (metadata-free renames). */
    private static final Map<String, String> ITEM_ALIASES = Map.ofEntries(
            Map.entry("minecraft:wool", "minecraft:white_wool"),
            Map.entry("minecraft:carpet", "minecraft:white_carpet"),
            Map.entry("minecraft:stained_glass", "minecraft:white_stained_glass"),
            Map.entry("minecraft:stained_glass_pane", "minecraft:white_stained_glass_pane"),
            Map.entry("minecraft:stained_hardened_clay", "minecraft:white_terracotta"),
            Map.entry("minecraft:hardened_clay", "minecraft:terracotta"),
            Map.entry("minecraft:concrete", "minecraft:white_concrete"),
            Map.entry("minecraft:concrete_powder", "minecraft:white_concrete_powder"),
            Map.entry("minecraft:planks", "minecraft:oak_planks"),
            Map.entry("minecraft:log", "minecraft:oak_log"),
            Map.entry("minecraft:log2", "minecraft:dark_oak_log"),
            Map.entry("minecraft:sapling", "minecraft:oak_sapling"),
            Map.entry("minecraft:leaves", "minecraft:oak_leaves"),
            Map.entry("minecraft:fence", "minecraft:oak_fence"),
            Map.entry("minecraft:fence_gate", "minecraft:oak_fence_gate"),
            Map.entry("minecraft:wooden_door", "minecraft:oak_door"),
            Map.entry("minecraft:trapdoor", "minecraft:oak_trapdoor"),
            Map.entry("minecraft:wooden_slab", "minecraft:oak_slab"),
            Map.entry("minecraft:stone_slab", "minecraft:smooth_stone_slab"),
            Map.entry("minecraft:red_flower", "minecraft:poppy"),
            Map.entry("minecraft:yellow_flower", "minecraft:dandelion"),
            Map.entry("minecraft:web", "minecraft:cobweb"),
            Map.entry("minecraft:waterlily", "minecraft:lily_pad"),
            Map.entry("minecraft:brick_block", "minecraft:bricks"),
            Map.entry("minecraft:nether_brick", "minecraft:netherbrick"),
            Map.entry("minecraft:red_nether_brick", "minecraft:nether_brick"),
            Map.entry("minecraft:noteblock", "minecraft:note_block"),
            Map.entry("minecraft:magma", "minecraft:magma_block"),
            Map.entry("minecraft:deadbush", "minecraft:dead_bush"),
            Map.entry("minecraft:snow_layer", "minecraft:snow"),
            Map.entry("minecraft:grass", "minecraft:grass_block"),
            Map.entry("minecraft:silver_glazed_terracotta", "minecraft:light_gray_glazed_terracotta"));

    private int consumeDurability;
    private hellfirepvp.modularmachinery.common.crafting.ItemRequirementChecker itemChecker;
    private final List<hellfirepvp.modularmachinery.common.crafting.ItemRequirementModifier> itemModifiers = new ArrayList<>();

    public int getConsumeDurability() { return consumeDurability; }

    public RequirementItem withConsumeDurability(int damage) {
        if (damage < 0 || (damage > 0 && ioType != IOType.INPUT)) {
            throw new IllegalArgumentException("consumeDurability must be nonnegative and is only valid for inputs");
        }
        consumeDurability = damage;
        return this;
    }

    private final IOType ioType;
    private final String rawId;
    private final int amount;
     
    private int parallelism = 1;
    private boolean parallelizeUnaffected = false;
    private final float chance;

    private final Item resolved;
    private final TagKey<Item> resolvedTag;
    private final String resolutionNote;

    










 
    private hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt nbt =
            hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt.EMPTY;

    public RequirementItem withNbt(hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt nbt) {
        this.nbt = nbt;
        return this;
    }

    public hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt getNbt() { return nbt; }

    /**
     * Attaches an NBT pattern.
     *
     * @param pattern the parsed pattern, or {@code null} for none
     * @return this requirement, so a constructor call can be followed inline
     */
    public RequirementItem withNbtPattern(NbtPattern pattern) {
        this.nbt = hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt.ofPattern(pattern);
        return this;
    }

    /** @return the pattern this requirement imposes, or {@code null}; used by the GUI to display it */
    @org.jetbrains.annotations.Nullable
    public NbtPattern getNbtPattern() {
        return nbt.pattern();
    }

    public RequirementItem(IOType ioType, ResourceLocation itemId, int amount) {
        this(ioType, itemId == null ? "" : itemId.toString(), amount, 1.0F);
    }

    public RequirementItem(IOType ioType, String rawId, int amount) {
        this(ioType, rawId, amount, 1.0F);
    }

    /**
     * @param ioType direction
     * @param rawId  the id exactly as written in the JSON; kept as a string because a legacy id such as
     *               {@code ore:ingotIron} or {@code minecraft:dye@3} is not a valid
     *               {@link ResourceLocation} and must not be normalised before resolution
     * @param amount required quantity
     * @param chance probability this requirement is satisfied, in {@code [0,1]}. 1.0 means "always".
     */
    public RequirementItem(IOType ioType, String rawId, int amount, float chance) {
        this.ioType = ioType;
        this.rawId = rawId;
        this.amount = amount;
        // Clamped rather than validated-and-rejected: the value comes from user-editable JSON, and a
        // pack author writing 1.5 or -1 clearly means "always" / "never". Silently accepting the raw
        // value would make a roll either always or never succeed depending on the comparison, which is
        // harder to reason about than a clamp.
        this.chance = Math.max(0.0F, Math.min(1.0F, chance));

        Item item = null;
        TagKey<Item> tag = null;
        String note = null;

        if (rawId.startsWith("tag:")) {
            ResourceLocation tagId = ResourceLocation.tryParse(rawId.substring(4));
            if (tagId != null) tag = net.minecraft.tags.ItemTags.create(tagId);
        }

        ResourceLocation parsed = ResourceLocation.tryParse(rawId);
        if (parsed != null) {
            item = ForgeRegistries.ITEMS.getValue(parsed);
        }

        if ((item == null || item == Items.AIR) && rawId.contains("@")) {
            ResourceLocation mapped = ItemMetadataShim.itemFor(rawId);
            if (mapped != null) {
                item = ForgeRegistries.ITEMS.getValue(mapped);
                note = "metadata -> " + mapped;
            }
        }

        if ((item == null || item == Items.AIR) && ItemTagShim.isOreDictName(rawId)) {
            tag = ItemTagShim.tagFor(rawId);
            if (tag != null) {
                note = "ore-dict -> tag #" + tag.location();
            }
        }

        if ((item == null || item == Items.AIR) && tag == null) {
            String mapped = ITEM_ALIASES.get(rawId);
            if (mapped != null) {
                item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(mapped));
                note = "renamed -> " + mapped;
            }
        }

        this.resolved = item == Items.AIR ? null : item;
        this.resolvedTag = tag;
        this.resolutionNote = note;
    }

    @Override
    public IOType getIOType() {
        return ioType;
    }

    /** @return the id as written in the JSON, unmodified */
    public String getRawId() {
        return rawId;
    }

    public int getAmount() {
        return amount;
    }

    /**
     * @return the probability this requirement is satisfied, in {@code [0,1]}.
     *
     * <p>Read from the JSON's {@code chance} field. It was previously <b>parsed-and-ignored</b>: the
     * loader did not read it at all, so a recipe declaring {@code "chance": 0.6} — as
     * {@code centrifuge_wash_redstone} does — always produced its output. That is a silent behavioural
     * divergence, the same category as the metadata and id issues, and the worst kind: nothing fails,
     * nothing warns, the machine simply gives more than the pack author specified.
     */
    public float getChance() {
        return chance;
    }

    /** @return true when this requirement is unconditional, i.e. needs no roll */
    public boolean isGuaranteed() {
        return chance >= 1.0F;
    }

    /** @return the resolved item, or {@code null} for a tag-based or unresolvable requirement */
    public Item getItem() {
        return resolved;
    }

    /** @return the resolved item tag, or {@code null} for an item-based or unresolvable requirement */
    public TagKey<Item> getTag() {
        return resolvedTag;
    }

    /** @return a description of how this id was resolved, or {@code null} when it needed no mapping */
    public String getResolutionNote() {
        return resolutionNote;
    }

    public boolean isResolvable() {
        return resolved != null || resolvedTag != null;
    }

    /**
     * @param stack the stack to test
     * @return true when the stack satisfies this requirement — comparing the item directly, or
     *         consulting the tag so that items added by other mods match
     */
    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        boolean itemMatches;
        if (resolved != null) {
            itemMatches = stack.getItem() == resolved;
        } else {
            itemMatches = resolvedTag != null && stack.is(resolvedTag);
        }
        if (!itemMatches) {
            return false;
        }
        // THE NBT PATTERN IS AN ADDITIONAL CONDITION, and the order above is deliberate: the item check is cheaper and it
        // is the one that fails most often, so a pattern is only consulted for a stack that already matched the item.
        //
        // A pattern NARROWS a requirement. It never widens one -- there is no path where a stack rejected above becomes
        // acceptable because of a pattern -- which is why the check is written as a separate gate rather than folded into
        // the item comparison.
        if (nbt.pattern() == null) {
            return true;
        }
        return nbt.matches(stack.getTag());
    }

    public void setItemChecker(hellfirepvp.modularmachinery.common.crafting.ItemRequirementChecker checker) {
        this.itemChecker = checker;
    }
    public void addItemModifier(hellfirepvp.modularmachinery.common.crafting.ItemRequirementModifier modifier) {
        if (modifier != null) itemModifiers.add(modifier);
    }
    public boolean hasItemModifiers() { return !itemModifiers.isEmpty(); }
    public ItemStack modifiedStack(hellfirepvp.modularmachinery.common.machine.MachineContext context, ItemStack base) {
        if (base == null || base.isEmpty() || itemModifiers.isEmpty()) return base;
        var controller = controller(context);
        ItemStack result = base.copy();
        for (var modifier : itemModifiers) {
            try {
                ItemStack updated = modifier.apply(controller, result.copy());
                if (updated != null) result = updated.copy();
            } catch (Throwable failure) {
                hellfirepvp.modularmachinery.Modularmachinery.LOG.warn("[MMCE] item requirement modifier failed", failure);
                return ItemStack.EMPTY;
            }
        }
        return result;
    }

    /**
     * Stack presented to an item modifier while consuming an input.
     *
     * <p>The 1.12 implementation starts with the complete required amount for an input.  A
     * modifier is therefore allowed to change both the item/NBT and the number of items that
     * have to be consumed.  Keeping this as a separate operation from the output path is
     * important: output modifiers are called with a one-item stack in the original mod.</p>
     */
    public ItemStack inputStack(hellfirepvp.modularmachinery.common.machine.MachineContext context) {
        ItemStack base = asModifierBaseStack(amount);
        return modifiedStack(context, base);
    }

    /**
     * Stack presented to an item modifier while producing an output.  The source mod invokes
     * output modifiers with one item and multiplies the recipe amount by the returned count.
     */
    public ItemStack outputStack(hellfirepvp.modularmachinery.common.machine.MachineContext context) {
        ItemStack base = asModifierBaseStack(1);
        return modifiedStack(context, base);
    }

    /** Number of output items produced by one recipe copy after output modifiers. */
    public long outputAmount(hellfirepvp.modularmachinery.common.machine.MachineContext context) {
        ItemStack stack = outputStack(context);
        return stack.isEmpty() || stack.getCount() <= 0 ? 0L : (long) amount * stack.getCount();
    }

    /** Number of input items consumed by one recipe copy after input modifiers. */
    public long inputAmount(hellfirepvp.modularmachinery.common.machine.MachineContext context) {
        ItemStack stack = inputStack(context);
        return stack.isEmpty() || stack.getCount() <= 0 ? 0L : stack.getCount();
    }

    /** Resolve a representative stack for a tag-backed requirement when a script modifier needs one. */
    private ItemStack asModifierBaseStack(int count) {
        ItemStack base = asStack();
        if (!base.isEmpty()) {
            base.setCount(Math.max(1, count));
            return base;
        }
        if (resolvedTag == null) return ItemStack.EMPTY;
        var tags = ForgeRegistries.ITEMS.tags();
        var representative = tags == null ? null : tags.getTag(resolvedTag).stream()
                .min(java.util.Comparator.comparing(item -> ForgeRegistries.ITEMS.getKey(item).toString()))
                .orElse(null);
        if (representative == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(representative, Math.max(1, count));
        stack.setTag(nbt.output());
        return stack;
    }
    public boolean matches(hellfirepvp.modularmachinery.common.machine.MachineContext context, ItemStack stack) {
        if (!itemModifiers.isEmpty()) {
            ItemStack expected = inputStack(context);
            if (expected.isEmpty() || expected.getItem() != stack.getItem()
                    || (expected.getTag() != null && !ItemStack.isSameItemSameTags(expected, stack))) return false;
        } else if (!matches(stack)) return false;
        if (itemChecker == null) return true;
        try { return itemChecker.test(controller(context), stack.copy()); }
        catch (Throwable failure) {
            hellfirepvp.modularmachinery.Modularmachinery.LOG.warn("[MMCE] item requirement checker failed", failure);
            return false;
        }
    }
    private static hellfirepvp.modularmachinery.common.tiles.TileController controller(
            hellfirepvp.modularmachinery.common.machine.MachineContext context) {
        if (context == null || context.getLevel() == null) return null;
        var entity = context.getLevel().getBlockEntity(context.getControllerPos());
        return entity instanceof hellfirepvp.modularmachinery.common.tiles.TileController tile ? tile : null;
    }

    /**
     * @return a representative stack, or {@link ItemStack#EMPTY} for a tag-based requirement
     *
     * <p>A tag deliberately has no single representative stack: picking one would misrepresent the
     * requirement in any UI that showed it, and would make an automated check look correct while
     * accepting only one of the valid items.
     */
    public ItemStack asStack() {
        if (resolved == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(resolved, amount);
        stack.setTag(nbt.output());
        return stack;
    }

    public ItemStack asDisplayStack() {
        ItemStack stack = asStack();
        if (!stack.isEmpty()) stack.setTag(nbt.display());
        return stack;
    }


    /**
     * {@inheritDoc}
     *
     * <p><b>THIS ONE IS SHORTER THAN ITS SIBLINGS BECAUSE THE PORT ALREADY HAS COUNTING PRIMITIVES:</b>
     * {@code countAvailable} (I332) and {@code countSpareRoom} (I348) were written for the start and finish checks
     * and they answer exactly the question the ceiling asks. WRITING A THIRD TRAVERSAL OF THE ITEM HANDLERS WOULD
     * HAVE BEEN THE SECOND CONVENTION FOR ONE RULE, WHICH IS WHAT RULE B EXISTS TO PREVENT.</p>
     *
     * <p><b>AND THE EXEMPT BRANCH KEEPS ACTUAL PARALLELISM AS SIBLINGS DO:</b> one copy that fits returns
     * {@code maxParallelism}, not one -- see RequirementEnergy own javadoc. A requirement that cannot do even one
     * returns zero, which blocks the craft rather than silently multiplying by nothing.</p>
     */
    @Override
    public int getMaxParallelism(MachineContext context, int maxParallelism,
                                 java.util.List<Requirement> siblings) {
        if (ioType == IOType.OUTPUT && isIgnoreOutputCheck()) return maxParallelism;
        if (!isResolvable()) {
            return 0;
        }
        boolean hasTool = consumeDurability > 0 || (siblings != null && hellfirepvp.modularmachinery.common.crafting.helper.ItemInputPlan.requiresPlan(siblings));
        if (ioType == IOType.INPUT && hasTool) {
            var inputs = new java.util.ArrayList<Requirement>(siblings == null ? java.util.List.of() : siblings);
            if (!inputs.contains(this)) inputs.add(this);
            int low = 0, high = Math.max(0, maxParallelism);
            while (low < high) {
                int copies = low + (int) (((long) high - low + 1) / 2);
                if (hellfirepvp.modularmachinery.common.crafting.helper.ItemInputPlan.plan(context, inputs, copies, false).valid()) low = copies;
                else high = copies - 1;
            }
            return low;
        }
        if (parallelizeUnaffected) {
            return canStart(context) ? maxParallelism : 0;
        }
        if (amount <= 0) {
            return maxParallelism;
        }
        long available = ioType == IOType.INPUT
                ? countAvailable(context)
                : countSpareRoom(context);
        long perCopy = ioType == IOType.INPUT ? inputAmount(context) : outputAmount(context);
        if (available <= 0) {
            return 0;
        }
        if (perCopy <= 0) return maxParallelism;
        return (int) Math.max(1, Math.min(maxParallelism, available / perCopy));
    }

    @Override
    public int getParallelism() {
        return parallelism;
    }

    @Override
    public void setParallelism(int parallelism) {
        if (!parallelizeUnaffected) {
            this.parallelism = parallelism;
        }
    }

    @Override
    public boolean isParallelizeUnaffected() {
        return parallelizeUnaffected;
    }

    @Override
    public void setParallelizeUnaffected(boolean unaffected) {
        this.parallelizeUnaffected = unaffected;
        if (parallelizeUnaffected) {
            this.parallelism = 1;
        }
    }
    /**
     * {@inheritDoc}
     *
     * <p>An <b>input</b> is satisfied when the machine's input components collectively hold at least
     * {@link #amount} of the item. An <b>output</b> is satisfied when the output components have room
     * for it.
     *
     * <p>Counting across <b>all</b> input components rather than stopping at the first is deliberate:
     * a machine with three input busses holding 10, 10 and 12 coal satisfies a "32 coal" requirement, and
     * a first-fit check would wrongly refuse it. The 1.12.2 code aggregated the same way.
     */
    @Override
    public boolean canStart(MachineContext context) {
        if (!isResolvable()) {
            // An unresolvable id can never be satisfied. Reporting false rather than true is what keeps
            // a broken recipe from running and consuming whatever happens to be in the bus.
            return false;
        }

        if (ioType == IOType.INPUT) {
            if (consumeDurability > 0) return hellfirepvp.modularmachinery.common.crafting.helper.ItemInputPlan
                    .plan(context, java.util.List.of(this), 1, false).valid();
            return countAvailable(context) >= inputAmount(context);
        }
        return (ioType == IOType.OUTPUT && isIgnoreOutputCheck()) || countSpareRoom(context) >= outputAmount(context);
    }

    /**
     * {@inheritDoc}
     *
     * <p>An input requirement can only finish if it can also start — the inputs must still be there at
     * the end, and consumption is gradual rather than immediate. An output requirement is the same
     * question as {@link #canStart}, since "there is room" is equally true now and at completion unless
     * something else fills the bus in between (which the tick loop re-checks).
     */
    @Override
    public boolean canFinish(MachineContext context) {
        return canStart(context);
    }

    



















 
    @Override
    public boolean canEmit(MachineContext context, int parallelism) {
        if (ioType == IOType.OUTPUT && isIgnoreOutputCheck()) return true;
        if (ioType != IOType.OUTPUT || !isResolvable()) {
            return canFinish(context);
        }
        int copies = Math.max(1, parallelism);
        return countSpareRoom(context) >= outputAmount(context) * copies;
    }

    /** @return how many matching items the machine's input components hold in total */
    public long countAvailable(MachineContext context) {
        long total = 0;
        for (var handler : context.forSelector(getSelectorTag()).capabilities(
                net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,
                hellfirepvp.modularmachinery.common.machine.MachineContext.ComponentKind.INPUT)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                var stack = handler.getStackInSlot(slot);
                if (matches(context, stack)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    /** @return how many more matching items the machine's output components could accept */
    public long countSpareRoom(MachineContext context) {
        long total = 0;
        ItemStack product = outputStack(context);
        if (product.isEmpty()) return 0;
        for (var handler : context.forSelector(getSelectorTag()).capabilities(
                net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,
                MachineContext.ComponentKind.OUTPUT)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack remainder = handler.insertItem(slot, product.copyWithCount(Integer.MAX_VALUE), true);
                total += (long) Integer.MAX_VALUE - remainder.getCount();
            }
        }
        return total;
    }

    /** Reserve real slot capacity without mixing different item tags in an empty slot. */
    public long reserveOutput(MachineContext context, long demand,
            java.util.IdentityHashMap<net.minecraftforge.items.IItemHandler, java.util.Map<Integer, ItemStack>> reserved) {
        ItemStack product = outputStack(context);
        if (product.isEmpty()) return 0;
        long remaining = demand;
        var staged = new java.util.IdentityHashMap<net.minecraftforge.items.IItemHandler, java.util.Map<Integer, ItemStack>>();
        for (var handler : context.forSelector(getSelectorTag()).capabilities(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,
                MachineContext.ComponentKind.OUTPUT)) {
            var existing = reserved.getOrDefault(handler, java.util.Map.of());
            for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
                ItemStack prior = existing.getOrDefault(slot, ItemStack.EMPTY);
                if (!prior.isEmpty() && !ItemStack.isSameItemSameTags(prior, product)) continue;
                int offer = (int) Math.min(Integer.MAX_VALUE, remaining + prior.getCount());
                ItemStack rest = handler.insertItem(slot, product.copyWithCount(offer), true);
                int take = (int) Math.min(remaining, Math.max(0, (long) offer - rest.getCount() - prior.getCount()));
                if (take > 0) {
                    staged.computeIfAbsent(handler, ignored -> new java.util.HashMap<>())
                            .put(slot, product.copyWithCount(prior.getCount() + take));
                    remaining -= take;
                }
            }
        }
        if (remaining == 0) staged.forEach((handler, slots) ->
                reserved.computeIfAbsent(handler, ignored -> new java.util.HashMap<>()).putAll(slots));
        return demand - remaining;
    }

    @Override
    public String describe() {
        return amount + " x " + rawId + " (" + ioType + ")"
                + (resolutionNote != null ? " [" + resolutionNote + "]" : "")
                + (isResolvable() ? "" : " [UNRESOLVED]");
    }

    @Override
    public String typeId() {
        return "modularmachinery:item";
    }

    /**
     * Applies a machine modifier. `chance` selects WHICH number is scaled, because an item requirement is
     * the one kind carrying both a count and a probability -- upstream's RecipeModifier has a chance flag
     * for exactly this distinction, and scaling the wrong one would turn "twice as much" into
     * "twice as likely".
     */
    @Override
    public Requirement scaled(float value, int operation, IOType io, boolean chance) {
        if (io != null && io != ioType) {
            return this;   // the modifier targets the other direction
        }
        if (chance) {
            RequirementItem copy = new RequirementItem(ioType, rawId, amount, Requirement.apply(this.chance, value, operation)).withNbt(nbt).withConsumeDurability(consumeDurability);
            copy.itemChecker = itemChecker; copy.itemModifiers.addAll(itemModifiers);
            return copySelectorTo(copy);
        }
        int scaled = Math.max(0, Math.round(Requirement.apply(amount, value, operation)));
        RequirementItem copy = new RequirementItem(ioType, rawId, scaled, this.chance).withNbt(nbt).withConsumeDurability(consumeDurability);
        copy.itemChecker = itemChecker; copy.itemModifiers.addAll(itemModifiers);
        return copySelectorTo(copy);
    }
}
