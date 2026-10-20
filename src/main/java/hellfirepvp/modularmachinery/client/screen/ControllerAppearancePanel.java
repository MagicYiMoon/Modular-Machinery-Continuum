/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.registry.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Consumer;

/** Isolated modal: all widgets share the same lifecycle and never leak over the recipe editor. */
final class ControllerAppearancePanel {
    record Settings(int color, boolean factory) {}
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private EditBox hex;
    private Button factoryButton;
    private Font font;
    private boolean factory, registered, factoryRegistered;
    private String machine = "", title = "", error = "", draftColor = "FFFFFF";
    private static final int X=60, Y=24, W=320, H=216;

    void init(Font font, int left, int top, Consumer<AbstractWidget> adder,
              Consumer<Settings> save, Runnable cancel) {
        this.font=font; widgets.clear();
        hex=new EditBox(font, left+X+77, top+Y+40, 100, 20, Component.literal("RGB 颜色"));
        hex.setMaxLength(7); hex.setFilter(value -> value.matches("#?[0-9a-fA-F]{0,6}"));
        hex.setValue(draftColor); hex.setResponder(value -> draftColor = value);
        hex.setTooltip(Tooltip.create(Component.literal("RGB 十六进制颜色，例如 #55AAFF；只染控制器外壳，保留正面图案\n普通和工厂控制器共用机器 color；不修改 GeckoLib 模型纹理")));
        widgets.add(hex);
        int[] colors={0xFFFFFF,0xB0B0B0,0x505050,0xE85D5D,0xF4A340,0xE9D75B,0x60B878,0x55AAFF,0x9E78D6,0xE280BD};
        for(int i=0;i<colors.length;i++) {
            final int color=colors[i];
            widgets.add(Button.builder(Component.literal("■").withStyle(s->s.withColor(color)), b -> hex.setValue(String.format(Locale.ROOT,"%06X",color)))
                    .bounds(left+X+10+i*30,top+Y+68,28,20).build());
        }
        factoryButton=Button.builder(Component.empty(), b->{factory=!factory; updateFactory();})
                .bounds(left+X+10,top+Y+144,190,20)
                .tooltip(Tooltip.create(Component.literal("保存 has-factory 配置；新增工厂控制器需要客户端和服务端一起重启\n关闭此项不会删除已注册的旧 ID 或已有方块\n全局默认启用工厂时，以全局注册设置为准"))).build();
        widgets.add(factoryButton); updateFactory();
        widgets.add(Button.builder(Component.literal("保存"),b->{
            try { error=""; save.accept(new Settings(parse(),factory)); }
            catch(IllegalArgumentException invalid) { error="请输入六位十六进制 RGB 颜色"; }
        }).bounds(left+X+174,top+Y+188,62,20).build());
        widgets.add(Button.builder(Component.literal("返回"),b->cancel.run()).bounds(left+X+244,top+Y+188,62,20).build());
        widgets.forEach(adder); setVisible(false,false);
    }

    void open(JsonObject json) {
        machine=json.get("name").getAsString(); title=json.get("localizedName").getAsString();
        hex.setValue(String.format(Locale.ROOT,"%06X",json.get("color").getAsInt()&0xFFFFFF));
        factory=json.get("hasFactory").getAsBoolean(); registered=json.get("controllerRegistered").getAsBoolean();
        factoryRegistered=json.get("factoryRegistered").getAsBoolean(); error=""; updateFactory();
    }
    private void updateFactory(){factoryButton.setMessage(Component.literal("独立工厂控制器："+(factory?"开启":"关闭")));}
    private int parse(){String value=hex.getValue().replace("#",""); if(!value.matches("[0-9a-fA-F]{6}"))throw new IllegalArgumentException(); return Integer.parseInt(value,16);}
    void setVisible(boolean visible,boolean pending){for(var widget:widgets){widget.visible=visible;widget.active=visible&&!pending;}}
    void draw(GuiGraphics g,String status,boolean failed){
        g.fill(0,0,440,260,0xD0101216); g.fill(X,Y,X+W,Y+H,0xFF242A31);
        g.drawString(font,"控制器外观",X+10,Y+9,0xFFFFFF,false);
        g.drawString(font,font.plainSubstrByWidth(title,292),X+10,Y+24,0xB5C5D5,false);
        g.drawString(font,"RGB 颜色",X+10,Y+46,0xDDDDDD,false);
        int color; try{color=parse();}catch(IllegalArgumentException invalid){color=0xFFFFFF;}
        g.fill(X+190,Y+40,X+W-12,Y+60,0xFF000000|color);
        drawController(g,false,X+76,Y+96,color); drawController(g,true,X+221,Y+96,color);
        g.drawString(font,"普通控制器",X+55,Y+132,0xDDDDDD,false);
        g.drawString(font,"工厂控制器",X+200,Y+132,0xDDDDDD,false);
        String hint=!registered||factory&&!factoryRegistered?"新增控制器需重启客户端与服务端":"颜色保存后同步生效；注册 ID 保持不变";
        g.drawString(font,hint,X+10,Y+169,0xD9C98E,false);
        String message=!error.isEmpty()?error:status;
        if(!message.isEmpty())g.drawString(font,font.plainSubstrByWidth(message,150),X+10,Y+194,!error.isEmpty()||failed?0xFF8888:0x9BDEAA,false);
    }
    private void drawController(GuiGraphics g,boolean factory,int x,int y,int color){
        ItemStack stack=DedicatedControllers.stack(machine,factory);
        if(stack.isEmpty())stack=new ItemStack(factory?RegistryBlocks.blockFactoryController.get():RegistryBlocks.blockController.get());
        stack.getOrCreateTag().putInt("mmcPreviewColor",color);
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(2,2,1);g.renderItem(stack,0,0);g.pose().popPose();
    }
}
