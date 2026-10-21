package com.cupoftea;

/**
 * Defines the mutation strategy for {@link CupOfTea} transformations.
 */
public enum MutationMode {
    /**
     * In-place: transformations mutate the current {@link CupOfTea} instance in place
     * and return {@code this} for fluent chaining. This is the default mode.
     */
    IN_PLACE,

    /**
     * Copy-on-write: transformations return a new {@link CupOfTea} instance with
     * the changes applied, leaving the original instance unmodified.
     */
    COPY_ON_WRITE
}
