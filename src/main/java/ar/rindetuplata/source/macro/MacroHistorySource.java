package ar.rindetuplata.source.macro;

import java.time.LocalDate;
import java.util.List;

/** Macro observations of one variable inside an inclusive date range (T4a backfill), newest first. */
public interface MacroHistorySource {

    List<MacroObservation> fetchRange(MacroVariable variable, LocalDate from, LocalDate to);
}
