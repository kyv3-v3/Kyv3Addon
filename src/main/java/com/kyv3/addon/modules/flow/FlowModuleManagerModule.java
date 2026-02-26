package com.kyv3.addon.modules.flow;

import com.kyv3.addon.Kyv3Addon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;

public class FlowModuleManagerModule extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> openOnActivate = sg.add(new BoolSetting.Builder()
        .name("open-on-activate")
        .description("Opens the manager screen as soon as this module is enabled.")
        .defaultValue(true)
        .build());

    private final Setting<Keybind> openManagerBind = sg.add(new KeybindSetting.Builder()
        .name("open-manager-bind")
        .description("Opens the manager screen while this module is active.")
        .defaultValue(Keybind.none())
        .build());

    private boolean wasPressed;

    public FlowModuleManagerModule() {
        super(Kyv3Addon.CUSTOM_CATEGORY, "flow-module-manager", "Manage generated custom modules (activate, disable, delete). ");
        toggleOnBindRelease = true;
    }

    @Override
    public void onActivate() {
        wasPressed = false;

        FlowForgeModule forge = Modules.get().get(FlowForgeModule.class);
        if (forge == null) {
            error("Flow Forge module is missing.");
            return;
        }

        forge.ensureHiddenFromClickGui();
        forge.ensureInitialized();
        if (openOnActivate.get()) forge.openManager();
    }

    @Override
    public void onDeactivate() {
        wasPressed = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        FlowForgeModule forge = Modules.get().get(FlowForgeModule.class);
        if (forge == null) return;

        boolean pressed = openManagerBind.get().isPressed();
        if (pressed && !wasPressed) {
            forge.ensureHiddenFromClickGui();
            forge.ensureInitialized();
            forge.openManager();
        }

        wasPressed = pressed;
    }
}
