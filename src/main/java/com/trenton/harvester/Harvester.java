package com.trenton.harvester;

import com.trenton.harvester.listeners.CropListener;
import com.trenton.harvester.updater.Updater;
import org.bstats.bukkit.Metrics;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class Harvester extends JavaPlugin {

    private FileConfiguration messagesConfig;
    private Updater updater;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultMessagesConfig();
        File messagesFile = new File(getDataFolder(), "messages.yml");
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        getServer().getPluginManager().registerEvents(new CropListener(this), this);
        boolean autoUpdaterEnabled = getConfig().getBoolean("auto_updater.enabled", true);
        updater = new Updater(this, 124141);
        if (autoUpdaterEnabled) {
            updater.checkForUpdates(true);
        }
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent event) {
                if (event.getPlayer().hasPermission("harvester.update.notify") && updater.isUpdateAvailable()) {
                    String message = messagesConfig.getString("update_available", "&eA new version of Harvester is available!");
                    event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                }
            }
        }, this);
        new Metrics(this, 25508);
        getLogger().info("Harvester plugin enabled!");
    }

    @Override
    public void onDisable() {
        updater.handleUpdateOnShutdown();
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
}