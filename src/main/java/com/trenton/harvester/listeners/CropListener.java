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
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class CropListener implements Listener {

    private final Harvester plugin;
    private final FileConfiguration config;
    private final Map<Material, Material> cropToSeed;
    private final Map<Material, Material> cropToPlantingBlock;
    private final Set<Material> enabledCrops;
    private final Set<Material> hoes;
    private final Object griefPrevention;
    private final Object worldGuard;
    private final Method griefPreventionGetClaimAt;
    private final Method griefPreventionAllowBreak;
    private final Method griefPreventionAllowBuild;
    private final Method worldGuardCreateProtectionQuery;
    private final Method protectionQueryTestBlockBreak;
    private final Method protectionQueryTestBlockPlace;
    private final Field griefPreventionDataStore;
    private final boolean requireHoe;
    private final boolean useHoeDurability;
    private final boolean requirePermission;
    private final boolean particlesEnabled;
    private final boolean soundEnabled;
    private final String noPermissionMessage;
    private final String noHoeMessage;
    private final String particleType;
    private final String soundType;
    private final int particleCount;
    private final float soundVolume;
    private final float soundPitch;

    public CropListener(Harvester plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        cropToSeed = new HashMap<>();
        cropToPlantingBlock = new HashMap<>();
        enabledCrops = new HashSet<>();
        hoes = new HashSet<>(Arrays.asList(
                Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
                Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE
        ));

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
                if (Boolean.parseBoolean(entry.getValue().toString())) {
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

        requireHoe = config.getBoolean("require_hoe", false);
        useHoeDurability = config.getBoolean("hoe_durability", false);
        requirePermission = config.getBoolean("permissions.require_permission", false);
        particlesEnabled = config.getBoolean("effects.particles.enabled", true);
        soundEnabled = config.getBoolean("effects.sound.enabled", true);
        noPermissionMessage = config.getString("messages.no_permission", "&cYou don't have permission to harvest here!");
        noHoeMessage = config.getString("messages.no_hoe", "&cYou must hold a hoe to harvest crops!");
        particleType = config.getString("effects.particles.type", "VILLAGER_HAPPY");
        soundType = config.getString("effects.sound.type", "BLOCK_CROP_BREAK");
        particleCount = config.getInt("effects.particles.count", 10);
        soundVolume = (float) config.getDouble("effects.sound.volume", 1.0);
        soundPitch = (float) config.getDouble("effects.sound.pitch", 1.0);

        Object gpInstance = null;
        Field gpDataStore = null;
        Method gpGetClaimAt = null;
        Method gpAllowBreak = null;
        Method gpAllowBuild = null;
        Object wgInstance = null;
        Method wgCreateProtectionQuery = null;
        Method pqTestBlockBreak = null;
        Method pqTestBlockPlace = null;

        try {
            Plugin gp = plugin.getServer().getPluginManager().getPlugin("GriefPrevention");
            if (gp != null && gp.getClass().getName().equals("me.ryanhamshire.GriefPrevention.GriefPrevention")) {
                gpInstance = gp;
                gpDataStore = gp.getClass().getField("dataStore");
                Class<?> claimClass = Class.forName("me.ryanhamshire.GriefPrevention.Claim");
                gpGetClaimAt = gpDataStore.getType().getMethod("getClaimAt", org.bukkit.Location.class, boolean.class, claimClass);
                gpAllowBreak = claimClass.getMethod("allowBreak", Player.class, Material.class);
                gpAllowBuild = claimClass.getMethod("allowBuild", Player.class, Material.class);
            }
        } catch (Exception e) {
            plugin.getLogger().info("GriefPrevention not found, skipping integration.");
        }

        try {
            Plugin wg = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
            if (wg != null && wg.getClass().getName().equals("com.sk89q.worldguard.bukkit.WorldGuardPlugin")) {
                wgInstance = wg;
                wgCreateProtectionQuery = wg.getClass().getMethod("createProtectionQuery");
                Class<?> protectionQueryClass = Class.forName("com.sk89q.worldguard.bukkit.ProtectionQuery");
                pqTestBlockBreak = protectionQueryClass.getMethod("testBlockBreak", Player.class, Block.class);
                pqTestBlockPlace = protectionQueryClass.getMethod("testBlockPlace", Player.class, org.bukkit.Location.class, Material.class);
            }
        } catch (Exception e) {
            plugin.getLogger().info("WorldGuard not found, skipping integration.");
        }

        griefPrevention = gpInstance;
        griefPreventionDataStore = gpDataStore;
        griefPreventionGetClaimAt = gpGetClaimAt;
        griefPreventionAllowBreak = gpAllowBreak;
        griefPreventionAllowBuild = gpAllowBuild;
        worldGuard = wgInstance;
        worldGuardCreateProtectionQuery = wgCreateProtectionQuery;
        protectionQueryTestBlockBreak = pqTestBlockBreak;
        protectionQueryTestBlockPlace = pqTestBlockPlace;
    }

    private void sendConfigMessage(Player player, String message) {
        if (message != null && !message.isEmpty()) {
            plugin.getLogger().info("Sending message to " + player.getName() + ": " + message);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        } else {
            plugin.getLogger().warning("Message is null or empty in config.yml");
        }
    }

    private boolean isHoe(ItemStack item) {
        return item != null && hoes.contains(item.getType());
    }

    private void applyHoeDurability(Player player, ItemStack hoe) {
        if (useHoeDurability && hoe.getItemMeta() instanceof Damageable damageable) {
            damageable.setDamage(damageable.getDamage() + 1);
            hoe.setItemMeta(damageable);
            if (damageable.getDamage() >= hoe.getType().getMaxDurability()) {
                player.getInventory().setItemInMainHand(null);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            }
        }
    }

    private boolean canHarvestAndReplant(Player player, Block block) {
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
        plugin.getServer().getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) {
            sendConfigMessage(player, noPermissionMessage);
            return false;
        }

        BlockPlaceEvent placeEvent = new BlockPlaceEvent(block, block.getState(), block.getRelative(0, -1, 0), new ItemStack(block.getType()), player, true);
        plugin.getServer().getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) {
            sendConfigMessage(player, noPermissionMessage);
            return false;
        }

        if (griefPrevention != null && griefPreventionDataStore != null && griefPreventionGetClaimAt != null) {
            try {
                Object claim = griefPreventionGetClaimAt.invoke(griefPreventionDataStore.get(griefPrevention), block.getLocation(), false, null);
                if (claim != null) {
                    if (griefPreventionAllowBreak != null && griefPreventionAllowBreak.invoke(claim, player, block.getType()) != null) {
                        sendConfigMessage(player, noPermissionMessage);
                        return false;
                    }
                    if (griefPreventionAllowBuild != null && griefPreventionAllowBuild.invoke(claim, player, block.getType()) != null) {
                        sendConfigMessage(player, noPermissionMessage);
                        return false;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check GriefPrevention permissions: " + e.getMessage());
            }
        }

        if (worldGuard != null && worldGuardCreateProtectionQuery != null && protectionQueryTestBlockBreak != null && protectionQueryTestBlockPlace != null) {
            try {
                Object protectionQuery = worldGuardCreateProtectionQuery.invoke(worldGuard);
                if (!(Boolean) protectionQueryTestBlockBreak.invoke(protectionQuery, player, block)) {
                    sendConfigMessage(player, noPermissionMessage);
                    return false;
                }
                if (!(Boolean) protectionQueryTestBlockPlace.invoke(protectionQuery, player, block.getLocation(), block.getType())) {
                    sendConfigMessage(player, noPermissionMessage);
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

        if (!enabledCrops.contains(blockType) || (requirePermission && !player.hasPermission("harvester.use"))) {
            return;
        }

        if (!(block.getBlockData() instanceof Ageable ageable) || ageable.getAge() != ageable.getMaximumAge()) {
            return;
        }

        if (!cropToPlantingBlock.get(blockType).equals(block.getRelative(0, -1, 0).getType())) {
            return;
        }

        if (requireHoe && !isHoe(player.getInventory().getItemInMainHand())) {
            sendConfigMessage(player, noHoeMessage);
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

        if (requireHoe) {
            applyHoeDurability(player, player.getInventory().getItemInMainHand());
        }

        if (particlesEnabled) {
            block.getWorld().spawnParticle(Particle.valueOf(particleType), block.getLocation().add(0.5, 0.5, 0.5), particleCount);
        }

        if (soundEnabled) {
            block.getWorld().playSound(block.getLocation(), Sound.valueOf(soundType), soundVolume, soundPitch);
        }
    }
}