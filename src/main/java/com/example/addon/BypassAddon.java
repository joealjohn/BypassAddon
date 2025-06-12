package com.example.addon;

import com.example.addon.modules.ServerBypassESP;
import com.example.addon.modules.ModulePlugins;
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
        LOG.info("Initializing Bypass Addon");

        // Modules
        Modules.get().add(new ServerBypassESP());
        Modules.get().add(new ModulePlugins());
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
