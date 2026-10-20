/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.crafting.requirement.type;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.machine.IOType;

/** JSON alias for the working upstream item.consumeDurability behavior. */
public final class ItemDurabilityRequirementType implements RequirementType {
    public static final String NAME = "item_durability";
    @Override public Requirement createRequirement(IOType io, JsonObject json) {
        if (!json.has("consumeDurability") || json.get("consumeDurability").getAsInt() <= 0) {
            throw new JsonParseException("item_durability requires a positive consumeDurability");
        }
        return new ItemRequirementType().createRequirement(io, json);
    }
}
