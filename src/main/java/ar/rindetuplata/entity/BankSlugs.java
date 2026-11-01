package ar.rindetuplata.entity;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The URL slug of a bank entity page: the lowercase name with accents stripped and every run of non-alphanumerics as
 * one dash. Two banks whose names normalize alike get distinct slugs by taking banks in instrument id order: the
 * lowest id keeps the plain slug, the next ones take {@code -2}, {@code -3}... and a candidate already taken (by a
 * bank literally named like a suffixed slug) is suffixed again, so the result is unique and identical on every run.
 */
public final class BankSlugs {

	/** The slug of a name that has no letter or digit. */
	static final String FALLBACK = "banco";

	private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
	private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
	private static final Pattern EDGE_DASHES = Pattern.compile("^-+|-+$");

	private BankSlugs() {
	}

	/** A bank as the slug assignment sees it: its instrument id and its name. */
	public record Named(long id, String name) {
	}

	public static String slugify(String name) {
		String folded = COMBINING_MARKS.matcher(Normalizer.normalize(name, Normalizer.Form.NFD)).replaceAll("");
		String dashed = NOT_ALPHANUMERIC.matcher(folded.toLowerCase(Locale.ROOT)).replaceAll("-");
		String slug = EDGE_DASHES.matcher(dashed).replaceAll("");
		return slug.isEmpty() ? FALLBACK : slug;
	}

	/** One unique slug per bank, keyed by instrument id. */
	public static Map<Long, String> assign(List<Named> banks) {
		Map<Long, String> slugs = new LinkedHashMap<>();
		Set<String> taken = new HashSet<>();
		for (Named bank : banks.stream().sorted(Comparator.comparingLong(Named::id)).toList()) {
			String base = slugify(bank.name());
			String candidate = base;
			for (int suffix = 2; !taken.add(candidate); suffix++) {
				candidate = base + "-" + suffix;
			}
			slugs.put(bank.id(), candidate);
		}
		return slugs;
	}
}
