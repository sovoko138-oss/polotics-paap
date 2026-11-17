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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class CountryData extends SavedData {
    /** A member of a country. cityId is "capital" until more cities exist. */
    /** Rights of a trusted player inside a country, see Rights. */
    public static class Trust {
        public String name = "";
        public int rights = 0;
    }

    /** A letter from one country to the president of another. */
    public static class Mail {
        public String fromCountry = "";
        public int fromColor = 0xFFFFFF;
        public String fromPlayer = "";
        public String text = "";
    }

    /** A declared war. The capture itself is not implemented yet. */
    public static class War {
        public UUID attacker;
        public UUID defender;
    }

    public static class Resident {
        public String name = "";
        public String cityId = "capital";
        public String title = "";
    }

    public static class Country {
        public UUID id;
        public String name = "";
        public String capitalName = "";
        public UUID presidentId;
        public String presidentName = "";
        public List<String> deputies = new ArrayList<>();
        public String flagItem = "minecraft:white_banner";
        public String mayor = "";
        public String cityFlag = "minecraft:white_banner";
        public String dimension = "minecraft:overworld";
        public BlockPos capitalPos = BlockPos.ZERO;
        public float hue;
        public int color;
        /** Claimed chunks, see chunkKey(). The capital chunk is always included. */
        public Set<Long> chunks = new LinkedHashSet<>();
        public List<Resident> residents = new ArrayList<>();
        public Map<String, Trust> trusts = new LinkedHashMap<>();
        /** A country run by the mod itself (the pigs): it has no real player as president. */
        public boolean npc = false;
        /** Letters that came while the president was offline. */
        public List<Mail> inbox = new ArrayList<>();

        public int rightsOf(String n) {
            Trust t = trusts.get(n.toLowerCase());
            return t == null ? 0 : t.rights;
        }

        /** rights == 0 removes the player from the trusted list. */
        public void setRights(String n, int rights) {
            String key = n.toLowerCase();
            if (rights == 0) { trusts.remove(key); return; }
            Trust t = trusts.computeIfAbsent(key, k -> new Trust());
            t.name = n;
            t.rights = rights;
        }

        public Resident findResident(String n) {
            for (Resident r : residents) if (r.name.equalsIgnoreCase(n)) return r;
            return null;
        }

        public Resident addResident(String n) {
            Resident r = findResident(n);
            if (r == null) {
                r = new Resident();
                r.name = n;
                residents.add(r);
            }
            return r;
        }

        public boolean isPresident(String n) { return presidentName.equalsIgnoreCase(n); }

        public boolean isMayor(String n) { return !mayor.isEmpty() && mayor.equalsIgnoreCase(n); }

        public boolean isDeputy(String n) {
            for (String d : deputies) if (d.equalsIgnoreCase(n)) return true;
            return false;
        }

        /** president 3, deputy 2, mayor 1, ordinary resident 0 */
        public int rankOf(String n) {
            if (isPresident(n)) return 3;
            if (isDeputy(n)) return 2;
            if (isMayor(n)) return 1;
            return 0;
        }

        public boolean isMember(String n) { return findResident(n) != null || rankOf(n) > 0; }

        /** The president, deputies and mayor are always residents. */
        public void ensureCore() {
            addResident(presidentName);
            for (String d : deputies) addResident(d);
            if (!mayor.isEmpty()) addResident(mayor);
        }

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
            t.putString("Mayor", mayor);
            t.putString("CityFlag", cityFlag);
            t.putString("Dim", dimension);
            t.putLong("Pos", capitalPos.asLong());
            t.putFloat("Hue", hue);
            t.putInt("Color", color);
            ListTag rl = new ListTag();
            for (Resident r : residents) {
                CompoundTag rt = new CompoundTag();
                rt.putString("Name", r.name);
                rt.putString("City", r.cityId);
                rt.putString("Title", r.title);
                rl.add(rt);
            }
            t.put("Residents", rl);
            ListTag tl = new ListTag();
            for (Trust tr : trusts.values()) {
                CompoundTag tt = new CompoundTag();
                tt.putString("Name", tr.name);
                tt.putInt("Rights", tr.rights);
                tl.add(tt);
            }
            t.put("Trusts", tl);
            t.putLongArray("Chunks", chunks.stream().mapToLong(Long::longValue).toArray());
            t.putBoolean("Npc", npc);
            ListTag il = new ListTag();
            for (Mail m : inbox) {
                CompoundTag mt = new CompoundTag();
                mt.putString("From", m.fromCountry);
                mt.putInt("Color", m.fromColor);
                mt.putString("Player", m.fromPlayer);
                mt.putString("Text", m.text);
                il.add(mt);
            }
            t.put("Inbox", il);
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
            c.mayor = t.contains("Mayor") ? t.getString("Mayor") : c.presidentName;
            c.cityFlag = t.contains("CityFlag") ? t.getString("CityFlag") : c.flagItem;
            for (long l : t.getLongArray("Chunks")) c.chunks.add(l);
            if (c.chunks.isEmpty()) c.chunks.add(chunkKey(c.capitalPos.getX() >> 4, c.capitalPos.getZ() >> 4));
            ListTag rl = t.getList("Residents", Tag.TAG_COMPOUND);
            for (int i = 0; i < rl.size(); i++) {
                CompoundTag rt = rl.getCompound(i);
                Resident r = new Resident();
                r.name = rt.getString("Name");
                r.cityId = rt.getString("City");
                r.title = rt.getString("Title");
                c.residents.add(r);
            }
            ListTag tl = t.getList("Trusts", Tag.TAG_COMPOUND);
            for (int i = 0; i < tl.size(); i++) {
                CompoundTag tt = tl.getCompound(i);
                c.setRights(tt.getString("Name"), tt.getInt("Rights"));
            }
            c.npc = t.getBoolean("Npc");
            ListTag il = t.getList("Inbox", Tag.TAG_COMPOUND);
            for (int i = 0; i < il.size(); i++) {
                CompoundTag mt = il.getCompound(i);
                Mail m = new Mail();
                m.fromCountry = mt.getString("From");
                m.fromColor = mt.getInt("Color");
                m.fromPlayer = mt.getString("Player");
                m.text = mt.getString("Text");
                c.inbox.add(m);
            }
            c.ensureCore();
            return c;
        }
    }

    private final List<Country> countries = new ArrayList<>();
    private final List<War> wars = new ArrayList<>();

    public static CountryData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(CountryData::load, CountryData::new, "politics_countries");
    }

    public static CountryData load(CompoundTag tag) {
        CountryData d = new CountryData();
        ListTag list = tag.getList("Countries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) d.countries.add(Country.load(list.getCompound(i)));
        ListTag wl = tag.getList("Wars", Tag.TAG_COMPOUND);
        for (int i = 0; i < wl.size(); i++) {
            CompoundTag wt = wl.getCompound(i);
            War w = new War();
            w.attacker = wt.getUUID("Attacker");
            w.defender = wt.getUUID("Defender");
            d.wars.add(w);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Country c : countries) list.add(c.save());
        tag.put("Countries", list);
        ListTag wl = new ListTag();
        for (War w : wars) {
            CompoundTag wt = new CompoundTag();
            wt.putUUID("Attacker", w.attacker);
            wt.putUUID("Defender", w.defender);
            wl.add(wt);
        }
        tag.put("Wars", wl);
        return tag;
    }

    public List<Country> all() { return countries; }

    public List<War> wars() { return wars; }

    /** True if a war between the two countries is going on, whoever started it. */
    public boolean atWar(Country a, Country b) {
        for (War w : wars) {
            if ((w.attacker.equals(a.id) && w.defender.equals(b.id))
                    || (w.attacker.equals(b.id) && w.defender.equals(a.id))) return true;
        }
        return false;
    }

    public void declareWar(Country attacker, Country defender) {
        War w = new War();
        w.attacker = attacker.id;
        w.defender = defender.id;
        wars.add(w);
        setDirty();
    }

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
        c.chunks.add(chunkKey(c.capitalPos.getX() >> 4, c.capitalPos.getZ() >> 4));
        countries.add(c);
        setDirty();
        return c;
    }

    public boolean remove(Country c) {
        boolean r = countries.remove(c);
        if (r) {
            wars.removeIf(w -> w.attacker.equals(c.id) || w.defender.equals(c.id));
            setDirty();
        }
        return r;
    }

    public static long chunkKey(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }

    public static int keyX(long k) { return (int) k; }

    public static int keyZ(long k) { return (int) (k >>> 32); }

    /** The country that owns this chunk in the given dimension, or null if it is free. */
    public Country byChunk(String dim, int cx, int cz) {
        long k = chunkKey(cx, cz);
        for (Country c : countries) if (c.dimension.equals(dim) && c.chunks.contains(k)) return c;
        return null;
    }

    /** True if the chunk shares a side with one of the country's chunks. */
    public Country byId(UUID id) {
        for (Country c : countries) if (c.id.equals(id)) return c;
        return null;
    }

    /** The country whose capital city stands in this chunk, or null. */
    public Country cityAt(String dim, int cx, int cz) {
        for (Country c : countries) {
            if (c.dimension.equals(dim) && (c.capitalPos.getX() >> 4) == cx && (c.capitalPos.getZ() >> 4) == cz) return c;
        }
        return null;
    }

    public boolean touches(Country c, int cx, int cz) {
        return c.chunks.contains(chunkKey(cx + 1, cz)) || c.chunks.contains(chunkKey(cx - 1, cz))
                || c.chunks.contains(chunkKey(cx, cz + 1)) || c.chunks.contains(chunkKey(cx, cz - 1));
    }

    public void claim(Country c, int cx, int cz) {
        c.chunks.add(chunkKey(cx, cz));
        setDirty();
    }

    public static String norm(String s) { return s.toLowerCase(Locale.ROOT); }
}
