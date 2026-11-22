package com.scottcaldwell.industriousgolems.needs;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Helpers for writing a profession's candidate list: each entry is only worked out when the dispatcher gets that far. */
public final class Jobs {
    private Jobs() { }

    /** The jobs the suppliers make, in order, skipping nulls; a supplier runs only when the stream is read up to it. */
    @SafeVarargs
    public static Stream<Job> of(Supplier<Job>... candidates) {
        return Arrays.stream(candidates).map(Supplier::get).filter(Objects::nonNull);
    }
}
