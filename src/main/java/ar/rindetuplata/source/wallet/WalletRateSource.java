package ar.rindetuplata.source.wallet;

import java.util.List;

/** Wallet rates without an API: scraped from the entity web or curated by hand. */
public interface WalletRateSource {

    List<WalletRate> fetch();
}
