# Third-party notices

## NovaEngineering Core

The ES graph options, label/guide coordinates, global byte-capacity ratios and
idle-drain/stored-energy ratio adapt EStorageGraph and Graph from the revision
below. Labels use this port's existing native-size compact font; per-open random
offsets and the source's value interpolation are not reproduced. Energy-cell
item overrides adapt EStorageEnergyCellItemRenderer's five charge states using
vanilla item properties and the registered tier's capacity. Reproduction:
tools/prepare_es_energy_item_assets.py. GUI transforms show the charge face, and
charged models' solid shells are inset 0.01 model units below their coplanar
full-bright overlays. Source PNGs are unchanged.

The initial HyperNet budget and controller lifecycle adapt the old
ComputationCenter/NetNode/DataProcessor roles, core-thread gates, connection
limits and per-tick budget concept. Thread-confined decimal all-or-nothing
accounting, revocable provider leases, loaded-only world lookups and revision-bound
lifetimes are new port code. CPU/GPU/RAM module capacities, ordered load costs
and existing-item connection cards adapt old.upgrade module types and
HyperNetEventHandler/HyperNetConnectCardInfo. Server-owned hardware profiles,
cumulative exact FE rounding, reversible native-hatch payments and dimension/
machine/owner card checks are new port code. Binding/ownership and physical
thermal metadata are persisted; computation credit is transient. JSON/ZenCode computation requirements and their batch, per-tick and
default-pause behavior adapt RecipePrimerHyperNet and old.NetNodeImpl. Grouped
decimal checks, explicit missing-backend rejection and progress-preserving
disconnection are new port code. Active recipe demand follows old.NetNodeImpl
start/tick/finish registration. Per-controller end-of-tick scalar snapshots,
exact batch/exemption aggregation and completed-tick read-only demand views
replace the source recipe-thread map and its ten-tick refresh; no recipe/thread
references or demand credit are persisted. Processor heat generation, thermal
derating, latched overheating and passive/dynamic-pattern dissipation adapt
old.DataProcessor/DataProcessorType. Exact fractional heat/cooling, tick-scoped
rational efficiency, private persisted state and corrupt-data bounds are port
corrections. Active radiator completion effects, center circuit wear, the 5% core
threshold and repair recipes adapt old.DataProcessorType, ComputationCenterType,
ComputationCenter and base.NetNodeTypeRepairable. Aggregated batch checks,
bounded zero/full completion, world-tick wear cadence with no offline catch-up,
equal-bound wear safety and private versioned circuit state are port corrections.
Material/FE profiles remain explicitly pack-defined; no source-pack defaults
are silently generated. The completed-tick commit statistics, read-only TOP
inspection, bounded visual forms and display-only machine/module tier labels
and descriptions are new port code; the modpack scripts and balance data are
not used as implementation assets. Research/database terminals remain separate work. Fair scheduling is an optional port extension.
It does not copy the modpack's
scripts, balance tables or machinery.

The EF TOP worker provider adapts EFabricatorInfoProvider and its ColorUtils gradient
from the pinned revision below. It retains the source worker-only scope, AE cache,
queue/craft counts and current plus at most 32 queued work-entry icons, using
TOP native transport, NBT-free illustrations and explicit unknown-data handling.

EF physical component identities, tier/queue/parallel specifications, exclusive
structure ownership, the crafting-count FIFO concept and the 72-slot physical
pattern inventory are adapted from the same pinned NECO revision below.
EF queue serialization is a new bounded format: it retains per-craft outputs and
remainders rather than copying the upstream batch split/recount defects.
EF offline/formed controller and OFF/ON/RUN worker artwork is adapted with tools/prepare_ef_base_assets.py;
its PNG files are copied unchanged. The EF ME channel, crafting-only pattern
publication, channel flags and 64 AE/t plus valid pattern-slot idle drain follow
the same source. The port uses AE2 15's crafting grid and remainder behavior,
with atomic single-craft queue admission. EF work cycles, worker energy caches,
per-craft energy costs, shared parallel allowances and powered output delivery
follow the same source concepts. Completed craft counts are exact; a new bounded
long-count output codec retains rejected products and preserves private data in
controller drops. Active coolant rules, conversion cache and per-craft/acceleration
charges adapt NECO MachineCoolants and EFabricatorController. The port resolves
namespaced fluids, checks full output ratios and retains partially delivered hot
fluid or collected cold fluid in a private durable ledger. EF host model hiding,
source light values and queue-driven worker LEDs with a twenty-tick linger
adapt EFabricatorController.HIDE_POS_LIST and EFabricatorWorker.updateStatus.
Worker visual metadata is bounded and does not alter the world-state palette.
Source bloom sprites use Forge full-bright element data; coincident solid shells
are inset by 0.01 model units beneath the original overlay for a single cutout
pass. Source PNGs and animation metadata are unchanged; no screen-space bloom
effect is bundled. The EF controller monitor adapts the source status,
control, statistics and heat panels. Six GUI PNGs are copied unchanged via
  tools/prepare_ef_gui_assets.py. Its derived efabricator_modes.png recolors only
  mode glyphs (blue enabled, orange disabled), retaining upstream frames, alpha
  and pressed shading. The port adds bounded worker pages, private-NBT-free
