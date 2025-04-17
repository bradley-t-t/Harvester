package com.trenton.harvester;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class CropListener implements Listener {

    private final Harvester plugin;
    private final FileConfiguration config;
    private final Map<Material, Material> cropToSeed;
    private final Map<Material, Material> cropToPlantingBlock;
    private final Set<Material> enabledCrops;

    public CropListener(Harvester plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.cropToSeed = new HashMap<>();
        this.cropToPlantingBlock = new HashMap<>();
        this.enabledCrops = new HashSet<>();

        // Define crop-to-seed and crop-to-planting-block mappings
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

        // Load enabled crops from config
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
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Check if action is right-click on a block
        if (!event.getAction().toString().contains("RIGHT_CLICK") || !event.hasBlock()) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Material blockType = block.getType();

        // Check if block is an enabled crop and player has permission
        if (!enabledCrops.contains(blockType) ||
                (config.getBoolean("permissions.require_permission") && !player.hasPermission("harvester.use"))) {
            return;
        }

        // Check if crop is fully grown and on correct planting block
        if (block.getBlockData() instanceof Ageable ageable && ageable.getAge() == ageable.getMaximumAge()) {
            Block belowBlock = block.getRelative(0, -1, 0);
            if (!cropToPlantingBlock.get(blockType).equals(belowBlock.getType())) {
                return; // Invalid planting block (e.g., wheat not on farmland)
            }

            event.setCancelled(true); // Prevent default interaction

            // Get drops
            Collection<ItemStack> drops = block.getDrops();
            Material seedType = cropToSeed.get(blockType);
            boolean hasSeed = false;

            // Check drops for at least one seed
            for (ItemStack drop : drops) {
                if (drop.getType() == seedType && drop.getAmount() >= 1) {
                    hasSeed = true;
                    break;
                }
            }

            if (!hasSeed) {
                return; // No seed available to replant
            }

            // Process drops: consume one seed, drop the rest
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

            // Drop modified items
            for (ItemStack drop : modifiedDrops) {
                block.getWorld().dropItemNaturally(block.getLocation(), drop);
            }

            // Replant crop
            block.setType(blockType); // Reset to crop block
            Ageable newAgeable = (Ageable) block.getBlockData();
            newAgeable.setAge(0); // Set to newly planted
            block.setBlockData(newAgeable);

            // Play effects
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