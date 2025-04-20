package com.trenton.harvester;

import com.trenton.coreapi.api.CoreAPI;
import com.trenton.updater.api.UpdaterImpl;
import com.trenton.updater.api.UpdaterService;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public class Harvester extends JavaPlugin {
    private UpdaterService updater;
    private CoreAPI coreAPI;

    @Override
    public void onEnable() {
        String packageName = getClass().getPackageName();
        coreAPI = new CoreAPI(this, packageName);
        coreAPI.initialize();

        boolean autoUpdaterEnabled = coreAPI.getConfig().getBoolean("auto_updater.enabled", true);
        updater = new UpdaterImpl(this, 124141);
        if (autoUpdaterEnabled) {
            updater.checkForUpdates(true);
        }

        new Metrics(this, 25508);
        getLogger().info("Harvester plugin enabled!");
    }

    @Override
    public void onDisable() {
        if (coreAPI != null) {
            coreAPI.shutdown();
        }
        if (updater != null) {
            updater.handleUpdateOnShutdown();
        }
        getLogger().info("Harvester plugin disabled!");
    }

    public CoreAPI getCoreAPI() {
        return coreAPI;
    }

    public UpdaterService getUpdater() {
        return updater;
    }
}