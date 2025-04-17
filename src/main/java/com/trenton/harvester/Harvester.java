package com.trenton.harvester;

import org.bukkit.plugin.java.JavaPlugin;

public final class Harvester extends JavaPlugin {

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();

        // Register event listener
        getServer().getPluginManager().registerEvents(new CropListener(this), this);

        getLogger().info("Harvester plugin enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Harvester plugin disabled!");
    }
}