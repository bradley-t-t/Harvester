package com.trenton.harvester;

import com.trenton.harvester.listeners.CropListener;
import com.trenton.harvester.updater.UpdateChecker;
import org.bstats.bukkit.Metrics;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class Harvester extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new CropListener(this), this);
        UpdateChecker updateChecker = new UpdateChecker(this, 124141);
        if (getConfig().getBoolean("auto_updater.enabled", true)) {
            updateChecker.checkForUpdates(true);
            if (!updateChecker.isUpdateAvailable()) {
                getLogger().info("No update available or update check failed.");
            }
        }
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent event) {
                if (event.getPlayer().hasPermission("harvester.update.notify") && updateChecker.isUpdateAvailable()) {
                    event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.update_available", "&eA new version of Harvester is available!")));
                }
            }
        }, this);
        if (getConfig().getBoolean("bstats.enabled", true)) {
            new Metrics(this,25508);
        }
        getLogger().info("Harvester plugin enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Harvester plugin disabled!");
    }
}