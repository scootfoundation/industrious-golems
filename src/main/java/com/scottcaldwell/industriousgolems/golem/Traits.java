package com.scottcaldwell.industriousgolems.golem;

/**
 * What sets one kind of golem's body and habits apart from another's, apart from its work. Shared code asks these questions and never
 * which profession a golem is.
 *
 * @param carry         how it carries things
 * @param softLanding   takes no fall damage
 * @param strolls       wanders about near its chest when it has nothing to do (otherwise it stands)
 * @param sneezes       gets out of tight spots by sneezing, see {@link Sneeze}
 * @param sitsWhenFull  sits down with what it holds when there is nowhere to put it
 * @param shoosAnimals  waves animals away from a gate before opening it, see {@link Shoo}
 * @param pathRange     how far it can find a path, in blocks
 */
public record Traits(Carry carry, boolean softLanding, boolean strolls, boolean sneezes, boolean sitsWhenFull, boolean shoosAnimals, double pathRange) {
}
