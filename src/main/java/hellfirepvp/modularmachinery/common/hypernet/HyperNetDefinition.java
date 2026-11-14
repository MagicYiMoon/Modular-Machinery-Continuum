/* SPDX-License-Identifier: GPL-3.0-only
 * Role/thread/limit concepts adapted from NECO RegistryHyperNet and old node types, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import com.google.gson.*;

/** Immutable machine-side configuration. Module output and recipes are deliberately separate. */
public record HyperNetDefinition(Role role, String workingThread, double carrying,
                                 int maxConnections, int maxNodesPerType,
                                 java.util.List<HyperNetModule> modules, String connectionCard, HyperNetThermalDefinition thermal,
                                 HyperNetCircuitDefinition circuit, boolean fairScheduling, int storageCapacity, double maxTechLevel) {
    public HyperNetDefinition(Role role,String thread,double carrying,int connections,int perType,
                              java.util.List<HyperNetModule> modules,String card,HyperNetThermalDefinition thermal,
                              HyperNetCircuitDefinition circuit,boolean fairScheduling,int storageCapacity) {
        this(role,thread,carrying,connections,perType,modules,card,thermal,circuit,fairScheduling,storageCapacity,0);
    }
    public HyperNetDefinition(Role role,String thread,double carrying,int connections,int perType,
                              java.util.List<HyperNetModule> modules,String card,HyperNetThermalDefinition thermal,
                              HyperNetCircuitDefinition circuit,boolean fairScheduling) {
        this(role,thread,carrying,connections,perType,modules,card,thermal,circuit,fairScheduling,0);
    }
    public HyperNetDefinition(Role role,String thread,double carrying,int connections,int perType,
                              java.util.List<HyperNetModule> modules,String card,HyperNetThermalDefinition thermal,
                              HyperNetCircuitDefinition circuit) {
        this(role,thread,carrying,connections,perType,modules,card,thermal,circuit,false);
    }
    public enum Role { CENTER, PROCESSOR, NODE, DATABASE, RESEARCH_STATION, TERMINAL }
    public static final String DEFAULT_CONNECTION_CARD = "modularmachinery:hypernet_connect_card";
    public static final String RESEARCH_THREAD = "novaeng.hypernet.research_station.hypercol";
    public static final String DATABASE_THREAD = "novaeng.hypernet.data_forwarding_processor";
    public static final String CENTER_THREAD = "novaeng.hypernet.data_forwarding_processor";
    public static final String PROCESSOR_THREAD = "novaeng.hypernet.data_processor.compute_scheduler";
    public static final String RADIATOR_THREAD = "novaeng.hypernet.data_processor.radiator";
    public static final String MAINTENANCE_THREAD = "novaeng.hypernet.maintenance_controller";
    public HyperNetDefinition(Role role,String thread,double carrying,int connections,int perType,
                              java.util.List<HyperNetModule> modules,String card,HyperNetThermalDefinition thermal) {
        this(role,thread,carrying,connections,perType,modules,card,thermal,null);
    }
    public HyperNetDefinition(Role role,String thread,double carrying,int connections,int perType) {
        this(role,thread,carrying,connections,perType,java.util.List.of(),"",null);
    }
    public HyperNetDefinition(Role role,String thread,double carrying,int connections,int perType,
                              java.util.List<HyperNetModule> modules,String card) {
        this(role,thread,carrying,connections,perType,modules,card,null);
    }
    public HyperNetDefinition {
        modules=java.util.List.copyOf(modules);
        if (!Double.isFinite(maxTechLevel) || maxTechLevel < 0 || role != Role.RESEARCH_STATION && maxTechLevel != 0)
            throw new IllegalArgumentException("Only research stations accept a finite nonnegative max-tech-level");
        if (role == Role.DATABASE ? storageCapacity < 1 || storageCapacity > HyperNetResearchRegistry.MAX_RESEARCH : storageCapacity != 0)
            throw new IllegalArgumentException("Only databases accept storage-capacity (1..4096)");
        if(connectionCard==null)throw new IllegalArgumentException("Missing connection card setting");
        if(!modules.isEmpty()&&role!=Role.PROCESSOR)throw new IllegalArgumentException("Only processors accept modules");
        if(thermal!=null&&role!=Role.PROCESSOR)throw new IllegalArgumentException("Only processors accept thermal settings");
        if(fairScheduling&&role!=Role.CENTER)throw new IllegalArgumentException("Only centers accept fair scheduling");
        if(circuit!=null&&role!=Role.CENTER)throw new IllegalArgumentException("Only centers accept circuit settings");
        if(!connectionCard.isEmpty()&&role!=Role.CENTER)throw new IllegalArgumentException("Only centers select a connection card item");
        if (role == null || workingThread == null || !Double.isFinite(carrying) || carrying < 0
                || maxConnections < 0 || maxNodesPerType < 0)
            throw new IllegalArgumentException("Invalid HyperNet definition");
        if (role != Role.NODE && role != Role.TERMINAL && workingThread.isBlank()) throw new IllegalArgumentException("HyperNet work thread is empty");
        if (role == Role.CENTER && (carrying <= 0 || maxConnections == 0 || maxNodesPerType == 0))
            throw new IllegalArgumentException("HyperNet centre limits must be positive");
        if (role != Role.CENTER && (carrying != 0 || maxConnections != 0 || maxNodesPerType != 0))
            throw new IllegalArgumentException("Only a HyperNet centre may define network limits");
        if ((role == Role.NODE || role == Role.TERMINAL) && !workingThread.isEmpty())
            throw new IllegalArgumentException("A consumer/terminal node has no core work thread");
    }
    public static HyperNetDefinition parse(JsonElement value) {
        if (!value.isJsonObject()) throw new IllegalArgumentException("hypernet must be an object");
        var json = value.getAsJsonObject();
        var allowed = java.util.Set.of("role", "working-thread", "carrying", "max-connections", "max-nodes-per-type", "modules", "connection-card", "thermal", "circuit", "fair-scheduling", "storage-capacity", "max-tech-level");
        for (String key : json.keySet()) if (!allowed.contains(key))
            throw new IllegalArgumentException("Unknown hypernet field: " + key);
        if (!json.has("role") || !json.get("role").isJsonPrimitive() || !json.getAsJsonPrimitive("role").isString())
            throw new IllegalArgumentException("hypernet.role must be a string");
        var role = Role.valueOf(json.get("role").getAsString().toUpperCase(java.util.Locale.ROOT));
        if (role != Role.CENTER && (json.has("carrying") || json.has("max-connections") || json.has("max-nodes-per-type")))
            throw new IllegalArgumentException("Only a HyperNet centre may define network limits");
        if (json.has("working-thread") && (!json.get("working-thread").isJsonPrimitive() || !json.getAsJsonPrimitive("working-thread").isString()))
            throw new IllegalArgumentException("hypernet.working-thread must be a string");
        if (role == Role.CENTER && (!json.has("carrying") || !json.get("carrying").isJsonPrimitive() || !json.getAsJsonPrimitive("carrying").isNumber()))
            throw new IllegalArgumentException("hypernet.carrying must be a number");
        String thread = json.has("working-thread") ? json.get("working-thread").getAsString()
                : role == Role.CENTER ? CENTER_THREAD : role == Role.PROCESSOR ? PROCESSOR_THREAD : role == Role.DATABASE ? DATABASE_THREAD : role == Role.RESEARCH_STATION ? RESEARCH_THREAD : "";
        double carrying = role == Role.CENTER ? json.get("carrying").getAsDouble() : 0;
        int connections = role == Role.CENTER ? integer(json, "max-connections", -1) : 0;
        if (json.has("max-tech-level") && (role != Role.RESEARCH_STATION || !json.get("max-tech-level").isJsonPrimitive()
                || !json.getAsJsonPrimitive("max-tech-level").isNumber())) throw new IllegalArgumentException("Only research stations accept numeric max-tech-level");
        if (role == Role.RESEARCH_STATION && !json.has("max-tech-level")) throw new IllegalArgumentException("Research station requires max-tech-level");
        if (role != Role.DATABASE && json.has("storage-capacity")) throw new IllegalArgumentException("Only databases accept storage-capacity");
        if(json.has("modules")&&role!=Role.PROCESSOR)throw new IllegalArgumentException("Only processors accept modules");
        String card=role==Role.CENTER?DEFAULT_CONNECTION_CARD:"";
        if(json.has("connection-card")){
            if(role!=Role.CENTER||!json.get("connection-card").isJsonPrimitive()||!json.getAsJsonPrimitive("connection-card").isString())
                throw new IllegalArgumentException("Only centers accept a connection-card item id");
            card=json.get("connection-card").getAsString();
            var id=net.minecraft.resources.ResourceLocation.tryParse(card);
            if(id==null||!net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(id)
                    ||net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id)==net.minecraft.world.item.Items.AIR)
                throw new IllegalArgumentException("Unknown connection-card item: "+card);
            card=id.toString();
        }
        if (json.has("fair-scheduling") && (role != Role.CENTER || !json.get("fair-scheduling").isJsonPrimitive()
                || !json.getAsJsonPrimitive("fair-scheduling").isBoolean()))
            throw new IllegalArgumentException("Only centers accept a boolean fair-scheduling");
        return new HyperNetDefinition(role, thread, carrying, connections,
                role == Role.CENTER ? integer(json, "max-nodes-per-type", 1000) : 0,
                json.has("modules")?HyperNetModule.parse(json.get("modules")):java.util.List.of(),card,
                json.has("thermal")?HyperNetThermalDefinition.parse(json.get("thermal")):null,
                json.has("circuit")?HyperNetCircuitDefinition.parse(json.get("circuit")):null,
                json.has("fair-scheduling") && json.get("fair-scheduling").getAsBoolean(),
                role == Role.DATABASE ? integer(json, "storage-capacity", -1) : 0,
                role == Role.RESEARCH_STATION ? json.get("max-tech-level").getAsDouble() : 0);
    }
    private static int integer(JsonObject json, String key, int fallback) {
        if (!json.has(key)) return fallback;
        if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException(key + " must be a number");
        try { return json.get(key).getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException invalid) { throw new IllegalArgumentException(key + " must be an exact int", invalid); }
    }
}
