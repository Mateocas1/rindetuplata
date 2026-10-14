package ar.rindetuplata.source;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** The validation rules found in the recorded data. They flag rows; nothing here removes one. */
public final class ValidationRules {

    public static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    public static final BigDecimal MAX_RATE = new BigDecimal("3");
    public static final int PLAZO_FIJO_STALE_DAYS = 45;
    public static final int FUND_STALE_DAYS = 7;
    public static final int CURATED_WALLET_STALE_DAYS = 7;
    public static final int CRYPTO_STALE_DAYS = 7;

    private static final Pattern LEGAL_ENTITY = Pattern.compile("PPJJ");
    private static final Pattern FOREIGN_CURRENCY = Pattern.compile("DOLAR|DÓLAR|USD|U\\$S");
    private static final Pattern UVA = Pattern.compile("UVA");

    private ValidationRules() {
    }

    /** Null or non-positive → INVALID; above 300% → INVALID. {@code fraction} is already normalized. */
    public static List<ValidationFlag> rateFlags(BigDecimal fraction) {
        List<ValidationFlag> flags = new ArrayList<>();
        if (fraction == null || fraction.signum() <= 0) {
            flags.add(new ValidationFlag(FlagCode.NON_POSITIVE_RATE, Severity.INVALID, "Rate is missing, zero or negative"));
        } else if (fraction.compareTo(MAX_RATE) > 0) {
            flags.add(new ValidationFlag(FlagCode.RATE_ABOVE_MAXIMUM, Severity.INVALID, "Rate is above 300%: " + fraction));
        }
        return flags;
    }

    public static List<ValidationFlag> stalenessFlags(LocalDate dataDate, LocalDate reference, int windowDays) {
        if (dataDate == null || ChronoUnit.DAYS.between(dataDate, reference) <= windowDays) {
            return List.of();
        }
        return List.of(new ValidationFlag(FlagCode.STALE, Severity.WARNING,
                "Data date " + dataDate + " is older than " + windowDays + " days"));
    }

    public static boolean hasInvalid(List<ValidationFlag> flags) {
        return flags.stream().anyMatch(f -> f.severity() == Severity.INVALID);
    }

    public static Segment segmentOf(String productName) {
        if (productName == null) {
            return Segment.SAVER;
        }
        String name = productName.toUpperCase(java.util.Locale.ROOT);
        if (LEGAL_ENTITY.matcher(name).find()) {
            return Segment.LEGAL_ENTITY;
        }
        if (FOREIGN_CURRENCY.matcher(name).find()) {
            return Segment.FOREIGN_CURRENCY;
        }
        if (UVA.matcher(name).find()) {
            return Segment.UVA_ADJUSTED;
        }
        return Segment.SAVER;
    }

    public static LocalDate localDate(Instant instant) {
        return instant.atZone(ARGENTINA).toLocalDate();
    }
}
