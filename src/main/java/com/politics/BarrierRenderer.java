package com.politics;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3f;

public class BarrierRenderer {
    /** Draws the border of the capital chunk with coloured particles, visible to the given player. */
    public static void show(ServerPlayer p, CountryData.Country c) {
        ServerLevel level = p.serverLevel();
        int cx = c.capitalPos.getX() >> 4;
        int cz = c.capitalPos.getZ() >> 4;
        double x0 = cx * 16.0, z0 = cz * 16.0, x1 = x0 + 16.0, z1 = z0 + 16.0;

        float r = ((c.color >> 16) & 0xFF) / 255f;
        float g = ((c.color >> 8) & 0xFF) / 255f;
        float b = (c.color & 0xFF) / 255f;
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(r, g, b), 1.6f);

        int baseY = (int) Math.floor(p.getY());
        for (int dy = -2; dy <= 6; dy += 2) {
            double y = baseY + dy + 0.5;
            for (int i = 0; i <= 16; i += 2) {
                level.sendParticles(p, dust, true, x0 + i, y, z0, 1, 0, 0, 0, 0);
                level.sendParticles(p, dust, true, x0 + i, y, z1, 1, 0, 0, 0, 0);
                if (i > 0 && i < 16) {
                    level.sendParticles(p, dust, true, x0, y, z0 + i, 1, 0, 0, 0, 0);
                    level.sendParticles(p, dust, true, x1, y, z0 + i, 1, 0, 0, 0, 0);
                }
            }
        }
    }
}
