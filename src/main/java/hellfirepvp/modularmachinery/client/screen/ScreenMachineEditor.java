/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.editor.MachineNameFormatting;
import hellfirepvp.modularmachinery.common.editor.BasicRequirementType;
import hellfirepvp.modularmachinery.common.editor.RequirementResourceSelection;
import hellfirepvp.modularmachinery.common.editor.ItemTagSelection;
import hellfirepvp.modularmachinery.common.editor.IngredientGroupEditor;
import hellfirepvp.modularmachinery.common.editor.CatalystEditor;
import hellfirepvp.modularmachinery.common.editor.DurabilityEditor;
import hellfirepvp.modularmachinery.common.editor.FuelEditor;
import hellfirepvp.modularmachinery.common.editor.FluidTickEditor;
import hellfirepvp.modularmachinery.common.editor.RecipeOptionsEditor;
import hellfirepvp.modularmachinery.common.editor.RecipeTooltipEditor;
import hellfirepvp.modularmachinery.common.network.PktMachineEditorRequirementBatch;
import hellfirepvp.modularmachinery.common.network.PktMachineEditorRequirementTrigger;
import hellfirepvp.modularmachinery.common.editor.RequirementBatchEditor;
import hellfirepvp.modularmachinery.common.network.PktMachineEditorRecipeOptions;
import hellfirepvp.modularmachinery.common.network.PktMachineEditorFluidTick;
import hellfirepvp.modularmachinery.common.editor.SmartInterfaceEditor;
import hellfirepvp.modularmachinery.common.editor.PneumaticEditor;
import hellfirepvp.modularmachinery.common.editor.EnvironmentEditor;
import hellfirepvp.modularmachinery.common.network.PktMachineEditorEnvironment;
import hellfirepvp.modularmachinery.common.editor.ResourceAmountEditor;
import hellfirepvp.modularmachinery.common.network.PktMachineEditorResourceAmount;
import hellfirepvp.modularmachinery.common.network.PktMachineEditorPneumatic;
import hellfirepvp.modularmachinery.common.network.PktMachineEditorSmartInterface;
import hellfirepvp.modularmachinery.common.network.PktMachineEditorFuel;
import hellfirepvp.modularmachinery.common.network.PktMachineEditorSelector;
import hellfirepvp.modularmachinery.common.network.PktMachineEditorDurability;
import hellfirepvp.modularmachinery.common.network.PktMachineEditorIngredientGroup;
import hellfirepvp.modularmachinery.common.network.PktMachineEditorRequirementAdd;
import hellfirepvp.modularmachinery.common.network.PktMachineEditorRequest;
import hellfirepvp.modularmachinery.common.editor.EditorSaveState;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pack-author editor with an FTB Quests-style item picker and property overlay. */
public final class ScreenMachineEditor extends AbstractContainerScreen<MenuMachineEditor> {
    private static final int WIDTH = 440;
    private static final int HEIGHT = 260;
    private static final int ROW_HEIGHT = 22;
    private static final int LIST_TOP = 22;
    private static final int LIST_BOTTOM = 162;
    private static final int MACHINE_X = 8;
    private static final int MACHINE_W = 137;
    private static final int RECIPE_X = 148;
    private static final int RECIPE_W = 130;
    private static final int REQUIREMENT_X = 282;
    private static final int REQUIREMENT_W = 152;
    private static final int INPUT_HEADER_Y = 22;
    private static final int INPUT_TOP = 39;
    private static final int OUTPUT_HEADER_Y = 114;
    private static final int OUTPUT_TOP = 131;
    private static final int SIDE_ROWS = 3;
    private static final int REQUIREMENT_BOTTOM = 208;
    private static final int PICKER_X = 58;
    private static final int PICKER_Y = 18;
    private static final int PICKER_W = 324;
    private static final int PICKER_H = 220;
    private static final int PICKER_PADDING = 8;
    private static final int PICKER_SEARCH_Y = 25;
    private static final int PICKER_SEARCH_H = 20;
    private static final int PICKER_LIST_Y = 62;
    private static final int PICKER_ROW_H = 23;
    private static final int PICKER_ROWS = 6;
    private static final int GROUP_TOP = 64;
    private static final int GROUP_ROW_H = 24;
    private static final int GROUP_ROWS = 4;

    private JsonObject document;
    private boolean adapterOpen, templateModifiers;
    private final AdapterTemplatePanel adapterPanel = new AdapterTemplatePanel();
    private JsonObject templateDraft;
    private java.util.function.Consumer<JsonArray> templateReturn;
    private int machineIndex;
    private int recipeIndex;
    private int requirementIndex;
    private int machineScroll;
    private int recipeScroll;
    private int inputScroll;
    private int outputScroll;
    private EditBox amount;
    private EditBox chance;
    private EditBox duration;
    private EditBox pickerSearch;
    private EditBox localizedName;
    private EditBox recipeDisplayName;
    private Button editorSave;
    private Button editorCancel;
    private Button pickerCancel, inventoryPicker, inventoryNbt;
    private boolean pickerInventory, importInventoryNbt = true, inventoryItemDraft;
    private final List<ItemStack> inventoryChoices = new ArrayList<>();
    private Button namePreviewClose;
    private Button recipeNamePreview;
    private Button recipeAdvanced;
    private final RecipeOptionsPanel recipeOptionsPanel = new RecipeOptionsPanel();
    private RecipeOptionsEditor.Options recipeOptionsDraft;
    private boolean recipeOptionsOpen;
    private Button requirementReselect;
    private boolean pickerOpen;
    private boolean pickerOutput;
    private boolean typePickerOpen;
    private BasicRequirementType pickerType = BasicRequirementType.ITEM;
    private boolean pickerTags;
    private boolean pickerDurability, cachedPickerDurability;
    private boolean pickerFluidTick;
    private Button durabilityType, durabilityTagType;
    private Button fuelType;
    private Button smartType;
    private final SmartInterfacePanel smartPanel = new SmartInterfacePanel();
    private boolean smartOpen;
    private final PneumaticPanel pneumaticPanel = new PneumaticPanel();
    private boolean pneumaticOpen;
    private boolean environmentOpen;
    private Button environmentType;
    private final EnvironmentPanel environmentPanel = new EnvironmentPanel();
    private Button pneumaticType;
    private final ResourceAmountPanel resourcePanel = new ResourceAmountPanel();
    private boolean resourceOpen, nbtOpen;
    private final RequirementNbtPanel nbtPanel = new RequirementNbtPanel();
    private Button requirementNbt;
    private Button candidateNbt, groupDefaultNbt;
    private int nestedNbt;
    private Button resourceType;
    private EditBox toolDamage;
    private Button itemTagType;
    private Button ingredientGroupType;
    private Button catalystType, catalystModifiers, catalystTooltips;
    private boolean modifiersOpen, catalystTooltipsOpen;
    private final CatalystTooltipPanel catalystTooltipPanel = new CatalystTooltipPanel();
    private final CatalystModifierPanel modifierPanel = new CatalystModifierPanel();
    private final SelectorBindingPanel selectorPanel = new SelectorBindingPanel();
    private boolean selectorOpen, batchOpen;
    private final RequirementBatchPanel batchPanel = new RequirementBatchPanel();
    private Button requirementBatch, requirementTrigger;
    private boolean triggerOpen;
    private final RequirementTriggerPanel triggerPanel = new RequirementTriggerPanel();
    private Button selectorBinding;
    private final List<AbstractWidget> groupWidgets = new ArrayList<>();
    private Button groupUp, groupDown;
    private Button groupAddItem, groupAddTag;
    private EditBox groupMax, groupWeight;
    private int groupIndex, groupScroll;
    // -2: ordinary picker, -1: add a group candidate, >=0: replace that candidate.
    private int groupPickerIndex = -2;
    private JsonObject pendingRequirement;
    private boolean addingRequirement;
    private boolean pickerReplacing;
    private boolean editorOpen;
    private boolean editorRecipe;
    private boolean colorOpen;
    private boolean copyOpen;
    private String copiedMachineId = "";
    private final RecipeCopyPanel copyPanel = new RecipeCopyPanel();
    private Button recipeCopy;
    private String copiedRecipeId = "";
    private final ControllerAppearancePanel controllerAppearance = new ControllerAppearancePanel();
    private boolean namePreviewOpen;
    private boolean namePreviewRecipe;
    private int pickerScroll;
    private String loadedSnapshot;
    private String lastClickedRecipeId;
    private long lastRecipeClickTime;
    private final List<AbstractWidget> mainWidgets = new ArrayList<>();
    private final List<AbstractWidget> typeWidgets = new ArrayList<>();
    private String cachedPickerQuery;
    private BasicRequirementType cachedPickerType;
    private boolean cachedPickerTags;
    private List<ResourceLocation> cachedPickerResults = List.of();
    private String saveStatus = "";
    private boolean saveFailed;
    private boolean closeAfterSave;
    private int savedRequirementIndex;

    public ScreenMachineEditor(MenuMachineEditor menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
        try {
            document = JsonParser.parseString(menu.snapshot()).getAsJsonObject();
        } catch (RuntimeException ignored) {
            document = new JsonObject();
            document.add("machines", new JsonArray());
        }
        loadedSnapshot = menu.snapshot();
        hellfirepvp.modularmachinery.client.ClientMachineEditorSnapshot.menuOpened(menu);
    }

    @Override
    protected void init() {
        lastClickedRecipeId = null;
        String nameDraft = localizedName == null ? null : localizedName.getValue();
        String recipeDraft = recipeDisplayName == null ? null : recipeDisplayName.getValue();
        String durationDraft = duration == null ? null : duration.getValue();
        String amountDraft = amount == null ? null : amount.getValue();
        String chanceDraft = chance == null ? null : chance.getValue();
        String damageDraft = toolDamage == null ? null : toolDamage.getValue();
        String maxDraft = groupMax == null ? null : groupMax.getValue();
        String weightDraft = groupWeight == null ? null : groupWeight.getValue();
        String searchDraft = pickerSearch == null ? null : pickerSearch.getValue();
        super.init();
        mainWidgets.clear();
        typeWidgets.clear();
        groupWidgets.clear();
        amount = new EditBox(font, leftPos + 170, topPos + 104, 86, 18, Component.literal("数量"));
        amount.setMaxLength(10);
        amount.setFilter(value -> value.chars().allMatch(Character::isDigit));
        addRenderableWidget(amount);
        chance = new EditBox(font, leftPos + 170, topPos + 143, 86, 18, Component.literal("概率"));
        chance.setMaxLength(48);
        chance.setFilter(ScreenMachineEditor::decimalInput);
        chance.setTooltip(Tooltip.create(Component.literal("概率范围 0～1，支持小数和科学计数法")));
        addRenderableWidget(chance);
        toolDamage = new EditBox(font, leftPos + 226, topPos + 128, 120, 18, Component.literal("每次扣耐久"));
        toolDamage.setMaxLength(10);
        toolDamage.setFilter(value -> value.chars().allMatch(Character::isDigit));
        toolDamage.setTooltip(Tooltip.create(Component.literal("每次使用扣除的耐久值，范围为 1～2000000000\n达到最大损伤后工具损坏；最后不足一次的剩余耐久仍算一次使用\n显式耐久扣除不受耐久附魔的随机减免影响")));
        addRenderableWidget(toolDamage);
        groupMax = new EditBox(font, leftPos + 178, topPos + 146, 44, 18, Component.literal("最多数量"));
        groupMax.setMaxLength(10);
        groupMax.setFilter(value -> value.chars().allMatch(Character::isDigit));
        addRenderableWidget(groupMax);
        groupWeight = new EditBox(font, leftPos + 260, topPos + 146, 104, 18, Component.literal("候选权重"));
        groupWeight.setMaxLength(48);
        groupWeight.setFilter(ScreenMachineEditor::decimalInput);
        groupWeight.setTooltip(Tooltip.create(Component.literal("权重为非负数，支持小数和科学计数法\n权重 2 和 1 的抽中占比约为 66.67% 和 33.33%\n权重 0 的候选项不参与抽取\n占比按全部候选权重计算；实际抽取受输出空间影响\n整组产出概率另行判定，不计入此占比")));
        addRenderableWidget(groupWeight);
        duration = new EditBox(font, leftPos + 170, topPos + 104, 86, 18, Component.literal("时间"));
        duration.setMaxLength(10);
        duration.setFilter(value -> value.chars().allMatch(Character::isDigit));
        addRenderableWidget(duration);

        // Fill the available row; the close button belongs to the title bar.
        pickerSearch = new EditBox(font, leftPos + PICKER_X + PICKER_PADDING,
                topPos + PICKER_Y + PICKER_SEARCH_Y,
                PICKER_W - PICKER_PADDING * 2, PICKER_SEARCH_H, Component.literal("搜索物品"));
        pickerSearch.setMaxLength(200);
        pickerSearch.setResponder(value -> { pickerScroll = 0; cachedPickerQuery = null; });
        pickerSearch.setHint(Component.literal(pickerHint()));
        addRenderableWidget(pickerSearch);

        controllerAppearance.init(font, leftPos, topPos, widget -> addRenderableWidget(widget), settings -> {
            if (machine() != null) submitMutation(new hellfirepvp.modularmachinery.common.network.PktMachineEditorControllerSettings(
                    machine().get("name").getAsString(), settings.color(), settings.factory()));
        }, this::closeControllerAppearance);
        copyPanel.init(font, leftPos, topPos, widget -> addRenderableWidget(widget), target -> {
            copiedRecipeId = target.newId(); copiedMachineId = target.machine();
            submitMutation(new ModularmachineryNetwork.PktMachineEditorAction(
                    ModularmachineryNetwork.PktMachineEditorAction.COPY_RECIPE, target.machine(), target.sourceId(), -1, target.newId(), false));
        }, this::closeRecipeCopy);
        recipeCopy = addMainWidget(Button.builder(Component.literal("复制"), b -> openRecipeCopy())
                .bounds(leftPos + 212, topPos + 2, 44, 18).build());
        addMainWidget(Button.builder(Component.literal("控制器外观"), b -> openControllerAppearance())
                .bounds(leftPos + 8, topPos + 174, 128, 20)
                .tooltip(Tooltip.create(Component.literal("设置该机器普通／工厂控制器的外壳颜色，预览并保存\n新增机器或开启独立工厂控制器后需重启"))).build());
        addMainWidget(Button.builder(Component.literal(templateDraft == null ? "导入模板" : "完成追加需求"), b -> {
            if(templateDraft != null) onClose(); else openAdapterTemplates();
        }).bounds(leftPos+8,topPos+199,128,18).build());
        requirementNbt = addMainWidget(Button.builder(Component.literal("NBT 数据"), b -> openRequirementNbt())
                .bounds(leftPos + 146, topPos + 174, 70, 20)
                .tooltip(Tooltip.create(Component.literal("选中已保存的物品／流体需求，编辑匹配／实际输出和独立显示数据\n先保存新需求；高级候选组与催化剂暂不开放此入口"))).build());
        localizedName = new EditBox(font, leftPos + 8, topPos + 232, 114, 18, Component.literal("显示名称"));
        localizedName.setMaxLength(200);
        localizedName.setTooltip(Tooltip.create(Component.literal(
                "名称支持 Minecraft 颜色和格式代码\n&0–&9、&a–&f：颜色\n&l：粗体  &o：斜体\n&n：下划线  &m：删除线\n&k：随机字符  &r：重置\n示例：&6&l合金熔炉&r")));
        addMainWidget(localizedName);
        recipeDisplayName = new EditBox(font, leftPos + 74, topPos + 74, 292, 18, Component.literal("配方显示名称"));
        recipeDisplayName.setMaxLength(200);
        recipeDisplayName.setHint(Component.literal("留空使用原配方 ID"));
        recipeDisplayName.setTooltip(Tooltip.create(Component.literal("支持 &a 颜色、&l 粗体等格式代码；仅修改显示名称")));
        addRenderableWidget(recipeDisplayName);

        addMainWidget(Button.builder(Component.literal("+"), b -> machineAddHint())
                .bounds(leftPos + 126, topPos + 2, 18, 18).build());
        addMainWidget(Button.builder(Component.literal("+"), b -> createRecipe())
                .bounds(leftPos + 260, topPos + 2, 18, 18).build());
        addMainWidget(Button.builder(Component.literal("+"), b -> openTypePicker(false))
                .bounds(leftPos + REQUIREMENT_X + REQUIREMENT_W - 22, topPos + INPUT_HEADER_Y - 3, 18, 18).build());
        addMainWidget(Button.builder(Component.literal("+"), b -> openTypePicker(true))
                .bounds(leftPos + REQUIREMENT_X + REQUIREMENT_W - 22, topPos + OUTPUT_HEADER_Y - 3, 18, 18).build());
        addMainWidget(Button.builder(Component.literal("保存名称"), b -> saveMachineName())
                .bounds(leftPos + 126, topPos + 231, 64, 20).build());
        addMainWidget(Button.builder(Component.literal("预览"), b -> openNamePreview())
                .bounds(leftPos + 194, topPos + 231, 40, 20)
                .tooltip(Tooltip.create(Component.literal("预览当前名称的颜色和格式，不会保存修改"))).build());
        selectorBinding = addMainWidget(Button.builder(Component.literal("接口分组"), b -> openSelectorBinding(requirementIndex))
                .bounds(leftPos + 240, topPos + 231, 60, 20)
                .tooltip(Tooltip.create(Component.literal("编辑选中需求的 selector-tag；也可右键需求行打开\n用于按结构标签选择物品、流体等接口，不是智能接口数值类型\n新建需求请先保存，再设置接口分组"))).build());

        requirementBatch = addMainWidget(Button.builder(Component.literal("批量设置"), b -> openRequirementBatch())
                .bounds(leftPos + 304, topPos + 231, 60, 20)
                .tooltip(Tooltip.create(Component.literal("设置选中需求是否随批量放大；也可 Shift+右键需求行打开\n独立保存，不修改数量、概率、NBT 或接口分组\n催化剂保留默认每批一份；智能接口条件不支持此设置"))).build());

        requirementTrigger = addMainWidget(Button.builder(Component.literal("触发设置"), b -> openRequirementTrigger())
                .bounds(leftPos + 368, topPos + 231, 64, 20)
                .tooltip(Tooltip.create(Component.literal("编辑一次性输入的触发 tick 与重复策略；Ctrl+右键需求也可打开\n新建需求请先保存；持续需求、催化剂、智能条件和输出不开放此入口"))).build());

        editorSave = Button.builder(Component.literal("保存"), b -> saveEditor())
                .bounds(leftPos + 170, topPos + 177, 66, 20).build();
        editorCancel = Button.builder(Component.literal("取消"), b -> closeEditor())
                .bounds(leftPos + 240, topPos + 177, 66, 20).build();
        pickerCancel = Button.builder(Component.literal("关闭"), b -> closePicker())
                .bounds(leftPos + PICKER_X + PICKER_W - PICKER_PADDING - 44,
                        topPos + PICKER_Y + 2, 44, 20).build();
        addRenderableWidget(editorSave);
        addRenderableWidget(editorCancel);
        addRenderableWidget(pickerCancel);
        inventoryPicker = addRenderableWidget(Button.builder(Component.literal("背包"), b -> {
            pickerInventory=!pickerInventory;pickerScroll=0;cachedPickerQuery=null;setPickerVisible(true);
        }).bounds(leftPos+218,topPos+20,54,20).tooltip(Tooltip.create(Component.literal("切换注册表／背包内物品；只复制编辑数据，不消耗物品"))).build());
        inventoryNbt = addRenderableWidget(Button.builder(Component.literal("带入 NBT ✓"), b -> {
            importInventoryNbt=!importInventoryNbt;inventoryNbt.setMessage(Component.literal(importInventoryNbt?"带入 NBT ✓":"带入 NBT ×"));
        }).bounds(leftPos+276,topPos+20,58,20).tooltip(Tooltip.create(Component.literal("选择物品时带入其 NBT；输入字符串按字面匹配\n不支持的 NBT 数组会提示关闭本选项\n数量仍可修改，背包不会被取走任何物品"))).build());
        inventoryNbt.setMessage(Component.literal(importInventoryNbt ? "带入 NBT ✓" : "带入 NBT ×"));
        namePreviewClose = addRenderableWidget(Button.builder(Component.literal("返回"), b -> closeNamePreview())
                .bounds(leftPos + 188, topPos + 208, 64, 20).build());
        recipeNamePreview = addRenderableWidget(Button.builder(Component.literal("预览名称"), b -> openNamePreview(true))
                .bounds(leftPos + 74, topPos + 150, 76, 20).build());
        recipeAdvanced = addRenderableWidget(Button.builder(Component.literal("高级设置"), b -> openRecipeOptions())
                .bounds(leftPos + 286, topPos + 150, 80, 20).build());
        requirementReselect = addRenderableWidget(Button.builder(Component.literal("重新选择"), b -> openReplacementPicker())
                .bounds(leftPos + 286, topPos + 55, 72, 20).build());
        for (var type : BasicRequirementType.values()) {
            typeWidgets.add(addRenderableWidget(Button.builder(Component.literal(type.label), b -> chooseType(type))
                    .bounds(leftPos + 105 + type.ordinal() % 2 * 118, topPos + 62 + type.ordinal() / 2 * 28, 112, 26).build()));
        }
        itemTagType = addRenderableWidget(Button.builder(Component.literal("物品标签（输入）"), b -> chooseItemTag())
                .bounds(leftPos + 223, topPos + 90, 112, 26)
                .tooltip(Tooltip.create(Component.literal("接受标签内任意物品；输出需选择具体物品"))).build());
        typeWidgets.add(itemTagType);
        ingredientGroupType = addRenderableWidget(Button.builder(Component.literal("候选物品组（输入）"), b -> beginGroupDraft())
                .bounds(leftPos + 105, topPos + 118, 112, 26)
                .tooltip(Tooltip.create(Component.literal("每份加工选择能付清的一项，可分别设置数量"))).build());
        typeWidgets.add(ingredientGroupType);
        catalystType = addRenderableWidget(Button.builder(Component.literal("催化剂（可选输入）"), b -> beginCatalystDraft())
                .bounds(leftPos + 223, topPos + 118, 112, 26)
                .tooltip(Tooltip.create(Component.literal("缺少时仍可加工；存在时应用修改器，按消耗概率扣除"))).build());
        typeWidgets.add(catalystType);
        durabilityType = addRenderableWidget(Button.builder(Component.literal("工具耐久（输入）"), b -> chooseDurability(false))
                .bounds(leftPos + 105, topPos + 146, 112, 26)
                .tooltip(Tooltip.create(Component.literal("按使用次数扣工具耐久；达到最大损伤后损坏"))).build());
        durabilityTagType = addRenderableWidget(Button.builder(Component.literal("耐久标签（输入）"), b -> chooseDurability(true))
                .bounds(leftPos + 223, topPos + 146, 112, 26)
                .tooltip(Tooltip.create(Component.literal("接受标签内可扣耐久的物品；不接受不可损坏物品"))).build());
        typeWidgets.add(durabilityType);
        typeWidgets.add(durabilityTagType);
        fuelType = addRenderableWidget(Button.builder(Component.literal("熔炉燃料（输入）"), b -> beginFuelDraft())
                .bounds(leftPos + 105, topPos + 174, 112, 26)
                .tooltip(Tooltip.create(Component.literal("按熔炉燃烧时间消耗任意燃料，支持混用；多余燃烧时间丢弃"))).build());
        typeWidgets.add(fuelType);
        smartType = addRenderableWidget(Button.builder(Component.literal("智能接口数值（输入）"), b -> openSmartInterface(-1))
                .bounds(leftPos + 223, topPos + 174, 112, 26)
                .tooltip(Tooltip.create(Component.literal("选择机器已声明的数值类型，设置包含两端的匹配范围\n需先在机器定义中声明 smart-interface-types"))).build());
        typeWidgets.add(smartType);
        typeWidgets.add(addRenderableWidget(Button.builder(Component.literal("流体/刻"), b -> chooseFluidTick())
                .bounds(leftPos + 105, topPos + 202, 112, 26)
                .tooltip(Tooltip.create(Component.literal("每个配方进度 tick 输入或输出指定流体，单位 mB/t\n批量倍率放大速率；缺流体或输出满时暂停，不设置概率"))).build()));
        pneumaticType = addRenderableWidget(Button.builder(Component.literal("气动／温度"), b -> openPneumatic(-1))
                .bounds(leftPos + 223, topPos + 202, 112, 20)
                .tooltip(Tooltip.create(Component.literal("压缩空气（单次或逐 tick）、压力／真空、持续 Kelvin 温度条件"))).build());
        typeWidgets.add(pneumaticType);
        resourceType = addRenderableWidget(Button.builder(Component.literal("魔法资源"), b -> openResourceAmount(-1))
                .bounds(leftPos + 105, topPos + 230, 112, 20)
                .tooltip(Tooltip.create(Component.literal("Botania 魔力、Embers 余烬、Blood Magic LP／意志、Nature’s Aura 灵气\n按服务端依赖和实际支持的方向开放"))).build());
        typeWidgets.add(resourceType);
        environmentType = addRenderableWidget(Button.builder(Component.literal("环境条件（输入）"), b -> openEnvironment(-1))
                .bounds(leftPos + 223, topPos + 230, 112, 20)
                .tooltip(Tooltip.create(Component.literal("高度、时间、维度、群系、月相和天气；需要环境检测器"))).build());
        typeWidgets.add(environmentType);
        typeWidgets.add(addRenderableWidget(Button.builder(Component.literal("取消"), b -> closeTypePicker())
                .bounds(leftPos + 292, topPos + 40, 48, 18).build()));
        groupAddItem = addRenderableWidget(Button.builder(Component.literal("+ 物品"), b -> openGroupPicker(false, -1))
                .bounds(leftPos + 238, topPos + 24, 62, 20).build());
        groupAddTag = addRenderableWidget(Button.builder(Component.literal("+ 标签"), b -> openGroupPicker(true, -1))
                .bounds(leftPos + 304, topPos + 24, 62, 20).build());
        groupWidgets.add(groupAddItem);
        groupWidgets.add(groupAddTag);
        groupUp = addRenderableWidget(Button.builder(Component.literal("↑"), b -> moveGroupCandidate(-1))
                .bounds(leftPos + 155, topPos + 190, 26, 18).tooltip(Tooltip.create(Component.literal("上移，提高优先顺序"))).build());
        groupDown = addRenderableWidget(Button.builder(Component.literal("↓"), b -> moveGroupCandidate(1))
                .bounds(leftPos + 185, topPos + 190, 26, 18).tooltip(Tooltip.create(Component.literal("下移，降低优先顺序"))).build());
        groupWidgets.add(groupUp);
        groupWidgets.add(groupDown);
        candidateNbt = addRenderableWidget(Button.builder(Component.literal("候选 NBT"), b -> openNestedNbt(false)).bounds(leftPos+70,topPos+208,72,20).build());
        groupDefaultNbt = addRenderableWidget(Button.builder(Component.literal("默认 NBT"), b -> openNestedNbt(true)).bounds(leftPos+148,topPos+208,72,20).build());
        groupWidgets.add(candidateNbt); groupWidgets.add(groupDefaultNbt);
        catalystModifiers = addRenderableWidget(Button.builder(Component.literal("修改器"), b -> openCatalystModifiers())
                .bounds(leftPos + 70, topPos + 142, 134, 20)
                .tooltip(Tooltip.create(Component.literal("编辑找到催化剂后应用的效果；缺少催化剂不阻止加工\n消耗概率 0 仍可生效，但不消耗催化剂"))).build());
        catalystTooltips = addRenderableWidget(Button.builder(Component.literal("提示文字"), b -> openCatalystTooltips())
                .bounds(leftPos + 214, topPos + 142, 150, 20)
                .tooltip(Tooltip.create(Component.literal("编辑当前催化剂在 JEI 中的悬停提示；按原文显示，不改变修改器"))).build());
        catalystTooltipPanel.init(font, leftPos, topPos, widget -> addRenderableWidget(widget), lines -> {
            if (pendingRequirement.has("tooltips") || !lines.isEmpty()) {
                var tips = new JsonArray(); lines.forEach(tips::add); pendingRequirement.add("tooltips", tips);
            }
            closeCatalystTooltips();
        }, this::closeCatalystTooltips);
        modifierPanel.init(font, leftPos, topPos, widget -> addRenderableWidget(widget), mods -> {
            if(templateModifiers)adapterPanel.modifiers(mods);else pendingRequirement.add("modifiers", mods);
            closeCatalystModifiers();
        }, this::closeCatalystModifiers);
        triggerPanel.init(font, leftPos, topPos, widget -> addRenderableWidget(widget), (tick, repeat) ->
                submitMutation(new PktMachineEditorRequirementTrigger(recipe().get("name").getAsString(), requirementIndex, tick, repeat)),
                this::closeRequirementTrigger);
        batchPanel.init(font, leftPos, topPos, widget -> addRenderableWidget(widget), policy ->
                submitMutation(new PktMachineEditorRequirementBatch(recipe().get("name").getAsString(), requirementIndex, policy)),
                this::closeRequirementBatch);
        selectorPanel.init(font, leftPos, topPos, widget -> addRenderableWidget(widget), tag ->
                submitMutation(new PktMachineEditorSelector(recipe().get("name").getAsString(), requirementIndex, tag)),
                this::closeSelectorBinding);
        nbtPanel.init(font, leftPos, topPos, widget -> addRenderableWidget(widget), draft -> {
            if (nestedNbt != 0) {
                var target = nestedNbt == 2 ? pendingRequirement : groupItems().get(groupIndex).getAsJsonObject();
                var context=target.deepCopy();context.addProperty("io-type", IngredientGroupEditor.isOutput(pendingRequirement)?"output":"input");
                var merged=hellfirepvp.modularmachinery.common.editor.RequirementNbtEditor.merge(context,draft.dataMode(),draft.data(),draft.displayMode(),draft.display());
                for(String key:List.of("nbt","nbt-display")){if(merged.has(key))target.add(key,merged.get(key));else target.remove(key);}
                closeRequirementNbt();
            } else submitMutation(new hellfirepvp.modularmachinery.common.network.PktMachineEditorRequirementNbt(recipe().get("name").getAsString(), requirementIndex,
                        draft.dataMode(),draft.data(),draft.displayMode(),draft.display()));
        }, this::closeRequirementNbt);
        adapterPanel.init(font, leftPos, topPos, widget -> addRenderableWidget(widget), this::submitMutation, this::closeAdapterTemplates,
                this::openTemplateModifiers, this::openTemplateRequirements);
        resourcePanel.init(font, leftPos, topPos, widget -> addRenderableWidget(widget), this::saveResourceAmount, this::closeResourceAmount);
        environmentPanel.init(font, leftPos, topPos, widget -> addRenderableWidget(widget), this::saveEnvironment, this::closeEnvironment);
        pneumaticPanel.init(font, leftPos, topPos, widget -> addRenderableWidget(widget), this::savePneumatic, this::closePneumatic);
        smartPanel.init(font, leftPos, topPos, widget -> addRenderableWidget(widget), this::saveSmartInterface, this::closeSmartInterface);
        recipeOptionsPanel.init(font, leftPos, topPos, widget -> addRenderableWidget(widget), options -> {
            recipeOptionsDraft = options; closeRecipeOptions();
        }, this::closeRecipeOptions);
        setEditorVisible(editorOpen);
        setPickerVisible(pickerOpen);
        setNamePreviewVisible(namePreviewOpen);
        controllerAppearance.setVisible(colorOpen, menu.saves().pending());
        setTypePickerVisible(typePickerOpen);
        modifierPanel.setVisible(modifiersOpen);
        catalystTooltipPanel.setVisible(catalystTooltipsOpen);
        selectorPanel.setVisible(selectorOpen, menu.saves().pending());
        batchPanel.setVisible(batchOpen, menu.saves().pending());
        triggerPanel.setVisible(triggerOpen, menu.saves().pending());
        smartPanel.setVisible(smartOpen, menu.saves().pending());
        adapterPanel.setVisible(adapterOpen, menu.saves().pending());
        environmentPanel.setVisible(environmentOpen, menu.saves().pending());
        pneumaticPanel.setVisible(pneumaticOpen, menu.saves().pending());
        nbtPanel.setVisible(nbtOpen, menu.saves().pending());
        resourcePanel.setVisible(resourceOpen, menu.saves().pending());
        recipeOptionsPanel.setVisible(recipeOptionsOpen);
        syncFields();
        syncMachineName();
        if (nameDraft != null) localizedName.setValue(nameDraft);
        if (pickerOpen && searchDraft != null) pickerSearch.setValue(searchDraft);
        if ((editorOpen || pickerReplacing || modifiersOpen || catalystTooltipsOpen) && !editorRecipe) {
            if (amountDraft != null) amount.setValue(amountDraft);
            if (chanceDraft != null) chance.setValue(chanceDraft);
            if (damageDraft != null) toolDamage.setValue(damageDraft);
            if (maxDraft != null) groupMax.setValue(maxDraft);
            if (weightDraft != null) groupWeight.setValue(weightDraft);
        }
        if (editorRecipe && (editorOpen || recipeOptionsOpen || namePreviewOpen && namePreviewRecipe)) {
            if (recipeDraft != null) recipeDisplayName.setValue(recipeDraft);
            if (durationDraft != null) duration.setValue(durationDraft);
        }
    }