illustrations, long totals, and session-checked explicit mode requests. The
72-slot pattern-bus and pattern-search menus adapt NECO Container/GuiEFabricatorPatternBus,
Container/GuiEFabricatorPatternSearch, PatternPanel, PatternSlot, SearchPanel,
TitleButtonLine and the pattern-search action/data packets. The port adds bounded
pages and localized search identifiers, server-side item identity validation,
current-menu sessions and unknown-data retention. Original input/output search
uses a union; unsupported patterns remain physically stored and removable.

Research definitions, physical per-database completed/progress storage and
startup-only union gates adapt old.research.ResearchCognitionData, old.Database,
old.NetNodeImpl and RecipePrimerHyperNet.requireResearch from the same revision.
Bounded strict catalog/graph validation, decimal monotonic progress, atomic slot
conversion, unknown-record retention and future/corrupt format write protection
are new port code. Station task admission, technology limits, per-network singleton,
base work and leftover-budget overclocking adapt old.research.ResearchStation,
ResearchStationType and PktResearchTaskProvide from the same revision. Private
versioned task persistence, paid-point recovery, network-bound tasks and native
factory progress/output coordination are port code. The independent research
terminal now adapts TileHyperNetTerminal, BlockHyperNetTerminal,
ContainerHyperNetTerminal, old.HyperNetTerminal, GuiHyperNetTerminal and the
provide/reset task packet behavior from the same revision: fixed controller,
350x250 panel/slot coordinates, one retained connection card, per-network
singleton, 200/2000 FE idle/connected costs, research grouping and controls.
The unchanged guiterminalelement.png is included as
assets/modularmachinery/textures/gui/neco/hypernet_terminal_elements.png.
Bounded server-owned pages, exact authenticated menu/task identities, permission
and stale-state checks, strict packet validation and small-window fitting are
new port code. The targeted five-second research completion notice adapts
ResearchCompleteToast and PktResearchTaskComplete at the same revision, using
Minecraft's toast background and item renderer. The port sends bounded display
data only after successful database commit, independent of the open menu;
persisted committed state prevents repeats while core output is blocked.
Research terminal network summary rows, registered-demand semantics, T/P FloPS
formatting and fifteen-tick updates also adapt GuiHyperNetTerminal,
PktHyperNetStatus, ComputationCenter, NetNodeImpl and NovaEngUtils from the same
revision. Completed-tick read-only samples, separate actual-delivery diagnostics
and session-scoped explicit offline clearing are port code.
The card's reusable/overwrite instructions and empty/valid/invalid terminal
states also adapt HyperNetClientEventHandler and GuiHyperNetTerminal.
NECO selects the physical card item through scripts; the port's dedicated
hypernet_connect_card registration and single-stack default are new port code.
The card texture is copied byte-for-byte from the
NovaEng-CleanroomMC modpack resources/contenttweaker/textures/items/
hypernet_connect_card.png into assets/modularmachinery/textures/item/.
Its SHA-256 is 1019132da73bfe16fdaa4dbc4e9ca3efe3f4409b0319e2b958ffffbd652f6bd8.
This bitmap is a modpack asset, not a texture found in the audited NECO source;
NECO's code license is not asserted as the license of this separately supplied
bitmap. The original pack model and registration scripts were inspected to
identify the resource; no source-pack item scripts or recipes are included.
Creative-OP middle-click research adapts GuiHyperNetTerminal,
PktResearchTaskProvideCreative and ResearchStation from the same revision:
fill a station task's required points, then use its normal core/database commit.
Authenticated actor checks, existing-task protection, strict menu identities
and immediate persistence of the pending points are port code.
The terminal block model reuses the existing MMCE factory
controller artwork. No source-pack structure, recipes or research data is copied;
the minimal terminal example is new author-configurable port data.
Cycle-research getters do not imply a working cycle implementation.
The existing network monitor remains read-only.

