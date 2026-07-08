package ru.vibecraft.vibeendstructures.dragon.reward;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import ru.vibecraft.vibeendstructures.dragon.contribution.ContributionResult;
import ru.vibecraft.vibeendstructures.dragon.contribution.ContributionSnapshot;
import ru.vibecraft.vibeendstructures.dragon.model.DragonArena;
import ru.vibecraft.vibeendstructures.dragon.model.DragonDefinition;
import ru.vibecraft.vibeendstructures.dragon.model.DragonType;
import ru.vibecraft.vibeendstructures.dragon.model.RewardTier;

import java.util.List;
import java.util.Comparator;
import java.util.Optional;
import java.util.Random;

public final class RewardDistributor {

    private final JavaPlugin plugin;
    private final TitleManager titleManager;
    private final DragonEggManager eggManager;
    private final Random random = new Random();

    public RewardDistributor(JavaPlugin plugin, TitleManager titleManager, DragonEggManager eggManager) {
        this.plugin = plugin;
        this.titleManager = titleManager;
        this.eggManager = eggManager;
    }

    public void distribute(ContributionSnapshot snapshot, DragonDefinition definition, DragonArena arena, World world, double minContribution, boolean eggDropEligible) {
        if (world == null || arena == null) {
            return;
        }
        if (snapshot.results().isEmpty()) {
            scatterDefaultLoot(world, arena, definition);
            tryDropBattleEgg(world, arena, definition, eggDropEligible, null);
            return;
        }
        ContributionResult top = snapshot.results().getFirst();
        boolean rewardedAny = false;
        for (ContributionResult result : snapshot.results()) {
            if (result.contribution() < minContribution) {
                continue;
            }
            RewardTier tier = tierFor(definition, result.contribution()).orElse(null);
            if (tier == null) {
                continue;
            }
            titleManager.executeRewardCommands(result.playerUuid(), result.playerName(), tier);
            scatterLoot(world, arena, rewardLoot(definition, tier, result.contribution()), scatterCount(tier));
            rewardedAny = true;
            Player player = Bukkit.getPlayer(result.playerUuid());
            if (player != null && player.isOnline()) {
                player.sendMessage(Component.text("Награда за вклад в бой: " + definition.displayName() + " (" + percent(result.contribution()) + ")"));
            }
        }
        if (!rewardedAny) {
            scatterDefaultLoot(world, arena, definition);
        }
        Player topPlayer = Bukkit.getPlayer(top.playerUuid());
        tryDropBattleEgg(world, arena, definition, eggDropEligible, topPlayer);
    }

    public boolean rewardManually(Player player, DragonDefinition definition, String tierName) {
        RewardTier tier = tierByName(definition, tierName).orElseGet(() -> definition.rewardTiers().stream()
                .max(Comparator.comparingDouble(RewardTier::minContribution))
                .orElse(null));
        if (tier == null) {
            return false;
        }
        titleManager.executeRewardCommands(player.getUniqueId(), player.getName(), tier);
        eggManager.giveOrDrop(player, rewardLoot(definition, tier, -1));
        eggManager.tryGiveEgg(player, definition.eggDropChance());
        return true;
    }

    private Optional<RewardTier> tierFor(DragonDefinition definition, double contribution) {
        return definition.rewardTiers().stream()
                .filter(tier -> contribution >= tier.minContribution())
                .max(Comparator.comparingDouble(RewardTier::minContribution));
    }

    private Optional<RewardTier> tierByName(DragonDefinition definition, String tierName) {
        if (tierName == null || tierName.isBlank()) {
            return Optional.empty();
        }
        String needle = "/" + tierName.toLowerCase();
        return definition.rewardTiers().stream()
                .filter(tier -> tier.lootTable().toLowerCase().endsWith(needle))
                .findFirst();
    }

