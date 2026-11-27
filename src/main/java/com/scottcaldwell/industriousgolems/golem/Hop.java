package com.scottcaldwell.industriousgolems.golem;

import com.scottcaldwell.industriousgolems.behavior.ClipRun;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * The mushroom golem's sneeze: the vanish clip, a puff of spores, it is gone and back at {@code landing}, and the appear clip. Used
 * to get out of tight spots ({@link Sneeze}) and to get up on top of a tree.
 */
public final class Hop {

    private final ServerLevel level;
    private final WorkGolem golem;
    private final Vec3 landing;
    private ClipRun clip;
    private boolean landed;

    public Hop(ServerLevel level, WorkGolem golem, Vec3 landing) {
        this.level = level;
        this.golem = golem;
        this.landing = landing;
        golem.getNavigation().stop();
        clip = ClipRun.play(golem, Anim.M_SNEEZE_VANISH, Anim.M_VANISH_AT)
                .at(Anim.M_VANISH_AT - 0.1F, () -> puff(golem.position()));
    }

    /** Whether it has reappeared (the appear clip may still be playing). */
    public boolean landed() {
        return landed;
    }

    /** Advances one tick. True once it has reappeared and finished the appear clip. */
    public boolean tick() {
        if (!clip.tick()) return false;
        if (landed) return true;
        golem.teleportTo(landing.x, landing.y, landing.z);
        golem.getNavigation().stop();
        golem.setDeltaMovement(Vec3.ZERO);
        puff(golem.position());
        landed = true;
        clip = ClipRun.play(golem, Anim.M_SNEEZE_APPEAR, Anim.M_APPEAR_LEN);
        return false;
    }

    private void puff(Vec3 at) {
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y + 0.6, at.z, 24, 0.35, 0.3, 0.35, 0.05);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.6, at.z, 8, 0.25, 0.25, 0.25, 0.03);
    }
}
