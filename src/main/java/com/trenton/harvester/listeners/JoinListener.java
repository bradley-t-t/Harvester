package com.trenton.harvester.listeners;

import com.trenton.coreapi.annotations.CoreListener;
import com.trenton.coreapi.api.CoreListenerInterface;
import com.trenton.coreapi.util.MessageUtils;
import com.trenton.harvester.Harvester;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerJoinEvent;

@CoreListener(name = "JoinListener")
public class JoinListener implements CoreListenerInterface {
    private Harvester plugin;

    public void init(Harvester plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handleEvent(Event event) {
        if (!(event instanceof PlayerJoinEvent joinEvent)) {
            return;
        }
        if (plugin == null) {
            return;
        }
        if (joinEvent.getPlayer().hasPermission("harvester.update.notify") && plugin.getUpdater().isUpdateAvailable()) {
            MessageUtils.sendMessage(plugin.getCoreAPI().getMessages(), joinEvent.getPlayer(), "update_available");
        }
    }

    @Override
    public Class<? extends Event>[] getHandledEvents() {
        return new Class[]{PlayerJoinEvent.class};
    }
}