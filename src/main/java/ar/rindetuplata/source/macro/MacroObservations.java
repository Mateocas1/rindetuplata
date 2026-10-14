package ar.rindetuplata.source.macro;

import java.util.Comparator;

/**
 * Ordering shared by the macro parsers: newest observation first. An observation whose source gave no
 * {@code fecha} is flagged INVALID, not dropped, and sorts last instead of crashing (T4c follow-up).
 */
final class MacroObservations {

    static final Comparator<MacroObservation> NEWEST_FIRST =
            Comparator.comparing(MacroObservation::date, Comparator.nullsLast(Comparator.reverseOrder()));

    private MacroObservations() {
    }
}