The HyperNet monitor reuses the unchanged upstream guiterminal.png as
assets/modularmachinery/textures/gui/neco/hypernet_monitor.png. The 1.20.1
monitor layout, bounded read-only snapshots and controller-menu entry are new
port code; the upstream research/database terminal is implemented separately,
using this same background plus its original element sheet and slot coordinates.
Only the physical research terminal has the source terminal power charge.
The optional center fair-scheduling policy is new MMC code, not upstream NECO
behavior. It schedules native running computation recipes using bounded,
transient scalar tickets; the default remains upstream request-order charging.


The ES matrix specifications, storage, host/drive interaction, gas-only chemical filtering and physical matrix automation, ME channel, internal energy-cell and cell-workbench configuration and host statistics GUI, drive visual-state and formed-neighbour hiding logic, and ES
block/item models and textures are adapted from NovaEngineering Core by KasumiNova and contributors, licensed under GNU GPL version 3.
EC physical component identities, memory/slot/parallel specifications, insertion,
runtime ownership, transmitter adjacency, virtual/running CPU lifecycle,
normal/hyper-thread allocation and offline/online/formed block/item artwork are also adapted
from that revision. EC execution uses AE2 15's crafting engine through selective
host/list/lifecycle Mixins. The EC task monitor adapts the source StorageBar,
CPUStatusPanel and MonitorPanel with bounded pages and UUID-checked cancellation.
EC runtime measurements follow the source CPU-update timing and the 100-update
window of MMCE's TimeRecorder (KasumiNova and contributors, GPLv3). The port
retains nanoseconds and fractional averages, and counts AE2 15's successful
executeCrafting returns instead of reusing stale usedOps on early-return ticks.
The EC TOP provider adapts ECalculatorInfoProvider and ColorUtils from the same revision, using bounded task rows, native TOP transport, physical capacity while stopped, and actual runtime measurements.
The three EC GUI panel PNGs are copied unchanged; six core GUI thumbnails use
the first 16x16 frame of the source on-state sprites without resampling.
Reproduction: tools/prepare_ec_gui_assets.py and tools/prepare_ec_base_assets.py.
The EC actual-state/light rules and fixed/dynamic neighbour hiding follow the same revision.
Six upstream hyper-thread core models (L6/L9, off/on/run) contain six undefined
`#missing` face references each; these point to their existing tier panel texture
in the port, following the equivalent L4 element. PNG files are retained unchanged.
The L4 offline tail model also receives the missing housing particle reference,
following the equivalent L6 model.
Unnamed EC elements using bloom sprites also receive Forge full-bright data.
Coincident body shells are inset by 0.01 model units beneath the original glow shell
for the single cutout pass. The transmitter south/up/NONE fallback is made consistent
with the other facings; formed connections always carry a real controller tier.
Tail item GUI transforms are reduced to fit inventory/JEI icons without changing
their three-block-tall world geometry.
Source: https://github.com/NovaEngineering-Source/NovaEngineering-Core
Reference revision: `b37fbb53e4012d15baac2d95a8c1d4a32c62d6a8`.
The port replaces the 1.12 AE storage internals with the public AE2 15 API and
introduces a versioned 1.20.1 data format. ES assets under
`assets/modularmachinery/models/block/neco`, `textures/neco`, `textures/gui/neco` and their item models
retain upstream artwork; resource namespaces, blockstates and Forge
render-type declarations are adapted for this port. The opaque bodies of the three
formed ES controller models are inset by 0.01 model units to separate their
coplanar full-bright overlays in the 1.20.1 renderer; texture files are unchanged. Upstream bloom-named elements use Forge full-bright face data; no screen-space bloom effect is bundled. The complete GPLv3 text is in `LICENSE`.

