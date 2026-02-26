package com.kyv3.addon;

import com.kyv3.addon.modules.flow.FlowForgeModule;
import com.kyv3.addon.modules.flow.FlowModuleManagerModule;
import com.kyv3.addon.gui.Kyv3GuiTheme;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Items;

public class Kyv3Addon extends MeteorAddon {
    public static final Category CUSTOM_CATEGORY = new Category("Kyv3 Custom", Items.COMMAND_BLOCK.getDefaultStack());

    @Override
    public void onInitialize() {
        GuiThemes.add(new Kyv3GuiTheme());

        FlowForgeModule flowForgeModule = new FlowForgeModule();
        Modules.get().add(flowForgeModule);
        Modules.get().add(new FlowModuleManagerModule());
        flowForgeModule.ensureHiddenFromClickGui();
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CUSTOM_CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.kyv3.addon";
    }
}
