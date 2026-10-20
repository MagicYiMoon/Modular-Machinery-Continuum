/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.upgrade;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.data.IData;
import com.blamejared.crafttweaker.api.data.converter.tag.TagToDataConverter;
import com.blamejared.crafttweaker.api.item.IItemStack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.openzen.zencode.java.ZenCodeType;

/** Dynamic upgrade whose item and bus descriptions are computed from the live upgrade instance. */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.SimpleDynamicMachineUpgrade")
public class SimpleDynamicMachineUpgrade extends DynamicMachineUpgrade {
    private final List<String> descriptions = new ArrayList<>();
    private final List<String> busGuiDescriptions = new ArrayList<>();
    private Function<SimpleDynamicMachineUpgrade, String[]> descriptionHandler;
    private Function<SimpleDynamicMachineUpgrade, String[]> busGuiDescriptionHandler;

    public SimpleDynamicMachineUpgrade(UpgradeType type) {
        super(type);
    }

    public SimpleDynamicMachineUpgrade describe(String description) {
        if (description != null) descriptions.add(description);
        return this;
    }

    public SimpleDynamicMachineUpgrade describeBus(String description) {
        if (description != null) busGuiDescriptions.add(description);
        return this;
    }

    public void setDescriptionHandler(Function<SimpleDynamicMachineUpgrade, String[]> handler) {
        descriptionHandler = handler;
    }

    public void setBusGUIDescriptionHandler(Function<SimpleDynamicMachineUpgrade, String[]> handler) {
        busGuiDescriptionHandler = handler;
    }

    @ZenCodeType.Getter("itemData")
    public IData getItemData() {
        return TagToDataConverter.convert(itemData);
    }

    @ZenCodeType.Setter("itemData")
    public void setItemData(IData data) {
        if (data == null || !(data.getInternal() instanceof CompoundTag tag)) {
            throw new IllegalArgumentException("itemData must be a compound IData value");
        }
        setItemDataTag(tag);
    }

    @ZenCodeType.Getter("customData")
    public IData getCustomDataValue() {
        return TagToDataConverter.convert(customData);
    }

    @ZenCodeType.Setter("customData")
    public void setCustomDataValue(IData data) {
        if (data == null || !(data.getInternal() instanceof CompoundTag tag)) {
            throw new IllegalArgumentException("customData must be a compound IData value");
        }
        setCustomData(tag);
    }

    @ZenCodeType.Getter("parentStack")
    public IItemStack getParentStackCT() {
        return IItemStack.of(parentStack);
    }

    @ZenCodeType.Getter("valid")
    public boolean isValidForScript() {
        return isValid();
    }

    @ZenCodeType.Method
    @Override
    public void decrementItemDurability(int durability) {
        super.decrementItemDurability(durability);
    }

    @Override
    public List<String> getDescriptions() {
        return evaluate(descriptionHandler, descriptions);
    }

    @Override
    public List<String> getBusGUIDescriptions() {
        return evaluate(busGuiDescriptionHandler, busGuiDescriptions);
    }

    private List<String> evaluate(Function<SimpleDynamicMachineUpgrade, String[]> handler, List<String> fallback) {
        if (handler == null) return Collections.unmodifiableList(fallback);
        try {
            String[] values = handler.apply(this);
            if (values == null) return List.of();
            List<String> result = new ArrayList<>(values.length);
            for (String value : values) if (value != null) result.add(value);
            return Collections.unmodifiableList(result);
        } catch (Throwable failure) {
            hellfirepvp.modularmachinery.Modularmachinery.LOG.warn(
                    "[MMCE] Dynamic upgrade description handler failed for {}", getType().getName(), failure);
            return List.of();
        }
    }

    @Override
    public DynamicMachineUpgrade copy(ItemStack owner) {
        SimpleDynamicMachineUpgrade copy = new SimpleDynamicMachineUpgrade(getType());
        copy.descriptions.addAll(descriptions);
        copy.busGuiDescriptions.addAll(busGuiDescriptions);
        copy.descriptionHandler = descriptionHandler;
        copy.busGuiDescriptionHandler = busGuiDescriptionHandler;
        copy.itemData = itemData.copy();
        copy.customData = customData.copy();
        copy.stackSize = stackSize;
        copy.valid = valid;
        copy.parentStack = owner == null ? ItemStack.EMPTY : owner.copy();
        copyEventHandlersTo(copy);
        return copy;
    }
}
