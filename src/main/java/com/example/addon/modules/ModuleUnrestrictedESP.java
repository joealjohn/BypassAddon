package com.example.addon.modules;

import com.example.addon.BypassAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class ModuleUnrestrictedESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> enableExtendedESP = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-extended-esp")
        .description("Automatically enables EnhancedStorageESP when this module is activated.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enableExtendedTracers = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-extended-tracers")
        .description("Automatically enables TracersExtended when this module is activated.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableStandardESP = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-standard-esp")
        .description("Automatically disables Meteor's standard ESP modules when this module is activated.")
        .defaultValue(true)
        .build()
    );

    public ModuleUnrestrictedESP() {
        super(BypassAddon.CATEGORY, "unrestricted-esp", "Toggles enhanced ESP modules with no Y-level restrictions.");
    }

    @Override
    public void onActivate() {
        // Enable our enhanced modules
        if (enableExtendedESP.get()) {
            Module enhancedESP = Modules.get().get(EnhancedStorageESP.class);
            if (enhancedESP != null && !enhancedESP.isActive()) enhancedESP.toggle();
        }

        if (enableExtendedTracers.get()) {
            Module tracersExtended = Modules.get().get(TracersExtended.class);
            if (tracersExtended != null && !tracersExtended.isActive()) tracersExtended.toggle();
        }

        // Disable standard modules if configured
        if (disableStandardESP.get()) {
            try {
                // Try to get and disable standard ESP modules
                Module storageESP = Modules.get().get("StorageESP");
                if (storageESP != null && storageESP.isActive()) storageESP.toggle();

                Module tracers = Modules.get().get("Tracers");
                if (tracers != null && tracers.isActive()) tracers.toggle();
            } catch (Exception e) {
                info("Could not disable standard ESP modules: " + e.getMessage());
            }
        }

        info("Enhanced ESP modules activated with no Y-level restrictions!");
    }

    @Override
    public void onDeactivate() {
        // You could add logic here to disable our modules and re-enable standard ones if desired
    }

    // These methods are still here for backward compatibility, but we don't need them anymore
    public boolean shouldBypassStorageESP() {
        return isActive();
    }

    public boolean shouldBypassTracers() {
        return isActive();
    }
}
