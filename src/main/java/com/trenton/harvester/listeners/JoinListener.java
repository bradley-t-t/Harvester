package com.trenton.harvester.listeners;

import com.trenton.coreapi.api.ListenerBase;
import com.trenton.coreapi.util.MessageUtils;
import com.trenton.harvester.Harvester;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public class JoinListener implements ListenerBase, Listener {
    private Harvester plugin;

    @Override
    public void register(Plugin plugin) {
        this.plugin = (Harvester) plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event.getPlayer().hasPermission("harvester.update.notify") && plugin.getUpdater().isUpdateAvailable()) {
            MessageUtils.sendMessage(plugin, plugin.getMessagesConfig(), event.getPlayer(), "update_available");
        }
    }
}