package com.trenton.harvester.listeners;

import com.trenton.coreapi.annotations.CoreListener;
import com.trenton.coreapi.api.CoreListenerInterface;
import com.trenton.coreapi.util.MessageUtils;
import com.trenton.harvester.Harvester;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.*;

@CoreListener(name = "CropListener")
public class CropListener implements CoreListenerInterface {
    private Harvester plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private final Map<Material, Material> cropToSeed = new HashMap<>();
    private final Map<Material, Material> cropToPlantingBlock = new HashMap<>();
    private final Set<Material> enabledCrops = new HashSet<>();
    private final Set<Material> hoes = Set.of(
            Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
            Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE
    );
    private boolean requireHoe;
    private boolean useHoeDurability;
    private boolean requirePermission;
    private boolean particlesEnabled;
    private boolean soundEnabled;
    private String particleType;
    private String soundType;
    private int particleCount;
    private float soundVolume;
    private float soundPitch;

    public void init(Harvester plugin) {
        this.plugin = plugin;
        this.config = plugin.getCoreAPI().getConfig();
        this.messages = plugin.getCoreAPI().getMessages();
        // Map crop blocks to their seed items
        cropToSeed.put(Material.WHEAT, Material.WHEAT_SEEDS);
        cropToSeed.put(Material.CARROTS, Material.CARROT);
        cropToSeed.put(Material.POTATOES, Material.POTATO);
        cropToSeed.put(Material.BEETROOTS, Material.BEETROOT_SEEDS);
        cropToSeed.put(Material.NETHER_WART, Material.NETHER_WART);
        // Map crop blocks to their planting blocks
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
                if (Boolean.parseBoolean(entry.getValue().toString())) {
                    try {
                        Material cropBlock = switch (cropName) {
                            case "WHEAT" -> Material.WHEAT;
                            case "CARROTS" -> Material.CARROTS;
                            case "POTATOES" -> Material.POTATOES;
                            case "BEETROOTS" -> Material.BEETROOTS;
                            case "NETHER_WART" -> Material.NETHER_WART;
                            default -> Material.valueOf(cropName);
                        };
                        if (cropToPlantingBlock.containsKey(cropBlock)) {
                            enabledCrops.add(cropBlock);
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

    @Override
    public void handleEvent(Event event) {
        if (!(event instanceof PlayerInteractEvent interactEvent)) {
            return;
        }
        if (!interactEvent.getAction().toString().contains("RIGHT_CLICK") || !interactEvent.hasBlock()) {
            return;
        }
        Player player = interactEvent.getPlayer();
        Block block = interactEvent.getClickedBlock();
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
        interactEvent.setCancelled(true);
        Collection<ItemStack> drops = block.getDrops();
        // Map crop block to item type
        Material itemType = switch (blockType) {
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT;
            case WHEAT -> Material.WHEAT;
            case NETHER_WART -> Material.NETHER_WART;
            default -> cropToSeed.get(blockType); // Fallback to seed type if defined
        };
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

    @Override
    public Class<? extends Event>[] getHandledEvents() {
        return new Class[]{PlayerInteractEvent.class};
    }

    private void sendConfigMessage(Player player, String messageKey) {
        if (plugin == null || messages == null) {
            plugin.getLogger().warning("Cannot send message '" + messageKey + "': plugin or messages not initialized");
            return;
        }
        MessageUtils.sendMessage(messages, player, messageKey);
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
        if (plugin == null) {
            plugin.getLogger().warning("Cannot check harvest permissions: plugin not initialized");
            return false;
        }
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
        plugin.getServer().getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) {
            sendConfigMessage(player, "no_permission");
            return false;
        }
        // Use the seed item for the BlockPlaceEvent
        Material seedType = cropToSeed.get(block.getType());
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(
                block,
                block.getState(),
                block.getRelative(0, -1, 0),
                new ItemStack(seedType != null ? seedType : Material.AIR), // Use seed item, fallback to AIR
                player,
                true
        );
        plugin.getServer().getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) {
            sendConfigMessage(player, "no_permission");
            return false;
        }
        return true;
    }
}