package ar.rindetuplata.source;

import java.util.List;

/** A source record that carries validation flags. */
public interface Flagged {

    List<ValidationFlag> flags();

    /** True when at least one flag is {@link Severity#INVALID}: the row must not be ranked. */
    default boolean isInvalid() {
        return ValidationRules.hasInvalid(flags());
    }
}
