package ar.rindetuplata.source.plazofijo;

import java.util.List;

/** Per-bank plazo fijo rates with client vs non-client TNA (S19). */
public interface BankPlazoFijoSource {

    List<BankPlazoFijoRate> fetch();
}
