package com.example.addon.utils;

import com.example.addon.modules.ModuleUnrestrictedESP;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;

public class ESPHelper {
    // This is for logging/debugging to make sure our mixin is working
    public static double getDistanceForESP(double x, double y, double z) {
        ModuleUnrestrictedESP module = Modules.get().get(ModuleUnrestrictedESP.class);
        if (module != null && module.isActive() && module.shouldBypassStorageESP()) {
            // Just for logging to confirm our logic is executing
            System.out.println("[BypassESP] Bypassing distance check for ESP");
            return 1.0;
        }
        return PlayerUtils.squaredDistanceTo(x, y, z);
    }
}
