package com.scottcaldwell.industriousgolems.behavior;

import com.google.common.collect.ImmutableMap;
import com.scottcaldwell.industriousgolems.ModActivities;
import com.scottcaldwell.industriousgolems.ModAttachments;
import com.scottcaldwell.industriousgolems.golem.Anim;
import com.scottcaldwell.industriousgolems.needs.Job;
import com.scottcaldwell.industriousgolems.needs.ProfessionNeeds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;

/**
 * Decides what the golem should do next and hands it over to the exclusive task activity. Lives in the work and idle activities; a dormant golem starts nothing.
 */
public class StartTask extends Behavior<WorkGolem> {

    /** How soon the golem looks for the next thing to do after starting a task (this and the walking speed set how fast it works). */
    private static final long BUSY_INTERVAL = 20;
    /** How soon it looks again when it found nothing: the searches scan the surroundings. */
    private static final long IDLE_INTERVAL = 80;

    public StartTask() {
        super(ImmutableMap.of(), 1);
    }

    /**
     * The profession lists the jobs it could do, best first (see {@link Job}); this takes them in this order: fetching the supply of a
     * pressing job listed ahead of anything that could start, the first that can start with the hand as it is, then topping up the carried stack from more of the same lying about, putting it in the chest, picking up a stack
     * lying about, fetching the supply of the first job that needs one the chest can give, and last the first job that waits on all of those.
     */
    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, WorkGolem golem) {
        TaskState state = golem.getData(ModAttachments.TASK);
        if (!Tasks.mayWork(golem)) { standUp(golem); return false; }
        if (state.plan != null) return false;
        if (golem.gates().busy()) return false; // through the gate and shut it before anything else
        long now = level.getGameTime();
        if (!state.planDue(now)) return false;

        JobList jobs = new JobList(ProfessionNeeds.of(golem).work().jobs().jobs(level, golem).iterator());
        Job ready = jobs.first(Job::ready);
        Task plan = null;
        if (!golem.isCarrying()) {
            for (Job job : jobs) {
                if (job == ready) break;
                if (job.pressing()) plan = ContainerInteraction.planSupply(level, golem, new JobList(java.util.List.of(job).iterator()));
                if (plan != null) break;
            }
        }
        if (plan == null && ready != null) plan = ready.plan();
        if (plan == null && golem.isCarrying()) plan = PickUp.plan(level, golem); // top up the stack in hand first
        if (plan == null) plan = ContainerInteraction.planDeposit(level, golem);
        if (plan == null) plan = PickUp.plan(level, golem);
        if (plan == null) plan = ContainerInteraction.planSupply(level, golem, jobs);
        if (plan == null) {
            Job tidy = jobs.first(Job::afterTidying);
            plan = tidy == null ? null : tidy.plan();
        }
        state.plan = plan;
        // some golems with a stack in hand and nowhere to put it (the chest is full) sits down with it, rather than standing about looking busy
        if (plan == null && golem.isCarrying() && golem.profession().traits().sitsWhenFull()) {
            if (!Anim.M_SIT_FULL.equals(golem.clip())) {
                golem.getNavigation().stop();
                golem.playClip(Anim.M_SIT_FULL);
            }
        } else standUp(golem);
        // nothing to do: look again later, the searches are not free
        state.planAt(now + (plan == null && !golem.isCarrying() ? IDLE_INTERVAL : BUSY_INTERVAL)); // a full hand is never left idle for long
        return plan != null;
    }

    private static void standUp(WorkGolem golem) {
        if (Anim.M_SIT_FULL.equals(golem.clip())) golem.stopClip();
    }

    @Override
    protected void start(ServerLevel level, WorkGolem golem, long time) {
        golem.getBrain().setActiveActivityIfPossible(ModActivities.TASK.get());
        if (!Tasks.active(golem)) golem.getData(ModAttachments.TASK).clear();
    }
}