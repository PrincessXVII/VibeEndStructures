package ru.vibecraft.vibeendstructures.dragon.reward;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import ru.vibecraft.vibeendstructures.dragon.contribution.ContributionResult;
import ru.vibecraft.vibeendstructures.dragon.contribution.ContributionSnapshot;
import ru.vibecraft.vibeendstructures.dragon.model.DragonArena;
import ru.vibecraft.vibeendstructures.dragon.model.DragonDefinition;
import ru.vibecraft.vibeendstructures.dragon.model.DragonType;
import ru.vibecraft.vibeendstructures.dragon.model.RewardTier;
import ru.vibecraft.vibeendstructures.dragon.runtime.DragonParticles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class RewardDistributor {

    private static final int LOOT_GLOW_TICKS = 20 * 4;
    private static final double SCATTER_MIN = 6.0;

    private final JavaPlugin plugin;
    private final TitleManager titleManager;
    private final DragonEggManager eggManager;
    private final Random random = new Random();

    public RewardDistributor(JavaPlugin plugin, TitleManager titleManager, DragonEggManager eggManager) {
        this.plugin = plugin;
        this.titleManager = titleManager;
        this.eggManager = eggManager;
    }

    public void distribute(ContributionSnapshot snapshot, DragonDefinition definition, DragonArena arena, World world,
                           double minContribution, boolean eggDropEligible, boolean scheduledSpawn) {
        distribute(snapshot, definition, arena, world, null, minContribution, eggDropEligible, scheduledSpawn);
    }

    public void distribute(ContributionSnapshot snapshot, DragonDefinition definition, DragonArena arena, World world,
                           Location deathLocation, double minContribution, boolean eggDropEligible, boolean scheduledSpawn) {
        if (world == null || arena == null || definition == null) {
            plugin.getLogger().warning("Skipped dragon rewards: world/arena/definition missing");
            return;
        }

        Location anchor = resolveAnchor(world, arena, deathLocation);
        int dropped = 0;
        try {
            if (!hasCombatDamage(snapshot)) {
                plugin.getLogger().info("Skipped dragon loot for " + definition.id()
                        + " at " + arena.id() + ": no player dealt damage");
                return;
            }

            ContributionResult top = snapshot.results().getFirst();
            boolean rewardedAny = false;
            for (ContributionResult result : snapshot.results()) {
                if (result.damageDealt() <= 0 || result.contribution() < minContribution) {
                    continue;
                }
                RewardTier tier = tierFor(definition, result.contribution()).orElse(null);
                if (tier == null) {
                    continue;
                }
                titleManager.executeRewardCommands(result.playerUuid(), result.playerName(), tier);
                String tierName = tierName(tier);
                dropped += scatterLoot(world, arena, rewardLoot(definition, tierName, result.contribution()),
                        scatterCount(definition, tierName), anchor);
                dropped += scatterExtraLoot(world, arena, definition.type(), tierName, anchor);
                rewardedAny = true;
                Player player = Bukkit.getPlayer(result.playerUuid());
                if (player != null && player.isOnline()) {
                    player.sendMessage(Component.text("Награда за вклад в бой: " + definition.displayName()
                            + " (" + percent(result.contribution()) + ")"));
                }
            }
            if (!rewardedAny) {
                // At least one player hit the dragon, but nobody reached reward threshold.
                dropped += scatterDefaultLoot(world, arena, definition, anchor);
            }
            dropped += scatterGuaranteedBurst(world, arena, definition, anchor);
            Player topPlayer = Bukkit.getPlayer(top.playerUuid());
            tryDropBattleEgg(world, arena, definition, eggDropEligible, scheduledSpawn, anchor, topPlayer);
            logLoot(arena, dropped);
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Dragon reward distribution failed for " + definition.id()
                    + " at " + arena.id() + ": " + ex.getMessage());
            ex.printStackTrace();
            if (hasCombatDamage(snapshot)) {
                try {
                    dropped += scatterDefaultLoot(world, arena, definition, anchor);
                    dropped += scatterGuaranteedBurst(world, arena, definition, anchor);
                    logLoot(arena, dropped);
                } catch (RuntimeException nested) {
                    plugin.getLogger().severe("Fallback dragon loot also failed: " + nested.getMessage());
                }
            }
        }
    }

    private boolean hasCombatDamage(ContributionSnapshot snapshot) {
        if (snapshot == null || snapshot.results().isEmpty()) {
            return false;
        }
        for (ContributionResult result : snapshot.results()) {
            if (result.damageDealt() > 0) {
                return true;
            }
        }
        return false;
    }

    public boolean rewardManually(Player player, DragonDefinition definition, String tierName) {
        String resolvedTier = normalizeTierName(tierName);
        if (resolvedTier == null) {
            resolvedTier = definition.rewardTiers().stream()
                    .max(Comparator.comparingDouble(RewardTier::minContribution))
                    .map(this::tierName)
                    .orElse("rare");
        }
        RewardTier tier = tierByName(definition, resolvedTier).orElse(null);
        if (tier != null) {
            titleManager.executeRewardCommands(player.getUniqueId(), player.getName(), tier);
        }
        eggManager.giveOrDrop(player, rewardLoot(definition, resolvedTier, -1));
        for (ItemStack extra : thematicExtras(definition.type(), resolvedTier, 2)) {
            eggManager.giveOrDrop(player, extra);
        }
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

    private String normalizeTierName(String tierName) {
        if (tierName == null || tierName.isBlank()) {
            return null;
        }
        String lower = tierName.toLowerCase();
        return switch (lower) {
            case "common", "uncommon", "rare", "epic", "legendary" -> lower;
            default -> null;
        };
    }

    private ItemStack rewardLoot(DragonDefinition definition, String tierName, double contribution) {
        Material material = rewardMaterial(definition.type(), tierName);
        int amount = rewardAmount(definition.type(), tierName);
        return themedItem(material, amount, rewardName(definition.type(), tierName), definition, tierName, contribution,
                flavorLore(definition.type(), tierName));
    }

    private ItemStack themedItem(Material material, int amount, String name, DragonDefinition definition,
                                 String tierName, double contribution, List<String> flavor) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            TextColor color = tierColor(tierName);
            meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Трофей: ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(definition.displayName(), NamedTextColor.GRAY))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Редкость: ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(tierLabel(tierName), color))
                    .decoration(TextDecoration.ITALIC, false));
            if (contribution >= 0) {
                lore.add(Component.text("Вклад: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(percent(contribution), NamedTextColor.GRAY))
                        .decoration(TextDecoration.ITALIC, false));
            }
            for (String line : flavor) {
                lore.add(Component.text(line, NamedTextColor.DARK_PURPLE)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            meta.setRarity(itemRarity(tierName));
            if ("epic".equals(tierName) || "legendary".equals(tierName) || "rare".equals(tierName)) {
                meta.setEnchantmentGlintOverride(true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<String> flavorLore(DragonType type, String tierName) {
        if (type == DragonType.FIRE) {
            return switch (tierName) {
                case "legendary" -> List.of("Ещё тлеет жаром последнего вздоха.");
                case "epic" -> List.of("Ковка в лаве Энда оставила шрамы.");
                case "rare" -> List.of("Пульсирует, будто живое пламя.");
                case "uncommon" -> List.of("Пахнет серой и раскалённым камнем.");
                default -> List.of("Пепел, переживший падение дракона.");
            };
        }
        if (type == DragonType.ICE) {
            return switch (tierName) {
                case "legendary" -> List.of("Внутри всё ещё воет метель.");
                case "epic" -> List.of("Холод кусает даже сквозь перчатки.");
                case "rare" -> List.of("Лёд не тает — он помнит дракона.");
                case "uncommon" -> List.of("Иней рисует руны Края.");
                default -> List.of("Крошечная искра северного сияния.");
            };
        }
        return switch (tierName) {
            case "legendary" -> List.of("Взгляд пустоты застыл в кости.");
            case "epic" -> List.of("Чешуя, закалённая порталами.");
            case "rare" -> List.of("Осколок звезды, упавшей в Энд.");
            case "uncommon" -> List.of("Шёпот опыта древних битв.");
            default -> List.of("Жемчуг, впитавший тень дракона.");
        };
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
                case "rare" -> 12;
                case "uncommon" -> 6;
                default -> 12;
            };
        }
        if (type == DragonType.ICE) {
            return switch (tierName) {
                case "legendary", "epic" -> 1;
                case "rare" -> 6;
                case "uncommon", "common" -> 12;
                default -> 12;
            };
        }
        return switch (tierName) {
            case "legendary", "epic" -> 1;
            case "rare" -> 2;
            case "uncommon" -> 8;
            default -> 12;
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

    private int scatterCount(DragonDefinition definition, String tierName) {
        if (definition.type() == DragonType.FIRE && "legendary".equals(tierName)) {
            return 1;
        }
        return switch (tierName) {
            case "legendary" -> 1;
            case "epic" -> 3;
            case "rare" -> 5;
            case "uncommon" -> 7;
            default -> 8;
        };
    }

    private int scatterDefaultLoot(World world, DragonArena arena, DragonDefinition definition, Location anchor) {
        int dropped = 0;
        dropped += scatterLoot(world, arena, rewardLoot(definition, "common", -1), 10, anchor);
        dropped += scatterLoot(world, arena, rewardLoot(definition, "uncommon", -1), 5, anchor);
        dropped += scatterLoot(world, arena, rewardLoot(definition, "rare", -1), 2, anchor);
        dropped += scatterExtraLoot(world, arena, definition.type(), "rare", anchor);
        plugin.getLogger().info("Dropped fallback dragon loot for " + definition.id()
                + " at arena " + arena.id() + " (" + dropped + " stacks)");
        return dropped;
    }

    private int scatterGuaranteedBurst(World world, DragonArena arena, DragonDefinition definition, Location anchor) {
        int dropped = 0;
        for (ItemStack stack : thematicExtras(definition.type(), "common", 6)) {
            dropped += scatterLoot(world, arena, stack, 1, anchor);
        }
        for (ItemStack stack : thematicExtras(definition.type(), "uncommon", 3)) {
            dropped += scatterLoot(world, arena, stack, 1, anchor);
        }
        return dropped;
    }

    private void tryDropBattleEgg(World world, DragonArena arena, DragonDefinition definition, boolean eggDropEligible,
                                  boolean scheduledSpawn, Location anchor, Player notifyPlayer) {
        if (!eggDropEligible) {
            plugin.getLogger().info("Dragon egg skipped for " + definition.id() + ": dragon was summoned by egg");
            return;
        }
        double chance = scheduledSpawn ? scheduledEggDropChance() : definition.eggDropChance();
        boolean eggDropped = eggManager.tryDropEgg(world, arena, chance, anchor);
        plugin.getLogger().info("Dragon egg roll for " + definition.id() + " at " + arena.id()
                + ": chance=" + chance + ", scheduled=" + scheduledSpawn + ", dropped=" + eggDropped);
        if (eggDropped && notifyPlayer != null && notifyPlayer.isOnline()) {
            notifyPlayer.sendMessage(Component.text("Яйцо дракона падает с неба!", NamedTextColor.LIGHT_PURPLE));
        }
    }

    private double scheduledEggDropChance() {
        if (plugin instanceof ru.vibecraft.vibeendstructures.VibeEndStructuresPlugin vibePlugin) {
            return vibePlugin.getDragonConfig().getGeneralConfig().scheduledEggDropChance();
        }
        return 0.08;
    }

    private int scatterExtraLoot(World world, DragonArena arena, DragonType type, String tierName, Location anchor) {
        int attempts = switch (tierName) {
            case "legendary" -> 4;
            case "epic" -> 3;
            case "rare" -> 2;
            case "uncommon" -> 1;
            default -> 1;
        };
        int dropped = 0;
        for (ItemStack extra : thematicExtras(type, tierName, attempts)) {
            dropped += scatterLoot(world, arena, extra, 1, anchor);
        }
        return dropped;
    }

    private List<ItemStack> thematicExtras(DragonType type, String tierName, int count) {
        List<ItemStack> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(randomThematicExtra(type, tierName));
        }
        return items;
    }

    private ItemStack randomThematicExtra(DragonType type, String tierName) {
        if (type == DragonType.FIRE) {
            return switch (random.nextInt(10)) {
                case 0 -> namedExtra(Material.MAGMA_CREAM, 4, "Магмовая слеза", "uncommon",
                        "Застыла, не успев остыть.");
                case 1 -> namedExtra(Material.GOLD_INGOT, 3, "Оплавленное золото", "uncommon",
                        "Края слитка ещё мерцают.");
                case 2 -> namedExtra(Material.COAL, 16, "Уголь драконьего жара", "common",
                        "Горит дольше обычного.");
                case 3 -> namedExtra(Material.GUNPOWDER, 8, "Пепел вспышки", "common",
                        "Пахнет метеоритным ударом.");
                case 4 -> namedExtra(Material.BLAZE_POWDER, 8, "Искра Фларе", "uncommon",
                        "Тянется к ладони, как к топливу.");
                case 5 -> namedExtra(Material.FIRE_CORAL, 2, "Коралл пепельных рифов", "rare",
                        "Редкий след огненных бурь.");
                case 6 -> namedExtra(Material.ORANGE_DYE, 6, "Пигмент пламени", "common",
                        "Краска цвета заката над Эндом.");
                case 7 -> namedExtra(Material.NETHERRACK, 12, "Осколок адского ложа", "common",
                        "Принесён метеором.");
                case 8 -> namedExtra(Material.LAVA_BUCKET, 1, "Ковш живого жара", "rare",
                        "Не остывает в Эндовом воздухе.");
                default -> namedExtra(Material.FIRE_CHARGE, 6, "Сгусток метеора", "uncommon",
                        "Ещё слышно далёкий свист.");
            };
        }
        if (type == DragonType.ICE) {
            return switch (random.nextInt(10)) {
                case 0 -> namedExtra(Material.SNOWBALL, 16, "Снежок бурана", "common",
                        "Не тает на ладони.");
                case 1 -> namedExtra(Material.PACKED_ICE, 4, "Спрессованная метель", "uncommon",
                        "Внутри крутится белый вихрь.");
                case 2 -> namedExtra(Material.PRISMARINE_CRYSTALS, 4, "Кристалл инея", "uncommon",
                        "Светится холодным бирюзовым.");
                case 3 -> namedExtra(Material.LAPIS_LAZULI, 6, "Лазурь ледника", "common",
                        "Пигмент северного неба.");
                case 4 -> namedExtra(Material.ICE, 8, "Тонкий энд-лёд", "common",
                        "Хрустит, как стекло.");
                case 5 -> namedExtra(Material.BLUE_ICE, 2, "Осколок синего сердца", "rare",
                        "Жжёт холодом.");
                case 6 -> namedExtra(Material.POWDER_SNOW_BUCKET, 1, "Урна пороши", "rare",
                        "Дышит морозным паром.");
                case 7 -> namedExtra(Material.WHITE_DYE, 6, "Инейная пыль", "common",
                        "Оседает узорами.");
                case 8 -> namedExtra(Material.SEA_LANTERN, 1, "Фонарь метели", "epic",
                        "Свет режет тьму Края.");
                default -> namedExtra(Material.PRISMARINE_SHARD, 5, "Осколок ледяной чешуи", "uncommon",
                        "Острый, как клык.");
            };
        }
        return switch (random.nextInt(10)) {
            case 0 -> namedExtra(Material.ENDER_PEARL, 4, "Жемчуг тени", "common",
                    "Тянется к порталам.");
            case 1 -> namedExtra(Material.EXPERIENCE_BOTTLE, 6, "Флакон энд-опыта", "uncommon",
                    "Шепчет о победах.");
            case 2 -> namedExtra(Material.OBSIDIAN, 4, "Плита пустоты", "common",
                    "Тяжёлая, как ночь.");
            case 3 -> namedExtra(Material.CHORUS_FRUIT, 6, "Хорус победителя", "common",
                    "Сладкий привкус телепорта.");
            case 4 -> namedExtra(Material.END_ROD, 2, "Стержень сияния", "uncommon",
                    "Мягко пульсирует.");
            case 5 -> namedExtra(Material.PURPUR_BLOCK, 8, "Обломок пурпурных стен", "common",
                    "Память городов Края.");
            case 6 -> namedExtra(Material.ENDER_EYE, 2, "Око после битвы", "rare",
                    "Смотрит туда, где был дракон.");
            case 7 -> namedExtra(Material.SHULKER_SHELL, 1, "Обломок шалкера", "rare",
                    "Притянут бурей порталов.");
            case 8 -> namedExtra(Material.AMETHYST_SHARD, 5, "Осколок энд-аметиста", "uncommon",
                    "Звенит едва слышно.");
            default -> namedExtra(Material.DIAMOND, 1, "Звезда Края", "rare",
                    "Упала вместе с рёвом дракона.");
        };
    }

    private ItemStack namedExtra(Material material, int amount, String name, String tierName, String flavor) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            TextColor color = tierColor(tierName);
            meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Редкость: ", NamedTextColor.DARK_GRAY)
                            .append(Component.text(tierLabel(tierName), color))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text(flavor, NamedTextColor.DARK_PURPLE)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            meta.setRarity(itemRarity(tierName));
            if ("epic".equals(tierName) || "legendary".equals(tierName) || "rare".equals(tierName)) {
                meta.setEnchantmentGlintOverride(true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private int scatterLoot(World world, DragonArena arena, ItemStack template, int count, Location anchor) {
        if (world == null || arena == null || template == null || template.getType().isAir() || count <= 0) {
            return 0;
        }
        int dropped = 0;
        for (int i = 0; i < count; i++) {
            Location location = randomIslandLocation(world, arena, anchor);
            ItemStack item = template.clone();
            int perStack = Math.max(1, template.getAmount() / Math.max(1, Math.max(1, count / 2)));
            item.setAmount(perStack);
            Item entity = world.dropItem(location, item);
            entity.setVelocity(new Vector(
                    (random.nextDouble() - 0.5) * 0.15,
                    0.18 + random.nextDouble() * 0.12,
                    (random.nextDouble() - 0.5) * 0.15
            ));
            entity.setPickupDelay(25);
            entity.setTicksLived(1);
            entity.addScoreboardTag("vibedragon:loot");
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.displayName() != null) {
                entity.customName(meta.displayName());
                entity.setCustomNameVisible(true);
            }
            highlightLoot(entity, definitionParticle(template));
            dropped++;
        }
        return dropped;
    }

    private Particle definitionParticle(ItemStack template) {
        Material type = template.getType();
        if (type == Material.BLAZE_POWDER || type == Material.FIRE_CHARGE || type == Material.MAGMA_CREAM
                || type == Material.LAVA_BUCKET || type == Material.BLAZE_ROD) {
            return Particle.FLAME;
        }
        if (type == Material.SNOWBALL || type == Material.ICE || type == Material.PACKED_ICE
                || type == Material.BLUE_ICE || type == Material.POWDER_SNOW_BUCKET) {
            return Particle.SNOWFLAKE;
        }
        return Particle.END_ROD;
    }

    private void highlightLoot(Item entity, Particle particle) {
        entity.setGlowing(true);
        Location loc = entity.getLocation();
        World world = loc.getWorld();
        if (world != null) {
            DragonParticles.spawn(plugin, world, particle, loc, 18, 0.35, 0.35, 0.35, 0.02);
            DragonParticles.spawn(plugin, world, Particle.PORTAL, loc, 12, 0.4, 0.35, 0.4, 0.05);
            world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.55f, 1.35f);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!entity.isValid()) {
                return;
            }
            Location pulse = entity.getLocation();
            World pulseWorld = pulse.getWorld();
            if (pulseWorld != null) {
                DragonParticles.spawn(plugin, pulseWorld, particle, pulse, 24, 0.45, 0.45, 0.45, 0.03);
                DragonParticles.spawn(plugin, pulseWorld, Particle.END_ROD, pulse, 10, 0.3, 0.4, 0.3, 0.02);
            }
        }, 10L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (entity.isValid()) {
                entity.setGlowing(false);
            }
        }, LOOT_GLOW_TICKS);
    }

    private void logLoot(DragonArena arena, int dropped) {
        plugin.getLogger().info("Dragon loot scatter complete for arena " + arena.id()
                + ": " + dropped + " item stacks near "
                + arena.centerX() + "," + arena.centerZ());
        if (dropped <= 0) {
            plugin.getLogger().warning("Dragon loot scatter produced 0 stacks for arena " + arena.id());
        }
    }

    private Location resolveAnchor(World world, DragonArena arena, Location deathLocation) {
        if (deathLocation != null && deathLocation.getWorld() != null) {
            Location grounded = highestSolid(world, deathLocation.getBlockX(), deathLocation.getBlockZ());
            if (grounded != null) {
                return grounded.add(0.5, 1.2, 0.5);
            }
            return deathLocation.clone().add(0, 1.0, 0);
        }
        Location centerGround = highestSolid(world, arena.centerX(), arena.centerZ());
        if (centerGround != null) {
            return centerGround.add(0.5, 1.2, 0.5);
        }
        return new Location(world, arena.centerX() + 0.5, arena.height(), arena.centerZ() + 0.5);
    }

    private Location randomIslandLocation(World world, DragonArena arena, Location anchor) {
        Location base = anchor != null ? anchor : resolveAnchor(world, arena, null);
        double maxDistance = Math.max(28.0, Math.min(arena.radius() - 8.0, 96.0));
        for (int attempt = 0; attempt < 96; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = SCATTER_MIN + random.nextDouble() * Math.max(8.0, maxDistance - SCATTER_MIN);
            int x = (int) Math.round(base.getX() + Math.cos(angle) * distance);
            int z = (int) Math.round(base.getZ() + Math.sin(angle) * distance);
            Location drop = safeLootLocation(world, x, z, base.getY());
            if (drop != null) {
                return drop;
            }
        }
        // Last resort: walk outward from arena center until solid ground is found.
        for (int radius = 2; radius <= 48; radius += 2) {
            for (int i = 0; i < 12; i++) {
                double angle = (Math.PI * 2.0 * i) / 12.0;
                int x = arena.centerX() + (int) Math.round(Math.cos(angle) * radius);
                int z = arena.centerZ() + (int) Math.round(Math.sin(angle) * radius);
                Location drop = safeLootLocation(world, x, z, base.getY());
                if (drop != null) {
                    return drop;
                }
            }
        }
        Location center = safeLootLocation(world, arena.centerX(), arena.centerZ(), base.getY());
        if (center != null) {
            return center;
        }
        return base.clone().add((random.nextDouble() - 0.5) * 2.0, 0.2, (random.nextDouble() - 0.5) * 2.0);
    }

    /**
     * Returns a drop point only when there is a real solid block under it (not void / air column).
     */
    private Location safeLootLocation(World world, int x, int z, double referenceY) {
        Location ground = highestSolid(world, x, z);
        if (ground == null) {
            return null;
        }
        Material under = ground.getBlock().getType();
        if (!under.isSolid() || under == Material.BARRIER || under == Material.BEDROCK) {
            return null;
        }
        // Reject thin floating scraps / void-edge pillars far below the island surface.
        if (Math.abs(ground.getY() - referenceY) > 28.0) {
            return null;
        }
        Location above = ground.clone().add(0.5, 1.15, 0.5);
        Material airCheck = world.getBlockAt(above.getBlockX(), above.getBlockY(), above.getBlockZ()).getType();
        if (airCheck.isSolid() && airCheck != Material.BARRIER) {
            above = ground.clone().add(0.5, 1.05, 0.5);
        }
        return above;
    }

    private Location highestSolid(World world, int x, int z) {
        int max = Math.min(world.getMaxHeight() - 1, 180);
        int min = Math.max(world.getMinHeight(), 0);
        for (int y = max; y >= min; y--) {
            Material type = world.getBlockAt(x, y, z).getType();
            if (type.isSolid() && type != Material.BARRIER) {
                // Require at least one non-air block — pure void columns never reach here.
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
