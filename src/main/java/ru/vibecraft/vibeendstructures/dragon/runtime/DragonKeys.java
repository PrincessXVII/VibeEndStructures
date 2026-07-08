package ru.vibecraft.vibeendstructures.dragon.runtime;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class DragonKeys {

    private final NamespacedKey dragonId;
    private final NamespacedKey dragonType;
    private final NamespacedKey arenaId;
    private final NamespacedKey eggDropEligible;
    private final NamespacedKey renewableEgg;

    public DragonKeys(JavaPlugin plugin) {
        this.dragonId = new NamespacedKey(plugin, "dragon_id");
        this.dragonType = new NamespacedKey(plugin, "dragon_type");
        this.arenaId = new NamespacedKey(plugin, "arena_id");
        this.eggDropEligible = new NamespacedKey(plugin, "egg_drop_eligible");
        this.renewableEgg = new NamespacedKey(plugin, "renewable_dragon_egg");
    }

    public NamespacedKey dragonId() {
        return dragonId;
    }

    public NamespacedKey dragonType() {
        return dragonType;
    }

    public NamespacedKey arenaId() {
        return arenaId;
    }

    public NamespacedKey eggDropEligible() {
        return eggDropEligible;
    }

    public NamespacedKey renewableEgg() {
        return renewableEgg;
    }
}
