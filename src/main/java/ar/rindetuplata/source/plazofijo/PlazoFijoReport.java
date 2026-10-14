package ar.rindetuplata.source.plazofijo;

import ar.rindetuplata.source.SourceFailure;
import ar.rindetuplata.source.SourceStatus;
import java.util.List;

/** Result of the composite fetch. {@link SourceStatus#DEGRADED} means one set is empty because its source failed. */
public record PlazoFijoReport(
        List<OfficialPlazoFijoRate> official,
        List<BankPlazoFijoRate> banks,
        SourceStatus status,
        List<SourceFailure> failures) {

    public PlazoFijoReport {
        official = List.copyOf(official);
        banks = List.copyOf(banks);
        failures = List.copyOf(failures);
    }
}