    private static boolean decimalInput(String value) {
        return value.chars().allMatch(c -> Character.isDigit(c) || ".eE+-".indexOf(c) >= 0);
    }

    private <T extends AbstractWidget> T addMainWidget(T widget) {
        mainWidgets.add(widget);
        return addRenderableWidget(widget);
    }

    private void syncMainWidgets() {
        boolean visible = !copyOpen && !colorOpen && !pickerOpen && !editorOpen && !namePreviewOpen && !typePickerOpen && !modifiersOpen && !catalystTooltipsOpen && !selectorOpen && !batchOpen && !triggerOpen && !smartOpen && !adapterOpen && !environmentOpen && !pneumaticOpen && !nbtOpen && !resourceOpen && !recipeOptionsOpen;
        for (AbstractWidget widget : mainWidgets) {
            widget.visible = visible;
            widget.active = visible && !menu.saves().pending();
            if (!visible && getFocused() == widget) setFocused(null);
        }
        if (requirementNbt != null) requirementNbt.active &= nbtEditable(requirement());
        if (selectorBinding != null) selectorBinding.active &= selectorEditable(requirement());
        if (requirementBatch != null) requirementBatch.active &= batchEditable(requirement());
        if (requirementTrigger != null) requirementTrigger.active &= triggerEditable(requirement());
        if (recipeCopy != null) {
            boolean supported = recipe() != null && !isNewRecipe(recipe()) && !isReadOnly(recipe());
            recipeCopy.active &= supported;
            recipeCopy.setTooltip(Tooltip.create(Component.literal(supported ? "将已保存的 JSON 配方复制为独立新配方，原配方保持不变"
                    : recipe() == null ? "请先选择配方" : isNewRecipe(recipe()) ? "请先保存配方草稿" : readOnlyReason(recipe()) + "\n不能直接复制动态配方为普通 JSON")));
        }
        if(templateDraft != null)for(var widget:mainWidgets){
            String label=widget.getMessage().getString();boolean allowed=widget==requirementNbt||widget==selectorBinding||widget==requirementBatch||label.equals("完成追加需求")||label.equals("+")&&widget.getX()>=leftPos+REQUIREMENT_X;
            widget.visible&=allowed;widget.active&=allowed;
        }
        copyPanel.setVisible(copyOpen, menu.saves().pending());
    }

    private void openAdapterTemplates(){if(machine()==null||menu.saves().pending())return;adapterOpen=true;saveStatus="";saveFailed=false;adapterPanel.open(document,machine());syncMainWidgets();setFocused(null);}
    private void closeAdapterTemplates(){adapterOpen=false;adapterPanel.setVisible(false,false);syncMainWidgets();setFocused(null);}
    private void openTemplateModifiers(){try{var mods=adapterPanel.modifiers();adapterOpen=false;adapterPanel.setVisible(false,false);templateModifiers=true;modifiersOpen=true;modifierPanel.open(mods,true);syncMainWidgets();setFocused(null);}catch(RuntimeException e){saveStatus=e.getMessage();saveFailed=true;}}
    private void openTemplateRequirements(){
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        var child=new ScreenMachineEditor(menu,player.getInventory(),Component.literal("模板追加需求"));
        child.document=document.deepCopy();var owner=adapterPanel.owner().deepCopy();var recipe=new JsonObject();recipe.addProperty("name","template_draft");recipe.addProperty("duration",2000000000);recipe.addProperty("editable",true);
        recipe.add("selectorTags",owner.get("selectorTags").deepCopy());recipe.add("smartInterfaceTypes",owner.get("smartInterfaceTypes").deepCopy());recipe.add("requirements",hellfirepvp.modularmachinery.common.network.TemplateRequirementDraft.decorate(adapterPanel.requirements()));
        child.templateDraft=recipe;var recipes=new JsonArray();recipes.add(recipe);owner.add("recipes",recipes);var owners=new JsonArray();owners.add(owner);child.document.add("machines",owners);
        child.templateReturn=values->{adapterPanel.requirements(values);Minecraft.getInstance().setScreen(this);};Minecraft.getInstance().setScreen(child);
    }
    @Override public void onClose(){if(templateDraft!=null){templateReturn.accept(hellfirepvp.modularmachinery.common.network.TemplateRequirementDraft.clean(templateDraft.getAsJsonArray("requirements")));return;}super.onClose();}

    private void openRecipeCopy() {
        if (recipe() == null || isNewRecipe(recipe()) || isReadOnly(recipe()) || menu.saves().pending()) return;
        copyOpen = true; copiedRecipeId = ""; copiedMachineId = ""; saveStatus = ""; saveFailed = false;
        copyPanel.open(recipe(), machine(), document.getAsJsonArray("machines")); syncMainWidgets(); setFocused(copyPanel.field());
    }
    private void closeRecipeCopy() {
        copyOpen = false; copyPanel.setVisible(false, false); syncMainWidgets(); setFocused(null);
    }

    private boolean nbtEditable(JsonObject req) {
        return recipe()!=null && !isReadOnly(recipe()) && !isNewRecipe(recipe()) && req!=null && req.has("editableNbt") && req.get("editableNbt").getAsBoolean();
    }
    private void openRequirementNbt() {
        var req=requirement(); if(!nbtEditable(req) || menu.saves().pending())return;
        try { nbtPanel.open(req); }
        catch(RuntimeException invalid) { if(Minecraft.getInstance().player!=null)Minecraft.getInstance().player.displayClientMessage(Component.literal(invalid.getMessage()),false);return; }
        nbtOpen=true; saveStatus="";saveFailed=false;syncMainWidgets();setFocused(null);
    }
    private void openNestedNbt(boolean defaults) {
        if (!editingGroup() || !storeGroupAmount() || !defaults && groupItems().isEmpty()) return;
        var target=(defaults?pendingRequirement:groupItems().get(groupIndex).getAsJsonObject()).deepCopy();
        target.addProperty("io-type",outputGroup()?"output":"input");
        if(defaults)target.addProperty("item","候选组默认 NBT");
        try { nbtPanel.open(target,true); } catch(RuntimeException invalid){saveStatus=invalid.getMessage();saveFailed=true;return;}
        nestedNbt=defaults?2:1;editorOpen=false;setEditorVisible(false);nbtOpen=true;syncMainWidgets();setFocused(null);
    }
    private void closeRequirementNbt() {nbtOpen=false;nbtPanel.setVisible(false,false);boolean nested=nestedNbt!=0;nestedNbt=0;if(nested){editorOpen=true;setEditorVisible(true);}syncMainWidgets();setFocused(null);}

    private boolean selectorEditable(JsonObject req) {
        return recipe() != null && !isReadOnly(recipe()) && !isNewRecipe(recipe()) && req != null
                && req.has("editableSelector") && req.get("editableSelector").getAsBoolean();
    }

    private boolean triggerEditable(JsonObject req) {
        return recipe() != null && !isReadOnly(recipe()) && !isNewRecipe(recipe()) && req != null
                && req.has("editableTrigger") && req.get("editableTrigger").getAsBoolean();
    }
    private void openRequirementTrigger() {
        var req = requirement(); if (!triggerEditable(req)) return;
        triggerOpen = true; saveStatus = ""; saveFailed = false;
        triggerPanel.open(req, recipe().get("duration").getAsInt());
        syncMainWidgets(); setFocused(null);
    }
    private void closeRequirementTrigger() {
        triggerOpen = false; triggerPanel.setVisible(false, false); syncMainWidgets(); setFocused(null);
    }

    private boolean batchEditable(JsonObject req) {
        return recipe() != null && !isReadOnly(recipe()) && !isNewRecipe(recipe()) && req != null
                && req.has("editableBatch") && req.get("editableBatch").getAsBoolean();
    }

    private void openRequirementBatch() {
        var req = requirement(); if (!batchEditable(req)) return;
        batchOpen = true; saveStatus = ""; saveFailed = false;
        batchPanel.open(req.has("batchPolicy") ? req.get("batchPolicy").getAsInt() : RequirementBatchEditor.DEFAULT);
        syncMainWidgets(); setFocused(null);
    }

    private void closeRequirementBatch() {
        batchOpen = false; batchPanel.setVisible(false, false); syncMainWidgets(); setFocused(null);
    }

    private void openSelectorBinding(int index) {
        if (index < 0 || index >= recipeRequirements().size()) return;
        JsonObject req = recipeRequirements().get(index).getAsJsonObject();
        if (!selectorEditable(req)) return;
        requirementIndex = index;
        selectorOpen = true;
        saveStatus = ""; saveFailed = false;
        selectorPanel.open(recipe().has("selectorTags") ? recipe().getAsJsonArray("selectorTags") : new JsonArray(),
                req.has("selector-tag") ? req.get("selector-tag").getAsString() : "");
        syncMainWidgets(); setFocused(null);
    }

    private void closeSelectorBinding() {
        selectorOpen = false;
        selectorPanel.setVisible(false, false);
        syncMainWidgets(); setFocused(null);
    }

