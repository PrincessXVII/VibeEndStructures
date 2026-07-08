package ru.vibecraft.vibeendstructures.dragon.contribution;

import java.util.UUID;

public final class ContributionData {

    private final UUID playerUuid;
    private String playerName;
    private double damageDealt;
    private double healingDone;
    private double ccTimeApplied;
    private int blocksPlaced;
    private int revives;
    private int deaths;

    public ContributionData(UUID playerUuid, String playerName) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
    }

    public void addDamage(double amount) {
        damageDealt += Math.max(0, amount);
    }

    public void addHealing(double amount) {
        healingDone += Math.max(0, amount);
    }

    public void addCcTime(double seconds) {
        ccTimeApplied += Math.max(0, seconds);
    }

    public void addBlockPlaced() {
        blocksPlaced++;
    }

    public void addRevive() {
        revives++;
    }

    public void addDeath() {
        deaths++;
    }

    public double score() {
        return Math.max(0, damageDealt + healingDone * 1.5 + ccTimeApplied * 50.0 + blocksPlaced * 0.1 + revives * 200.0 - deaths * 50.0);
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public String playerName() {
        return playerName;
    }

    public void playerName(String playerName) {
        this.playerName = playerName;
    }

    public double damageDealt() {
        return damageDealt;
    }

    public double healingDone() {
        return healingDone;
    }

    public double ccTimeApplied() {
        return ccTimeApplied;
    }

    public int blocksPlaced() {
        return blocksPlaced;
    }

    public int revives() {
        return revives;
    }

    public int deaths() {
        return deaths;
    }
}
