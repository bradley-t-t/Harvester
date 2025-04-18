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

import java.util.*;

public class CropListener implements Listener {

    private final Harvester plugin;
    private final FileConfiguration config;
    private final FileConfiguration messages;
    private final Map<Material, Material> cropToSeed = new HashMap<>();
    private final Map<Material, Material> cropToPlantingBlock = new HashMap<>();
    private final Set<Material> enabledCrops = new HashSet<>();
    private final Set<Material> hoes = Set.of(
            Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
            Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE
    );
    private final boolean requireHoe;
    private final boolean useHoeDurability;
    private final boolean requirePermission;
    private final boolean particlesEnabled;
    private final boolean soundEnabled;
    private final String particleType;
    private final String soundType;
    private final int particleCount;
    private final float soundVolume;
    private final float soundPitch;

    public CropListener(Harvester plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.messages = plugin.getMessagesConfig();
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
        particleType = config.getString("effects.particles.type", "VILLAGER_HAPPY");
        soundType = config.getString("effects.sound.type", "BLOCK_CROP_BREAK");
        particleCount = config.getInt("effects.particles.count", 10);
        soundVolume = (float) config.getDouble("effects.sound.volume", 1.0);
        soundPitch = (float) config.getDouble("effects.sound.pitch", 1.0);
    }

    private void sendConfigMessage(Player player, String messageKey) {
        String message = messages.getString(messageKey);
        if (message != null && !message.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        } else {
            plugin.getLogger().warning("Message key '" + messageKey + "' is missing or empty in messages.yml");
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
            sendConfigMessage(player, "no_permission");
            return false;
        }
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(block, block.getState(), block.getRelative(0, -1, 0),
                new ItemStack(block.getType()), player, true);
        plugin.getServer().getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) {
            sendConfigMessage(player, "no_permission");
            return false;
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
            sendConfigMessage(player, "no_hoe");
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