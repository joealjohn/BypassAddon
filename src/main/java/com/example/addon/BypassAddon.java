package com.example.addon;

import com.example.addon.modules.*;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class BypassAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Bypass");
    public static final HudGroup HUD_GROUP = new HudGroup("Bypass");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Comprehensive Bypass Addon");

        // Core ESP and bypass modules
        Modules.get().add(new ServerBypassESP());
        Modules.get().add(new EnhancedStorageESP());
        
        // New comprehensive bypass modules
        Modules.get().add(new PositionSpoofer());
        Modules.get().add(new FreecamBypass());
        Modules.get().add(new ESPRenderer());
        Modules.get().add(new ChunkSniffer());
        Modules.get().add(new BypassController());
        
        // Utility modules
        Modules.get().add(new ModulePlugins());
        
        LOG.info("Loaded {} bypass modules with anti-cheat evasion capabilities", 7);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("joealjohn", "bypass-addon");
    }
}
