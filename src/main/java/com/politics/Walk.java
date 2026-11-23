package com.politics;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * Moving the inhabitants of the kingdoms. Pigs, creepers and wither skeletons obey the path finder directly;
 * villagers and piglins are steered by their brain, which would overwrite a plain path, so they also get a walk target.
 */
public final class Walk {
    private Walk() {}

    /** Mobs whose movement is decided by a brain and not by simple goals. */
    public static boolean isBrainMob(Mob mob) {
        return mob instanceof Villager || mob instanceof AbstractPiglin;
    }

    public static void to(Mob mob, double x, double y, double z, double speed) {
        mob.getNavigation().moveTo(x, y, z, speed);
        if (isBrainMob(mob)) {
            mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new Vec3(x, y, z), (float) speed, 1));
        }
    }

    public static void toEntity(Mob mob, LivingEntity target, double speed) {
        mob.getNavigation().moveTo(target, speed);
        if (isBrainMob(mob)) {
            mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(target, false), (float) speed, 1));
            mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        }
    }

    public static void stop(Mob mob) {
        mob.getNavigation().stop();
        if (isBrainMob(mob)) mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    /** A fighter does not run away in panic when it is hit. */
    public static void calm(Mob mob) {
        if (!isBrainMob(mob)) return;
        mob.getBrain().eraseMemory(MemoryModuleType.HURT_BY);
        mob.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
    }
}