Database continuous service-core progress and completed-research input-energy scaling
adapt old.Database.onWorkingTick at the same reference revision. The port keeps
unknown records for recovery, charges only recognized completed entries, and
applies a transient overlay without replacing or serializing named user modifiers.
The current count is used each tick instead of reproducing the upstream 40-tick
modifier-refresh skip.

## GeckoLib

The released Modular Machinery: Continuum JAR embeds the Forge 1.20.1
artifact `software.bernie.geckolib:geckolib-forge-1.20.1:4.8.4` using Forge's
Jar-in-Jar mechanism. GeckoLib is Copyright (c) Gecko, Eliot, AzureDoom,
DerToaster, Tslat, Witixin and contributors, and is licensed under the MIT
License. The embedded GeckoLib artifact contains its upstream `LICENSE` file;
the complete license text is reproduced in the nested artifact and applies to
that component.

## MMC UI Sans / Noto Sans SC

MMC UI Sans is a renamed BMP subset of Noto Sans SC at weight 400.
Its hmtx left bearings are neutralized for the Minecraft 1.20.1 TTF provider;
outline positions and advance widths are retained.

Copyright 2014-2021 Adobe (http://www.adobe.com/), with Reserved Font Name
'Source'. The font remains licensed under the SIL Open Font License 1.1,
independently of this project's GPL license. Original copyright and license
records are retained inside the TTF; the complete OFL text is distributed in
`assets/modularmachinery/font/ofl.txt`.
Upstream: https://github.com/google/fonts/tree/main/ofl/notosanssc
Source file: NotoSansSC[wght].ttf
Source SHA-256: a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da
Reproduction tool: tools/prepare_ui_font.py (FontTools 4.65.0).
The font is used in MMC-owned screens with a resource-pack fallback for
uncovered glyphs; it does not replace font files of Minecraft or other mods.


Research preview stack adaptation (2026-11-14): full single-count ItemStack semantics follow NovaEngineering Core revision b37fbb53e4012d15baac2d95a8c1d4a32c62d6a8, ResearchCognitionData constructors/getPreviewStack, GuiHyperNetTerminal preview rendering, and ResearchCompleteToast. MMC adds its own bounded immutable SNBT representation, self-contained server synchronization and editor controls; no new texture is copied for this change. Existing NECO license and attribution above apply.

Runtime statistics adaptation (2026-11-14): the processor's bounded 100-working-tick load mean follows old.DataProcessor.onMachineTick and HyperNetInfoProvider; the ten-update EF crafting rate and ten-tick display cadence follow TotalCraftedLabel and EFabricatorEventHandler from the same NECO revision. MMC uses completed committed work, exact decimal accumulation for load, long counter differences and actual client game-tick intervals for display rate; these are read-only transient displays, with no history chart or new texture. Existing NECO license and attribution above apply.

Physical server assembly backend adaptation (2026-11-14): slot counts, identities,
expansion dependencies and soft cooling dependencies follow NovaEngineering Core
revision b37fbb53e4012d15baac2d95a8c1d4a32c62d6a8, AssemblyInv*Const,
AssemblySlotManager, SlotConditionItemHandler, ModularServer and
TileModularServerAssembler, by KasumiNova and contributors. MMC supplies the
bounded 1.20.1 inventory serialization, atomic candidate validation and immutable
layout. No new artwork is copied. This backend does not implement the upstream
unfinished host/network runtime or the assembler GUI. GPLv3 attribution above applies.

Server module definitions and preview adaptation (2026-11-14): concrete module
parameters, CPU/GPU/soul/quantum type efficiencies, RAM hardware bandwidth,
PSU/capacitor limits and assembly diagnostics follow NECO Module*Base,
ModuleCalculable, ModularServer, ServerModuleRegistry and ServerInfoColumn at
revision b37fbb53e4012d15baac2d95a8c1d4a32c62d6a8. MMC adds strict bounded
server JSON catalogs, immutable publication and read-only 1.20.1 previews,
including non-wrapping integer aggregates. ModuleCPUExt's effective zero
bandwidth consumption is retained explicitly. Example values are MMC examples,
not the NovaEngineering pack's module tables. No additional artwork is copied;
upstream unfinished extension/host/network behavior is not represented as working.
