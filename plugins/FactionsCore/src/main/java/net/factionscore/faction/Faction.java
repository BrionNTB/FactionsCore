package net.factionscore.faction;

import org.powernukkitx.level.Position;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Faction {

    private final String id;
    private String name;
    private double power;
    private Position home;
    private boolean safezone;
    private boolean warzone;
    private double spawnerValue;
    private final Map<UUID, FactionRole> members = new ConcurrentHashMap<>();
    private final Map<String, RelationType> relations = new ConcurrentHashMap<>();

    public Faction(String id, String name, UUID leader) {
        this.id = id;
        this.name = name;
        if (leader != null) {
            this.members.put(leader, FactionRole.LEADER);
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getPower() {
        return power;
    }

    public void setPower(double power) {
        this.power = Math.max(0, power);
    }

    public void addPower(double delta) {
        setPower(this.power + delta);
    }

    public Position getHome() {
        return home;
    }

    public void setHome(Position home) {
        this.home = home;
    }

    public double getSpawnerValue() {
        return spawnerValue;
    }

    public void setSpawnerValue(double spawnerValue) {
        this.spawnerValue = Math.max(0, spawnerValue);
    }

    public boolean isSafezone() {
        return safezone;
    }

    public void setSafezone(boolean safezone) {
        this.safezone = safezone;
    }

    public boolean isWarzone() {
        return warzone;
    }

    public void setWarzone(boolean warzone) {
        this.warzone = warzone;
    }

    public Map<UUID, FactionRole> members() {
        return members;
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public FactionRole roleOf(UUID uuid) {
        return members.get(uuid);
    }

    public UUID getLeader() {
        return members.entrySet().stream()
                .filter(e -> e.getValue() == FactionRole.LEADER)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    public Map<String, RelationType> relations() {
        return relations;
    }

    public RelationType relationWith(String otherFactionId) {
        if (otherFactionId.equals(id)) {
            return RelationType.ALLY;
        }
        return relations.getOrDefault(otherFactionId, RelationType.NEUTRAL);
    }

    /** How many chunks this faction is entitled to claim at its current power. */
    public int maxClaims(double chunkCost) {
        if (chunkCost <= 0) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.floor(power / chunkCost);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("power", power);
        map.put("members", members.size());
        return map;
    }
}
