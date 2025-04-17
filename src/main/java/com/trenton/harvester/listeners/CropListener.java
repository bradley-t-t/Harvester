package com.trenton.harvester.listeners;

import com.trenton.harvester.Harvester;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.*;

public class CropListener implements Listener {

    private final Harvester plugin;
    private final FileConfiguration config;
    private final Map<Material, Material> cropToSeed;
    private final Map<Material, Material> cropToPlantingBlock;
    private final Set<Material> enabledCrops;
    private final Object griefPrevention;
    private final Object worldGuard;
    private final Method griefPreventionAllowBreak;
    private final Method griefPreventionAllowBuild;
    private final Method worldGuardCreateProtectionQuery;
    private final Method protectionQueryTestBlockBreak;
    private final Method protectionQueryTestBlockPlace;

    public CropListener(Harvester plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.cropToSeed = new HashMap<>();
        this.cropToPlantingBlock = new HashMap<>();
        this.enabledCrops = new HashSet<>();

        cropToSeed.put(Material.WHEAT, Material.WHEAT_SEEDS);
        cropToSeed.put(Material.CARROTS, Material.CARROT);
        cropToSeed.put(Material.POTATOES, Material.POTATO);
        cropToSeed.put(Material.BEETROOTS, Material.BEETROOT_SEEDS);
        cropToSeed.put(Material.NETHER_WART, Material.NETHER_WART);

        cropToPlantingBlock.put(Material.WHEAT, Material.FARMLAND);
        cropToPlantingBlock.put(Material.CARROTS, Material.FARMLAND);
        cropToPlantingBlock.put(Material.POTATOES, Material.FARMLAND);
        cropToPlantingBlock.put(Material.BEETROOTS, Material.FARMLAND);
        cropToPlantingBlock.put(Material.NETHER_WART, Material.SOUL_SAND);

        List<Map<?, ?>> configuredCrops = config.getMapList("crops.enabled");
        for (Map<?, ?> cropEntry : configuredCrops) {
            for (Map.Entry<?, ?> entry : cropEntry.entrySet()) {
                String cropName = entry.getKey().toString().toUpperCase();
                boolean isEnabled = Boolean.parseBoolean(entry.getValue().toString());
                if (isEnabled) {
                    try {
                        Material crop = Material.valueOf(cropName);
                        if (cropToSeed.containsKey(crop)) {
                            enabledCrops.add(crop);
                        }
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid crop type in config: " + cropName);
                    }
                }
            }
        }

        griefPrevention = getGriefPrevention();
        worldGuard = getWorldGuard();
        griefPreventionAllowBreak = getGriefPreventionMethod("allowBreak");
        griefPreventionAllowBuild = getGriefPreventionMethod("allowBuild");
        worldGuardCreateProtectionQuery = getWorldGuardMethod("createProtectionQuery");
        protectionQueryTestBlockBreak = getProtectionQueryMethod("testBlockBreak");
        protectionQueryTestBlockPlace = getProtectionQueryMethod("testBlockPlace");
    }

    private Object getGriefPrevention() {
        try {
            Plugin gp = plugin.getServer().getPluginManager().getPlugin("GriefPrevention");
            if (gp != null && gp.getClass().getName().equals("me.ryanhamshire.GriefPrevention.GriefPrevention")) {
                return gp;
            }
        } catch (Exception e) {
            plugin.getLogger().info("GriefPrevention not found, skipping integration.");
        }
        return null;
    }

    private Object getWorldGuard() {
        try {
            Plugin wg = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
            if (wg != null && wg.getClass().getName().equals("com.sk89q.worldguard.bukkit.WorldGuardPlugin")) {
                return wg;
            }
        } catch (Exception e) {
            plugin.getLogger().info("WorldGuard not found, skipping integration.");
        }
        return null;
    }

    private Method getGriefPreventionMethod(String methodName) {
        if (griefPrevention == null) return null;
        try {
            Class<?> claimClass = Class.forName("me.ryanhamshire.GriefPrevention.Claim");
            return claimClass.getMethod(methodName, Player.class, Material.class);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to find GriefPrevention method: " + methodName);
            return null;
        }
    }

    private Method getWorldGuardMethod(String methodName) {
        if (worldGuard == null) return null;
        try {
            return worldGuard.getClass().getMethod(methodName);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to find WorldGuard method: " + methodName);
            return null;
        }
    }

    private Method getProtectionQueryMethod(String methodName) {
        if (worldGuardCreateProtectionQuery == null) return null;
        try {
            Class<?> protectionQueryClass = Class.forName("com.sk89q.worldguard.bukkit.ProtectionQuery");
            if (methodName.equals("testBlockBreak")) {
                return protectionQueryClass.getMethod(methodName, Player.class, Block.class);
            } else if (methodName.equals("testBlockPlace")) {
                return protectionQueryClass.getMethod(methodName, Player.class, org.bukkit.Location.class, Material.class);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to find ProtectionQuery method: " + methodName);
        }
        return null;
    }

    private void sendNoPermissionMessage(Player player) {
        String message = config.getString("messages.no_permission", "&cYou don't have permission to harvest here!");
        if (message != null && !message.isEmpty()) {
            plugin.getLogger().info("Sending no-permission message to " + player.getName() + ": " + message);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        } else {
            plugin.getLogger().warning("No-permission message is null or empty in config.yml");
        }
    }

    private boolean canHarvestAndReplant(Player player, Block block) {
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
        plugin.getServer().getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) {
            sendNoPermissionMessage(player);
            return false;
        }

        BlockPlaceEvent placeEvent = new BlockPlaceEvent(block, block.getState(), block.getRelative(0, -1, 0), new ItemStack(block.getType()), player, true);
        plugin.getServer().getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) {
            sendNoPermissionMessage(player);
            return false;
        }

