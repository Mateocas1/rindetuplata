package ar.rindetuplata.store;

import java.util.List;

/**
 * What a writer did with one response: how many distinct rows it stored and which natural keys it kept only once
 * because a later record of the same response repeated them. Duplicates never fail the batch (T4 design notes);
 * {@link #failureText()} renders them for {@code source_run.failure}.
 */
public record WriteResult(int rows, List<String> duplicateKeys) {

	public WriteResult {
		duplicateKeys = List.copyOf(duplicateKeys);
	}

	public static WriteResult of(int rows, List<String> duplicateKeys) {
		return new WriteResult(rows, duplicateKeys);
	}

	public static WriteResult of(int rows) {
		return new WriteResult(rows, List.of());
	}

	public int duplicates() {
		return duplicateKeys.size();
	}

	/** The text to record in {@code source_run.failure}, or {@code null} when the response held no duplicates. */
	public String failureText() {
		if (duplicateKeys.isEmpty()) {
			return null;
		}
		return "duplicate natural keys kept-first: " + duplicateKeys.size() + " " + duplicateKeys;
	}
}
