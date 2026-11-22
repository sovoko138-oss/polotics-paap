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

    /** One line of an alliance chat. */
    public static class ChatLine {
        public String sender = "", country = "", text = "";
        public int color = 0xFFFFFF;
        public long time;
    }

    /** An alliance of countries. */
    public static class Alliance {
        public UUID id = UUID.randomUUID();
        public String name = "", capital = "", flag = "minecraft:white_banner", founderName = "";
        public int color = 0xFFFFFF;
        public UUID founderCountry;
        public long created;
        public List<UUID> members = new ArrayList<>();
        /** Countries that were invited and have not answered yet. */
        public Set<UUID> invites = new LinkedHashSet<>();
        public List<ChatLine> chat = new ArrayList<>();
    }

    /** A player who asked to join a country (/join). */
    public static class JoinRequest {
        public String nick = "";
        /** The country the player lived in before, or "" if none. */
        public String prevCountry = "";
    }

    /** A declared war. Chunks are captured by standing in them; everything transfers when one side has lost all. */
    public static class War {
        /** The leaders of the two sides (the first countries of the lists below). */
        public UUID attacker;
        public UUID defender;
        /** Everybody who fights on each side: the leader and the allies that joined. */
        public List<UUID> attackers = new ArrayList<>();
        public List<UUID> defenders = new ArrayList<>();
        /** Captured chunk -> the country that captured it. */
        public Map<Long, UUID> takers = new LinkedHashMap<>();

        /** A peace offer that is waiting for an answer: who offered and who has to answer (the leader of the other side). */
        public UUID peaceFrom, peaceTo;

        public boolean inWar(UUID id) { return attackers.contains(id) || defenders.contains(id); }
        public boolean onAttackers(UUID id) { return attackers.contains(id); }
        /** Overworld game time of the declaration. */
        public long declaredAt;
        /** Scheduled start timestamp in Unix milliseconds; 0 means legacy/immediate prep countdown. */
        public long scheduledStartMs;
        /** False during the preparation time before the fighting begins. */
        public boolean active = false;
        /** Chunk key -> capture progress 0..100 for chunks that are being captured. */
        public Map<Long, Float> progress = new LinkedHashMap<>();
        /** Chunks that are fully captured; they stay with the owner and are marked until the war ends. */
        public Set<Long> captured = new LinkedHashSet<>();
        /** How many chunks each side had when the fighting began. */
        public int attackerStart, defenderStart;
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
        /** What the inhabitants of an NPC country are: "pig" (default) or "creeper". */
        public String npcType = "";
        /** How many fortification towers the country has built so far (NPC countries). */
        public int fortBuilt = 0;
        /** Letters that came while the president was offline. */
        public List<Mail> inbox = new ArrayList<>();
        /** Players who want to join this country (answered with /accept). */
        public List<JoinRequest> joinRequests = new ArrayList<>();

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
            t.putString("NpcType", npcType);
            t.putInt("FortBuilt", fortBuilt);
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
            ListTag jl = new ListTag();
            for (JoinRequest jr : joinRequests) {
                CompoundTag jt = new CompoundTag();
                jt.putString("Nick", jr.nick);
                jt.putString("Prev", jr.prevCountry);
                jl.add(jt);
            }
            t.put("JoinRequests", jl);
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
            c.npcType = t.getString("NpcType");
            c.fortBuilt = t.getInt("FortBuilt");
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
            ListTag jl = t.getList("JoinRequests", Tag.TAG_COMPOUND);
            for (int i = 0; i < jl.size(); i++) {
                CompoundTag jt = jl.getCompound(i);
                JoinRequest jr = new JoinRequest();
                jr.nick = jt.getString("Nick");
                jr.prevCountry = jt.getString("Prev");
                c.joinRequests.add(jr);
            }
            c.ensureCore();
            return c;
        }
    }

    private final List<Country> countries = new ArrayList<>();
    private final List<War> wars = new ArrayList<>();
    private final List<Alliance> alliances = new ArrayList<>();
    /** -1 means unlimited; 0 blocks creation of all new countries. */
    private int countryLimit = -1;
    public int countryLimit() { return countryLimit; }
    public void setCountryLimit(int limit) { countryLimit = limit; setDirty(); }

    public List<Alliance> alliances() { return alliances; }

    public Alliance allianceById(UUID id) {
        for (Alliance a : alliances) if (a.id.equals(id)) return a;
        return null;
    }

    public Alliance allianceByName(String name) {
        if (name == null) return null;
        String n = name.trim();
        for (Alliance a : alliances) if (a.name.equalsIgnoreCase(n)) return a;
        return null;
    }

    /** All alliances the country belongs to (at most MAX_ALLIANCES). */
    public List<Alliance> alliancesOf(Country c) {
        List<Alliance> out = new ArrayList<>();
        for (Alliance a : alliances) if (a.members.contains(c.id)) out.add(a);
        return out;
    }

    /** True if the two countries share an alliance. */
    public boolean allied(Country x, Country y) {
        if (x == null || y == null || x == y) return false;
        for (Alliance a : alliances) if (a.members.contains(x.id) && a.members.contains(y.id)) return true;
        return false;
    }

    private void dropFromWars(Country c) {
        for (War w : new ArrayList<>(wars)) {
            w.attackers.remove(c.id);
            w.defenders.remove(c.id);
            w.takers.values().removeIf(id -> id.equals(c.id));
            if (w.attackers.isEmpty() || w.defenders.isEmpty()) wars.remove(w);
            else { w.attacker = w.attackers.get(0); w.defender = w.defenders.get(0); }
        }
    }

    /** The alliance this country belongs to, or null (the first one). */
    public Alliance allianceOf(Country c) {
        for (Alliance a : alliances) if (a.members.contains(c.id)) return a;
        return null;
    }

    /** Takes one country out of one alliance (a new founder is chosen, an empty alliance is dissolved). */
    public void leaveAlliance(Alliance a, Country c) {
        if (!a.members.remove(c.id)) return;
        if (a.members.isEmpty()) { alliances.remove(a); setDirty(); return; }
        if (c.id.equals(a.founderCountry)) {
            a.founderCountry = a.members.get(0);
            Country nf = byId(a.founderCountry);
            a.founderName = nf == null ? "" : nf.presidentName;
        }
        setDirty();
    }

    private void dropFromAlliances(Country c) {
        for (Alliance a : new ArrayList<>(alliances)) {
            a.invites.remove(c.id);
            if (!a.members.remove(c.id)) continue;
            if (a.members.isEmpty()) { alliances.remove(a); continue; }
            if (c.id.equals(a.founderCountry)) { // the next member becomes the founder
                a.founderCountry = a.members.get(0);
                Country nf = byId(a.founderCountry);
                a.founderName = nf == null ? "" : nf.presidentName;
            }
        }
    }
    /** True once the two start countries (Свиностан and Криперляндия) have been placed next to the spawn. */
    private boolean npcWorldDone = false;
    /** Number of player-declared wars since the latest 13:00 Moscow reset. */
    private int dailyWarDeclarations = 0;
    /** Next reset timestamp (Unix ms), Moscow time. */
    private long warDeclarationResetAt = 0L;

    public int dailyWarDeclarations() { return dailyWarDeclarations; }
    public long warDeclarationResetAt() { return warDeclarationResetAt; }
    public void setWarDeclarationState(int count, long resetAt) {
        dailyWarDeclarations = count; warDeclarationResetAt = resetAt; setDirty();
    }

    public boolean npcWorldDone() { return npcWorldDone; }

    public void setNpcWorldDone(boolean v) { npcWorldDone = v; setDirty(); }
    /** Lower-case nick -> the last country the player lived in (for the join requests). */
    private final Map<String, String> history = new LinkedHashMap<>();

    public void rememberCountry(String nick, String countryName) {
        history.put(nick.toLowerCase(Locale.ROOT), countryName);
        setDirty();
    }

    /** The last country this player lived in, or "" if none. */
    public String historyOf(String nick) {
        return history.getOrDefault(nick.toLowerCase(Locale.ROOT), "");
    }

    public static CountryData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(CountryData::load, CountryData::new, "politics_countries");
    }

    public static CountryData load(CompoundTag tag) {
        CountryData d = new CountryData();
        ListTag list = tag.getList("Countries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) d.countries.add(Country.load(list.getCompound(i)));
        d.npcWorldDone = tag.getBoolean("NpcWorldDone");
        d.countryLimit = tag.contains("CountryLimit") ? tag.getInt("CountryLimit") : -1;
        d.dailyWarDeclarations = tag.getInt("DailyWarDeclarations");
        d.warDeclarationResetAt = tag.getLong("WarDeclarationResetAt");
        ListTag al = tag.getList("Alliances", Tag.TAG_COMPOUND);
        for (int i = 0; i < al.size(); i++) {
            CompoundTag at = al.getCompound(i);
            Alliance a = new Alliance();
            a.id = at.getUUID("Id");
            a.name = at.getString("Name");
            a.capital = at.getString("Capital");
            a.flag = at.getString("Flag");
            a.founderName = at.getString("FounderName");
            a.color = at.getInt("Color");
            a.founderCountry = at.getUUID("FounderCountry");
            a.created = at.getLong("Created");
            ListTag ml = at.getList("Members", Tag.TAG_COMPOUND);
            for (int j = 0; j < ml.size(); j++) a.members.add(ml.getCompound(j).getUUID("Id"));
            ListTag il = at.getList("Invites", Tag.TAG_COMPOUND);
            for (int j = 0; j < il.size(); j++) a.invites.add(il.getCompound(j).getUUID("Id"));
            ListTag cl = at.getList("Chat", Tag.TAG_COMPOUND);
            for (int j = 0; j < cl.size(); j++) {
                CompoundTag t = cl.getCompound(j);
                ChatLine l = new ChatLine();
                l.sender = t.getString("Sender");
                l.country = t.getString("Country");
                l.text = t.getString("Text");
                l.color = t.getInt("Color");
                l.time = t.getLong("Time");
                a.chat.add(l);
            }
            d.alliances.add(a);
        }
        ListTag hl = tag.getList("History", Tag.TAG_COMPOUND);
        for (int i = 0; i < hl.size(); i++) {
            CompoundTag ht = hl.getCompound(i);
            d.history.put(ht.getString("Nick"), ht.getString("Country"));
        }
        ListTag wl = tag.getList("Wars", Tag.TAG_COMPOUND);
        for (int i = 0; i < wl.size(); i++) {
            CompoundTag wt = wl.getCompound(i);
            if (!wt.contains("DeclaredAt")) continue; // old format without capture data: dropped
            War w = new War();
            w.attacker = wt.getUUID("Attacker");
            w.defender = wt.getUUID("Defender");
            ListTag atl = wt.getList("Attackers", Tag.TAG_COMPOUND);
            for (int j = 0; j < atl.size(); j++) w.attackers.add(atl.getCompound(j).getUUID("Id"));
            ListTag dfl = wt.getList("Defenders", Tag.TAG_COMPOUND);
            for (int j = 0; j < dfl.size(); j++) w.defenders.add(dfl.getCompound(j).getUUID("Id"));
            if (w.attackers.isEmpty()) w.attackers.add(w.attacker);
            if (w.defenders.isEmpty()) w.defenders.add(w.defender);
            ListTag tkl = wt.getList("Takers", Tag.TAG_COMPOUND);
            for (int j = 0; j < tkl.size(); j++) w.takers.put(tkl.getCompound(j).getLong("Key"), tkl.getCompound(j).getUUID("Country"));
            if (wt.hasUUID("PeaceFrom") && wt.hasUUID("PeaceTo")) {
                w.peaceFrom = wt.getUUID("PeaceFrom");
                w.peaceTo = wt.getUUID("PeaceTo");
            }
            w.declaredAt = wt.getLong("DeclaredAt");
            w.scheduledStartMs = wt.getLong("ScheduledStartMs");
            w.active = wt.getBoolean("Active");
            for (long l : wt.getLongArray("Captured")) w.captured.add(l);
            w.attackerStart = wt.getInt("AttackerStart");
            w.defenderStart = wt.getInt("DefenderStart");
            ListTag pl = wt.getList("Progress", Tag.TAG_COMPOUND);
            for (int j = 0; j < pl.size(); j++) {
                CompoundTag pt = pl.getCompound(j);
                w.progress.put(pt.getLong("Key"), pt.getFloat("Value"));
            }
            d.wars.add(w);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Country c : countries) list.add(c.save());
        tag.put("Countries", list);
        ListTag al = new ListTag();
        for (Alliance a : alliances) {
            CompoundTag at = new CompoundTag();
            at.putUUID("Id", a.id);
            at.putString("Name", a.name);
            at.putString("Capital", a.capital);
            at.putString("Flag", a.flag);
            at.putString("FounderName", a.founderName);
            at.putInt("Color", a.color);
            at.putUUID("FounderCountry", a.founderCountry);
            at.putLong("Created", a.created);
            ListTag ml = new ListTag();
            for (UUID u : a.members) { CompoundTag t = new CompoundTag(); t.putUUID("Id", u); ml.add(t); }
            at.put("Members", ml);
            ListTag il = new ListTag();
            for (UUID u : a.invites) { CompoundTag t = new CompoundTag(); t.putUUID("Id", u); il.add(t); }
            at.put("Invites", il);
            ListTag cl = new ListTag();
            for (ChatLine l : a.chat) {
                CompoundTag t = new CompoundTag();
                t.putString("Sender", l.sender);
                t.putString("Country", l.country);
                t.putString("Text", l.text);
                t.putInt("Color", l.color);
                t.putLong("Time", l.time);
                cl.add(t);
            }
            at.put("Chat", cl);
            al.add(at);
        }
        tag.put("Alliances", al);
        tag.putBoolean("NpcWorldDone", npcWorldDone);
        tag.putInt("CountryLimit", countryLimit);
        tag.putInt("DailyWarDeclarations", dailyWarDeclarations);
        tag.putLong("WarDeclarationResetAt", warDeclarationResetAt);
        ListTag hl = new ListTag();
        for (Map.Entry<String, String> en : history.entrySet()) {
            CompoundTag ht = new CompoundTag();
            ht.putString("Nick", en.getKey());
            ht.putString("Country", en.getValue());
            hl.add(ht);
        }
        tag.put("History", hl);
        ListTag wl = new ListTag();
        for (War w : wars) {
            CompoundTag wt = new CompoundTag();
            wt.putUUID("Attacker", w.attacker);
            wt.putUUID("Defender", w.defender);
            ListTag atl = new ListTag();
            for (UUID u : w.attackers) { CompoundTag t = new CompoundTag(); t.putUUID("Id", u); atl.add(t); }
            wt.put("Attackers", atl);
            ListTag dfl = new ListTag();
            for (UUID u : w.defenders) { CompoundTag t = new CompoundTag(); t.putUUID("Id", u); dfl.add(t); }
            wt.put("Defenders", dfl);
            ListTag tkl = new ListTag();
            for (Map.Entry<Long, UUID> te : w.takers.entrySet()) {
                CompoundTag t = new CompoundTag();
                t.putLong("Key", te.getKey());
                t.putUUID("Country", te.getValue());
                tkl.add(t);
            }
            wt.put("Takers", tkl);
            if (w.peaceFrom != null && w.peaceTo != null) {
                wt.putUUID("PeaceFrom", w.peaceFrom);
                wt.putUUID("PeaceTo", w.peaceTo);
            }
            wt.putLong("DeclaredAt", w.declaredAt);
            wt.putLong("ScheduledStartMs", w.scheduledStartMs);
            wt.putBoolean("Active", w.active);
            wt.putLongArray("Captured", w.captured.stream().mapToLong(Long::longValue).toArray());
            wt.putInt("AttackerStart", w.attackerStart);
            wt.putInt("DefenderStart", w.defenderStart);
            ListTag pl = new ListTag();
            for (Map.Entry<Long, Float> en : w.progress.entrySet()) {
                CompoundTag pt = new CompoundTag();
                pt.putLong("Key", en.getKey());
                pt.putFloat("Value", en.getValue());
                pl.add(pt);
            }
            wt.put("Progress", pl);
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

    public War declareWar(Country attacker, Country defender, long gameTime) {
        War w = new War();
        w.attacker = attacker.id;
        w.defender = defender.id;
        w.attackers.add(attacker.id);
        w.defenders.add(defender.id);
        w.declaredAt = gameTime;
        wars.add(w);
        setDirty();
        return w;
    }

    /** The war this country is in (one war at a time), or null. */
    public War warOf(Country c) {
        for (War w : wars) if (w.inWar(c.id)) return w;
        return null;
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
            dropFromAlliances(c);
            for (Resident res : c.residents) history.put(res.name.toLowerCase(Locale.ROOT), c.name);
            dropFromWars(c);
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
