/* SPDX-License-Identifier: GPL-3.0-only
 * CPU/GPU/RAM capacities and peak-load costs adapt NECO old.upgrade module types, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import com.google.gson.*;
import java.util.*;

/** Server-authored attributes of an existing upgrade type; never trusts item-supplied capacities. */
public record HyperNetModule(String upgrade, Kind kind, double capacity, long energy,
                             hellfirepvp.modularmachinery.common.machine.MachineDisplayInfo displayInfo) {
    public HyperNetModule(String upgrade,Kind kind,double capacity,long energy) {
        this(upgrade,kind,capacity,energy,hellfirepvp.modularmachinery.common.machine.MachineDisplayInfo.EMPTY);
    }
    public enum Kind { CPU, GPU, RAM }
    public HyperNetModule {
        java.util.Objects.requireNonNull(displayInfo,"Module display info");
        if(upgrade==null||upgrade.isBlank()||upgrade.length()>256||kind==null
                ||!Double.isFinite(capacity)||capacity<=0||energy<0)
            throw new IllegalArgumentException("Invalid HyperNet module");
    }
    public static List<HyperNetModule> parse(JsonElement value) {
        if(!value.isJsonArray()||value.getAsJsonArray().size()>256)
            throw new IllegalArgumentException("hypernet.modules must be an array of at most 256 types");
        var out=new ArrayList<HyperNetModule>();var seen=new HashSet<String>();
        for(var element:value.getAsJsonArray()){
            if(!element.isJsonObject())throw new IllegalArgumentException("HyperNet module must be an object");
            var json=element.getAsJsonObject();
            for(String key:json.keySet())if(!Set.of("upgrade","kind","capacity","energy","display-info").contains(key))
                throw new IllegalArgumentException("Unknown HyperNet module field: "+key);
            String id=string(json,"upgrade"),kind=string(json,"kind");
            if(!seen.add(id))throw new IllegalArgumentException("Duplicate HyperNet module type: "+id);
            try {
                out.add(new HyperNetModule(id,Kind.valueOf(kind.toUpperCase(Locale.ROOT)),number(json,"capacity").getAsDouble(),
                        number(json,"energy").getAsBigDecimal().longValueExact(),
                        json.has("display-info")?hellfirepvp.modularmachinery.common.machine.MachineDisplayInfo.parse(json.get("display-info"))
                                :hellfirepvp.modularmachinery.common.machine.MachineDisplayInfo.EMPTY));
            }catch(ArithmeticException invalid){throw new IllegalArgumentException("Module energy must be an exact nonnegative long",invalid);}
        }
        return List.copyOf(out);
    }
    private static String string(JsonObject json,String key){
        if(!json.has(key)||!json.get(key).isJsonPrimitive()||!json.getAsJsonPrimitive(key).isString())
            throw new IllegalArgumentException("Module "+key+" must be a string");
        return json.get(key).getAsString();
    }
    private static JsonPrimitive number(JsonObject json,String key){
        if(!json.has(key)||!json.get(key).isJsonPrimitive()||!json.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException("Module "+key+" must be a number");
        return json.getAsJsonPrimitive(key);
    }
}