    private ItemStack rewardLoot(DragonDefinition definition, RewardTier tier, double contribution) {
        String tierName = tierName(tier);
        Material material = rewardMaterial(definition.type(), tierName);
        int amount = rewardAmount(definition.type(), tierName);
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            TextColor color = tierColor(tierName);
            meta.displayName(Component.text(rewardName(definition.type(), tierName), color)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(rewardLore(definition, tierName, contribution));
            meta.setRarity(itemRarity(tierName));
            if ("epic".equals(tierName) || "legendary".equals(tierName)) {
                meta.setEnchantmentGlintOverride(true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material rewardMaterial(DragonType type, String tierName) {
        if (type == DragonType.FIRE) {
            return switch (tierName) {
                case "legendary" -> Material.DRAGON_HEAD;
                case "epic" -> Material.NETHERITE_SCRAP;
                case "rare" -> Material.FIRE_CHARGE;
                case "uncommon" -> Material.BLAZE_ROD;
                default -> Material.BLAZE_POWDER;
            };
        }
        if (type == DragonType.ICE) {
            return switch (tierName) {
                case "legendary" -> Material.DRAGON_HEAD;
                case "epic" -> Material.NETHERITE_SCRAP;
                case "rare" -> Material.BLUE_ICE;
                case "uncommon" -> Material.PACKED_ICE;
                default -> Material.SNOWBALL;
            };
        }
        return switch (tierName) {
            case "legendary" -> Material.DRAGON_HEAD;
            case "epic" -> Material.NETHERITE_SCRAP;
            case "rare" -> Material.DIAMOND;
            case "uncommon" -> Material.EXPERIENCE_BOTTLE;
            default -> Material.ENDER_PEARL;
        };
    }

    private int rewardAmount(DragonType type, String tierName) {
        if (type == DragonType.FIRE) {
            return switch (tierName) {
                case "legendary", "epic" -> 1;
                case "rare" -> 16;
                case "uncommon" -> 8;
                default -> 16;
            };
        }
        if (type == DragonType.ICE) {
            return switch (tierName) {
                case "legendary", "epic" -> 1;
                case "rare" -> 8;
                case "uncommon", "common" -> 16;
                default -> 16;
            };
        }
        return switch (tierName) {
            case "legendary", "epic" -> 1;
            case "rare" -> 2;
            case "uncommon" -> 12;
            default -> 16;
        };
    }

    private String rewardName(DragonType type, String tierName) {
        if (type == DragonType.FIRE) {
            return switch (tierName) {
                case "legendary" -> "Голова Огненного Дракона";
                case "epic" -> "Оплавленная незеритовая чешуя";
                case "rare" -> "Сердце пламени";
                case "uncommon" -> "Пламенный жезл";
                default -> "Пепел огненного дракона";
            };
        }
        if (type == DragonType.ICE) {
            return switch (tierName) {
                case "legendary" -> "Голова Ледяного Дракона";
                case "epic" -> "Морозная незеритовая чешуя";
                case "rare" -> "Сердце синего льда";
                case "uncommon" -> "Ледяная пластина";
                default -> "Снежная искра";
            };
        }
        return switch (tierName) {
            case "legendary" -> "Голова Дракона Края";
            case "epic" -> "Незеритовая чешуя Края";
            case "rare" -> "Алмазный осколок Энда";
            case "uncommon" -> "Сосуд опыта Края";
            default -> "Жемчуг пустоты";
        };
    }

    private List<Component> rewardLore(DragonDefinition definition, String tierName, double contribution) {
        Component trophy = Component.text("Трофей: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(definition.displayName(), NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false);
        Component rarity = Component.text("Редкость: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(tierLabel(tierName), tierColor(tierName)))
                .decoration(TextDecoration.ITALIC, false);
        Component hint = Component.text("Разбросан по острову после победы над драконом.", NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.ITALIC, false);
        if (contribution >= 0) {
            return List.of(
                    trophy,
                    rarity,
                    Component.text("Вклад: ", NamedTextColor.DARK_GRAY)
                            .append(Component.text(percent(contribution), NamedTextColor.GRAY))
                            .decoration(TextDecoration.ITALIC, false),
                    hint
            );
        }
        return List.of(trophy, rarity, hint);
    }

    private TextColor tierColor(String tierName) {
        return switch (tierName) {
            case "legendary" -> NamedTextColor.GOLD;
            case "epic" -> NamedTextColor.LIGHT_PURPLE;
            case "rare" -> NamedTextColor.AQUA;
            case "uncommon" -> NamedTextColor.GREEN;
            default -> NamedTextColor.WHITE;
        };
    }

    private ItemRarity itemRarity(String tierName) {
        return switch (tierName) {
            case "legendary", "epic" -> ItemRarity.EPIC;
            case "rare" -> ItemRarity.RARE;
            case "uncommon" -> ItemRarity.UNCOMMON;
            default -> ItemRarity.COMMON;
        };
    }

    private String tierLabel(String tierName) {
        return switch (tierName) {
            case "legendary" -> "легендарная";
            case "epic" -> "эпическая";
            case "rare" -> "редкая";
            case "uncommon" -> "необычная";
            default -> "обычная";
        };
    }

    private int scatterCount(RewardTier tier) {
        return switch (tierName(tier)) {
            case "legendary" -> 2;
            case "epic" -> 4;
            case "rare" -> 6;
            case "uncommon" -> 8;
            default -> 10;
        };
    }

    private void scatterDefaultLoot(World world, DragonArena arena, DragonDefinition definition) {
        RewardTier common = tierByName(definition, "common").orElse(null);
        RewardTier uncommon = tierByName(definition, "uncommon").orElse(null);
        RewardTier rare = tierByName(definition, "rare").orElse(null);
        if (common != null) {
            scatterLoot(world, arena, rewardLoot(definition, common, -1), 12);
        }
        if (uncommon != null) {
            scatterLoot(world, arena, rewardLoot(definition, uncommon, -1), 6);
        }
        if (rare != null) {
            scatterLoot(world, arena, rewardLoot(definition, rare, -1), 2);
        }
        plugin.getLogger().info("Dropped fallback dragon loot for " + definition.id() + " at arena " + arena.id());
    }

    private void tryDropBattleEgg(World world, DragonArena arena, DragonDefinition definition, boolean eggDropEligible, Player notifyPlayer) {
        if (!eggDropEligible) {
            plugin.getLogger().info("Dragon egg skipped for " + definition.id() + ": dragon was summoned by egg");
            return;
        }
        boolean eggDropped = eggManager.tryDropEgg(world, arena, definition.eggDropChance());
        plugin.getLogger().info("Dragon egg roll for " + definition.id() + " at " + arena.id()
                + ": chance=" + definition.eggDropChance() + ", dropped=" + eggDropped);
        if (eggDropped && notifyPlayer != null && notifyPlayer.isOnline()) {
            notifyPlayer.sendMessage(Component.text("Яйцо дракона падает где-то на острове."));
        }
    }

    private void scatterLoot(World world, DragonArena arena, ItemStack template, int count) {
        if (world == null || arena == null) {
            return;
        }
        for (int i = 0; i < count; i++) {
            Location location = randomIslandLocation(world, arena);
            ItemStack item = template.clone();
            item.setAmount(Math.max(1, template.getAmount() / Math.max(1, count / 2)));
            Item dropped = world.dropItemNaturally(location, item);
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.displayName() != null) {
                dropped.customName(meta.displayName());
                dropped.setCustomNameVisible(true);
            }
        }
    }

    private Location randomIslandLocation(World world, DragonArena arena) {
        for (int attempt = 0; attempt < 48; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 8.0 + random.nextDouble() * Math.max(16.0, arena.radius() - 12.0);
            int x = arena.centerX() + (int) Math.round(Math.cos(angle) * distance);
            int z = arena.centerZ() + (int) Math.round(Math.sin(angle) * distance);
            Location ground = highestSolid(world, x, z);
            if (ground != null && ground.getBlock().getType() != Material.BEDROCK) {
                return ground.add(0.5, 1.15, 0.5);
            }
        }
        return new Location(world, arena.centerX() + 0.5, arena.height(), arena.centerZ() + 0.5);
    }

    private Location highestSolid(World world, int x, int z) {
        int max = Math.min(world.getMaxHeight() - 1, 160);
        for (int y = max; y >= world.getMinHeight(); y--) {
            Material type = world.getBlockAt(x, y, z).getType();
            if (type.isSolid() && type != Material.BARRIER) {
                return new Location(world, x, y, z);
            }
        }
        return null;
    }

    private String tierName(RewardTier tier) {
        String lootTable = tier.lootTable();
        int slash = lootTable.lastIndexOf('/');
        return slash < 0 ? "common" : lootTable.substring(slash + 1).toLowerCase();
    }

    private String percent(double value) {
        return String.format(java.util.Locale.US, "%.1f%%", value * 100.0);
    }
}
