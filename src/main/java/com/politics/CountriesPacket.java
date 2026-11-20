package com.politics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The full list of countries for the /countries window. */
public class CountriesPacket {
    public static class Info {
        public String name = "", flag = "", president = "";
        public int color, chunks, allianceColor;
        public String alliance = "";
        public List<String> deputies = new ArrayList<>();
        public List<String> cities = new ArrayList<>();
    }

    public final List<Info> countries;
    /** Name of the country whose details are opened at once, or "". */
    public final String selected;

    public CountriesPacket(List<Info> countries) { this(countries, ""); }

    public CountriesPacket(List<Info> countries, String selected) { this.countries = countries; this.selected = selected; }

    public static void encode(CountriesPacket m, FriendlyByteBuf buf) {
        buf.writeUtf(m.selected, 100);
        buf.writeVarInt(m.countries.size());
        for (Info i : m.countries) {
            buf.writeUtf(i.alliance, 100);
            buf.writeInt(i.allianceColor);
            buf.writeUtf(i.name, 100);
            buf.writeInt(i.color);
            buf.writeUtf(i.flag, 100);
            buf.writeUtf(i.president, 100);
            buf.writeVarInt(i.chunks);
            buf.writeVarInt(i.deputies.size());
            for (String d : i.deputies) buf.writeUtf(d, 100);
            buf.writeVarInt(i.cities.size());
            for (String c : i.cities) buf.writeUtf(c, 300);
        }
    }

    public static CountriesPacket decode(FriendlyByteBuf buf) {
        String selected = buf.readUtf(100);
        int n = buf.readVarInt();
        List<Info> list = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            Info i = new Info();
            i.alliance = buf.readUtf(100);
            i.allianceColor = buf.readInt();
            i.name = buf.readUtf(100);
            i.color = buf.readInt();
            i.flag = buf.readUtf(100);
            i.president = buf.readUtf(100);
            i.chunks = buf.readVarInt();
            int d = buf.readVarInt();
            for (int x = 0; x < d; x++) i.deputies.add(buf.readUtf(100));
            int c = buf.readVarInt();
            for (int x = 0; x < c; x++) i.cities.add(buf.readUtf(300));
            list.add(i);
        }
        return new CountriesPacket(list, selected);
    }

    public static void handle(CountriesPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.politics.client.ClientHooks.openCountries(m));
    }
}
