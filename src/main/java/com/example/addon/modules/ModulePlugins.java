package com.example.addon.modules;

import com.example.addon.BypassAddon;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModulePlugins extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    public enum TryState {
        SLASH,
        COLON_SLASH,
        TAB_COMPLETE,
    }

    private final Setting<TryState> tryState = sgGeneral.add(new EnumSetting.Builder<TryState>()
        .name("try-method")
        .description("The method to use for detecting plugins.")
        .defaultValue(TryState.SLASH)
        .build()
    );

    private int step = 0;
    private final Pattern pluginsPattern = Pattern.compile("Plugins \\((\\d+)\\): (.+)");
    private final List<String> detectedPlugins = new ArrayList<>();

    public ModulePlugins() {
        super(BypassAddon.CATEGORY, "plugins", "Detects server plugins.");
    }

    @Override
    public void onActivate() {
        step = 0;
        detectedPlugins.clear();
        info("Detecting plugins...");

        switch (tryState.get()) {
            case SLASH:
                mc.player.networkHandler.sendChatCommand("plugins");
                break;
            case COLON_SLASH:
                mc.player.networkHandler.sendChatCommand("bukkit:plugins");
                break;
            case TAB_COMPLETE:
                // This would use tab completion in a full implementation
                mc.player.networkHandler.sendChatCommand("bukkit:");
                break;
        }

        // Set a timeout to disable the module if no response
        int timeout = 20 * 5; // 5 seconds
        Runnable disableTask = () -> {
            try {
                Thread.sleep(timeout * 50); // Convert ticks to ms
                if (this.isActive()) {
                    info("No plugin information received. Server might be blocking plugin detection.");
                    this.toggle();
                }
            } catch (InterruptedException e) {
                // Ignore
            }
        };
        new Thread(disableTask).start();
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        String message = event.getMessage().getString();

        // Check if the message contains plugin information
        Matcher matcher = pluginsPattern.matcher(message);
        if (matcher.find()) {
            String pluginsStr = matcher.group(2);
            String[] plugins = pluginsStr.split(", ");

            info("Detected plugins (" + plugins.length + "): " + pluginsStr);

            for (String plugin : plugins) {
                detectedPlugins.add(plugin.trim());
            }

            this.toggle(); // Disable the module after detecting plugins
        }
    }

    @Override
    public void onDeactivate() {
        if (detectedPlugins.isEmpty()) {
            info("No plugins were detected.");
        } else {
            info("Detected " + detectedPlugins.size() + " plugins: " + String.join(", ", detectedPlugins));
        }
    }
}
