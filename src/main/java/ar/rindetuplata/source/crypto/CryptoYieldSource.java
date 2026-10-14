package ar.rindetuplata.source.crypto;

import java.util.List;

public interface CryptoYieldSource {

    List<CryptoYield> fetch();
}
