package ar.rindetuplata.api.instrument;

/** One validation flag of a stored row, as every public response shows it. */
public record ValidationFlagView(String code, String severity, String message) {
}
