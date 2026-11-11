package ar.rindetuplata.source.plazofijo;

import java.util.List;

/** Per-bank plazo fijo rates with client vs non-client TNA. */
public interface BankPlazoFijoSource {

    List<BankPlazoFijoRate> fetch();
}
