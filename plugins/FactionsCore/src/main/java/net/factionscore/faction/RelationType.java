package net.factionscore.faction;

public enum RelationType {
    NEUTRAL,
    ALLY,
    TRUCE,
    ENEMY;

    public boolean allowsFriendlyFire() {
        return this == NEUTRAL || this == ENEMY;
    }
}
