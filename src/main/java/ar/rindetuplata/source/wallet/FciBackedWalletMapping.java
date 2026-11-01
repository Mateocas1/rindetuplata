package ar.rindetuplata.source.wallet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A wallet whose balance sits in a money-market fund class. {@code fundClass} is the full primary class name and
 * {@code aliases} are other names the source may publish for the same class; a quote matches by {@link FundClassKey}.
 */
public record FciBackedWalletMapping(String walletId, String name, String fundClass, List<String> aliases) {

    public FciBackedWalletMapping {
        aliases = List.copyOf(aliases);
    }

    public FciBackedWalletMapping(String walletId, String name, String fundClass) {
        this(walletId, name, fundClass, List.of());
    }

    /** The keys of the primary name and every alias. */
    public Set<String> classKeys() {
        List<String> names = new ArrayList<>(aliases);
        names.add(fundClass);
        return names.stream().map(FundClassKey::of).collect(Collectors.toUnmodifiableSet());
    }

    /** Whether {@code className}, as the source publishes it, is this wallet's class. */
    public boolean matches(String className) {
        return classKeys().contains(FundClassKey.of(className));
    }
}
