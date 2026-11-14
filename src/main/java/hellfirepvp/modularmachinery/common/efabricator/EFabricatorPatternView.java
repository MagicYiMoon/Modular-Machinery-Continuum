/* SPDX-License-Identifier: GPL-3.0-only; bounded adaptation of NECO EFabricatorPatternData. */
package hellfirepvp.modularmachinery.common.efabricator;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class EFabricatorPatternView {
    public static final int PAGE_SIZE = 48, MAX_MATCHES = 4096, MAX_QUERY = 96;
    private EFabricatorPatternView() { }
    public record Ingredient(ResourceLocation id, Component name) {
        public Ingredient {
            if (id.toString().length() > 128) throw new IllegalArgumentException("EF ingredient id bound");
            // Preserve normal translation components; arbitrary custom display names cannot
            // turn a 48-entry monitor page into an oversized network payload.
            if (Component.Serializer.toJson(name).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 256) {
                var plain = name.getString(); name = Component.literal(plain.substring(0, Math.min(64, plain.length())));
            }
        }
    }
    public record Description(ItemStack icon, List<Ingredient> inputs, List<Ingredient> outputs, boolean valid) {
        public Description {
            icon = icon.isEmpty() ? ItemStack.EMPTY : new ItemStack(icon.getItem());
            inputs = List.copyOf(inputs); outputs = List.copyOf(outputs);
            if (inputs.size() > 16 || outputs.size() > 16) throw new IllegalArgumentException("EF pattern ingredient bound");
        }
        @Override public ItemStack icon() { return icon.copy(); }
        public static Description invalid() { return new Description(ItemStack.EMPTY, List.of(), List.of(), false); }
    }
    public record Entry(BlockPos bus, int slot, Description description) {
        public Entry { if (slot < 0 || slot >= 72) throw new IllegalArgumentException("EF pattern slot"); bus = bus.immutable(); }
    }
    public record Snapshot(long token, int page, int pages, int total, int buses, boolean formed, boolean readable, List<Entry> entries) {
        public Snapshot {
            entries = List.copyOf(entries);
            if (token < 0 || page < 0 || pages < 1 || page >= pages || total < 0 || buses < 0 || entries.size() > PAGE_SIZE) throw new IllegalArgumentException("EF pattern page");
        }
        public static Snapshot empty() { return new Snapshot(0, 0, 1, 0, 0, false, true, List.of()); }
        public void write(FriendlyByteBuf b) {
            b.writeVarLong(token); b.writeVarInt(page); b.writeVarInt(pages); b.writeVarInt(total); b.writeVarInt(buses); b.writeBoolean(formed); b.writeBoolean(readable); b.writeVarInt(entries.size());
            for (var e : entries) {
                b.writeBlockPos(e.bus); b.writeVarInt(e.slot); b.writeItem(e.description.icon()); b.writeBoolean(e.description.valid);
                writeIngredients(b, e.description.inputs); writeIngredients(b, e.description.outputs);
            }
        }
        public static Snapshot read(FriendlyByteBuf b) {
            long token = b.readVarLong(); int page = b.readVarInt(), pages = b.readVarInt(), total = b.readVarInt(), buses = b.readVarInt();
            boolean formed = b.readBoolean(), readable = b.readBoolean(); int count = bounded(b.readVarInt(), PAGE_SIZE);
            var entries = new ArrayList<Entry>();
            for (int i = 0; i < count; i++) {
                var pos = b.readBlockPos(); int slot = b.readVarInt(); var icon = b.readItem(); boolean valid = b.readBoolean();
                entries.add(new Entry(pos, slot, new Description(icon, readIngredients(b), readIngredients(b), valid)));
            }
            return new Snapshot(token, page, pages, total, buses, formed, readable, entries);
        }
    }
    private static int bounded(int n, int max) { if (n < 0 || n > max) throw new IllegalArgumentException("EF pattern count"); return n; }
    private static void writeIngredients(FriendlyByteBuf b, List<Ingredient> values) {
        b.writeVarInt(values.size()); for (var value : values) { b.writeResourceLocation(value.id); b.writeComponent(value.name); }
    }
    private static List<Ingredient> readIngredients(FriendlyByteBuf b) {
        int count = bounded(b.readVarInt(), 16); var values = new ArrayList<Ingredient>();
        for (int i = 0; i < count; i++) values.add(new Ingredient(b.readResourceLocation(), b.readComponent())); return values;
    }
    public record Query(String input, String output, Set<ResourceLocation> inputNames, Set<ResourceLocation> outputNames, int page) {
        public Query {
            if (input.length() > MAX_QUERY || output.length() > MAX_QUERY || inputNames.size() > MAX_MATCHES || outputNames.size() > MAX_MATCHES || page < 0
                    || inputNames.stream().anyMatch(id -> id.toString().length() > 128) || outputNames.stream().anyMatch(id -> id.toString().length() > 128)) throw new IllegalArgumentException("EF query bound");
            input = clean(input); output = clean(output); inputNames = Set.copyOf(inputNames); outputNames = Set.copyOf(outputNames);
        }
        public static Query empty() { return new Query("", "", Set.of(), Set.of(), 0); }
        public boolean matches(Description d) {
            if (input.isBlank() && output.isBlank()) return true;
            // Source search combines nonempty input/output matches as a union.
            return !input.isBlank() && matches(d.inputs, input, inputNames) || !output.isBlank() && matches(d.outputs, output, outputNames);
        }
        private static boolean matches(List<Ingredient> ingredients, String query, Set<ResourceLocation> names) {
            return ingredients.stream().anyMatch(i -> names.contains(i.id) || clean(i.id.toString()).contains(query) || clean(i.name.getString()).contains(query));
        }
        public void write(FriendlyByteBuf b) {
            b.writeUtf(input, MAX_QUERY); b.writeUtf(output, MAX_QUERY); writeIds(b, inputNames); writeIds(b, outputNames); b.writeVarInt(page);
        }
        public static Query read(FriendlyByteBuf b) { return new Query(b.readUtf(MAX_QUERY), b.readUtf(MAX_QUERY), readIds(b), readIds(b), b.readVarInt()); }
        private static void writeIds(FriendlyByteBuf b, Set<ResourceLocation> values) { b.writeVarInt(values.size()); for (var value : values) b.writeResourceLocation(value); }
        private static Set<ResourceLocation> readIds(FriendlyByteBuf b) {
            int count = bounded(b.readVarInt(), MAX_MATCHES); var values = new HashSet<ResourceLocation>();
            for (int i = 0; i < count; i++) values.add(b.readResourceLocation()); return values;
        }
    }
    private static String clean(String s) { return s.replaceAll("§.", "").strip().toLowerCase(Locale.ROOT); }
}
