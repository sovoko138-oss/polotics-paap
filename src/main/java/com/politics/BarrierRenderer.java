package com.politics;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3f;

public class BarrierRenderer {
    /**
     * Draws the border of the capital chunk as continuous vertical lines made of dense coloured particles.
     * Only the given player sees it.
     */
    public static void show(ServerPlayer p, CountryData.Country c) {
        ServerLevel level = p.serverLevel();
        int cx = c.capitalPos.getX() >> 4;
        int cz = c.capitalPos.getZ() >> 4;
        double x0 = cx * 16.0, z0 = cz * 16.0, x1 = x0 + 16.0, z1 = z0 + 16.0;

        float r = ((c.color >> 16) & 0xFF) / 255f;
        float g = ((c.color >> 8) & 0xFF) / 255f;
        float b = (c.color & 0xFF) / 255f;
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(r, g, b), 1.5f);

        double y = p.getY() + 1.0;
        for (int i = 0; i <= 16; i += 2) {
            column(level, p, dust, x0 + i, y, z0);
            column(level, p, dust, x0 + i, y, z1);
            if (i > 0 && i < 16) {
                column(level, p, dust, x0, y, z0 + i);
                column(level, p, dust, x1, y, z0 + i);
            }
        }
    }

    /** One vertical line: three overlapping dense clusters stacked above and below the player's height. */
    private static void column(ServerLevel level, ServerPlayer p, DustParticleOptions dust, double x, double y, double z) {
        for (int dy = -9; dy <= 9; dy += 9) {
            level.sendParticles(p, dust, true, x, y + dy, z, 28, 0, 3.5, 0, 0);
        }
    }
}
