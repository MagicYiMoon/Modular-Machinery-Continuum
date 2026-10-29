/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Derived from MMCE's preview ingredient lists.
 */
package hellfirepvp.modularmachinery.common.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.function.BiFunction;

/** Material snapshots and scrolling, independent of JEI and client rendering. */
public final class BlueprintMaterialState {
    /** Read-only material view of one sampled scene; revisions change when its contents are rebuilt. */
    public interface Source {
        BlueprintSceneDefinition definition();
        long revision();
        BlockState getBlockState(BlockPos pos);
        ItemStack materialStack(BlockPos pos, BlockState state);
        List<ItemStack> materialStacks(BlockPos pos);
    }
    public static final int COLUMNS = 9, ROWS = 2, ALTERNATIVE_ROWS = 3;
    private final int visibleRows, alternativeRows;
    private List<ItemStack> summary = List.of(), alternatives = List.of();
    private ItemStack primary = ItemStack.EMPTY;
    private BlockPos selected;
    private int materialScroll, alternativeScroll;
    private Source lastSource;
    private long lastRevision;

    public BlueprintMaterialState() { this(ROWS, ALTERNATIVE_ROWS); }

    public BlueprintMaterialState(int visibleRows, int alternativeRows) {
        if (visibleRows < 1 || alternativeRows < 1) throw new IllegalArgumentException("Empty material viewport");
        this.visibleRows = visibleRows;
        this.alternativeRows = alternativeRows;
    }

    /** Refresh totals on actual scene changes; refresh the clicked snapshot only when replacing its scene. */
    public boolean update(Source source) {
        if (lastSource == source && lastRevision == source.revision()) return false;
        if (lastSource != source && selected != null) {
            if (!source.definition().cells().containsKey(selected)) select(null, ItemStack.EMPTY, List.of());
            else refreshSelection(source.materialStack(selected, source.getBlockState(selected)), source.materialStacks(selected));
        }
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        source.definition().cells().keySet().forEach(pos -> states.put(pos, source.getBlockState(pos)));
        updateSummary(states, source::materialStack);
        lastSource = source;
        lastRevision = source.revision();
        return true;
    }

    /** Release the old scene reference without discarding the user's captured material or scroll positions. */
    public void invalidate() { lastSource = null; }

    public void select(Source source, BlockPos pos) {
        if (source != lastSource) invalidate();
        if (source != null && pos != null && !source.definition().cells().containsKey(pos)) pos = null;
        select(pos, source == null || pos == null ? ItemStack.EMPTY : source.materialStack(pos, source.getBlockState(pos)),
                source == null || pos == null ? List.of() : source.materialStacks(pos));
    }

    public void updateSummary(Map<BlockPos, BlockState> states, BiFunction<BlockPos, BlockState, ItemStack> resolver) {
        summary = BlueprintMaterials.summarize(states, resolver);
        materialScroll = Math.min(materialScroll, maxMaterialScroll());
    }

    /** Keep the clicked candidate stable even while the scene cycles to another candidate. */
    public void select(BlockPos pos, ItemStack current, List<ItemStack> candidates) {
        selected = pos == null ? null : pos.immutable();
        primary = pos == null ? ItemStack.EMPTY : current.copyWithCount(1);
        List<ItemStack> remaining = new ArrayList<>();
        if (pos != null) for (ItemStack stack : candidates) {
            if (!stack.isEmpty() && !ItemStack.isSameItemSameTags(stack, primary)
                    && remaining.stream().noneMatch(previous -> ItemStack.isSameItemSameTags(previous, stack))) {
                remaining.add(stack.copyWithCount(1));
            }
        }
        alternatives = List.copyOf(remaining);
        alternativeScroll = 0;
    }

    public void retain(BlueprintSceneDefinition definition) {
        invalidate();
        if (selected != null && !definition.cells().containsKey(selected)) select(null, ItemStack.EMPTY, List.of());
        summary = List.of();
        materialScroll = 0;
    }

    /** Dynamic resizing can replace the candidates at a surviving coordinate. */
    public void refreshSelection(ItemStack current, List<ItemStack> candidates) {
        if (selected == null) return;
        int previousScroll = alternativeScroll;
        boolean stillAccepted = ItemStack.isSameItemSameTags(primary, current)
                || candidates.stream().anyMatch(stack -> ItemStack.isSameItemSameTags(primary, stack));
        select(selected, stillAccepted ? primary : current, candidates);
        alternativeScroll = Math.min(previousScroll, maxAlternativeScroll());
    }

    public BlockPos selected() { return selected; }
    public int materialScroll() { return materialScroll; }
    public int alternativeScroll() { return alternativeScroll; }
    public int maxMaterialScroll() { return Math.max(0, (summary.size() + COLUMNS - 1) / COLUMNS - visibleRows); }
    public int maxAlternativeScroll() { return Math.max(0, alternatives.size() - alternativeRows); }

    public void scrollMaterials(int delta) { materialScroll = clamp((long) materialScroll + delta, maxMaterialScroll()); }
    public void scrollAlternatives(int delta) { alternativeScroll = clamp((long) alternativeScroll + delta, maxAlternativeScroll()); }
    public void scrollMaterialTo(double fraction) {
        materialScroll = clamp(Math.round(Math.max(0, Math.min(1, fraction)) * maxMaterialScroll()), maxMaterialScroll());
    }
    private static int clamp(long value, int max) { return (int) Math.max(0, Math.min(value, max)); }

    public ItemStack material(int visibleIndex) {
        return visibleIndex < 0 || visibleIndex >= COLUMNS * visibleRows ? ItemStack.EMPTY
                : at(summary, materialScroll * COLUMNS + visibleIndex);
    }
    public ItemStack primary() { return primary.copy(); }
    public ItemStack alternative(int visibleRow) {
        return visibleRow < 0 || visibleRow >= alternativeRows ? ItemStack.EMPTY : at(alternatives, alternativeScroll + visibleRow);
    }
    private static ItemStack at(List<ItemStack> list, int index) { return index < list.size() ? list.get(index).copy() : ItemStack.EMPTY; }
}
