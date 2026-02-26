package com.kyv3.addon.gui;
import com.kyv3.addon.gui.widgets.WKyv3Button;
import com.kyv3.addon.gui.widgets.WKyv3Checkbox;
import com.kyv3.addon.gui.widgets.WKyv3Dropdown;
import com.kyv3.addon.gui.widgets.WKyv3Module;
import com.kyv3.addon.gui.widgets.WKyv3Slider;
import com.kyv3.addon.gui.widgets.WKyv3TextBox;
import com.kyv3.addon.gui.widgets.WKyv3TopBar;
import com.kyv3.addon.gui.widgets.WKyv3Tooltip;
import com.kyv3.addon.gui.widgets.WKyv3Window;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.widgets.WTooltip;
import meteordevelopment.meteorclient.gui.widgets.WTopBar;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WSlider;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.lang.reflect.Field;
import java.util.Base64;

public class Kyv3GuiTheme extends MeteorGuiTheme {
    private static final String THEME_NAME = "Kyv3 Pro";
    private static final String EMBEDDED_PRESET = "H4sIAAAAAAAA/43UwYoTMRgH8G/caTudVRdFWLzInjwIc9GLJ8FdxIMsLK4gLF4yma/TsGlSkmk7fQTFB/AZvPgIXj3oGwi+gAfBB6iZkmZkOmmnl0yHb375J/lIDBBBKMgEIXq1nD85uVAyhlsLJjK5OJNixHIdQ18jUXTch6D8+gfWP/O83DwHEGE5JSLDLIAYem+l4lnH4rCatWvtOdO0Y23/gpMlqq7VZ3KSkqJjdXQu5zhB0bW+/xrNY7csFa+xKJjI9RD6uZKzqTkB80UARxppwaR4saneHN3gJQpUhA/rb6tPggB6c8Jn6ApvU1JgLtUyYVQKXc3tRc2ecKl0w+zF1jyAIDf/D81IzLgKYKAIE6lcgHmlzKuFGVMz/nItdpNQarYtoZUMDWngl95Z6T3UCxkjvU5l2W6t/NaVtT46K57ymW53rvzOyjqlcw4nTPign/uh5/XiRmQuFSvQWrtOKXyDZdE4oxuNyf/6J/9tJ1/Vu1EYsH0RX/zOJ+t8d879tWNiS5ER03D/sbvWE58Sel11vcia3dxIc2LT/NhOc8+meezSHE9kNuOYpE7vkGV4SZXkPCWqEeWgEeWDjfJtO8qxjRK7KEdObd/lp34sslhYr2tsriKFWbIHfehHQ4sGNTpVqHULuvO6uOTMXHJ7WvGzv4UebbXiHb0mE44jT0cmfu6B5Z457q7lFMvHrhUB/gHndkM8AwcAAA==";

    private final SettingGroup sgStyle = settings.createGroup("Kyv3 Style");

    public final Setting<Double> animationSpeed = sgStyle.add(new DoubleSetting.Builder()
        .name("animation-speed")
        .description("Global animation speed multiplier.")
        .defaultValue(1.0)
        .min(0.6)
        .sliderRange(0.6, 2.0)
        .build()
    );

    public final Setting<Double> motionSnappiness = sgStyle.add(new DoubleSetting.Builder()
        .name("motion-snappiness")
        .description("How quickly UI animations converge to their target state.")
        .defaultValue(1.0)
        .min(0.5)
        .sliderRange(0.5, 2.2)
        .build()
    );

    public final Setting<Double> springStiffnessSetting = sgStyle.add(new DoubleSetting.Builder()
        .name("spring-stiffness")
        .description("Spring stiffness for animated controls that use spring dynamics.")
        .defaultValue(22.0)
        .min(4.0)
        .sliderRange(4.0, 40.0)
        .build()
    );

    public final Setting<Double> springDampingSetting = sgStyle.add(new DoubleSetting.Builder()
        .name("spring-damping")
        .description("Spring damping for animated controls that use spring dynamics.")
        .defaultValue(8.0)
        .min(1.0)
        .sliderRange(1.0, 24.0)
        .build()
    );

    public final Setting<Integer> cornerRadius = sgStyle.add(new IntSetting.Builder()
        .name("corner-radius")
        .description("Corner radius for Kyv3 widgets.")
        .defaultValue(6)
        .min(0)
        .sliderRange(0, 14)
        .build()
    );

    public final Setting<Double> accentStrength = sgStyle.add(new DoubleSetting.Builder()
        .name("accent-strength")
        .description("How strongly accent color influences surfaces.")
        .defaultValue(0.16)
        .min(0)
        .sliderRange(0, 0.5)
        .build()
    );

