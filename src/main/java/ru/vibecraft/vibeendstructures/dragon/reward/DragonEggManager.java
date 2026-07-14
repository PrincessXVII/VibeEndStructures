package ru.vibecraft.vibeendstructures.dragon.reward;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.vibecraft.vibeendstructures.dragon.model.DragonArena;
import ru.vibecraft.vibeendstructures.dragon.runtime.DragonKeys;
import ru.vibecraft.vibeendstructures.dragon.runtime.DragonParticles;

import java.util.List;
import java.util.Map;
import java.util.Random;

public final class DragonEggManager {

    private final JavaPlugin plugin;
    private final DragonKeys keys;
    private final Random random = new Random();

    public DragonEggManager(JavaPlugin plugin, DragonKeys keys) {
        this.plugin = plugin;
        this.keys = keys;
    }

    public boolean tryDropEgg(World world, DragonArena arena, double chance) {
        return tryDropEgg(world, arena, chance, null);
    }

    public boolean tryDropEgg(World world, DragonArena arena, double chance, Location preferredAnchor) {
        if (chance <= 0 || random.nextDouble() > chance) {
            return false;
        }
        dropEggFromSky(world, arena, preferredAnchor);
        return true;
    }

    public void forceDropEgg(World world, DragonArena arena) {
        if (world == null || arena == null) {
            return;
        }
        dropEggFromSky(world, arena, null);
    }

    public boolean tryGiveEgg(Player player, double chance) {
        if (chance <= 0 || random.nextDouble() > chance) {
            return false;
        }
        giveOrDrop(player, createRenewableEgg());
        return true;
    }

    public ItemStack createRenewableEgg() {
        ItemStack egg = new ItemStack(Material.DRAGON_EGG);
        ItemMeta meta = egg.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Возобновляемое яйцо дракона", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Можно снова пробудить дракона.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Поставь в Энде на бедрок в центре острова 0 0.", NamedTextColor.DARK_PURPLE)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Призванный яйцом дракон новое яйцо не оставит.", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            meta.setRarity(ItemRarity.EPIC);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(keys.renewableEgg(), PersistentDataType.BYTE, (byte) 1);
            egg.setItemMeta(meta);
        }
        return egg;
    }

    public boolean isRenewableEgg(ItemStack item) {
        if (item == null || item.getType() != Material.DRAGON_EGG || !item.hasItemMeta()) {
            return false;
        }
        Byte value = item.getItemMeta().getPersistentDataContainer().get(keys.renewableEgg(), PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public boolean hasRenewableEgg(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isRenewableEgg(item)) {
                return true;
            }
        }
        return isRenewableEgg(player.getInventory().getItemInOffHand());
    }

    public void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    public void dropEggFromSky(World world, DragonArena arena, Location preferredAnchor) {
        Location target = randomIslandLocation(world, arena, preferredAnchor);
        Location start = target.clone().add(0, 56.0, 0);
        ArmorStand stand = (ArmorStand) world.spawnEntity(start, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setInvulnerable(true);
        stand.addScoreboardTag("vibedragon:egg_drop");
        stand.getEquipment().setHelmet(createRenewableEgg());
        stand.addEquipmentLock(EquipmentSlot.HEAD, ArmorStand.LockType.REMOVING_OR_CHANGING);
        world.playSound(start, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 2.0f, 1.45f);

        final BukkitTask[] taskRef = new BukkitTask[1];
        taskRef[0] = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int ticks;

            @Override
            public void run() {
                if (!stand.isValid()) {
                    taskRef[0].cancel();
                    return;
                }
                ticks += 2;
                double remaining = Math.max(0.0, stand.getLocation().getY() - target.getY());
                stand.teleport(stand.getLocation().subtract(0, Math.max(0.35, remaining * 0.065), 0));
                Location loc = stand.getLocation();
                DragonParticles.spawn(plugin, world, Particle.END_ROD, loc, 12, 0.35, 0.35, 0.35, 0.02);
                DragonParticles.spawn(plugin, world, Particle.DRAGON_BREATH, loc, 18, 0.55, 0.35, 0.55, 0.03);
                DragonParticles.spawn(plugin, world, Particle.PORTAL, loc, 10, 0.45, 0.3, 0.45, 0.08);
                if (ticks % 20 == 0) {
                    world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 1.1f, 1.2f);
                }
                if (loc.getY() <= target.getY() + 1.0 || ticks >= 20 * 12) {
                    stand.remove();
                    ItemStack egg = createRenewableEgg();
                    Item dropped = world.dropItem(target, egg);
                    dropped.setVelocity(dropped.getVelocity().multiply(0.05));
                    dropped.setGlowing(true);
                    dropped.setPickupDelay(30);
                    ItemMeta meta = egg.getItemMeta();
                    if (meta != null && meta.displayName() != null) {
                        dropped.customName(meta.displayName());
                        dropped.setCustomNameVisible(true);
                    }
                    dropped.addScoreboardTag("vibedragon:egg_drop");
                    DragonParticles.spawn(plugin, world, Particle.EXPLOSION_EMITTER, target, 1);
                    DragonParticles.spawn(plugin, world, Particle.END_ROD, target, 80, 1.4, 1.0, 1.4, 0.08);
                    DragonParticles.spawn(plugin, world, Particle.PORTAL, target, 40, 1.0, 0.8, 1.0, 0.1);
                    world.playSound(target, Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 2.0f, 1.35f);
                    org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (dropped.isValid()) {
                            dropped.setGlowing(false);
                        }
                    }, 20L * 6);
                    taskRef[0].cancel();
                }
            }
        }, 0L, 2L);
    }

    private Location randomIslandLocation(World world, DragonArena arena, Location preferredAnchor) {
        Location base = preferredAnchor;
        if (base == null || base.getWorld() == null) {
            Location ground = highestSolid(world, arena.centerX(), arena.centerZ());
            base = ground != null
                    ? ground.add(0.5, 1.1, 0.5)
                    : new Location(world, arena.centerX() + 0.5, arena.height(), arena.centerZ() + 0.5);
        }
        double maxDistance = Math.max(24.0, Math.min(arena.radius() - 10.0, 80.0));
        for (int attempt = 0; attempt < 80; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 6.0 + random.nextDouble() * Math.max(10.0, maxDistance - 6.0);
            int x = (int) Math.round(base.getX() + Math.cos(angle) * distance);
            int z = (int) Math.round(base.getZ() + Math.sin(angle) * distance);
            Location drop = safeDropLocation(world, x, z, base.getY());
            if (drop != null) {
                return drop;
            }
        }
        Location center = safeDropLocation(world, arena.centerX(), arena.centerZ(), base.getY());
        return center != null ? center : base.clone();
    }

    private Location safeDropLocation(World world, int x, int z, double referenceY) {
        Location ground = highestSolid(world, x, z);
        if (ground == null) {
            return null;
        }
        Material under = ground.getBlock().getType();
        if (!under.isSolid() || under == Material.BARRIER || under == Material.BEDROCK) {
            return null;
        }
        if (Math.abs(ground.getY() - referenceY) > 28.0) {
            return null;
        }
        return ground.add(0.5, 1.1, 0.5);
    }

    private Location highestSolid(World world, int x, int z) {
        int max = Math.min(world.getMaxHeight() - 1, 180);
        int min = Math.max(world.getMinHeight(), 0);
        for (int y = max; y >= min; y--) {
            Material type = world.getBlockAt(x, y, z).getType();
            if (type.isSolid() && type != Material.BARRIER) {
                return new Location(world, x, y, z);
            }
        }
        return null;
    }
}
