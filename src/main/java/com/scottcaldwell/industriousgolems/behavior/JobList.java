package com.scottcaldwell.industriousgolems.behavior;

import com.scottcaldwell.industriousgolems.needs.Job;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/** A profession's candidate jobs for one decision: worked out only as far as needed, and never twice. */
public final class JobList implements Iterable<Job> {
    private final Iterator<Job> source;
    private final List<Job> seen = new ArrayList<>();

    public JobList(Iterator<Job> source) {
        this.source = source;
    }

    private Job get(int i) {
        while (seen.size() <= i) {
            if (!source.hasNext()) return null;
            seen.add(source.next());
        }
        return seen.get(i);
    }

    @Override
    public Iterator<Job> iterator() {
        return new Iterator<>() {
            private int next;

            @Override
            public boolean hasNext() {
                return get(next) != null;
            }

            @Override
            public Job next() {
                return get(next++);
            }
        };
    }

    /** The best job that matches, or null. */
    public Job first(Predicate<Job> match) {
        for (int i = 0; ; i++) {
            Job job = get(i);
            if (job == null) return null;
            if (match.test(job)) return job;
        }
    }
}
