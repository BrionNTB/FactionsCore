package net.factionscore.faction;

public record ClaimKey(String levelName, int chunkX, int chunkZ) {

    public static ClaimKey of(String levelName, double x, double z) {
        return new ClaimKey(levelName, (int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
    }
}