        if (griefPrevention != null) {
            try {
                Object dataStore = griefPrevention.getClass().getField("dataStore").get(griefPrevention);
                Method getClaimAt = dataStore.getClass().getMethod("getClaimAt", org.bukkit.Location.class, boolean.class, Class.forName("me.ryanhamshire.GriefPrevention.Claim"));
                Object claim = getClaimAt.invoke(dataStore, block.getLocation(), false, null);
                if (claim != null) {
                    if (griefPreventionAllowBreak != null) {
                        String error = (String) griefPreventionAllowBreak.invoke(claim, player, block.getType());
                        if (error != null) {
                            sendNoPermissionMessage(player);
                            return false;
                        }
                    }
                    if (griefPreventionAllowBuild != null) {
                        String error = (String) griefPreventionAllowBuild.invoke(claim, player, block.getType());
                        if (error != null) {
                            sendNoPermissionMessage(player);
                            return false;
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check GriefPrevention permissions: " + e.getMessage());
            }
        }

        if (worldGuard != null && worldGuardCreateProtectionQuery != null && protectionQueryTestBlockBreak != null && protectionQueryTestBlockPlace != null) {
            try {
                Object protectionQuery = worldGuardCreateProtectionQuery.invoke(worldGuard);
                Boolean canBreak = (Boolean) protectionQueryTestBlockBreak.invoke(protectionQuery, player, block);
                if (!canBreak) {
                    sendNoPermissionMessage(player);
                    return false;
                }
                Boolean canPlace = (Boolean) protectionQueryTestBlockPlace.invoke(protectionQuery, player, block.getLocation(), block.getType());
                if (!canPlace) {
                    sendNoPermissionMessage(player);
                    return false;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check WorldGuard permissions: " + e.getMessage());
            }
        }

        return true;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().toString().contains("RIGHT_CLICK") || !event.hasBlock()) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Material blockType = block.getType();

        if (!enabledCrops.contains(blockType) ||
                (config.getBoolean("permissions.require_permission") && !player.hasPermission("harvester.use"))) {
            return;
        }

        if (block.getBlockData() instanceof Ageable ageable && ageable.getAge() == ageable.getMaximumAge()) {
            Block belowBlock = block.getRelative(0, -1, 0);
            if (!cropToPlantingBlock.get(blockType).equals(belowBlock.getType())) {
                return;
            }

            if (!canHarvestAndReplant(player, block)) {
                return;
            }

            event.setCancelled(true);

            Collection<ItemStack> drops = block.getDrops();
            Material seedType = cropToSeed.get(blockType);
            boolean hasSeed = false;

            for (ItemStack drop : drops) {
                if (drop.getType() == seedType && drop.getAmount() >= 1) {
                    hasSeed = true;
                    break;
                }
            }

            if (!hasSeed) {
                return;
            }

            boolean seedConsumed = false;
            List<ItemStack> modifiedDrops = new ArrayList<>();
            for (ItemStack drop : drops) {
                ItemStack dropCopy = drop.clone();
                if (dropCopy.getType() == seedType && !seedConsumed) {
                    if (dropCopy.getAmount() > 1) {
                        dropCopy.setAmount(dropCopy.getAmount() - 1);
                        modifiedDrops.add(dropCopy);
                    }
                    seedConsumed = true;
                } else if (dropCopy.getAmount() > 0) {
                    modifiedDrops.add(dropCopy);
                }
            }

            for (ItemStack drop : modifiedDrops) {
                block.getWorld().dropItemNaturally(block.getLocation(), drop);
            }

            block.setType(blockType);
            Ageable newAgeable = (Ageable) block.getBlockData();
            newAgeable.setAge(0);
            block.setBlockData(newAgeable);

            if (config.getBoolean("effects.particles.enabled")) {
                block.getWorld().spawnParticle(
                        Particle.valueOf(config.getString("effects.particles.type", "VILLAGER_HAPPY")),
                        block.getLocation().add(0.5, 0.5, 0.5),
                        config.getInt("effects.particles.count", 10)
                );
            }
            if (config.getBoolean("effects.sound.enabled")) {
                block.getWorld().playSound(
                        block.getLocation(),
                        Sound.valueOf(config.getString("effects.sound.type", "BLOCK_CROP_BREAK")),
                        (float) config.getDouble("effects.sound.volume", 1.0),
                        (float) config.getDouble("effects.sound.pitch", 1.0)
                );
            }
        }
    }
}