package com.politics;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class CountryData extends SavedData {
    public static class Country {
        public UUID id;
        public String name = "";
        public String capitalName = "";
        public UUID presidentId;
        public String presidentName = "";
        public List<String> deputies = new ArrayList<>();
        public String flagItem = "minecraft:white_banner";
        public String dimension = "minecraft:overworld";
        public BlockPos capitalPos = BlockPos.ZERO;
        public float hue;
        public int color;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            t.putString("Name", name);
            t.putString("Capital", capitalName);
            t.putUUID("President", presidentId);
            t.putString("PresidentName", presidentName);
            ListTag list = new ListTag();
            for (String d : deputies) list.add(StringTag.valueOf(d));
            t.put("Deputies", list);
            t.putString("Flag", flagItem);
            t.putString("Dim", dimension);
            t.putLong("Pos", capitalPos.asLong());
            t.putFloat("Hue", hue);
            t.putInt("Color", color);
            return t;
        }

        static Country load(CompoundTag t) {
            Country c = new Country();
            c.id = t.getUUID("Id");
            c.name = t.getString("Name");
            c.capitalName = t.getString("Capital");
            c.presidentId = t.getUUID("President");
            c.presidentName = t.getString("PresidentName");
            ListTag list = t.getList("Deputies", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) c.deputies.add(list.getString(i));
            c.flagItem = t.getString("Flag");
            c.dimension = t.getString("Dim");
            c.capitalPos = BlockPos.of(t.getLong("Pos"));
            c.hue = t.getFloat("Hue");
            c.color = t.getInt("Color");
            return c;
        }
    }

    private final List<Country> countries = new ArrayList<>();

    public static CountryData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(CountryData::load, CountryData::new, "politics_countries");
    }

    public static CountryData load(CompoundTag tag) {
        CountryData d = new CountryData();
        ListTag list = tag.getList("Countries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) d.countries.add(Country.load(list.getCompound(i)));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Country c : countries) list.add(c.save());
        tag.put("Countries", list);
        return tag;
    }

    public List<Country> all() { return countries; }

    public Country byPresident(UUID id) {
        for (Country c : countries) if (c.presidentId.equals(id)) return c;
        return null;
    }

    public Country byName(String name) {
        for (Country c : countries) if (c.name.equalsIgnoreCase(name.trim())) return c;
        return null;
    }

    public boolean nameTaken(String name) {
        return byName(name) != null;
    }

    /** Picks a random bright colour that differs as much as possible from already used ones. */
    public float pickHue(RandomSource rnd) {
        float best = rnd.nextFloat();
        float bestDist = -1f;
        for (int i = 0; i < 24; i++) {
            float h = rnd.nextFloat();
            float d = 1f;
            for (Country c : countries) {
                float diff = Math.abs(h - c.hue);
                d = Math.min(d, Math.min(diff, 1f - diff));
            }
            if (d > bestDist) { bestDist = d; best = h; }
        }
        return best;
    }

    public Country add(Country c, RandomSource rnd) {
        c.id = UUID.randomUUID();
        c.hue = pickHue(rnd);
        c.color = Mth.hsvToRgb(c.hue, 0.85f, 1f);
        countries.add(c);
        setDirty();
        return c;
    }

    public boolean remove(Country c) {
        boolean r = countries.remove(c);
        if (r) setDirty();
        return r;
    }

    public static String norm(String s) { return s.toLowerCase(Locale.ROOT); }
}
