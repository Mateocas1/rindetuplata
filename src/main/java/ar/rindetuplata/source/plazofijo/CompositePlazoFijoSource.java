package ar.rindetuplata.source.plazofijo;

import ar.rindetuplata.source.SourceException;
import ar.rindetuplata.source.SourceFailure;
import ar.rindetuplata.source.SourceRef;
import ar.rindetuplata.source.SourceStatus;
import java.util.ArrayList;
import java.util.List;

/**
 * BCRA transparency (primary) plus ArgentinaDatos (secondary). When one fails the other is returned with a
 * {@link SourceStatus#DEGRADED} status naming the failed source; it fails only when both fail (S5).
 */
public class CompositePlazoFijoSource implements PlazoFijoSource {

    private final OfficialPlazoFijoSource official;
    private final BankPlazoFijoSource banks;

    public CompositePlazoFijoSource(OfficialPlazoFijoSource official, BankPlazoFijoSource banks) {
        this.official = official;
        this.banks = banks;
    }

    @Override
    public PlazoFijoReport fetch() {
        List<SourceFailure> failures = new ArrayList<>();
        List<OfficialPlazoFijoRate> officialRates = List.of();
        List<BankPlazoFijoRate> bankRates = List.of();
        SourceException officialFailure = null;
        SourceException bankFailure = null;
        try {
            officialRates = official.fetch();
        } catch (SourceException e) {
            officialFailure = e;
            failures.add(new SourceFailure(e.source(), e.getMessage()));
        }
        try {
            bankRates = banks.fetch();
        } catch (SourceException e) {
            bankFailure = e;
            failures.add(new SourceFailure(e.source(), e.getMessage()));
        }
        if (officialFailure != null && bankFailure != null) {
            SourceRef composite = new SourceRef("plazo-fijo-composite", "Plazo fijo sources", false);
            SourceException both = new SourceException(composite,
                    "all sources failed: " + officialFailure.source().id() + ", " + bankFailure.source().id(), officialFailure);
            both.addSuppressed(bankFailure);
            throw both;
        }
        return new PlazoFijoReport(officialRates, bankRates,
                failures.isEmpty() ? SourceStatus.OK : SourceStatus.DEGRADED, failures);
    }
}
