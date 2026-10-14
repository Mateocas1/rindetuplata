package ar.rindetuplata.source.plazofijo;

import java.util.List;

/** Official per-product plazo fijo rates (BCRA transparency). */
public interface OfficialPlazoFijoSource {

    List<OfficialPlazoFijoRate> fetch();
}
