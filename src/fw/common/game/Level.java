package fw.common.game;

import java.util.List;

public class Level {
    public final String name;
    public final int cols;
    public final int rows;
    public final boolean[][] solid;
    public final SpawnDef spawnFire;
    public final SpawnDef spawnWater;
    public final List<KeyDef> keys;
    public final List<DoorDef> doors;
    public final List<RockDef> rocks;
    public final List<Zone> zones;
    public final List<LeverDef> levers;
    public final List<PlateDef> plates;
    public final List<MechDef> elevators;
    public final List<MechDef> gates;

    public Level(String name, int cols, int rows, boolean[][] solid,
                 SpawnDef spawnFire, SpawnDef spawnWater,
                 List<KeyDef> keys, List<DoorDef> doors, List<RockDef> rocks,
                 List<Zone> zones, List<LeverDef> levers, List<PlateDef> plates,
                 List<MechDef> elevators, List<MechDef> gates) {
        this.name = name;
        this.cols = cols;
        this.rows = rows;
        this.solid = solid;
        this.spawnFire = spawnFire;
        this.spawnWater = spawnWater;
        this.keys = keys;
        this.doors = doors;
        this.rocks = rocks;
        this.zones = zones;
        this.levers = levers;
        this.plates = plates;
        this.elevators = elevators;
        this.gates = gates;
    }

    public static class SpawnDef {
        public final float x, y;
        public SpawnDef(float x, float y) { this.x = x; this.y = y; }
    }

    public static class KeyDef {
        public final String id;
        public final float x, y;
        public KeyDef(String id, float x, float y) { this.id = id; this.x = x; this.y = y; }
    }

    public static class DoorDef {
        public final String id;
        public final float x, y, w, h;
        public DoorDef(String id, float x, float y, float w, float h) {
            this.id = id; this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }

    public static class RockDef {
        public final String id;
        public final float x, y;
        public RockDef(String id, float x, float y) { this.id = id; this.x = x; this.y = y; }
    }

    public static class LeverDef {
        public final String id;
        public final float x, y;
        public final String target;
        public LeverDef(String id, float x, float y, String target) {
            this.id = id; this.x = x; this.y = y; this.target = target;
        }
    }

    public static class PlateDef {
        public final String id;
        public final float x, y, w, h;
        public final String target;
        public PlateDef(String id, float x, float y, float w, float h, String target) {
            this.id = id; this.x = x; this.y = y; this.w = w; this.h = h; this.target = target;
        }
    }

    public static class MechDef {
        public final String id;
        public final float x, y, w, h;
        public final char axis;
        public final float dist;
        public final float speed;
        public MechDef(String id, float x, float y, float w, float h, char axis, float dist, float speed) {
            this.id = id; this.x = x; this.y = y; this.w = w; this.h = h;
            this.axis = axis; this.dist = dist; this.speed = speed;
        }
    }
}
