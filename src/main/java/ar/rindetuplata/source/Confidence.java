package ar.rindetuplata.source;

/** How much a rate can be trusted, by how it was obtained (S12). */
public enum Confidence {
    /** Automatic and published by the authority (BCRA). */
    OFFICIAL_AUTOMATIC,
    /** Automatic but from a community aggregator (ArgentinaDatos, dolarapi): not official. */
    AGGREGATOR_AUTOMATIC,
    /** Scraped from the entity's own web. */
    SCRAPED_ENTITY_WEB,
    /** Loaded by hand; carries a verified-at date. */
    CURATED_MANUAL
}
