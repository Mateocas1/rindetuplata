package ar.rindetuplata.source.wallet;

/** A wallet whose balance sits in a money-market fund class ({@code fundClass} is the full class name). */
public record FciBackedWalletMapping(String walletId, String name, String fundClass) {
}