    private boolean resourceAvailable(String key) { return document.has(key) && document.get(key).getAsBoolean(); }
    private void openResourceAmount(int index) {
        if (recipe() == null || isReadOnly(recipe()) || menu.saves().pending()) return;
        JsonObject req = index < 0 ? null : recipeRequirements().get(index).getAsJsonObject();
        boolean output = req == null ? pickerOutput : "output".equals(req.get("io-type").getAsString());
        boolean mana = resourceAvailable("manaEditorAvailable"), ember = resourceAvailable("emberEditorAvailable");
        if (req != null && !resourceAvailable(switch(ResourceAmountEditor.modId(req)) {
            case "botania" -> "manaEditorAvailable"; case "embers" -> "emberEditorAvailable";
            case "bloodmagic" -> "bloodMagicEditorAvailable"; default -> "auraEditorAvailable";
        })) return;
        if (req != null) {
            try { ResourceAmountEditor.validate(req); }
            catch (RuntimeException invalid) {
                if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("该资源需求请修改源文件：" + invalid.getMessage()), false);
                return;
            }
        }
        closeTypePicker(); requirementIndex = index; addingRequirement = index < 0; pendingRequirement = null;
        resourceOpen = true; saveStatus = ""; saveFailed = false;
        resourcePanel.open(req, output, document); syncMainWidgets(); setFocused(null);
    }
    private void closeResourceAmount() {
        resourceOpen = false; addingRequirement = false; resourcePanel.setVisible(false, false); syncMainWidgets(); setFocused(null);
    }
    private void saveResourceAmount(JsonObject requirement) {
        var current = recipe();
        submitMutation(new PktMachineEditorResourceAmount(current.get("name").getAsString(), addingRequirement ? -1 : requirementIndex,
                requirement.toString(), isNewRecipe(current) ? machine().get("name").getAsString() : "",
                current.get("duration").getAsInt(), current.has("displayName") ? current.get("displayName").getAsString() : ""));
    }

    private void openEnvironment(int index) {
        if (recipe() == null || isReadOnly(recipe()) || menu.saves().pending()) return;
        JsonObject req = index < 0 ? null : recipeRequirements().get(index).getAsJsonObject();
        try { if (req != null) EnvironmentEditor.validate(req); }
        catch (RuntimeException invalid) { saveStatus = "环境条件无效：" + invalid.getMessage(); saveFailed = true; return; }
        closeTypePicker(); requirementIndex = index; addingRequirement = index < 0; pendingRequirement = null;
        environmentOpen = true; saveStatus = ""; saveFailed = false; environmentPanel.open(req); syncMainWidgets(); setFocused(null);
    }
    private void closeEnvironment() {
        environmentOpen = false; addingRequirement = false; environmentPanel.setVisible(false, false); syncMainWidgets(); setFocused(null);
    }
    private void saveEnvironment(JsonObject requirement) {
        var current = recipe();
        submitMutation(new PktMachineEditorEnvironment(current.get("name").getAsString(), addingRequirement ? -1 : requirementIndex,
                requirement.toString(), isNewRecipe(current) ? machine().get("name").getAsString() : "",
                current.get("duration").getAsInt(), current.has("displayName") ? current.get("displayName").getAsString() : ""));
    }

    private boolean pneumaticAvailable() {
        return document.has("pneumaticEditorAvailable") && document.get("pneumaticEditorAvailable").getAsBoolean();
    }
    private void openPneumatic(int index) {
        if (!pneumaticAvailable() || recipe() == null || isReadOnly(recipe()) || menu.saves().pending()) return;
        JsonObject req = index < 0 ? null : recipeRequirements().get(index).getAsJsonObject();
        if (req != null) {
            try { PneumaticEditor.validate(req); }
            catch (RuntimeException invalid) {
                if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("该气动需求请修改源文件：" + invalid.getMessage()), false);
                return;
            }
        }
        closeTypePicker(); requirementIndex = index; addingRequirement = index < 0; pendingRequirement = null;
        pneumaticOpen = true; saveStatus = ""; saveFailed = false;
        pneumaticPanel.open(req, req == null ? pickerOutput : "output".equals(req.get("io-type").getAsString()));
        syncMainWidgets(); setFocused(null);
    }
    private void closePneumatic() {
        pneumaticOpen = false; addingRequirement = false; pneumaticPanel.setVisible(false, false);
        syncMainWidgets(); setFocused(null);
    }
    private void savePneumatic(JsonObject requirement) {
        JsonObject current = recipe();
        submitMutation(new PktMachineEditorPneumatic(current.get("name").getAsString(), addingRequirement ? -1 : requirementIndex,
                requirement.toString(), isNewRecipe(current) ? machine().get("name").getAsString() : "",
                current.get("duration").getAsInt(), current.has("displayName") ? current.get("displayName").getAsString() : ""));
    }

    private void openSmartInterface(int index) {
        if (recipe() == null || isReadOnly(recipe())) return;
        JsonObject req = index < 0 ? null : recipeRequirements().get(index).getAsJsonObject();
        if (req != null) {
            try { SmartInterfaceEditor.validate(req); }
            catch (RuntimeException invalid) {
                if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("该智能接口条件数据无效，请修改源文件：" + invalid.getMessage()), false);
                return;
            }
        }
        closeTypePicker();
        requirementIndex = index; addingRequirement = index < 0; pendingRequirement = null;
        smartOpen = true; saveStatus = ""; saveFailed = false;
        JsonObject owner = isNewRecipe(recipe()) ? machine() : recipe();
        smartPanel.open(owner.has("smartInterfaceTypes") ? owner.getAsJsonArray("smartInterfaceTypes") : new JsonArray(), req);
        syncMainWidgets(); setFocused(null);
    }

    private void closeSmartInterface() {
        smartOpen = false; addingRequirement = false;
        smartPanel.setVisible(false, false); syncMainWidgets(); setFocused(null);
    }

    private void saveSmartInterface(JsonObject condition) {
        JsonObject current = recipe();
        submitMutation(new PktMachineEditorSmartInterface(current.get("name").getAsString(), addingRequirement ? -1 : requirementIndex,
                condition.toString(), isNewRecipe(current) ? machine().get("name").getAsString() : "",
                current.get("duration").getAsInt(), current.has("displayName") ? current.get("displayName").getAsString() : ""));
    }

    private void openControllerAppearance() {
        if (machine() == null || menu.saves().pending()) return;
        colorOpen = true; saveStatus = ""; saveFailed = false;
        controllerAppearance.open(machine()); controllerAppearance.setVisible(true, false); syncMainWidgets();
    }

    private void closeControllerAppearance() {
        if (menu.saves().pending()) return;
        colorOpen = false; controllerAppearance.setVisible(false, false); syncMainWidgets(); setFocused(null);
    }

    private void openNamePreview() {
        openNamePreview(false);
    }

    private void openNamePreview(boolean recipePreview) {
        if (machine() == null || recipePreview && recipe() == null) return;
        namePreviewRecipe = recipePreview;
        namePreviewOpen = true;
        pickerOpen = editorOpen = false;
        setPickerVisible(false);
        setEditorVisible(false);
        setNamePreviewVisible(true);
        setFocused(namePreviewClose);
    }

    private void closeNamePreview() {
        namePreviewOpen = false;
        editorOpen = namePreviewRecipe;
        setNamePreviewVisible(false);
        setEditorVisible(editorOpen);
        setFocused(namePreviewRecipe ? recipeDisplayName : localizedName);
    }

    private void setNamePreviewVisible(boolean visible) {
        namePreviewClose.visible = visible;
        namePreviewClose.active = visible;
        if (!visible && getFocused() == namePreviewClose) setFocused(null);
        syncMainWidgets();
    }

    private void machineAddHint() {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(
                    "机器请使用 /modularmachinery register <名称> 或木棍构建工具创建"), false);
        }
    }

    private void createRecipe() {
        JsonObject currentMachine = machine();
        if (currentMachine == null || !currentMachine.has("name")) return;
        String base = currentMachine.get("name").getAsString().replaceAll("[^A-Za-z0-9_.-]", "_");
        if (base.length() > 80) base = base.substring(0, 80);
        if (base.isEmpty() || !Character.isLetterOrDigit(base.charAt(0))) base = "recipe_" + base;
        String recipeName = base + "_editor_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        JsonObject recipe = new JsonObject();
        recipe.addProperty("name", recipeName);
        recipe.addProperty("duration", 20);
        recipe.addProperty("editable", true);
        recipe.addProperty("newRecipe", true);
        recipe.addProperty("parallelized", !document.has("defaultRecipeParallelized")
                || document.get("defaultRecipeParallelized").getAsBoolean());
        recipe.add("requirements", new JsonArray());
        if (!currentMachine.has("recipes") || !currentMachine.get("recipes").isJsonArray())
            currentMachine.add("recipes", new JsonArray());
        currentMachine.getAsJsonArray("recipes").add(recipe);
        recipeIndex = currentMachine.getAsJsonArray("recipes").size() - 1;
        recipeScroll = clampScroll(recipeIndex - visibleRows() + 1, recipes().size(), visibleRows());
        requirementIndex = inputScroll = outputScroll = 0;
        syncFields();
        if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(
                Component.literal("已新建配方草稿，添加并保存首个需求后写入文件"), true);
    }

    private void openTypePicker(boolean output) {
        JsonObject current = recipe();
        if (current == null) return;
        if (isReadOnly(current)) {
            if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(
                    Component.literal(readOnlyReason(current)), true);
            return;
        }
        pickerOutput = output;
        pickerDurability = false;
        pickerFluidTick = false;
        typePickerOpen = true;
        setTypePickerVisible(true);
    }

    private void setTypePickerVisible(boolean visible) {
        for (var widget : typeWidgets) { widget.visible = visible; widget.active = visible; }
        itemTagType.visible = visible && !pickerOutput;
        itemTagType.active = itemTagType.visible;
        ingredientGroupType.setMessage(Component.literal(pickerOutput ? "随机物品组（输出）" : "候选物品组（输入）"));
        ingredientGroupType.setX(leftPos + (pickerOutput ? 223 : 105));
        ingredientGroupType.setY(topPos + (pickerOutput ? 90 : 118));
        ingredientGroupType.setTooltip(Tooltip.create(Component.literal(pickerOutput ? "按权重抽取一种产物，可设置数量范围和整组产出概率"
                : "每份加工选择能付清的一项，可分别设置数量")));
        ingredientGroupType.visible = visible;
        ingredientGroupType.active = ingredientGroupType.visible;
        catalystType.visible = visible && !pickerOutput;
        catalystType.active = catalystType.visible;
        durabilityType.visible = durabilityTagType.visible = visible && !pickerOutput;
        durabilityType.active = durabilityTagType.active = durabilityType.visible;
        fuelType.visible = visible && !pickerOutput;
        fuelType.active = fuelType.visible;
        smartType.visible = visible && !pickerOutput;
        smartType.active = smartType.visible;
        environmentType.visible = visible && !pickerOutput;
        environmentType.active = environmentType.visible;
        pneumaticType.visible = visible && pneumaticAvailable();
        pneumaticType.active = pneumaticType.visible;
        resourceType.visible = visible && (resourceAvailable("manaEditorAvailable") || !pickerOutput && resourceAvailable("emberEditorAvailable") || resourceAvailable("bloodMagicEditorAvailable") || resourceAvailable("auraEditorAvailable"));
        resourceType.active = resourceType.visible;
        syncMainWidgets();
    }

    private void closeTypePicker() {
        typePickerOpen = false;
        setTypePickerVisible(false);
        setFocused(null);
    }

    private void chooseType(BasicRequirementType type) {
        closeTypePicker();
        pickerFluidTick = false;
        pickerType = type;
        pickerTags = false;
        if (type == BasicRequirementType.ENERGY) beginRequirementDraft("");
        else openPicker();
    }

    private void chooseFluidTick() {
        closeTypePicker(); pickerType = BasicRequirementType.FLUID;
        pickerFluidTick = true; pickerTags = pickerDurability = false; openPicker();
    }

    private void chooseItemTag() {
        if (pickerOutput) return;
        closeTypePicker();
        pickerType = BasicRequirementType.ITEM;
        pickerTags = true;
        openPicker();
    }

    private void chooseDurability(boolean tags) {
        if (pickerOutput) return;
        closeTypePicker();
        pickerDurability = true;
        pickerTags = tags;
        pickerType = BasicRequirementType.ITEM;
        openPicker();
    }

    private void beginFuelDraft() {
        if (pickerOutput || recipe() == null) return;
        closeTypePicker();
        pendingRequirement = FuelEditor.create(1600, 1);
        addingRequirement = true;
        editorRecipe = false;
        editorOpen = true;
        pickerOpen = false;
        setPickerVisible(false);
        saveStatus = "";
        saveFailed = false;
        syncFields();
        setEditorVisible(true);
        setFocused(amount);
    }

    private String pickerHint() {
        return pickerTags ? "输入标签 ID 或标签内物品名称" : pickerDurability ? "搜索可扣耐久物品的名称或 ID"
                : pickerType == BasicRequirementType.FLUID ? "输入流体名称或 ID" : "输入物品名称或 ID";
    }

    private void openPicker() {
        pickerInventory=false;
        pickerScroll = 0;
        pickerOpen = true;
        editorOpen = false;
        setEditorVisible(false);
        pickerSearch.setValue("");
        cachedPickerQuery = null;
        pickerSearch.setHint(Component.literal(pickerHint()));
        setPickerVisible(true);
        setFocused(pickerSearch);
    }

    private void closePicker() {
        boolean returnToEditor = pickerReplacing;
        pickerReplacing = false;
        groupPickerIndex = -2;
        pickerOpen = false;
        editorOpen = returnToEditor;
        setPickerVisible(false);
        setEditorVisible(editorOpen);
        if (returnToEditor) setFocused(amount);
    }

    private void openReplacementPicker() {
        JsonObject req = requirement();
        if (editingGroup()) {
            if (groupIndex < groupItems().size()) openGroupPicker(ItemTagSelection.isTag(groupItems().get(groupIndex).getAsJsonObject()), groupIndex);
            return;
        }
        if (!RequirementResourceSelection.canReplace(req)) return;
        pickerType = BasicRequirementType.fromKey(RequirementResourceSelection.key(req));
        pickerTags = ItemTagSelection.isTag(req);
        pickerOutput = "output".equals(req.get("io-type").getAsString());
        pickerDurability = DurabilityEditor.isDurability(req);
        pickerFluidTick = FluidTickEditor.isRate(req);
        pickerReplacing = true;
        openPicker();
    }

    private void selectInventoryStack(ItemStack stack) {
        try {
            String probabilityDraft = pickerReplacing && groupPickerIndex < -1 ? chance.getValue() : null;
            boolean group=groupPickerIndex>=-1;boolean output=group?outputGroup():pickerOutput;
            var nbt=importInventoryNbt?hellfirepvp.modularmachinery.common.editor.InventoryIngredientEditor.data(stack,output):new JsonObject();
            selectPickerResource(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            if(pickerOpen)return;
            inventoryItemDraft = !group;
            var target=group?groupItems().get(groupIndex).getAsJsonObject():pendingRequirement;
            int count = Math.min(64, Math.max(1, stack.getCount()));
            target.addProperty("amount",count);
            if(group&&output){target.addProperty("minAmount",count);target.addProperty("maxAmount",count);}
            for(String key:List.of("nbt","nbt-display")){if(nbt.has(key))target.add(key,nbt.get(key));else target.remove(key);}
            if(group)loadGroupFields();else {
                syncFields();
                if (probabilityDraft != null) chance.setValue(probabilityDraft);
            }
        } catch(RuntimeException invalid){saveStatus=invalid.getMessage();saveFailed=true;if(Minecraft.getInstance().player!=null)Minecraft.getInstance().player.displayClientMessage(Component.literal(saveStatus),false);}
    }

    private void selectPickerResource(String id) {
        try {
            if (groupPickerIndex >= -1) {
                var candidate = IngredientGroupEditor.candidate(id, outputGroup());
                int index = groupPickerIndex;
                if (index < 0) {
                    if (groupItems().size() >= IngredientGroupEditor.MAX_CANDIDATES) throw new IllegalArgumentException("候选项已达上限");
                    groupItems().add(candidate);
                    groupIndex = groupItems().size() - 1;
                } else {
                    var existing = groupItems().get(index).getAsJsonObject();
                    existing.add("item", candidate.get("item"));
                    groupIndex = index;
                }
                closePicker();
                selectGroupAmount();
                return;
            }
            if (!pickerReplacing) {
                beginRequirementDraft(id);
                return;
            }
            // Keep the quantity/probability text, including unfinished edits, while changing the resource.
            pendingRequirement = RequirementResourceSelection.replace(pendingRequirement, id);
            closePicker();
        } catch (IllegalArgumentException invalid) {
            cachedPickerQuery = null;
            if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(
                    Component.literal(invalid.getMessage()), true);
        }
    }

    private void openRecipeOptions() {
        if (recipe() == null || isNewRecipe(recipe()) || isReadOnly(recipe()) || menu.saves().pending()) return;
        recipeOptionsOpen = true; editorOpen = false;
        setEditorVisible(false); setFocused(null);
        recipeOptionsPanel.open(recipeOptionsDraft == null ? RecipeOptionsEditor.read(recipe()) : recipeOptionsDraft);
    }

    private void closeRecipeOptions() {
        recipeOptionsOpen = false; recipeOptionsPanel.setVisible(false);
        editorOpen = true; setEditorVisible(true); setFocused(null);
    }

    private void openRecipeEditor() {
        if (recipe() == null) return;
        if (isReadOnly(recipe())) {
            if (Minecraft.getInstance().player != null)
                Minecraft.getInstance().player.displayClientMessage(Component.literal(readOnlyReason(recipe())), true);
            return;
        }
        saveStatus = "";
        saveFailed = false;
        editorRecipe = true;
        recipeOptionsDraft = RecipeOptionsEditor.read(recipe());
        editorOpen = true;
        pickerOpen = false;
        setPickerVisible(false);
        syncFields();
        setEditorVisible(true);
    }

    private void openRequirementEditor(int index) {
        inventoryItemDraft = false;
        JsonObject selectedRecipe = recipe();
        if (selectedRecipe == null || (selectedRecipe.has("editable") && !selectedRecipe.get("editable").getAsBoolean())) return;
        if (EnvironmentEditor.isEnvironment(recipeRequirements().get(index).getAsJsonObject())) { openEnvironment(index); return; }
        if (ResourceAmountEditor.isResource(recipeRequirements().get(index).getAsJsonObject())) { openResourceAmount(index); return; }
        if (PneumaticEditor.isPneumatic(recipeRequirements().get(index).getAsJsonObject())) { openPneumatic(index); return; }
        if (SmartInterfaceEditor.isSmart(recipeRequirements().get(index).getAsJsonObject())) {
            openSmartInterface(index); return;
        }
        var nbtRequirement=recipeRequirements().get(index).getAsJsonObject();
        if ((nbtRequirement.has("nbt") || nbtRequirement.has("nbt-display")) && nbtEditable(nbtRequirement)) {
            requirementIndex=index;openRequirementNbt();return;
        }
        if (FluidTickEditor.isRate(recipeRequirements().get(index).getAsJsonObject())
                && !FluidTickEditor.canEdit(recipeRequirements().get(index).getAsJsonObject())) {
            if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("该持续流体需求包含 NBT 或超出编辑范围的条件，请修改源文件"), false);
            return;
        }
        requirementIndex = index;
        saveStatus = "";
        saveFailed = false;
        pendingRequirement = recipeRequirements().get(index).getAsJsonObject().deepCopy();
        if (FuelEditor.isFuel(pendingRequirement) && !FuelEditor.canEdit(pendingRequirement)) {
            pendingRequirement = null;
            if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("该燃料需求包含 NBT 或超出编辑范围的条件，请修改源文件"), false);
            return;
        }
        if (DurabilityEditor.isDurability(pendingRequirement) && !DurabilityEditor.canEdit(pendingRequirement)) {
            pendingRequirement = null;
            if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("该耐久需求包含 NBT、高级选择器或超出编辑范围的条件，请修改源文件"), false);
            return;
        }
        if (IngredientGroupEditor.isGroup(pendingRequirement)) {
            if (!IngredientGroupEditor.canEdit(pendingRequirement)) {
                pendingRequirement = null;
                if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("该候选组包含标签输出或编辑器暂不支持的高级条件，请修改源文件"), false);
                return;
            }
            pendingRequirement = IngredientGroupEditor.draft(pendingRequirement);
            groupIndex = groupScroll = 0;
        }
        addingRequirement = false;
        editorRecipe = false;
        editorOpen = true;
        pickerOpen = false;
        setPickerVisible(false);
        syncFields();
        setEditorVisible(true);
    }

    private boolean editingGroup() {
        return !editorRecipe && IngredientGroupEditor.isGroup(requirement());
    }

    private boolean outputGroup() { return editingGroup() && IngredientGroupEditor.isOutput(requirement()); }
    private boolean catalystGroup() { return editingGroup() && CatalystEditor.isCatalyst(requirement()); }
    private boolean durabilityRequirement() { return !editorRecipe && DurabilityEditor.isDurability(requirement()); }
    private boolean fuelRequirement() { return !editorRecipe && FuelEditor.isFuel(requirement()); }
    private int groupRows() { return outputGroup() || catalystGroup() ? 3 : GROUP_ROWS; }

    private void beginCatalystDraft() {
        if (pickerOutput) return;
        beginGroupDraft();
        pendingRequirement = CatalystEditor.empty();
        syncFields();
        setEditorVisible(true);
    }

    private void openCatalystModifiers() {
        if (!catalystGroup() || !storeGroupAmount()) return;
        editorOpen = false;
        modifiersOpen = true;
        setEditorVisible(false);
        setFocused(null);
        modifierPanel.open(requirement().getAsJsonArray("modifiers"));
    }

    private void closeCatalystModifiers() {
        if(templateModifiers){templateModifiers=false;modifiersOpen=false;modifierPanel.setVisible(false);adapterOpen=true;adapterPanel.setVisible(true,false);syncMainWidgets();setFocused(null);return;}
        modifiersOpen = false;
        modifierPanel.setVisible(false);
        setFocused(null);
        editorOpen = true;
        setEditorVisible(true);
    }

    private void openCatalystTooltips() {
        if (!catalystGroup() || !storeGroupAmount()) return;
        editorOpen = false; catalystTooltipsOpen = true;
        setEditorVisible(false); setFocused(null);
        catalystTooltipPanel.open(RecipeTooltipEditor.read(requirement()));
    }

    private void closeCatalystTooltips() {
        catalystTooltipsOpen = false; catalystTooltipPanel.setVisible(false); setFocused(null);
        editorOpen = true; setEditorVisible(true);
    }

    private JsonArray groupItems() {
        JsonObject req = requirement();
        return IngredientGroupEditor.isGroup(req) && req.has("items") && req.get("items").isJsonArray()
                ? req.getAsJsonArray("items") : new JsonArray();
    }

    private void beginGroupDraft() {
        if (recipe() == null) return;
        closeTypePicker();
        saveStatus = "";
        saveFailed = false;
        pendingRequirement = IngredientGroupEditor.empty(pickerOutput);
        addingRequirement = true;
        editorRecipe = false;
        editorOpen = true;
        groupIndex = groupScroll = 0;
        syncFields();
        setEditorVisible(true);
    }

    private boolean storeGroupAmount() {
        if (groupItems().isEmpty()) return true;
        try {
            int count = Integer.parseInt(amount.getValue());
            if (count < 1 || count > 64) throw new NumberFormatException();
            var candidate = groupItems().get(groupIndex).getAsJsonObject();
            if (outputGroup()) {
                int max = Integer.parseInt(groupMax.getValue());
                float weight = Float.parseFloat(groupWeight.getValue());
                if (max < count || max > 64 || !Float.isFinite(weight) || weight < 0) throw new NumberFormatException();
                candidate.addProperty("minAmount", count);
                candidate.addProperty("maxAmount", max);
                candidate.addProperty("chance", weight);
            }
            candidate.addProperty("amount", count);
            return true;
        } catch (NumberFormatException invalid) {
            saveStatus = outputGroup() ? "数量须为 1～64 且最少≤最多；权重须非负且有限" : "请将当前候选项数量填写为 1～64";
            saveFailed = true;
            setFocused(amount);
            return false;
        }
    }

    private void selectGroupAmount() {
        groupIndex = Math.max(0, Math.min(groupIndex, groupItems().size() - 1));
        loadGroupFields();
        if (groupIndex < groupScroll) groupScroll = groupIndex;
        if (groupIndex >= groupScroll + groupRows()) groupScroll = groupIndex - groupRows() + 1;
        groupScroll = clampScroll(groupScroll, groupItems().size(), groupRows());
        setEditorVisible(editorOpen);
    }

    private void loadGroupFields() {
        JsonObject item = groupItems().isEmpty() ? null : groupItems().get(groupIndex).getAsJsonObject();
        amount.setValue(item == null ? "" : item.get(outputGroup() ? "minAmount" : "amount").getAsString());
        groupMax.setValue(item == null || !outputGroup() ? "" : item.get("maxAmount").getAsString());
        groupWeight.setValue(item == null || !outputGroup() ? "" : item.get("chance").getAsString());
    }

    private void openGroupPicker(boolean tags, int index) {
        if (!editingGroup() || !storeGroupAmount()) return;
        if (tags && outputGroup()) return;
        if (index < 0 && groupItems().size() >= IngredientGroupEditor.MAX_CANDIDATES) {
            saveStatus = "候选项已达上限：" + IngredientGroupEditor.MAX_CANDIDATES;
            saveFailed = true;
            return;
        }
        pickerType = BasicRequirementType.ITEM;
        pickerDurability = false;
        pickerFluidTick = false;
        pickerTags = tags;
        pickerOutput = outputGroup();
        pickerReplacing = true;
        groupPickerIndex = index;
        openPicker();
    }

    private void moveGroupCandidate(int delta) {
        int target = groupIndex + delta;
        if (target < 0 || target >= groupItems().size() || !storeGroupAmount()) return;
        var other = groupItems().get(target);
        groupItems().set(target, groupItems().get(groupIndex));
        groupItems().set(groupIndex, other);
        groupIndex = target;
        selectGroupAmount();
    }

    private void closeEditor() {
        editorOpen = false;
        inventoryItemDraft = false;
        pendingRequirement = null;
        addingRequirement = false;
        setEditorVisible(false);
    }

    private void setEditorVisible(boolean visible) {
        if (amount == null) return;
        boolean group = editingGroup();
        boolean output = outputGroup();
        boolean durability = durabilityRequirement();
        boolean fuel = fuelRequirement();
        amount.visible = visible && !editorRecipe;
        amount.active = amount.visible && (!group || !groupItems().isEmpty());
        amount.setX(leftPos + (durability || fuel ? 226 : output ? 98 : group ? 148 : 206));
        amount.setY(topPos + (durability ? 100 : output ? 146 : group ? 167 : 104));
        amount.setWidth(durability || fuel ? 120 : output ? 44 : group ? 56 : 86);
        amount.setTooltip(durability ? Tooltip.create(Component.literal("使用次数为 1～64；同一件工具可以承担多次使用\n总次数通常乘本次批量倍率，并可在多件匹配工具之间分担"))
                : FluidTickEditor.isRate(requirement()) ? Tooltip.create(Component.literal("速率为 1～2000000000 mB/t 的整数\n每个成功推进的配方 tick 处理一次，批量倍率通常放大速率\n缺流体或输出满仓时暂停；不设置概率"))
                : fuel ? Tooltip.create(Component.literal("所需熔炉燃烧时间为 1～2000000000 tick，不是配方运行时间\n煤炭和木炭通常各提供 1600 tick，实际以模组燃料规则为准\n批量倍率放大所需时间；混合燃料可合并支付，多出的时间丢弃")) : null);
        chance.setX(leftPos + (durability || fuel ? 226 : output ? 126 : group ? 300 : 206));
        chance.setY(topPos + (durability ? 156 : output ? 170 : group ? 167 : 143));
        chance.setWidth(durability || fuel ? 120 : output ? 70 : group ? 64 : 86);
        toolDamage.visible = visible && durability;
        toolDamage.active = toolDamage.visible && !menu.saves().pending();
        groupMax.visible = groupWeight.visible = visible && output;
        groupMax.active = groupWeight.active = groupMax.visible && !groupItems().isEmpty() && !menu.saves().pending();
        chance.visible = visible && !editorRecipe && !isEnergy(requirement()) && !FluidTickEditor.isRate(requirement());
        chance.active = chance.visible;
        chance.setTooltip(Tooltip.create(Component.literal(catalystGroup()
                ? "消耗概率范围 0～1；0 不扣除催化剂，但存在时效果仍生效\n缺少催化剂不会阻止配方加工；支持科学计数法"
                : durability ? "扣耐久概率为 0～1；0 不扣耐久，但开工仍需匹配且可损坏的工具\n支持科学计数法"
                : fuel ? "消耗概率为 0～1；0 不扣燃料，但开工仍需足够燃烧时间\n概率控制本次燃料扣除，不改变所需时间；支持科学计数法"
                : "概率范围 0～1，支持小数和科学计数法")));
        duration.visible = visible && editorRecipe;
        duration.active = duration.visible;
        duration.setX(leftPos + 170);
        duration.setY(topPos + (editorRecipe ? 115 : 104));
        recipeDisplayName.visible = visible && editorRecipe;
        recipeDisplayName.active = recipeDisplayName.visible;
        recipeNamePreview.visible = visible && editorRecipe;
        recipeNamePreview.active = recipeNamePreview.visible;
        recipeAdvanced.visible = visible && editorRecipe;
        recipeAdvanced.active = recipeAdvanced.visible && !isNewRecipe(recipe()) && !menu.saves().pending();
        recipeAdvanced.setTooltip(Tooltip.create(Component.literal(isNewRecipe(recipe())
                ? "新配方请先保存首个需求，再编辑高级设置" : "设置优先级、失败策略、批量加工、JEI 显示、工厂线程和附加说明\n应用后仍需在此窗口点击保存；取消放弃整次修改")));
        requirementReselect.setX(leftPos + (group ? 70 : 286));
        requirementReselect.setY(topPos + (group ? 190 : 55));
        requirementReselect.setWidth(group ? 80 : 72);
        requirementReselect.visible = visible && !editorRecipe && (group || !RequirementResourceSelection.key(requirement()).isEmpty());
        requirementReselect.active = requirementReselect.visible && (group ? !groupItems().isEmpty() : RequirementResourceSelection.canReplace(requirement()));
        requirementReselect.setTooltip(Tooltip.create(Component.literal(requirementReselect.active
                ? "重新搜索选择；保留数量、概率和输入输出方向，保存后生效"
                : "NBT、旧版表达式、无可用成员的标签或标签输出请在源文件中修改")));
        editorSave.setX(leftPos + (editorRecipe || group ? 228 : 170));
        editorSave.setY(topPos + (editorRecipe || group ? 208 : durability ? 185 : 177));
        editorCancel.setX(leftPos + (editorRecipe || group ? 298 : 240));
        editorCancel.setY(topPos + (editorRecipe || group ? 208 : durability ? 185 : 177));
        for (var widget : groupWidgets) {
            widget.visible = visible && group;
            widget.active = widget.visible && !menu.saves().pending();
            if (!widget.visible && getFocused() == widget) setFocused(null);
        }
        groupAddItem.setX(leftPos + (output ? 304 : 238));
        groupAddTag.visible &= !output;
        groupAddTag.active &= !output;
        candidateNbt.active &= !groupItems().isEmpty();
        groupUp.active &= groupIndex > 0;
        groupDown.active &= groupIndex + 1 < groupItems().size();
        groupUp.setTooltip(Tooltip.create(Component.literal(output ? "上移显示顺序；抽取概率由权重决定" : "上移，提高优先顺序")));
        groupDown.setTooltip(Tooltip.create(Component.literal(output ? "下移显示顺序；抽取概率由权重决定" : "下移，降低优先顺序")));
        catalystModifiers.visible = visible && catalystGroup();
        catalystModifiers.active = catalystModifiers.visible && !menu.saves().pending();
        catalystModifiers.setMessage(Component.literal("修改器（" + (catalystGroup() ? requirement().getAsJsonArray("modifiers").size() : 0) + "）"));
        catalystTooltips.visible = visible && catalystGroup();
        catalystTooltips.active = catalystTooltips.visible && !menu.saves().pending();
        catalystTooltips.setMessage(Component.literal("提示文字（" + (catalystGroup() ? RecipeTooltipEditor.read(requirement()).size() : 0) + "）"));
        editorSave.visible = visible;
        editorSave.active = visible;
        editorCancel.visible = visible;
        editorCancel.active = visible;
        if (menu.saves().pending()) {
            amount.active = chance.active = duration.active = recipeDisplayName.active = false;
            editorSave.active = editorCancel.active = recipeNamePreview.active = requirementReselect.active = false;
        }
        editorSave.setMessage(Component.literal(menu.saves().pending() ? "保存中…" : "保存"));
        if (!visible && (getFocused() == amount || getFocused() == chance || getFocused() == duration
                || getFocused() == recipeDisplayName || getFocused() == recipeNamePreview || getFocused() == recipeAdvanced || getFocused() == requirementReselect))
            setFocused(null);
        if ((!groupMax.visible && getFocused() == groupMax) || (!groupWeight.visible && getFocused() == groupWeight)) setFocused(null);
        if (!toolDamage.visible && getFocused() == toolDamage) setFocused(null);
        syncMainWidgets();
    }

    private void setPickerVisible(boolean visible) {
        if (pickerSearch == null) return;
        pickerSearch.visible = visible;
        pickerSearch.active = visible;
        pickerCancel.visible = visible;
        pickerCancel.active = visible;
        inventoryPicker.visible=visible&&pickerType==BasicRequirementType.ITEM&&!pickerTags;
        inventoryPicker.active=inventoryPicker.visible;inventoryPicker.setMessage(Component.literal(pickerInventory?"全部物品":"背包"));
        inventoryNbt.visible=inventoryPicker.visible&&pickerInventory;inventoryNbt.active=inventoryNbt.visible;
        pickerCancel.setMessage(Component.literal(pickerReplacing ? "返回" : "关闭"));
        if (!visible && getFocused() == pickerSearch) setFocused(null);
        syncMainWidgets();
    }

    private void saveEditor() {
        JsonObject current = recipe();
        if (current == null) return;
        try {
            if (editorRecipe) {
                int ticks = Integer.parseInt(duration.getValue());
                if (ticks < 1 || ticks > 2_000_000_000) throw new NumberFormatException();
                if (!isNewRecipe(current)) {
                    var options = recipeOptionsDraft == null ? RecipeOptionsEditor.read(current) : recipeOptionsDraft;
                    submitMutation(new PktMachineEditorRecipeOptions(current.get("name").getAsString(),
                            recipeDisplayName.getValue(), ticks, options.priority(), options.cancel(), options.threadName(), options.maxThreads(),
                            options.parallelized(), options.loadJEI(), options.tooltips()));
                    return;
                }
                current.addProperty("duration", ticks);
                current.addProperty("displayName", MachineNameFormatting.display(recipeDisplayName.getValue()));
            } else {
                if (!sendEdit()) return;
                if (menu.saves().pending()) return;
            }
            closeEditor();
        } catch (NumberFormatException ignored) {
            if (Minecraft.getInstance().player != null)
                Minecraft.getInstance().player.displayClientMessage(Component.literal("请输入有效数字"), true);
        }
    }

    private void beginRequirementDraft(String selected) {
        inventoryItemDraft = false;
        JsonObject current = recipe();
        if (current == null) return;
        JsonObject draft = pickerFluidTick ? FluidTickEditor.create(selected, pickerOutput, 100)
                : pickerDurability ? DurabilityEditor.create(selected, 1, 1, 1F)
                : pickerType.create(selected, pickerOutput, pickerType == BasicRequirementType.FLUID ? 1000 : 1, 1F);
        closePicker();
        saveStatus = "";
        saveFailed = false;
        pendingRequirement = draft;
        addingRequirement = true;
        editorRecipe = false;
        editorOpen = true;
        syncFields();
        setEditorVisible(true);
        setFocused(amount);
    }

    private List<ResourceLocation> pickerItems() {
        String query = pickerSearch == null ? "" : pickerSearch.getValue().trim().toLowerCase(Locale.ROOT);
        if (pickerInventory) {
            inventoryChoices.clear(); var player=Minecraft.getInstance().player;
            if(player!=null)for(int slot=0;slot<player.getInventory().getContainerSize();slot++){
                var stack=player.getInventory().getItem(slot);if(stack.isEmpty() || pickerDurability&&!stack.isDamageableItem())continue;
                var id=BuiltInRegistries.ITEM.getKey(stack.getItem());
                if(matchesPicker(query.isEmpty()?new String[0]:query.split("\\s+"),id,stack.getHoverName().getString().toLowerCase(Locale.ROOT),tag->stack.getTags().anyMatch(t->t.location().toString().contains(tag))))inventoryChoices.add(stack.copy());
            }
            pickerScroll = clampScroll(pickerScroll, inventoryChoices.size(), PICKER_ROWS);
            return inventoryChoices.stream().map(stack->BuiltInRegistries.ITEM.getKey(stack.getItem())).toList();
        }
        if (query.equals(cachedPickerQuery) && pickerType == cachedPickerType && pickerTags == cachedPickerTags
                && pickerDurability == cachedPickerDurability) return cachedPickerResults;
        pickerScroll = 0;
        String[] terms = query.isEmpty() ? new String[0] : query.split("\\s+");
        List<ResourceLocation> result = new ArrayList<>();
        if (pickerTags) {
            BuiltInRegistries.ITEM.getTagNames().forEach(tag -> {
                var members = ItemTagSelection.members(tag.location()).stream()
                        .filter(item -> !pickerDurability || DurabilityEditor.damageable(item)).toList();
                if (!members.isEmpty() && ItemTagSelection.matches(query, tag.location(), () -> members.stream()
                        .map(item -> item.getDefaultInstance().getHoverName().getString()).toList())) result.add(tag.location());
            });
        } else if (pickerType == BasicRequirementType.FLUID) {
            for (var entry : BuiltInRegistries.FLUID.entrySet()) {
                var fluid = entry.getValue();
                if (fluid == net.minecraft.world.level.material.Fluids.EMPTY || !fluid.isSource(fluid.defaultFluidState())) continue;
                ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
                String name = new net.minecraftforge.fluids.FluidStack(fluid, 1000).getDisplayName().getString().toLowerCase(Locale.ROOT);
                if (matchesPicker(terms, id, name, tag -> fluid.builtInRegistryHolder().tags()
                        .anyMatch(value -> value.location().toString().contains(tag)))) result.add(id);
            }
        } else {
            for (var entry : BuiltInRegistries.ITEM.entrySet()) {
                var item = entry.getValue();
                if (item == net.minecraft.world.item.Items.AIR) continue;
                if (pickerDurability && !DurabilityEditor.damageable(item)) continue;
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                String name = item.getName(item.getDefaultInstance()).getString().toLowerCase(Locale.ROOT);
                if (matchesPicker(terms, id, name, tag -> item.builtInRegistryHolder().tags()
                        .anyMatch(value -> value.location().toString().contains(tag)))) result.add(id);
            }
        }
        result.sort(Comparator.comparing(ResourceLocation::toString));
        cachedPickerQuery = query;
        cachedPickerType = pickerType;
        cachedPickerTags = pickerTags;
        cachedPickerDurability = pickerDurability;
        cachedPickerResults = result;
        return result;
    }

    private static boolean matchesPicker(String[] terms, ResourceLocation id, String name,
                                         java.util.function.Predicate<String> tagMatches) {
        for (String term : terms) {
            if (term.startsWith("@")) {
                if (term.length() == 1 || !id.getNamespace().contains(term.substring(1))) return false;
            } else if (term.startsWith("#")) {
                if (term.length() == 1 || !tagMatches.test(term.substring(1))) return false;
            } else if (!id.toString().contains(term) && !name.contains(term)) return false;
        }
        return true;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (environmentOpen) environmentPanel.tick();
        if (nbtOpen) nbtPanel.tick();
        if(templateDraft!=null)return;
        var result = menu.saves().takeReady();
        if (result != null) {
            saveStatus = result.message();
            saveFailed = !result.success();
            if (result.success()) {
                if (copyOpen) closeRecipeCopy();
                if (colorOpen) closeControllerAppearance();
                if (batchOpen) closeRequirementBatch();
                if (triggerOpen) closeRequirementTrigger();
                if (nbtOpen) closeRequirementNbt();
                if (resourceOpen) closeResourceAmount();
                if (environmentOpen) closeEnvironment();
                if (pneumaticOpen) closePneumatic();
                if (smartOpen) closeSmartInterface();
                if (selectorOpen) closeSelectorBinding();
                requirementIndex = savedRequirementIndex;
                if (closeAfterSave) closeEditor();
                refreshDocument(menu.snapshot());
                if(adapterOpen)adapterPanel.refresh(document);
                if (!copiedRecipeId.isEmpty()) {
                    machineIndex = indexByName(machines(), copiedMachineId);
                    recipeIndex = indexByName(recipes(), copiedRecipeId);
                    if (machineIndex < machineScroll) machineScroll = machineIndex;
                    if (machineIndex >= machineScroll + visibleRows()) machineScroll = Math.max(0, machineIndex - visibleRows() + 1);
                    requirementIndex = inputScroll = outputScroll = 0;
                    copiedRecipeId = ""; copiedMachineId = ""; syncFields(); syncMainWidgets();
                }
                ensureVisibleRequirement();
                if (recipeIndex < recipeScroll) recipeScroll = recipeIndex;
                if (recipeIndex >= recipeScroll + visibleRows()) recipeScroll = Math.max(0, recipeIndex - visibleRows() + 1);
            } else if (Minecraft.getInstance().player != null) {
                copiedRecipeId = ""; copiedMachineId = "";
                Minecraft.getInstance().player.displayClientMessage(Component.literal(result.message()), false);
            }
            syncMainWidgets();
            setEditorVisible(editorOpen);
            selectorPanel.setVisible(selectorOpen, menu.saves().pending());
            batchPanel.setVisible(batchOpen, menu.saves().pending());
            triggerPanel.setVisible(triggerOpen, menu.saves().pending());
            smartPanel.setVisible(smartOpen, menu.saves().pending());
        adapterPanel.setVisible(adapterOpen, menu.saves().pending());
        environmentPanel.setVisible(environmentOpen, menu.saves().pending());
        pneumaticPanel.setVisible(pneumaticOpen, menu.saves().pending());
        nbtPanel.setVisible(nbtOpen, menu.saves().pending());
        resourcePanel.setVisible(resourceOpen, menu.saves().pending());
        }
        controllerAppearance.setVisible(colorOpen, menu.saves().pending());
        String snapshot = menu.snapshot();
        if (!menu.saves().pending() && !snapshot.equals(loadedSnapshot)) refreshDocument(snapshot);
    }

    private void refreshDocument(String snapshot) {
            try {
                String selectedMachine = machine() == null ? "" : machine().get("name").getAsString();
                String selectedRecipe = recipe() == null ? "" : recipe().get("name").getAsString();
                document = hellfirepvp.modularmachinery.common.editor.MachineEditorSnapshot.withLocalDrafts(snapshot, document);
                machineIndex = indexByName(machines(), selectedMachine);
                recipeIndex = indexByName(recipes(), selectedRecipe);
                requirementIndex = Math.max(0, Math.min(requirementIndex, recipeRequirements().size() - 1));
                machineScroll = clampScroll(machineScroll, machines().size(), visibleRows());
                recipeScroll = clampScroll(recipeScroll, recipes().size(), visibleRows());
                inputScroll = clampScroll(inputScroll, sideIndices(false).size(), SIDE_ROWS);
                outputScroll = clampScroll(outputScroll, sideIndices(true).size(), SIDE_ROWS);
                loadedSnapshot = snapshot;
                lastClickedRecipeId = null;
                syncFields();
                syncMachineName();
            } catch (RuntimeException ignored) {
                // Keep the last valid document until the network transfer is complete.
            }
    }

    private void submitMutation(Object mutation) {
        if (menu.saves().pending()) return;
        if(templateDraft!=null){
            try{
                var values=hellfirepvp.modularmachinery.common.network.TemplateRequirementDraft.apply(templateDraft.getAsJsonArray("requirements"),mutation,machine());
                int selected=addingRequirement?values.size()-1:requirementIndex;
                templateDraft.add("requirements",hellfirepvp.modularmachinery.common.network.TemplateRequirementDraft.decorate(values));
                if(nbtOpen)closeRequirementNbt();if(environmentOpen)closeEnvironment();if(resourceOpen)closeResourceAmount();if(pneumaticOpen)closePneumatic();if(smartOpen)closeSmartInterface();if(selectorOpen)closeSelectorBinding();if(batchOpen)closeRequirementBatch();
                if(editorOpen)closeEditor();pendingRequirement=null;addingRequirement=false;requirementIndex=Math.max(0,Math.min(selected,values.size()-1));saveStatus="已应用到模板草稿；返回后统一保存";saveFailed=false;syncFields();syncMainWidgets();ensureVisibleRequirement();
            }catch(RuntimeException e){saveStatus=e.getMessage();saveFailed=true;}
            return;
        }
        var id = menu.saves().begin();
        closeAfterSave = editorOpen;
        savedRequirementIndex = addingRequirement ? recipeRequirements().size() : requirementIndex;
        saveStatus = "正在等待服务器保存…";
        saveFailed = false;
        setFocused(null);
        syncMainWidgets();
        setEditorVisible(editorOpen);
        selectorPanel.setVisible(selectorOpen, menu.saves().pending());
        batchPanel.setVisible(batchOpen, menu.saves().pending());
        triggerPanel.setVisible(triggerOpen, menu.saves().pending());
        smartPanel.setVisible(smartOpen, menu.saves().pending());
        adapterPanel.setVisible(adapterOpen, menu.saves().pending());
        environmentPanel.setVisible(environmentOpen, menu.saves().pending());
        pneumaticPanel.setVisible(pneumaticOpen, menu.saves().pending());
        nbtPanel.setVisible(nbtOpen, menu.saves().pending());
        resourcePanel.setVisible(resourceOpen, menu.saves().pending());
        ModularmachineryNetwork.CHANNEL.sendToServer(new PktMachineEditorRequest(menu.containerId, id,
                EditorSaveState.revision(menu.snapshot()), mutation));
    }

    private static int indexByName(JsonArray entries, String name) {
        for (int i = 0; i < entries.size(); i++) {
            if (name.equals(entries.get(i).getAsJsonObject().get("name").getAsString())) return i;
        }
        return 0;
    }

    private JsonArray machines() {
        return document.has("machines") && document.get("machines").isJsonArray()
                ? document.getAsJsonArray("machines") : new JsonArray();
    }

    private JsonObject machine() {
        JsonArray all = machines();
        return all.isEmpty() ? null : all.get(Math.max(0, Math.min(machineIndex, all.size() - 1))).getAsJsonObject();
    }

    private JsonArray recipes() {
        JsonObject machine = machine();
        return machine != null && machine.has("recipes") && machine.get("recipes").isJsonArray()
                ? machine.getAsJsonArray("recipes") : new JsonArray();
    }

    private JsonObject recipe() {
        JsonArray all = recipes();
        return all.isEmpty() ? null : all.get(Math.max(0, Math.min(recipeIndex, all.size() - 1))).getAsJsonObject();
    }

    private JsonArray recipeRequirements() {
        JsonObject recipe = recipe();
        return recipe != null && recipe.has("requirements") && recipe.get("requirements").isJsonArray()
                ? recipe.getAsJsonArray("requirements") : new JsonArray();
    }

    private JsonObject requirement() {
        if (pendingRequirement != null) return pendingRequirement;
        JsonArray all = recipeRequirements();
        return all.isEmpty() ? null : all.get(Math.max(0, Math.min(requirementIndex, all.size() - 1))).getAsJsonObject();
    }

    private static boolean isEnergy(JsonObject req) {
        return req != null && "modularmachinery:energy".equals(req.has("type") ? req.get("type").getAsString() : "");
    }

    private static boolean isFluid(JsonObject req) {
        return req != null && ("modularmachinery:fluid".equals(req.has("type") ? req.get("type").getAsString() : "")
                || FluidTickEditor.isRate(req));
    }

    private static boolean isNewRecipe(JsonObject recipe) {
        return recipe != null && recipe.has("newRecipe") && recipe.get("newRecipe").getAsBoolean();
    }

    private static boolean isReadOnly(JsonObject recipe) {
        return recipe != null && recipe.has("editable") && !recipe.get("editable").getAsBoolean();
    }

    private static String readOnlyReason(JsonObject recipe) {
        return recipe.has("readOnlyReason") ? recipe.get("readOnlyReason").getAsString()
                : "该配方由脚本或适配器生成，请修改其来源";
    }

    private java.util.List<Integer> sideIndices(boolean output) {
        java.util.List<Integer> result = new java.util.ArrayList<>();
        JsonArray all = recipeRequirements();
        for (int i = 0; i < all.size(); i++) {
            JsonObject req = all.get(i).getAsJsonObject();
            if (output == "output".equals(req.has("io-type") ? req.get("io-type").getAsString() : "input")) result.add(i);
        }
        return result;
    }

    private int visibleRows() {
        return Math.max(1, (LIST_BOTTOM - LIST_TOP) / ROW_HEIGHT);
    }

    private void syncFields() {
        if (amount == null || chance == null || duration == null) return;
        syncMainWidgets();
        JsonObject selectedRecipe = recipe();
        recipeDisplayName.setValue(selectedRecipe != null && selectedRecipe.has("displayName")
                ? MachineNameFormatting.editable(selectedRecipe.get("displayName").getAsString()) : "");
        duration.setValue(selectedRecipe != null && selectedRecipe.has("duration")
                ? Integer.toString(selectedRecipe.get("duration").getAsInt()) : "");
        JsonObject req = requirement();
        toolDamage.setValue(req != null && DurabilityEditor.isDurability(req) && req.has("consumeDurability")
                ? req.get("consumeDurability").getAsString() : "");
        if (req == null) {
            amount.setValue("");
            chance.setValue("");
            return;
        }
        boolean energy = isEnergy(req);
        amount.setValue(Integer.toString(energy
                ? (req.has("energyPerTick") ? req.get("energyPerTick").getAsInt() : 0)
                : FuelEditor.isFuel(req) ? req.get("time").getAsInt()
                : (req.has("amount") ? req.get("amount").getAsInt() : 1)));
        if (editingGroup()) {
            groupIndex = Math.max(0, Math.min(groupIndex, groupItems().size() - 1));
            loadGroupFields();
        }
        chance.setValue(energy ? "" : Float.toString(req.has("chance") ? req.get("chance").getAsFloat() : 1F));
    }

    private void syncMachineName() {
        if (localizedName == null) return;
        JsonObject machine = machine();
        localizedName.setValue(machine != null && machine.has("localizedName")
                ? MachineNameFormatting.editable(machine.get("localizedName").getAsString()) : "");
    }

    private void saveMachineName() {
        JsonObject machine = machine();
        if (machine == null || localizedName.getValue().isBlank()) return;
        submitMutation(new ModularmachineryNetwork.PktMachineEditorMachineEdit(
                machine.get("name").getAsString(), localizedName.getValue()));
    }

    private boolean sendEdit() {
        JsonObject currentRecipe = recipe();
        JsonObject req = requirement();
        if (currentRecipe == null || req == null || isReadOnly(currentRecipe)) return false;
        int count;
        float probability;
        try {
            if (FluidTickEditor.isRate(req)) {
                var clean = FluidTickEditor.create(req.get("fluid").getAsString(), "output".equals(req.get("io-type").getAsString()),
                        Integer.parseInt(amount.getValue()));
                submitMutation(new PktMachineEditorFluidTick(currentRecipe.get("name").getAsString(),
                        addingRequirement ? -1 : requirementIndex, clean.toString(),
                        isNewRecipe(currentRecipe) ? machine().get("name").getAsString() : "",
                        currentRecipe.get("duration").getAsInt(), currentRecipe.has("displayName") ? currentRecipe.get("displayName").getAsString() : ""));
                return true;
            }
            if (fuelRequirement()) {
                var clean = FuelEditor.create(Integer.parseInt(amount.getValue()), Float.parseFloat(chance.getValue()));
                submitMutation(new PktMachineEditorFuel(currentRecipe.get("name").getAsString(),
                        addingRequirement ? -1 : requirementIndex, clean.toString(),
                        isNewRecipe(currentRecipe) ? machine().get("name").getAsString() : "",
                        currentRecipe.get("duration").getAsInt(), currentRecipe.has("displayName")
                        ? currentRecipe.get("displayName").getAsString() : ""));
                return true;
            }
            if (durabilityRequirement()) {
                var draft = pendingRequirement.deepCopy();
                draft.addProperty("amount", Integer.parseInt(amount.getValue()));
                draft.addProperty("consumeDurability", Integer.parseInt(toolDamage.getValue()));
                draft.addProperty("chance", Float.parseFloat(chance.getValue()));
                var clean = DurabilityEditor.validate(draft);
                submitMutation(new PktMachineEditorDurability(currentRecipe.get("name").getAsString(),
                        addingRequirement ? -1 : requirementIndex, clean.toString(),
                        isNewRecipe(currentRecipe) ? machine().get("name").getAsString() : "",
                        currentRecipe.get("duration").getAsInt(), currentRecipe.has("displayName")
                        ? currentRecipe.get("displayName").getAsString() : ""));
                return true;
            }
            if (editingGroup()) {
                if (!storeGroupAmount()) return false;
                var draft = pendingRequirement.deepCopy();
                draft.addProperty("chance", Float.parseFloat(chance.getValue()));
                var group = IngredientGroupEditor.validate(draft);
                submitMutation(new PktMachineEditorIngredientGroup(currentRecipe.get("name").getAsString(),
                        addingRequirement ? -1 : requirementIndex, group.toString(),
                        isNewRecipe(currentRecipe) ? machine().get("name").getAsString() : "",
                        currentRecipe.get("duration").getAsInt(), currentRecipe.has("displayName")
                        ? currentRecipe.get("displayName").getAsString() : ""));
                return true;
            }
            count = Integer.parseInt(amount.getValue());
            probability = isEnergy(req) ? 1F : Float.parseFloat(chance.getValue());
            if (count < 1 || count > 2_000_000_000 || !Float.isFinite(probability) || probability < 0 || probability > 1)
                throw new IllegalArgumentException("请输入有效数量和 0～1 的概率");
            if ("modularmachinery:item".equals(req.get("type").getAsString()) && count > 64)
                throw new IllegalArgumentException("普通物品需求数量为 1～64，可通过批量倍率放大");
            if (hellfirepvp.modularmachinery.common.editor.ItemRequirementEditor.isItem(req)
                    && (addingRequirement || inventoryItemDraft || req.has("nbt") || req.has("nbt-display"))) {
                var item=req.deepCopy();item.addProperty("amount",count);item.addProperty("chance",probability);
                submitMutation(new hellfirepvp.modularmachinery.common.network.PktMachineEditorItem(currentRecipe.get("name").getAsString(),addingRequirement?-1:requirementIndex,item.toString(),
                        isNewRecipe(currentRecipe)?machine().get("name").getAsString():"",currentRecipe.get("duration").getAsInt(),currentRecipe.has("displayName")?currentRecipe.get("displayName").getAsString():""));return true;
            }
            if (addingRequirement) {
                var type = BasicRequirementType.fromKey(req.get("type").getAsString().substring("modularmachinery:".length()));
                String id = type == BasicRequirementType.ENERGY ? "" : req.get(type.key).getAsString();
                boolean output = "output".equals(req.get("io-type").getAsString());
                type.create(id, output, count, probability);
                submitMutation(new PktMachineEditorRequirementAdd(
                        currentRecipe.get("name").getAsString(), type.key, id, output, count, probability,
                        isNewRecipe(currentRecipe) ? machine().get("name").getAsString() : "",
                        currentRecipe.get("duration").getAsInt(), currentRecipe.has("displayName")
                        ? currentRecipe.get("displayName").getAsString() : ""));
                return true;
            }
        } catch (NumberFormatException ignored) {
            saveStatus = "请输入有效数字；概率范围为 0～1";
            saveFailed = true;
            if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("请输入有效数字；概率范围为 0～1"), true);
            return false;
        } catch (IllegalArgumentException invalid) {
            saveStatus = invalid.getMessage();
            saveFailed = true;
            if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(
                    Component.literal(invalid.getMessage()), true);
            return false;
        }
        JsonObject original = recipeRequirements().get(requirementIndex).getAsJsonObject();
        String resourceKey = RequirementResourceSelection.key(req);
        String replacementId = !resourceKey.isEmpty() && !req.get(resourceKey).equals(original.get(resourceKey))
                ? req.get(resourceKey).getAsString() : "";
        submitMutation(new ModularmachineryNetwork.PktMachineEditorEdit(currentRecipe.get("name").getAsString(),
                requirementIndex, count, probability, replacementId));
        return true;
    }

    /** JEI drag targets are disabled; the picker is deterministic and does not require a held item. */
    public int requirementCount() { return 0; }
    /** Kept for the optional JEI integration ABI; the editor deliberately ignores ghost drops. */
    public void ghostItem(int index, ItemStack stack) { }
    public int requirementTargetX() { return leftPos + REQUIREMENT_X + 8; }
    public int requirementTargetY(int index) { return topPos + INPUT_TOP + (index - inputScroll) * ROW_HEIGHT; }

    private void ensureVisibleRequirement() {
        JsonObject req = requirement();
        boolean output = "output".equals(req != null && req.has("io-type") ? req.get("io-type").getAsString() : "input");
        List<Integer> indices = sideIndices(output);
        int local = indices.indexOf(requirementIndex);
        if (local < 0) return;
        if (output) {
            if (local < outputScroll) outputScroll = local;
            if (local >= outputScroll + SIDE_ROWS) outputScroll = local - SIDE_ROWS + 1;
            outputScroll = clampScroll(outputScroll, indices.size(), SIDE_ROWS);
        } else {
            if (local < inputScroll) inputScroll = local;
            if (local >= inputScroll + SIDE_ROWS) inputScroll = local - SIDE_ROWS + 1;
            inputScroll = clampScroll(inputScroll, indices.size(), SIDE_ROWS);
        }
    }

    private static int clampScroll(int scroll, int size, int rows) {
        return Math.max(0, Math.min(Math.max(0, size - rows), scroll));
    }

    private void deleteMachine(int index) {
        JsonArray all = machines();
        if (index < 0 || index >= all.size()) return;
        JsonObject entry = all.get(index).getAsJsonObject();
        if (!entry.has("name")) return;
        submitMutation(new ModularmachineryNetwork.PktMachineEditorAction(
                ModularmachineryNetwork.PktMachineEditorAction.DELETE_MACHINE, entry.get("name").getAsString(), "", -1, "", false));
    }

    private void deleteRecipe() {
        JsonArray all = recipes();
        if (recipeIndex < 0 || recipeIndex >= all.size()) return;
        JsonObject entry = all.get(recipeIndex).getAsJsonObject();
        if (!entry.has("name") || (entry.has("editable") && !entry.get("editable").getAsBoolean())) return;
        if (!isNewRecipe(entry)) {
            submitMutation(new ModularmachineryNetwork.PktMachineEditorAction(
                    ModularmachineryNetwork.PktMachineEditorAction.DELETE_RECIPE, "", entry.get("name").getAsString(), -1, "", false));
            return;
        }
        all.remove(recipeIndex);
        recipeIndex = Math.max(0, Math.min(recipeIndex, all.size() - 1));
        requirementIndex = inputScroll = outputScroll = 0;
        syncFields();
    }

    private void deleteRequirement() {
        JsonObject current = recipe();
        JsonArray all = recipeRequirements();
        if (current == null || isReadOnly(current) || requirementIndex < 0 || requirementIndex >= all.size() || !current.has("name")) return;
        if (all.size() == 1 && templateDraft == null) {
            if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("配方至少需要一个需求，请先添加替代需求，或删除整个配方"), true);
            return;
        }
        submitMutation(new ModularmachineryNetwork.PktMachineEditorAction(
                ModularmachineryNetwork.PktMachineEditorAction.DELETE_REQUIREMENT,
                "", current.get("name").getAsString(), requirementIndex, "", false));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (adapterOpen) { adapterPanel.scroll((int)mouseX-leftPos,(int)mouseY-topPos,delta); return true; }
        if (copyOpen) { copyPanel.scroll(delta); return true; }
        if (menu.saves().pending()) return true;
        if (environmentOpen) { super.mouseScrolled(mouseX,mouseY,delta); return true; }
        if (nbtOpen) { super.mouseScrolled(mouseX,mouseY,delta); return true; }
        if (batchOpen || triggerOpen) return true;
        if (recipeOptionsOpen) { recipeOptionsPanel.scroll((int) mouseX - leftPos, (int) mouseY - topPos, delta); return true; }
        if (resourceOpen || adapterOpen || environmentOpen || pneumaticOpen) return true;
        if (smartOpen) { smartPanel.scroll(delta); return true; }
        if (selectorOpen) { selectorPanel.scroll(delta); return true; }
        if (catalystTooltipsOpen) { catalystTooltipPanel.scroll((int) mouseX - leftPos, (int) mouseY - topPos, delta); return true; }
        if (modifiersOpen) { modifierPanel.scroll(delta); return true; }
        lastClickedRecipeId = null;
        int x = (int) mouseX - leftPos;
        int y = (int) mouseY - topPos;
        int rows = visibleRows();
        if (pickerOpen && x >= PICKER_X + 8 && x < PICKER_X + PICKER_W - 8
                && y >= PICKER_Y + PICKER_LIST_Y && y < PICKER_Y + PICKER_LIST_Y + PICKER_ROWS * PICKER_ROW_H) {
            pickerScroll = clampScroll(pickerScroll - (int) Math.signum(delta), pickerItems().size(), PICKER_ROWS);
            return true;
        }
        if (editorOpen && editingGroup() && x >= 66 && x < 374 && y >= GROUP_TOP && y < GROUP_TOP + groupRows() * GROUP_ROW_H) {
            groupScroll = clampScroll(groupScroll - (int) Math.signum(delta), groupItems().size(), groupRows());
            return true;
        }
        if (colorOpen || pickerOpen || editorOpen || namePreviewOpen || typePickerOpen) return true;
        if (y >= LIST_TOP && y < LIST_BOTTOM) {
            if (x >= MACHINE_X && x < MACHINE_X + MACHINE_W) {
                machineScroll = clampScroll(machineScroll - (int) Math.signum(delta), machines().size(), rows);
                return true;
            }
            if (x >= RECIPE_X && x < RECIPE_X + RECIPE_W) {
                recipeScroll = clampScroll(recipeScroll - (int) Math.signum(delta), recipes().size(), rows);
                return true;
            }
        }
        if (x >= REQUIREMENT_X && x < REQUIREMENT_X + REQUIREMENT_W
                && y >= INPUT_TOP && y < OUTPUT_TOP + SIDE_ROWS * ROW_HEIGHT) {
            boolean output = y >= OUTPUT_TOP;
            if (output) outputScroll = clampScroll(outputScroll - (int) Math.signum(delta), sideIndices(true).size(), SIDE_ROWS);
            else inputScroll = clampScroll(inputScroll - (int) Math.signum(delta), sideIndices(false).size(), SIDE_ROWS);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(adapterOpen){if(!menu.saves().pending())super.mouseClicked(mouseX,mouseY,button);return true;}
        if (copyOpen) { if (!menu.saves().pending()) super.mouseClicked(mouseX, mouseY, button); return true; }
        if (colorOpen) { super.mouseClicked(mouseX, mouseY, button); return true; }
        if (menu.saves().pending()) return true;
        if (batchOpen || triggerOpen) { super.mouseClicked(mouseX, mouseY, button); return true; }
        if (recipeOptionsOpen) {
            if (button == 0 && recipeOptionsPanel.click((int) mouseX - leftPos, (int) mouseY - topPos)) return true;
            super.mouseClicked(mouseX, mouseY, button); return true;
        }
        if (nbtOpen) { super.mouseClicked(mouseX,mouseY,button); return true; }
        if (resourceOpen) { super.mouseClicked(mouseX, mouseY, button); return true; }
        if (environmentOpen) { super.mouseClicked(mouseX,mouseY,button); return true; }
        if (pneumaticOpen) { super.mouseClicked(mouseX, mouseY, button); return true; }
        if (smartOpen) {
            if (button == 0 && smartPanel.click((int) mouseX - leftPos, (int) mouseY - topPos)) return true;
            super.mouseClicked(mouseX, mouseY, button); return true;
        }
        if (selectorOpen) {
            if (button == 0 && selectorPanel.click((int) mouseX - leftPos, (int) mouseY - topPos)) return true;
            super.mouseClicked(mouseX, mouseY, button); return true;
        }
        if (catalystTooltipsOpen) {
            if (button == 0 && catalystTooltipPanel.click((int) mouseX - leftPos, (int) mouseY - topPos)) return true;
            super.mouseClicked(mouseX, mouseY, button); return true;
        }
        if (modifiersOpen) {
            if (button == 0 && modifierPanel.click((int) mouseX - leftPos, (int) mouseY - topPos)) return true;
            super.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        String previousRecipeId = lastClickedRecipeId;
        long previousClickTime = lastRecipeClickTime;
        lastClickedRecipeId = null;
        if (namePreviewOpen) {
            super.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (button == 1 && !pickerOpen && !editorOpen && !typePickerOpen) {
            int selectorX = (int) mouseX - leftPos, selectorY = (int) mouseY - topPos;
            boolean output = selectorY >= OUTPUT_TOP;
            int top = output ? OUTPUT_TOP : INPUT_TOP;
            if (selectorX >= REQUIREMENT_X && selectorX < REQUIREMENT_X + REQUIREMENT_W - 18
                    && selectorY >= top && selectorY < top + SIDE_ROWS * ROW_HEIGHT) {
                var indices = sideIndices(output);
                int index = (output ? outputScroll : inputScroll) + (selectorY - top) / ROW_HEIGHT;
                if (index < indices.size()) {
                    if (hasControlDown()) { requirementIndex = indices.get(index); openRequirementTrigger(); }
                    else if (hasShiftDown()) { requirementIndex = indices.get(index); openRequirementBatch(); }
                    else openSelectorBinding(indices.get(index));
                }
                return true;
            }
        }
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (typePickerOpen) {
            super.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        int x = (int) mouseX - leftPos;
        int y = (int) mouseY - topPos;

        if(templateDraft!=null&&!pickerOpen&&!editorOpen&&!typePickerOpen&&x<REQUIREMENT_X){super.mouseClicked(mouseX,mouseY,button);return true;}
        if (pickerOpen) {
            if (x >= PICKER_X + 8 && x < PICKER_X + PICKER_W - 8 && y >= PICKER_Y + PICKER_LIST_Y
                    && y < PICKER_Y + PICKER_LIST_Y + PICKER_ROWS * PICKER_ROW_H) {
                int row = (y - PICKER_Y - PICKER_LIST_Y) / PICKER_ROW_H;
                List<ResourceLocation> items = pickerItems();
                int index = pickerScroll + row;
                if (index >= 0 && index < items.size()) {
                    if(pickerInventory)selectInventoryStack(inventoryChoices.get(index).copy());
                    else selectPickerResource((pickerTags ? "tag:" : "") + items.get(index));
                    return true;
                }
            }
            if (x < PICKER_X || x >= PICKER_X + PICKER_W || y < PICKER_Y || y >= PICKER_Y + PICKER_H) return true;
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (editorOpen && editingGroup() && x >= 66 && x < 374 && y >= GROUP_TOP && y < GROUP_TOP + groupRows() * GROUP_ROW_H) {
            int index = groupScroll + (y - GROUP_TOP) / GROUP_ROW_H;
            if (index < groupItems().size()) {
                if (x >= 350) {
                    if (index != groupIndex && !storeGroupAmount()) return true;
                    groupItems().remove(index);
                    if (index < groupIndex) groupIndex--;
                    selectGroupAmount();
                } else if (index != groupIndex && storeGroupAmount()) {
                    groupIndex = index;
                    selectGroupAmount();
                }
            }
            return true;
        }
        if (editorOpen) return super.mouseClicked(mouseX, mouseY, button);

        if (y >= LIST_TOP && y < LIST_BOTTOM) {
            int row = (y - LIST_TOP) / ROW_HEIGHT;
            if (x >= MACHINE_X && x < MACHINE_X + MACHINE_W) {
                int index = machineScroll + row;
                if (index < machines().size()) {
                    if (x >= MACHINE_X + MACHINE_W - 18) { deleteMachine(index); return true; }
                    machineIndex = index;
                    recipeIndex = requirementIndex = 0;
                    recipeScroll = inputScroll = outputScroll = 0;
                    syncFields();
                    syncMachineName();
                    return true;
                }
            } else if (x >= RECIPE_X && x < RECIPE_X + RECIPE_W) {
                int index = recipeScroll + row;
                if (index < recipes().size()) {
                    JsonObject clickedRecipe = recipes().get(index).getAsJsonObject();
                    if (x >= RECIPE_X + RECIPE_W - 18 && !isReadOnly(clickedRecipe)) {
                        recipeIndex = index;
                        deleteRecipe();
                    } else {
                        String clickedId = clickedRecipe.get("name").getAsString();
                        long now = net.minecraft.Util.getMillis();
                        boolean doubleClick = recipeIndex == index && clickedId.equals(previousRecipeId)
                                && now >= previousClickTime && now - previousClickTime < 250;
                        if (recipeIndex != index) requirementIndex = inputScroll = outputScroll = 0;
                        recipeIndex = index;
                        syncFields();
                        if (doubleClick) openRecipeEditor();
                        else {
                            lastClickedRecipeId = clickedId;
                            lastRecipeClickTime = now;
                        }
                    }
                    return true;
                }
            }
        }
        if (x >= REQUIREMENT_X && x < REQUIREMENT_X + REQUIREMENT_W) {
            if (y >= INPUT_TOP && y < INPUT_TOP + SIDE_ROWS * ROW_HEIGHT) {
                List<Integer> indices = sideIndices(false);
                int index = inputScroll + (y - INPUT_TOP) / ROW_HEIGHT;
                if (index < indices.size()) {
                    requirementIndex = indices.get(index);
                    if (x >= REQUIREMENT_X + REQUIREMENT_W - 18) deleteRequirement();
                    else openRequirementEditor(requirementIndex);
                    return true;
                }
            } else if (y >= OUTPUT_TOP && y < OUTPUT_TOP + SIDE_ROWS * ROW_HEIGHT) {
                List<Integer> indices = sideIndices(true);
                int index = outputScroll + (y - OUTPUT_TOP) / ROW_HEIGHT;
                if (index < indices.size()) {
                    requirementIndex = indices.get(index);
                    if (x >= REQUIREMENT_X + REQUIREMENT_W - 18) deleteRequirement();
                    else openRequirementEditor(requirementIndex);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xEE20242A);
        graphics.fill(leftPos + MACHINE_X, topPos + 18, leftPos + MACHINE_X + MACHINE_W, topPos + LIST_BOTTOM + 2, 0xFF30363D);
        graphics.fill(leftPos + RECIPE_X, topPos + 18, leftPos + RECIPE_X + RECIPE_W, topPos + LIST_BOTTOM + 2, 0xFF30363D);
        graphics.fill(leftPos + REQUIREMENT_X, topPos + 18, leftPos + REQUIREMENT_X + REQUIREMENT_W,
                topPos + REQUIREMENT_BOTTOM, 0xFF30363D);
        graphics.fill(leftPos + 6, topPos + 213, leftPos + imageWidth - 6, topPos + 214, 0xFF59616B);
        // renderBg uses screen coordinates, whereas overlay geometry is relative
        // to the container. Apply the same origin as the input widgets and hit tests.
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos, topPos, 0);
        if (pickerOpen) drawPickerOverlay(graphics);
        if (editorOpen) drawEditorOverlay(graphics);
        if (namePreviewOpen) drawNamePreviewOverlay(graphics);
        if (colorOpen) controllerAppearance.draw(graphics, saveStatus, saveFailed);
        if (typePickerOpen) drawTypePickerOverlay(graphics);
        if (modifiersOpen) modifierPanel.draw(graphics);
        if (catalystTooltipsOpen) catalystTooltipPanel.draw(graphics);
        if (batchOpen) batchPanel.draw(graphics, saveStatus, saveFailed);
        if (triggerOpen) triggerPanel.draw(graphics, saveStatus, saveFailed);
        if (selectorOpen) selectorPanel.draw(graphics, saveStatus, saveFailed);
        if (nbtOpen) nbtPanel.draw(graphics, saveStatus, saveFailed);
        if (resourceOpen) resourcePanel.draw(graphics, saveStatus, saveFailed);
        if (environmentOpen) environmentPanel.draw(graphics, saveStatus, saveFailed);
        if (pneumaticOpen) pneumaticPanel.draw(graphics, saveStatus, saveFailed);
        if (smartOpen) smartPanel.draw(graphics, saveStatus, saveFailed);
        if (recipeOptionsOpen) recipeOptionsPanel.draw(graphics);
        if (adapterOpen) adapterPanel.draw(graphics,saveStatus,saveFailed);
        if (copyOpen) copyPanel.draw(graphics, saveStatus, saveFailed);
        graphics.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (copyOpen || colorOpen || pickerOpen || editorOpen || namePreviewOpen || typePickerOpen || modifiersOpen || catalystTooltipsOpen || selectorOpen || batchOpen || triggerOpen || smartOpen || adapterOpen || environmentOpen || pneumaticOpen || nbtOpen || resourceOpen || recipeOptionsOpen) return;
        if(templateDraft!=null){graphics.drawString(font,Component.literal("导入模板 · 追加需求草稿"),8,6,0xFFFFFF,false);graphics.drawString(font,Component.literal("使用右侧 + 添加输入或输出"),12,32,0xDDDDDD,false);graphics.drawString(font,Component.literal("点击需求编辑；× 删除"),12,48,0xDDDDDD,false);graphics.drawString(font,Component.literal("完成后返回模板，保存并重新生成"),12,64,0xD9C98E,false);graphics.drawString(font,Component.literal("不会修改其他源配方或生成配方"),12,80,0xB5C5D5,false);graphics.drawString(font,Component.literal(trim(saveStatus,260)),12,112,saveFailed?0xFF8888:0x9BDEAA,false);drawRequirementRows(graphics);return;}
        graphics.drawString(font, Component.literal("机器"), MACHINE_X, 6, 0xFFFFFF, false);
        graphics.drawString(font, Component.literal("配方"), RECIPE_X, 6, 0xFFFFFF, false);
        graphics.drawString(font, Component.literal("需求"), REQUIREMENT_X, 6, 0xFFFFFF, false);
        drawMachineRows(graphics);
        drawRecipeRows(graphics);

        if (isReadOnly(recipe()))
            graphics.drawString(font, Component.literal("只读配方（悬停查看原因）"), RECIPE_X + 3, 190, 0xFF8ED6FF, false);
        drawRequirementRows(graphics);
        graphics.drawString(font, Component.literal("名称"), MACHINE_X, 218, 0xFFFFFF, false);
        graphics.drawString(font, Component.literal("颜色 &a  粗体 &l  重置 &r"), 224, 218, 0xFFB8C0C8, false);
        graphics.drawString(font, Component.literal(trim(saveStatus, RECIPE_W - 8)), RECIPE_X + 4, 203,
                saveFailed ? 0xFFFF7777 : 0xFF8EFF9C, false);
    }

    private void drawMachineRows(GuiGraphics graphics) {
        JsonArray all = machines();
        int rows = visibleRows();
        for (int row = 0; row < rows && machineScroll + row < all.size(); row++) {
            int index = machineScroll + row;
            int y = LIST_TOP + row * ROW_HEIGHT;
            if (index == machineIndex) graphics.fill(MACHINE_X + 2, y - 2, MACHINE_X + MACHINE_W - 2, y + 20, 0xFF5B4A28);
            JsonObject entry = all.get(index).getAsJsonObject();
            String display = entry.has("localizedName") ? entry.get("localizedName").getAsString() : entry.get("name").getAsString();
            graphics.drawString(font, Component.literal(trim(display, MACHINE_W - 12)), MACHINE_X + 7, y + 1,
                    index == machineIndex ? 0xFFFFD36A : 0xFFFFFF, false);
            graphics.drawString(font, Component.literal(trim(entry.get("name").getAsString(), MACHINE_W - 12)),
                    MACHINE_X + 7, y + 12, 0xFF9DA5AE, false);
            graphics.drawString(font, Component.literal("×"), MACHINE_X + MACHINE_W - 14, y + 4, 0xFFFF5555, false);
        }
        drawScrollHint(graphics, all.size(), rows, machineScroll, MACHINE_X + MACHINE_W - 16, LIST_BOTTOM - 13);
    }

    private void drawRecipeRows(GuiGraphics graphics) {
        JsonArray all = recipes();
        int rows = visibleRows();
        for (int row = 0; row < rows && recipeScroll + row < all.size(); row++) {
            int index = recipeScroll + row;
            int y = LIST_TOP + row * ROW_HEIGHT;
            if (index == recipeIndex) graphics.fill(RECIPE_X + 2, y - 2, RECIPE_X + RECIPE_W - 2, y + 20, 0xFF5B4A28);
            JsonObject entry = all.get(index).getAsJsonObject();
            String display = entry.has("displayName") && !entry.get("displayName").getAsString().isBlank()
                    ? entry.get("displayName").getAsString() : entry.get("name").getAsString();
            graphics.drawString(font, Component.literal(trim(display, RECIPE_W - 30)),
                    RECIPE_X + 7, y + 1, index == recipeIndex ? 0xFFFFD36A : 0xFFFFFF, false);
            graphics.drawString(font, Component.literal(trim(entry.get("name").getAsString(), RECIPE_W - 30)),
                    RECIPE_X + 7, y + 12, 0xFF9DA5AE, false);
            if (isReadOnly(entry))
                graphics.drawString(font, Component.literal("只读"), RECIPE_X + RECIPE_W - 23, y + 4, 0xFF8ED6FF, false);
            else
                graphics.drawString(font, Component.literal("×"), RECIPE_X + RECIPE_W - 14, y + 4, 0xFFFF5555, false);
        }
        drawScrollHint(graphics, all.size(), rows, recipeScroll, RECIPE_X + RECIPE_W - 16, LIST_BOTTOM - 13);
    }

    private void drawRequirementRows(GuiGraphics graphics) {
        graphics.drawString(font, Component.literal("输入"), REQUIREMENT_X + 3, INPUT_HEADER_Y, 0xFF8ED6FF, false);
        graphics.drawString(font, Component.literal("输出"), REQUIREMENT_X + 3, OUTPUT_HEADER_Y, 0xFF8EFF9C, false);
        String ticks = recipe() != null && recipe().has("duration") ? recipe().get("duration").getAsString() : "?";
        graphics.drawString(font, Component.literal(templateDraft!=null?"追加需求":"→ " + ticks + "t"), REQUIREMENT_X + 54, 104, 0xFFFFD36A, false);
        drawRequirementSide(graphics, sideIndices(false), INPUT_TOP, inputScroll);
        drawRequirementSide(graphics, sideIndices(true), OUTPUT_TOP, outputScroll);
    }

    private void drawRequirementSide(GuiGraphics graphics, java.util.List<Integer> indices, int top, int scroll) {
        JsonArray all = recipeRequirements();
        for (int row = 0; row < SIDE_ROWS && scroll + row < indices.size(); row++) {
            int index = indices.get(scroll + row);
            int y = top + row * ROW_HEIGHT;
            if (index == requirementIndex) graphics.fill(REQUIREMENT_X + 2, y - 2,
                    REQUIREMENT_X + REQUIREMENT_W - 2, y + 19, 0xFF5B4A28);
            JsonObject req = all.get(index).getAsJsonObject();
            if (isEnergy(req)) {
                graphics.fill(REQUIREMENT_X + 6, y + 1, REQUIREMENT_X + 25, y + 19, 0xFF8B6B25);
                graphics.drawString(font, Component.literal("FE"), REQUIREMENT_X + 7, y + 5, 0xFFFFFF, false);
            } else {
                ItemStack stack = stackFor(req);
                if (isFluid(req)) drawFluidIcon(graphics, ResourceLocation.tryParse(req.get("fluid").getAsString()),
                        REQUIREMENT_X + 5, y + 1);
                else if (!stack.isEmpty()) graphics.renderItem(stack, REQUIREMENT_X + 5, y - 1);
            }
            String key = IngredientGroupEditor.isGroup(req) ? (CatalystEditor.isCatalyst(req) ? "催化剂（" : "output".equals(req.get("io-type").getAsString()) ? "随机物品组（" : "候选物品组（") + req.getAsJsonArray("items").size() + " 项）"
                    : req.has("item") ? req.get("item").getAsString()
                    : req.has("fluid") ? req.get("fluid").getAsString() : "FE";
            String value = req.has("energyPerTick") ? req.get("energyPerTick").getAsString() + " FE/t"
                    : (req.has("amount") ? (isFluid(req) ? req.get("amount").getAsString() + " mB"
                    : "x" + req.get("amount").getAsString()) : "");
            if (IngredientGroupEditor.isGroup(req)) value = CatalystEditor.isCatalyst(req) ? "可选输入" : "output".equals(req.get("io-type").getAsString()) ? "随机产出" : "任选一项";
            if (DurabilityEditor.isDurability(req)) value = "使用 " + req.get("amount").getAsString()
                    + " 次 · -" + req.get("consumeDurability").getAsString();
            if (FuelEditor.isFuel(req)) value = req.get("time").getAsString() + " 燃烧 tick";
            if (FluidTickEditor.isRate(req)) value = req.get("amount").getAsString() + " mB/t";
            if (SmartInterfaceEditor.isSmart(req)) value = "[" + req.get("min").getAsString() + ", "
                    + (req.has("max") ? req.get("max").getAsString() : req.get("min").getAsString()) + "]";
            if (PneumaticEditor.isPneumatic(req)) {
                try {
                    var clean = PneumaticEditor.validate(req);
                    value = PneumaticEditor.isTemperature(clean) ? clean.get("min").getAsString() + "～" + clean.get("max").getAsString() + " K"
                            : clean.get("air").getAsString() + (PneumaticEditor.perTick(clean) ? " mL/t" : " mL")
                            + ("input".equals(clean.get("io-type").getAsString()) ? " · " + clean.get("pressure").getAsString() + " bar" : "");
                } catch (RuntimeException invalid) { value = "高级定义，请检查源文件"; }
            }
            if (ResourceAmountEditor.isResource(req)) {
                try {
                    var clean = ResourceAmountEditor.validate(req);
                    value = ResourceAmountEditor.amountText(clean);
                } catch (RuntimeException invalid) { value = "高级定义，请检查源文件"; }
            }
            String label = ItemTagSelection.isTag(req) ? "#" + key.substring(4) : key;
            if (EnvironmentEditor.isEnvironment(req)) { label = "环境 · " + EnvironmentEditor.label(req); try { value = EnvironmentEditor.summary(req); } catch (RuntimeException invalid) { value = "条件无效，请检查源文件"; } }
            if (ResourceAmountEditor.isResource(req)) label = ResourceAmountEditor.label(req);
            if (PneumaticEditor.isPneumatic(req)) label = PneumaticEditor.isTemperature(req) ? "持续温度条件" : "压缩空气";
            if (FuelEditor.isFuel(req)) label = "熔炉燃料（任意）";
            if (SmartInterfaceEditor.isSmart(req)) label = "智能接口：" + req.get("interface").getAsString();
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (isFluid(req) && id != null && BuiltInRegistries.FLUID.containsKey(id))
                label = new net.minecraftforge.fluids.FluidStack(BuiltInRegistries.FLUID.get(id), 1000).getDisplayName().getString();
            else if (req.has("item") && id != null && BuiltInRegistries.ITEM.containsKey(id))
                label = BuiltInRegistries.ITEM.get(id).getDefaultInstance().getHoverName().getString();
            int textWidth = REQUIREMENT_W - (req.has("selector-tag") ? 60 : 48);
            graphics.drawString(font, Component.literal(trim(label, textWidth)),
                    REQUIREMENT_X + 29, y, index == requirementIndex ? 0xFFFFD36A : 0xFFFFFF, false);
            String probability = req.has("chance") && req.get("chance").getAsFloat() != 1F
                    ? "  " + Math.round(req.get("chance").getAsFloat() * 100) + "%" : "";
            graphics.drawString(font, Component.literal(trim(value + probability, textWidth)),
                    REQUIREMENT_X + 29, y + 11, 0xFFB8C0C8, false);
            if (req.has("selector-tag")) graphics.drawString(font, Component.literal("组"),
                    REQUIREMENT_X + REQUIREMENT_W - 28, y + 4, 0xFF8ED6FF, false);
            JsonObject selectedRecipe = recipe();
            if (selectedRecipe == null || !selectedRecipe.has("editable") || selectedRecipe.get("editable").getAsBoolean())
                graphics.drawString(font, Component.literal("×"), REQUIREMENT_X + REQUIREMENT_W - 14, y + 4, 0xFFFF5555, false);
        }
        if (indices.size() > SIDE_ROWS)
            graphics.drawString(font, Component.literal("↕"), REQUIREMENT_X + REQUIREMENT_W - 14, top + SIDE_ROWS * ROW_HEIGHT - 5, 0xFFB8C0C8, false);
    }

    private void drawPickerOverlay(GuiGraphics graphics) {
        graphics.fill(0, 0, WIDTH, HEIGHT, 0x99000000);
        graphics.fill(PICKER_X, PICKER_Y, PICKER_X + PICKER_W, PICKER_Y + PICKER_H, 0xFF252A31);
        graphics.fill(PICKER_X + 8, PICKER_Y + PICKER_LIST_Y - 2, PICKER_X + PICKER_W - 8,
                PICKER_Y + PICKER_H - 10, 0xFF30363D);
        graphics.drawString(font, Component.literal((pickerReplacing ? "更换" : "选择") + (pickerOutput ? "输出" : "输入") + (pickerInventory ? "背包物品" : pickerTags ? "物品标签" : pickerFluidTick ? "流体/刻" : pickerType.label)),
                PICKER_X + 10, PICKER_Y + 8, 0xFFFFFF, false);
        graphics.drawString(font, Component.literal(pickerTags ? "物品名 / 标签ID   @标签命名空间   #标签" : "名称 / ID   @模组ID   #标签"), PICKER_X + 8, PICKER_Y + 49, 0xFFB8C0C8, false);
        List<ResourceLocation> items = pickerItems();
        for (int row = 0; row < PICKER_ROWS && pickerScroll + row < items.size(); row++) {
            ResourceLocation id = items.get(pickerScroll + row);
            int y = PICKER_Y + PICKER_LIST_Y + row * PICKER_ROW_H;
            Component name;
            if (pickerTags) {
                graphics.renderItem(pickerDurability ? DurabilityEditor.representative("tag:" + id)
                        : ItemTagSelection.representative("tag:" + id), PICKER_X + 14, y + 2);
                name = Component.literal("#" + id);
            } else if (pickerType == BasicRequirementType.FLUID) {
                drawFluidIcon(graphics, id, PICKER_X + 14, y + 2);
                name = new net.minecraftforge.fluids.FluidStack(BuiltInRegistries.FLUID.get(id), 1000).getDisplayName();
            } else {
                ItemStack stack = pickerInventory ? inventoryChoices.get(pickerScroll+row) : new ItemStack(BuiltInRegistries.ITEM.get(id));
                graphics.renderItem(stack, PICKER_X + 14, y + 2);
                name = stack.getHoverName();
            }
            graphics.drawString(font, Component.literal(trim(name.getString(), PICKER_W - 48)), PICKER_X + 38, y + 1, 0xFFFFFF, false);
            String detail = pickerTags ? (pickerDurability
                    ? ItemTagSelection.members(id).stream().filter(DurabilityEditor::damageable).count() + " 种可扣耐久物品 · 图标仅示例"
                    : ItemTagSelection.members(id).size() + " 种物品 · 接受任意一种（图标仅示例）") : pickerInventory ? id + " · x" + inventoryChoices.get(pickerScroll+row).getCount() + (inventoryChoices.get(pickerScroll+row).hasTag()?" · NBT":"") : id.toString();
            graphics.drawString(font, Component.literal(trim(detail, PICKER_W - 48)), PICKER_X + 38, y + 12, 0xFFB8C0C8, false);
        }
        if (items.isEmpty()) graphics.drawString(font, Component.literal("没有匹配结果"), PICKER_X + 14, PICKER_Y + PICKER_LIST_Y + 8, 0xFFB8C0C8, false);
        if (items.size() > PICKER_ROWS)
            graphics.drawString(font, Component.literal((pickerScroll + 1) + "/" + Math.max(1, items.size() - PICKER_ROWS + 1)),
                    PICKER_X + PICKER_W - 52, PICKER_Y + PICKER_H - 8, 0xFFB8C0C8, false);
    }

    private void drawTypePickerOverlay(GuiGraphics graphics) {
        graphics.fill(0, 0, WIDTH, HEIGHT, 0x99000000);
        graphics.fill(88, 38, 352, 256, 0xFF252A31);
        graphics.drawString(font, Component.literal(pickerOutput ? "添加输出需求" : "添加输入需求"), 105, 50, 0xFFFFFF, false);
    }

    private void drawFluidIcon(GuiGraphics graphics, ResourceLocation id, int x, int y) {
        if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) return;
        var fluid = BuiltInRegistries.FLUID.get(id);
        var extension = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid);
        ResourceLocation still = extension.getStillTexture();
        if (still == null) return;
        var sprite = Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(still);
        int tint = extension.getTintColor();
        graphics.blit(x, y, 0, 16, 16, sprite, ((tint >> 16) & 255) / 255F,
                ((tint >> 8) & 255) / 255F, (tint & 255) / 255F, 1F);
    }

    private void drawEditorOverlay(GuiGraphics graphics) {
        if (editingGroup()) {
            drawGroupEditor(graphics);
            return;
        }
        if (editorRecipe) {
            graphics.fill(0, 0, WIDTH, HEIGHT, 0x99000000);
            graphics.fill(58, 18, 382, 238, 0xFF252A31);
            graphics.drawString(font, Component.literal("编辑配方"), 70, 29, 0xFFFFFF, false);
            if (recipe() != null) graphics.drawString(font, Component.literal(trim("ID：" +
                    recipe().get("name").getAsString(), 292)), 74, 47, 0xFF9DA5AE, false);
            graphics.drawString(font, Component.literal("显示名称"), 74, 61, 0xFFFFFF, false);
            graphics.drawString(font, Component.literal("运行时间(t)"), 74, 120, 0xFFFFFF, false);
            if (!isNewRecipe(recipe()) && recipeOptionsDraft != null) graphics.drawString(font,
                    Component.literal(trim("优先级 " + recipeOptionsDraft.priority() + " · 批量" + (recipeOptionsDraft.parallelized() ? "开" : "关")
                            + " · JEI " + (recipeOptionsDraft.loadJEI() ? "显示" : "隐藏"), 292)), 74, 180, 0xFF8ED6FF, false);
            else graphics.drawString(font, Component.literal("颜色 &a  粗体 &l  重置 &r"), 74, 180, 0xFFB8C0C8, false);
            graphics.drawString(font, Component.literal(trim(saveStatus.isBlank() ? "留空恢复原 ID；名称不影响配方匹配" : saveStatus, 292)),
                    74, 194, saveFailed ? 0xFFFF7777 : 0xFFB8C0C8, false);
            return;
        }
        graphics.fill(0, 0, WIDTH, HEIGHT, 0x99000000);
        graphics.fill(70, 24, 370, 235, 0xFF252A31);
        graphics.drawString(font, Component.literal((addingRequirement ? "添加" : "编辑")
                + (durabilityRequirement() ? "耐久需求" : fuelRequirement() ? "燃料需求" : FluidTickEditor.isRate(requirement()) ? "持续流体需求" : "需求")), 82, 35, 0xFFFFFF, false);
        JsonObject req = requirement();
        String key = RequirementResourceSelection.key(req);
        String id = fuelRequirement() ? "any:fuel" : key.isEmpty() ? "能量 FE" : req.has(key) ? req.get(key).getAsString() : "";
        Component display = Component.literal(id);
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (isFluid(req)) {
            drawFluidIcon(graphics, location, 84, 58);
            if (location != null && BuiltInRegistries.FLUID.containsKey(location))
                display = new net.minecraftforge.fluids.FluidStack(BuiltInRegistries.FLUID.get(location), 1000).getDisplayName();
        } else if (isEnergy(req)) {
            graphics.drawString(font, Component.literal("FE"), 84, 60, 0xFFFFD36A, false);
        } else {
            ItemStack stack = stackFor(req);
            if (!stack.isEmpty()) { graphics.renderItem(stack, 84, 58); display = stack.getHoverName(); }
            if (ItemTagSelection.isTag(req)) display = Component.literal("物品标签 #" + id.substring(4));
            if (fuelRequirement()) display = Component.literal("任意熔炉燃料");
        }
        graphics.drawString(font, Component.literal(trim(display.getString(), 170)), 108, 54, 0xFFFFFF, false);
        graphics.drawString(font, Component.literal(trim(id, 170)), 108, 68, 0xFF9DA5AE, false);
        if (durabilityRequirement()) {
            graphics.drawString(font, Component.literal("使用次数"), 108, 105, 0xFFFFFF, false);
            graphics.drawString(font, Component.literal("每次扣耐久"), 108, 133, 0xFFFFFF, false);
            graphics.drawString(font, Component.literal("扣耐久概率 0～1"), 108, 161, 0xFFFFFF, false);
            graphics.drawString(font, Component.literal(ItemTagSelection.isTag(req)
                    ? "仅接受可扣耐久成员；图标仅为示例" : "按使用次数扣耐久，不按件消耗"), 82, 87, 0xFF8ED6FF, false);
        } else if (fuelRequirement()) {
            graphics.drawString(font, Component.literal("燃烧时间(t)"), 108, 108, 0xFFFFFF, false);
            graphics.drawString(font, Component.literal("消耗概率 0～1"), 108, 147, 0xFFFFFF, false);
            graphics.drawString(font, Component.literal("接受熔炉燃料；图标仅为示例"), 82, 87, 0xFF8ED6FF, false);
        } else {
            graphics.drawString(font, Component.literal(isEnergy(req) ? "FE/t" : FluidTickEditor.isRate(req) ? "mB/t" : isFluid(req) ? "流体(mB)" : "数量(个)"), 142, 108, 0xFFFFFF, false);
            if (!isEnergy(req) && !FluidTickEditor.isRate(req)) graphics.drawString(font, Component.literal("概率 0～1"), 142, 147, 0xFFFFFF, false);
            if (FluidTickEditor.isRate(req)) {
                graphics.drawString(font, Component.literal("随配方进度持续" + ("output".equals(req.get("io-type").getAsString()) ? "产出" : "消耗")), 82, 87, 0xFF8ED6FF, false);
                graphics.drawString(font, Component.literal("速率通常随批量放大；暂停时不处理"), 82, 139, 0xFFB8C0C8, false);
                graphics.drawString(font, Component.literal("1 tick = 1/20 秒；不设置概率"), 82, 153, 0xFFB8C0C8, false);
            }
            if (ItemTagSelection.isTag(req)) graphics.drawString(font, Component.literal("接受标签内任意物品；图标仅为示例"), 82, 87, 0xFF8ED6FF, false);
        }
        graphics.drawString(font, Component.literal(trim(saveStatus.isBlank() ? "点击保存后生效；取消放弃本次修改" : saveStatus, 276)),
                82, 213, saveFailed ? 0xFFFF7777 : 0xFFB8C0C8, false);
    }

    private void drawGroupEditor(GuiGraphics graphics) {
        boolean output = outputGroup();
        graphics.fill(0, 0, WIDTH, HEIGHT, 0x99000000);
        graphics.fill(58, 18, 382, 248, 0xFF252A31);
        graphics.drawString(font, Component.literal(output ? "随机物品组 · 输出" : catalystGroup() ? "催化剂 · 可选输入" : "候选物品组 · 输入"), 70, 29, 0xFFFFFF, false);
        graphics.drawString(font, Component.literal(output ? "先判定整组产出概率，再按权重抽取一种" : catalystGroup()
                ? "存在时生效；概率控制消耗，缺少仍可加工" : "按顺序选择能付清的一项；每项数量独立"), 70, 50, 0xFFB8C0C8, false);
        graphics.fill(66, GROUP_TOP - 2, 374, GROUP_TOP + groupRows() * GROUP_ROW_H, 0xFF30363D);
        for (int row = 0; row < groupRows() && groupScroll + row < groupItems().size(); row++) {
            int index = groupScroll + row, y = GROUP_TOP + row * GROUP_ROW_H;
            var candidate = groupItems().get(index).getAsJsonObject();
            if (index == groupIndex) graphics.fill(68, y, 372, y + GROUP_ROW_H, 0xFF5B4A28);
            var stack = stackFor(candidate);
            if (!stack.isEmpty()) graphics.renderItem(stack, 74, y + 3);
            String id = candidate.get("item").getAsString();
            String label = ItemTagSelection.isTag(candidate) ? "#" + id.substring(4) : stack.isEmpty() ? id : stack.getHoverName().getString();
            graphics.drawString(font, Component.literal(trim((index + 1) + ". " + label, 244)), 96, y + 2, 0xFFFFFF, false);
            String detail = output ? candidate.get("minAmount").getAsString() + "～" + candidate.get("maxAmount").getAsString()
                    + " 个  权重 " + candidate.get("chance").getAsString() : "×" + candidate.get("amount").getAsString() + "  " + id;
            graphics.drawString(font, Component.literal(trim(detail, 244)),
                    96, y + 13, 0xFFB8C0C8, false);
            graphics.drawString(font, Component.literal("×"), 355, y + 8, 0xFFFF5555, false);
        }
        if (groupItems().isEmpty()) graphics.drawString(font, Component.literal(output ? "点击上方 + 物品添加候选产物" : "点击上方 + 物品或 + 标签添加候选项"), 74, 88, 0xFFB8C0C8, false);
        if (output) {
            graphics.drawString(font, Component.literal("最少"), 70, 151, 0xFFFFFF, false);
            graphics.drawString(font, Component.literal("最多"), 150, 151, 0xFFFFFF, false);
            graphics.drawString(font, Component.literal("权重"), 232, 151, 0xFFFFFF, false);
            graphics.drawString(font, Component.literal("产出概率"), 70, 175, 0xFFFFFF, false);
            String share = "权重 0 不参与抽取";
            if (!groupItems().isEmpty()) {
                try {
                    float weight = Float.parseFloat(groupWeight.getValue());
                    share = Float.isFinite(weight) && weight >= 0 ? "抽中占比 " + String.format(Locale.ROOT, "%.2f%%",
                            IngredientGroupEditor.selectionShare(requirement(), groupIndex, weight) * 100) : "请填写有效权重";
                } catch (NumberFormatException invalid) { share = "请填写有效权重"; }
            }
            graphics.drawString(font, Component.literal(share), 210, 175, 0xFF8ED6FF, false);
        } else {
            graphics.drawString(font, Component.literal("选中项数量"), 70, 172, 0xFFFFFF, false);
            graphics.drawString(font, Component.literal("消耗概率 0～1"), 218, 172, 0xFFFFFF, false);
        }
        graphics.drawString(font, Component.literal((groupIndex + (groupItems().isEmpty() ? 0 : 1)) + "/" + groupItems().size()
                + (groupItems().size() > groupRows() ? "  滚轮翻页" : output ? "  权重抽取" : "  上方优先")), 224, 195, 0xFFB8C0C8, false);
        graphics.drawString(font, Component.literal(trim(saveStatus.isBlank() ? "保存全部候选项；取消放弃本次修改" : saveStatus, 296)),
                70, 234, saveFailed ? 0xFFFF7777 : 0xFFB8C0C8, false);
    }

    private void drawNamePreviewOverlay(GuiGraphics graphics) {
        graphics.fill(0, 0, WIDTH, HEIGHT, 0x99000000);
        graphics.fill(58, 38, 382, 238, 0xFF252A31);
        graphics.drawString(font, Component.literal(namePreviewRecipe ? "配方名称预览" : "名称预览"), 70, 50, 0xFFFFFF, false);
        graphics.fill(66, 72, 374, 188, 0xFF30363D);
        String name = MachineNameFormatting.display(namePreviewRecipe ? recipeDisplayName.getValue() : localizedName.getValue());
        if (namePreviewRecipe && name.isBlank() && recipe() != null) name = recipe().get("name").getAsString();
        var lines = font.split(Component.literal(name.isBlank() ? "（名称为空）" : name), 292);
        int y = 130 - lines.size() * (font.lineHeight + 2) / 2;
        for (var line : lines) {
            graphics.drawString(font, line, (WIDTH - font.width(line)) / 2, y, 0xFFFFFF, false);
            y += font.lineHeight + 2;
        }
        graphics.drawString(font, Component.literal(namePreviewRecipe ? "仅预览，返回后点击“保存”生效" :
                "仅预览，点击“保存名称”后生效"), 74, 195, 0xFFB8C0C8, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (menu.saves().pending()) {
            if (keyCode == 256) onClose();
            return true;
        }
        if (colorOpen && keyCode == 256) { closeControllerAppearance(); return true; }
        if(adapterOpen&&keyCode==256){closeAdapterTemplates();return true;}
        if (copyOpen && keyCode == 256) { closeRecipeCopy(); return true; }
        if (namePreviewOpen && keyCode == 256) {
            closeNamePreview();
            return true;
        }
        if (keyCode == 256) {
            if (nbtOpen) { closeRequirementNbt(); return true; }
            if (triggerOpen) { closeRequirementTrigger(); return true; }
            if (batchOpen) { closeRequirementBatch(); return true; }
            if (recipeOptionsOpen) { if (!recipeOptionsPanel.escape()) closeRecipeOptions(); return true; }
            if (resourceOpen) { closeResourceAmount(); return true; }
            if (environmentOpen) { closeEnvironment(); return true; }
            if (pneumaticOpen) { closePneumatic(); return true; }
            if (smartOpen) { closeSmartInterface(); return true; }
            if (selectorOpen) { closeSelectorBinding(); return true; }
            if (catalystTooltipsOpen) { if (!catalystTooltipPanel.escape()) closeCatalystTooltips(); return true; }
            if (modifiersOpen) { closeCatalystModifiers(); return true; }
            if (typePickerOpen) { closeTypePicker(); return true; }
            if (pickerOpen) { closePicker(); return true; }
            if (editorOpen) { closeEditor(); return true; }
        }
        // Multiline editing must consume inventory/hotbar shortcuts while typing NBT.
        if ((nbtOpen || adapterOpen || environmentOpen) && keyCode != 258) {
            if (getFocused() != null) getFocused().keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void drawScrollHint(GuiGraphics graphics, int size, int rows, int scroll, int x, int y) {
        if (size > rows) {
            graphics.drawString(font, Component.literal("↕"), x, y, 0xFFB8C0C8, false);
            graphics.drawString(font, Component.literal((scroll + 1) + "/" + Math.max(1, size - rows + 1)),
                    x - 24, y, 0xFFB8C0C8, false);
        }
    }

    private String trim(String value, int width) {
        if (font.width(value) <= width) return value;
        return font.plainSubstrByWidth(value, Math.max(1, width - font.width("..."))) + "...";
    }

    private static ItemStack stackFor(JsonObject req) {
        if (EnvironmentEditor.isEnvironment(req)) return new ItemStack(hellfirepvp.modularmachinery.common.registry.RegistryBlocks.environmentDetector.get());
        if (ResourceAmountEditor.isResource(req)) {
            var id = ResourceLocation.tryParse(ResourceAmountEditor.isMana(req) ? "botania:mana_pearl" : ResourceAmountEditor.isEmber(req) ? "embers:ember_shard" : ResourceAmountEditor.isLife(req) ? "bloodmagic:weakbloodorb" : ResourceAmountEditor.isWill(req) ? "bloodmagic:basemonstersoul" : "naturesaura:aura_bottle");
            return new ItemStack(BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id) : net.minecraft.world.item.Items.BLAZE_POWDER);
        }
        if (PneumaticEditor.isTemperature(req)) return new ItemStack(net.minecraft.world.item.Items.BLAZE_POWDER);
        if (PneumaticEditor.isAir(req)) return new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE);
        if (SmartInterfaceEditor.isSmart(req)) return new ItemStack(net.minecraft.world.item.Items.COMPARATOR);
        if (FuelEditor.isFuel(req)) return new ItemStack(net.minecraft.world.item.Items.COAL);
        if (IngredientGroupEditor.isGroup(req) && req.has("items") && !req.getAsJsonArray("items").isEmpty())
            return stackFor(req.getAsJsonArray("items").get(0).getAsJsonObject());
        if (ItemTagSelection.isTag(req)) return DurabilityEditor.isDurability(req)
                ? DurabilityEditor.representative(req.get("item").getAsString()) : ItemTagSelection.representative(req.get("item").getAsString());
        String key = req.has("item") ? "item" : req.has("fluid") ? "fluid" : "";
        if (key.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(req.get(key).getAsString());
        if (id == null) return ItemStack.EMPTY;
        if ("item".equals(key)) {
            if (!BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
            return new ItemStack(BuiltInRegistries.ITEM.get(id));
        }
        net.minecraft.world.level.material.Fluid fluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(id);
        return fluid == null ? ItemStack.EMPTY : new ItemStack(fluid.getBucket());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if(adapterOpen){adapterPanel.tooltip(graphics,mouseX,mouseY,mouseX-leftPos,mouseY-topPos);return;}
        if (environmentOpen) { environmentPanel.tooltip(graphics,mouseX,mouseY,mouseX-leftPos,mouseY-topPos); return; }
        if (copyOpen) return;
        if (recipeOptionsOpen || batchOpen || triggerOpen) return;
        if (nbtOpen) { nbtPanel.tooltip(graphics,mouseX,mouseY,mouseX-leftPos,mouseY-topPos); return; }
        if (resourceOpen) { resourcePanel.tooltip(graphics, mouseX, mouseY, mouseX - leftPos, mouseY - topPos); return; }
        if (environmentOpen) { environmentPanel.tooltip(graphics,mouseX,mouseY,mouseX-leftPos,mouseY-topPos); return; }
        if (pneumaticOpen) { pneumaticPanel.tooltip(graphics, mouseX, mouseY, mouseX - leftPos, mouseY - topPos); return; }
        if (smartOpen) {
            smartPanel.tooltip(graphics, mouseX, mouseY, mouseX - leftPos, mouseY - topPos);
            return;
        }
        if (selectorOpen) {
            selectorPanel.tooltip(graphics, mouseX, mouseY, mouseX - leftPos, mouseY - topPos);
            return;
        }
        if(pickerOpen&&pickerInventory){int x=mouseX-leftPos,y=mouseY-topPos;if(x>=PICKER_X+8&&x<PICKER_X+PICKER_W-8&&y>=PICKER_Y+PICKER_LIST_Y&&y<PICKER_Y+PICKER_LIST_Y+PICKER_ROWS*PICKER_ROW_H){pickerItems();int n=pickerScroll+(y-PICKER_Y-PICKER_LIST_Y)/PICKER_ROW_H;if(n<inventoryChoices.size())graphics.renderTooltip(font,inventoryChoices.get(n),mouseX,mouseY);}return;}
        renderTagTooltip(graphics, mouseX, mouseY);
        renderGroupTooltip(graphics, mouseX, mouseY);
        renderDurabilityTooltip(graphics, mouseX, mouseY);
        renderFuelTooltip(graphics, mouseX, mouseY);
        renderFluidTickTooltip(graphics, mouseX, mouseY);
        renderSmartTooltip(graphics, mouseX, mouseY);
        renderPneumaticTooltip(graphics, mouseX, mouseY);
        renderResourceTooltip(graphics, mouseX, mouseY);
        renderSelectorTooltip(graphics, mouseX, mouseY);
        if (!colorOpen && !pickerOpen && !editorOpen && !namePreviewOpen && !typePickerOpen && !modifiersOpen && !catalystTooltipsOpen) {
            int x = mouseX - leftPos;
            int y = mouseY - topPos;
            if (templateDraft == null && !saveStatus.isBlank() && x >= RECIPE_X && x < RECIPE_X + RECIPE_W
                    && y >= 202 && y < 215) {
                graphics.renderTooltip(font, font.split(Component.literal(saveStatus), 280), mouseX, mouseY);
                return;
            }
            JsonObject hovered = null;
            if (x >= RECIPE_X && x < RECIPE_X + RECIPE_W) {
                if (y >= LIST_TOP && y < LIST_TOP + visibleRows() * ROW_HEIGHT) {
                    int index = recipeScroll + (y - LIST_TOP) / ROW_HEIGHT;
                    if (index < recipes().size()) hovered = recipes().get(index).getAsJsonObject();
                } else if (y >= 188 && y < 202 && isReadOnly(recipe())) hovered = recipe();
            }
            if (hovered != null) {
                List<Component> lines = new ArrayList<>();
                if (hovered.has("displayName") && !hovered.get("displayName").getAsString().isBlank())
                    lines.add(Component.literal(hovered.get("displayName").getAsString()));
                lines.add(Component.literal("ID: " + hovered.get("name").getAsString()));
                if (isReadOnly(hovered)) {
                    lines.add(Component.literal("只读：" + readOnlyReason(hovered)));
                    lines.add(Component.literal("生成配方没有独立 JSON，重载时会重新生成"));
                } else {
                    lines.add(Component.literal("单击选中；双击编辑名称和运行时间"));
                    if (isNewRecipe(hovered)) lines.add(Component.literal("草稿：保存首个需求后才写入文件"));
                }
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
        }
    }

    private void renderTagTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = mouseX - leftPos, y = mouseY - topPos;
        ResourceLocation tag = null;
        boolean durability = false;
        if (pickerOpen && pickerTags && x >= PICKER_X + 8 && x < PICKER_X + PICKER_W - 8
                && y >= PICKER_Y + PICKER_LIST_Y && y < PICKER_Y + PICKER_LIST_Y + PICKER_ROWS * PICKER_ROW_H) {
            int index = pickerScroll + (y - PICKER_Y - PICKER_LIST_Y) / PICKER_ROW_H;
            var items = pickerItems();
            if (index < items.size()) { tag = items.get(index); durability = pickerDurability; }
        } else if (editorOpen && !editorRecipe && ItemTagSelection.isTag(requirement())
                && x >= 82 && x < 280 && y >= 52 && y < 78) {
            tag = ItemTagSelection.id(requirement().get("item").getAsString());
            durability = durabilityRequirement();
        } else if (!colorOpen && !pickerOpen && !editorOpen && !namePreviewOpen && !typePickerOpen && !modifiersOpen && !catalystTooltipsOpen
                && x >= REQUIREMENT_X && x < REQUIREMENT_X + REQUIREMENT_W - 18) {
            boolean output = y >= OUTPUT_TOP;
            int row = (y - (output ? OUTPUT_TOP : INPUT_TOP)) / ROW_HEIGHT;
            if (y >= (output ? OUTPUT_TOP : INPUT_TOP) && row < SIDE_ROWS) {
                var indices = sideIndices(output);
                int index = (output ? outputScroll : inputScroll) + row;
                if (index < indices.size()) {
                    var req = recipeRequirements().get(indices.get(index)).getAsJsonObject();
                    if (ItemTagSelection.isTag(req) && !DurabilityEditor.isDurability(req)) tag = ItemTagSelection.id(req.get("item").getAsString());
                }
            }
        }
        if (tag == null) return;
        final boolean toolsOnly = durability;
        var members = ItemTagSelection.members(tag).stream().filter(item -> !toolsOnly || DurabilityEditor.damageable(item)).toList();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("#" + tag));
        lines.add(Component.literal((durability ? "接受可扣耐久的成员，共 " : "接受标签内任意物品，共 ") + members.size() + " 种；图标仅为示例"));
        members.stream().limit(5).forEach(item -> lines.add(item.getDefaultInstance().getHoverName()));
        if (members.size() > 5) lines.add(Component.literal("……"));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private void renderFluidTickTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (colorOpen || pickerOpen || editorOpen || namePreviewOpen || typePickerOpen || modifiersOpen || catalystTooltipsOpen || selectorOpen || batchOpen || triggerOpen || smartOpen || adapterOpen || environmentOpen || pneumaticOpen || nbtOpen || resourceOpen) return;
        int x = mouseX - leftPos, y = mouseY - topPos;
        if (x < REQUIREMENT_X || x >= REQUIREMENT_X + REQUIREMENT_W - 28) return;
        boolean output = y >= OUTPUT_TOP; int top = output ? OUTPUT_TOP : INPUT_TOP;
        if (y < top || y >= top + SIDE_ROWS * ROW_HEIGHT) return;
        var indices = sideIndices(output); int index = (output ? outputScroll : inputScroll) + (y - top) / ROW_HEIGHT;
        if (index >= indices.size()) return;
        var req = recipeRequirements().get(indices.get(index)).getAsJsonObject();
        if (!FluidTickEditor.isRate(req)) return;
        var lines = new ArrayList<Component>();
        lines.add(Component.literal("持续流体" + (output ? "输出：" : "输入：") + req.get("fluid").getAsString()));
        lines.add(Component.literal("速率 " + req.get("amount").getAsString() + " mB/t；每个配方进度 tick 处理一次"));
        lines.add(Component.literal("批量倍率通常放大速率；缺料或满仓时暂停，不设置概率"));
        lines.add(Component.literal(FluidTickEditor.canEdit(req) ? "单击修改速率或更换流体" : "包含 NBT 等高级条件，请修改源文件"));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private void renderPneumaticTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (copyOpen || colorOpen || pickerOpen || editorOpen || namePreviewOpen || typePickerOpen || modifiersOpen
                || catalystTooltipsOpen || selectorOpen || batchOpen || triggerOpen || smartOpen || adapterOpen || environmentOpen || pneumaticOpen || nbtOpen || resourceOpen || recipeOptionsOpen) return;
        int x = mouseX - leftPos, y = mouseY - topPos;
        if (x < REQUIREMENT_X || x >= REQUIREMENT_X + REQUIREMENT_W - 18) return;
        boolean output = y >= OUTPUT_TOP; int top = output ? OUTPUT_TOP : INPUT_TOP;
        if (y < top || y >= top + SIDE_ROWS * ROW_HEIGHT) return;
        var indices = sideIndices(output); int row = (output ? outputScroll : inputScroll) + (y - top) / ROW_HEIGHT;
        if (row >= indices.size()) return;
        var req = recipeRequirements().get(indices.get(row)).getAsJsonObject();
        if (!PneumaticEditor.isPneumatic(req)) return;
        var lines = new ArrayList<Component>();
        try {
            var clean = PneumaticEditor.validate(req);
            if (PneumaticEditor.isTemperature(clean)) {
                lines.add(Component.literal("持续温度：" + clean.get("min").getAsString() + "～" + clean.get("max").getAsString() + " K（两端包含）"));
                lines.add(Component.literal("开工和每个加工 tick 检查；不消耗温度，不随批量缩放"));
            } else {
                lines.add(Component.literal("压缩空气：" + clean.get("air").getAsString() + (PneumaticEditor.perTick(clean) ? " mL/t" : " mL")));
                if (!output) lines.add(Component.literal("压力：" + (clean.get("pressure").getAsFloat() < 0 ? "≤ " : "≥ ") + clean.get("pressure").getAsString() + " bar"));
                lines.add(Component.literal("批量仅放大空气用量；输入为 0 时仅检查压力"));
            }
            lines.add(Component.literal(isReadOnly(recipe()) ? "此配方只读，请修改其来源" : "单击修改；右键设置接口分组"));
        } catch (RuntimeException invalid) { lines.add(Component.literal("请检查源文件：" + invalid.getMessage())); }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private void renderResourceTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (copyOpen || colorOpen || pickerOpen || editorOpen || namePreviewOpen || typePickerOpen || modifiersOpen
                || catalystTooltipsOpen || selectorOpen || batchOpen || triggerOpen || smartOpen || adapterOpen || environmentOpen || pneumaticOpen || nbtOpen || resourceOpen || recipeOptionsOpen) return;
        int x = mouseX - leftPos, y = mouseY - topPos;
        if (x < REQUIREMENT_X || x >= REQUIREMENT_X + REQUIREMENT_W - 18) return;
        boolean output = y >= OUTPUT_TOP; int top = output ? OUTPUT_TOP : INPUT_TOP;
        if (y < top || y >= top + SIDE_ROWS * ROW_HEIGHT) return;
        var indices = sideIndices(output); int row = (output ? outputScroll : inputScroll) + (y - top) / ROW_HEIGHT;
        if (row >= indices.size()) return;
        var req = recipeRequirements().get(indices.get(row)).getAsJsonObject();
        if (!ResourceAmountEditor.isResource(req)) return;
        var lines = new ArrayList<Component>();
        try {
            var clean = ResourceAmountEditor.validate(req);
            lines.add(Component.literal(ResourceAmountEditor.label(clean) + "：" + ResourceAmountEditor.amountText(clean)));
            if (ResourceAmountEditor.isWill(clean)) {
                lines.add(Component.literal("意志类型：" + clean.get("will-type").getAsString() + "；整批只算一份"));
                lines.add(Component.literal(output ? "产出后至多 " + clean.get("max").getAsString() : "消耗后至少保留 " + clean.get("min").getAsString()));
            } else if (ResourceAmountEditor.isAura(clean)) {
                lines.add(Component.literal("灵气类型：" + clean.get("aura-type").getAsString()));
                lines.add(Component.literal(output ? "产出后至多 " + clean.get("max-aura").getAsString() : "消耗后至少保留 " + clean.get("min-aura").getAsString()));
                lines.add(Component.literal("用量受批量策略控制；阈值不放大"));
            } else lines.add(Component.literal("单次输入开工扣除／输出完工添加；rate 按加工 tick；用量受批量策略控制"));
            lines.add(Component.literal(isReadOnly(recipe()) ? "此配方只读，请修改其来源" : "单击修改；右键设置接口分组"));
        } catch (RuntimeException invalid) { lines.add(Component.literal("请检查源文件：" + invalid.getMessage())); }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private void renderSmartTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (colorOpen || pickerOpen || editorOpen || namePreviewOpen || typePickerOpen || modifiersOpen || catalystTooltipsOpen || selectorOpen || batchOpen || triggerOpen || smartOpen || adapterOpen || environmentOpen || pneumaticOpen || nbtOpen || resourceOpen) return;
        int x = mouseX - leftPos, y = mouseY - topPos;
        if (x < REQUIREMENT_X || x >= REQUIREMENT_X + REQUIREMENT_W - 28 || y < INPUT_TOP || y >= INPUT_TOP + SIDE_ROWS * ROW_HEIGHT) return;
        var indices = sideIndices(false); int index = inputScroll + (y - INPUT_TOP) / ROW_HEIGHT;
        if (index >= indices.size()) return;
        var req = recipeRequirements().get(indices.get(index)).getAsJsonObject();
        if (!SmartInterfaceEditor.isSmart(req)) return;
        graphics.renderComponentTooltip(font, List.of(Component.literal("智能接口类型：" + req.get("interface").getAsString()),
                Component.literal("范围：[" + req.get("min").getAsString() + ", "
                        + (req.has("max") ? req.get("max").getAsString() : req.get("min").getAsString()) + "]（包含两端）"),
                Component.literal("单击修改；不消耗物品，不受批量倍率影响")), mouseX, mouseY);
    }

    private void renderSelectorTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (colorOpen || pickerOpen || editorOpen || namePreviewOpen || typePickerOpen || modifiersOpen || catalystTooltipsOpen || selectorOpen || batchOpen || triggerOpen) return;
        int x = mouseX - leftPos, y = mouseY - topPos;
        if (x < REQUIREMENT_X + REQUIREMENT_W - 28 || x >= REQUIREMENT_X + REQUIREMENT_W - 18) return;
        boolean output = y >= OUTPUT_TOP; int top = output ? OUTPUT_TOP : INPUT_TOP;
        if (y < top || y >= top + SIDE_ROWS * ROW_HEIGHT) return;
        var indices = sideIndices(output); int index = (output ? outputScroll : inputScroll) + (y - top) / ROW_HEIGHT;
        if (index >= indices.size()) return;
        var req = recipeRequirements().get(indices.get(index)).getAsJsonObject();
        graphics.renderComponentTooltip(font, List.of(Component.literal("接口分组：" + (req.has("selector-tag")
                ? req.get("selector-tag").getAsString() : "不限接口")), Component.literal("右键需求行修改；标签精确匹配，区分大小写")), mouseX, mouseY);
    }

    private void renderFuelTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (colorOpen || pickerOpen || editorOpen || namePreviewOpen || typePickerOpen || modifiersOpen || catalystTooltipsOpen) return;
        int x = mouseX - leftPos, y = mouseY - topPos;
        if (x < REQUIREMENT_X || x >= REQUIREMENT_X + REQUIREMENT_W - 18) return;
        boolean output = y >= OUTPUT_TOP;
        int top = output ? OUTPUT_TOP : INPUT_TOP;
        if (y < top || y >= top + SIDE_ROWS * ROW_HEIGHT) return;
        var indices = sideIndices(output);
        int index = (output ? outputScroll : inputScroll) + (y - top) / ROW_HEIGHT;
        if (index >= indices.size()) return;
        var req = recipeRequirements().get(indices.get(index)).getAsJsonObject();
        if (!FuelEditor.isFuel(req)) return;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("任意熔炉燃料：" + req.get("time").getAsString() + " 燃烧 tick"));
        lines.add(Component.literal("消耗概率 " + (req.has("chance") ? req.get("chance").getAsString() : "1") + "；批量通常放大所需燃烧时间"));
        lines.add(Component.literal("燃料可混用，多余燃烧时间丢弃；容器燃料使用后留下容器"));
        lines.add(Component.literal("概率 0 不扣燃料，但开工仍需足够燃烧时间；图标仅为示例"));
        if (!FuelEditor.canEdit(req)) lines.add(Component.literal("包含高级条件，请修改源文件"));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private void renderDurabilityTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (colorOpen || pickerOpen || editorOpen || namePreviewOpen || typePickerOpen || modifiersOpen || catalystTooltipsOpen) return;
        int x = mouseX - leftPos, y = mouseY - topPos;
        if (x < REQUIREMENT_X || x >= REQUIREMENT_X + REQUIREMENT_W - 18) return;
        boolean output = y >= OUTPUT_TOP;
        int top = output ? OUTPUT_TOP : INPUT_TOP;
        if (y < top || y >= top + SIDE_ROWS * ROW_HEIGHT) return;
        var indices = sideIndices(output);
        int index = (output ? outputScroll : inputScroll) + (y - top) / ROW_HEIGHT;
        if (index >= indices.size()) return;
        var req = recipeRequirements().get(indices.get(index)).getAsJsonObject();
        if (!DurabilityEditor.isDurability(req)) return;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("耐久输入：" + req.get("item").getAsString()));
        lines.add(Component.literal("使用 " + req.get("amount").getAsString() + " 次，每次扣 "
                + req.get("consumeDurability").getAsString() + " 耐久；概率 " + (req.has("chance") ? req.get("chance").getAsString() : "1")));
        lines.add(Component.literal("同件工具可反复使用；最后不足一次的剩余耐久也算一次"));
        lines.add(Component.literal("批量放大使用次数；概率 0 保留工具，开工仍需足量可用次数"));
        if (ItemTagSelection.isTag(req)) lines.add(Component.literal("仅接受标签内可扣耐久的成员；图标仅为示例"));
        if (!DurabilityEditor.canEdit(req)) lines.add(Component.literal("含高级条件，请修改源文件"));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private void renderGroupTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = mouseX - leftPos, y = mouseY - topPos;
        JsonObject group = null;
        if (editorOpen && editingGroup() && x >= 68 && x < 350 && y >= GROUP_TOP && y < GROUP_TOP + groupRows() * GROUP_ROW_H) {
            int index = groupScroll + (y - GROUP_TOP) / GROUP_ROW_H;
            if (index >= groupItems().size()) return;
            var candidate = groupItems().get(index).getAsJsonObject();
            String id = candidate.get("item").getAsString();
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(ItemTagSelection.isTag(candidate) ? "#" + id.substring(4) : id));
            lines.add(Component.literal(outputGroup() ? "产出 " + candidate.get("minAmount").getAsString() + "～"
                    + candidate.get("maxAmount").getAsString() + " 个；权重 " + candidate.get("chance").getAsString()
                    : "需要 " + candidate.get("amount").getAsString() + " 个；单击选中后修改数量"));
            if (outputGroup()) lines.add(Component.literal("抽中占比不包含整组产出概率；每次抽出一种产物"));
            if (ItemTagSelection.isTag(candidate)) lines.add(Component.literal("接受标签内任意物品；图标仅为示例"));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
        if (!colorOpen && !pickerOpen && !editorOpen && !namePreviewOpen && !typePickerOpen && !modifiersOpen && !catalystTooltipsOpen && x >= REQUIREMENT_X && x < REQUIREMENT_X + REQUIREMENT_W - 18) {
            boolean output = y >= OUTPUT_TOP;
            int top = output ? OUTPUT_TOP : INPUT_TOP;
            if (y < top || y >= top + SIDE_ROWS * ROW_HEIGHT) return;
            var indices = sideIndices(output);
            int index = (output ? outputScroll : inputScroll) + (y - top) / ROW_HEIGHT;
            if (index < indices.size()) group = recipeRequirements().get(indices.get(index)).getAsJsonObject();
        }
        if (!IngredientGroupEditor.isGroup(group)) return;
        List<Component> lines = new ArrayList<>();
        boolean output = group.has("io-type") && "output".equals(group.get("io-type").getAsString());
        lines.add(Component.literal(output ? "随机物品输出组：按权重选择候选项" : CatalystEditor.isCatalyst(group)
                ? "催化剂：可选输入，存在时应用修改器" : "候选物品组：每份加工选择能付清的一项"));
        if (CatalystEditor.isCatalyst(group)) {
            lines.add(Component.literal("缺少仍可加工；消耗概率为 0 时保留催化剂，效果仍生效"));
            if (group.has("modifiers") && group.get("modifiers").isJsonArray()) lines.add(Component.literal("修改器：" + group.getAsJsonArray("modifiers").size() + " 项"));
            if (group.has("tooltips") && group.get("tooltips").isJsonArray()) group.getAsJsonArray("tooltips").asList().stream()
                    .filter(tip -> tip.isJsonPrimitive()).limit(4)
                    .forEach(tip -> lines.add(Component.literal(tip.getAsString())));
        } else if (!output) lines.add(Component.literal("不能将不同候选项的不足数量拼凑；批量可分别选择"));
        group.getAsJsonArray("items").asList().stream().limit(6).forEach(element -> {
            var candidate = element.getAsJsonObject();
            lines.add(Component.literal(candidate.get("item").getAsString() + (output ? " ×" + candidate.get("minAmount").getAsString()
                    + "～" + candidate.get("maxAmount").getAsString() + "  权重 " + candidate.get("chance").getAsString()
                    : " ×" + candidate.get("amount").getAsString())));
        });
        if (!IngredientGroupEditor.canEdit(group)) lines.add(Component.literal("包含高级条件，请修改源文件"));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }
}
