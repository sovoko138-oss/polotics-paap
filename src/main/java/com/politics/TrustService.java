package com.politics;

import net.minecraft.server.level.ServerPlayer;

public class TrustService {
    /** Checks that the sender leads this country and the nickname can be given rights. Null means fine. */
    public static String checkTarget(ServerPlayer p, CountryData.Country c, String nick) {
        if (c == null) return "Страна не найдена.";
        if (ResidentService.viewerRank(p, c) < 2) return "Доверять игроков могут только президент и заместители.";
        if (!ResidentService.validNick(nick)) return "Некорректный ник.";
        if (c.rankOf(nick) >= 2) return "У президента и заместителей и так полный доступ.";
        return null;
    }

    /** Returns an error message, or null if the rights were saved. Zero rights removes the trust. */
    public static String setRights(ServerPlayer p, TrustPackets.Set m) {
        CountryData data = CountryData.get(p.getServer());
        CountryData.Country c = data.byId(m.countryId);
        String nick = m.nick.trim();
        String error = checkTarget(p, c, nick);
        if (error != null) return error;
        c.setRights(nick, m.rights & Rights.FULL);
        data.setDirty();
        return null;
    }
}