    public final Setting<Double> surfaceContrast = sgStyle.add(new DoubleSetting.Builder()
        .name("surface-contrast")
        .description("Additional contrast and separation between background and outlines.")
        .defaultValue(0.55)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    public final Setting<Double> glowStrength = sgStyle.add(new DoubleSetting.Builder()
        .name("glow-strength")
        .description("Strength of top glows and active highlights.")
        .defaultValue(0.4)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    public final Setting<Double> focusRingStrength = sgStyle.add(new DoubleSetting.Builder()
        .name("focus-ring-strength")
        .description("Visibility of keyboard focus and interactive outline hints.")
        .defaultValue(0.55)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    public final Setting<Double> hoverLift = sgStyle.add(new DoubleSetting.Builder()
        .name("hover-lift")
        .description("Vertical hover offset intensity used by interactive widgets.")
        .defaultValue(0.45)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    public final Setting<Boolean> rainbowAccent = sgStyle.add(new BoolSetting.Builder()
        .name("rainbow-accent")
        .description("Enable rainbow accent for dynamic color cycling.")
        .defaultValue(false)
        .build()
    );

    public Kyv3GuiTheme() {
        super();
        renameTheme(THEME_NAME);
        applyFallbackDefaults();
        applyEmbeddedPreset();
    }

    private void applyFallbackDefaults() {
        scale.set(1.05);
        accentColor.set(new SettingColor(44, 182, 255));
        checkboxColor.set(new SettingColor(44, 182, 255));
        plusColor.set(new SettingColor(90, 255, 140));
        minusColor.set(new SettingColor(255, 90, 120));
        favoriteColor.set(new SettingColor(255, 220, 65));

        textColor.set(new SettingColor(236, 243, 255));
        textSecondaryColor.set(new SettingColor(152, 175, 204));
        titleTextColor.set(new SettingColor(255, 255, 255));
        moduleBackground.set(new SettingColor(20, 32, 50, 205));
        sliderLeft.set(new SettingColor(42, 170, 255));
        sliderRight.set(new SettingColor(30, 45, 62));
    }

    private void applyEmbeddedPreset() {
        try {
            byte[] decoded = Base64.getDecoder().decode(EMBEDDED_PRESET.trim());
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(decoded))) {
                NbtCompound tag = NbtIo.readCompressed(in, NbtSizeTracker.ofUnlimitedBytes());
                if (tag != null) fromTag(tag);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public WWindow window(WWidget icon, String title) {
        return w(new WKyv3Window(icon, title));
    }

    @Override
    protected WButton button(String text, GuiTexture texture) {
        return w(new WKyv3Button(text, texture));
    }

    @Override
    public WWidget module(Module module, String title) {
        return w(new WKyv3Module(module, title));
    }

    @Override
    public WTopBar topBar() {
        return w(new WKyv3TopBar());
    }

    @Override
    public WCheckbox checkbox(boolean checked) {
        return w(new WKyv3Checkbox(checked));
    }

    @Override
    public WSlider slider(double value, double min, double max) {
        return w(new WKyv3Slider(value, min, max));
    }

    @Override
    public WTextBox textBox(String text, String placeholder, CharFilter filter, Class<? extends WTextBox.Renderer> renderer) {
        return w(new WKyv3TextBox(text, placeholder, filter, renderer));
    }

    @Override
    public <T> WDropdown<T> dropdown(T[] values, T value) {
        return w(new WKyv3Dropdown<>(values, value));
    }

    @Override
    public WTooltip tooltip(String text) {
        return w(new WKyv3Tooltip(text));
    }

    public int roundAmount() {
        return Math.max(0, (int) scale(cornerRadius.get()));
    }

    public Color accentAt(double x, double y) {
        if (rainbowAccent.get()) return com.kyv3.addon.gui.widgets.Kyv3Colors.rainbow(x * 0.4 + y * 0.2, 255);
        return accentColor.get();
    }

    public double motionSpeed(double base) {
        return Math.max(0.01, base * animationSpeed.get() * motionSnappiness.get());
    }

    public double springStiffness() {
        return Math.max(0, springStiffnessSetting.get());
    }

    public double springDamping() {
        return Math.max(0, springDampingSetting.get());
    }

    public double hoverLiftAmount() {
        return Math.max(0, hoverLift.get());
    }

    private void renameTheme(String name) {
        try {
            Field field = GuiTheme.class.getDeclaredField("name");
            field.setAccessible(true);
            field.set(this, name);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
