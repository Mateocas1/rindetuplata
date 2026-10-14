package ar.rindetuplata.source.macro;

import java.util.List;

/** Macro series, newest observation first. */
public interface MacroSource {

    boolean supports(MacroVariable variable);

    /** @throws IllegalArgumentException when {@link #supports} is false for the variable */
    List<MacroObservation> fetch(MacroVariable variable);
}
