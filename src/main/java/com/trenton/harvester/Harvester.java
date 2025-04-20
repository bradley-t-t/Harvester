package com.trenton.harvester;

import com.trenton.coreapi.api.PluginInitializer;
import com.trenton.updater.api.UpdaterImpl;
import com.trenton.updater.api.UpdaterService;
import org.bstats.bukkit.Metrics;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class Harvester extends JavaPlugin {
    private FileConfiguration messagesConfig;
    private UpdaterService updater;
    private PluginInitializer initializer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultMessagesConfig();
        File messagesFile = new File(getDataFolder(), "messages.yml");
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        String packageName = getClass().getPackageName();
        initializer = new PluginInitializer(this, packageName);
        initializer.initialize();

        boolean autoUpdaterEnabled = getConfig().getBoolean("auto_updater.enabled", true);
        updater = new UpdaterImpl(this, 124141);
        if (autoUpdaterEnabled) {
            updater.checkForUpdates(true);
        }

        new Metrics(this, 25508);
        getLogger().info("Harvester plugin enabled!");
    }

    @Override
    public void onDisable() {
        if (initializer != null) {
            initializer.shutdown();
        }
        if (updater != null) {
            updater.handleUpdateOnShutdown();
        }
        getLogger().info("Harvester plugin disabled!");
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    private void saveDefaultMessagesConfig() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
    }

    public UpdaterService getUpdater() {
        return updater;
    }
}