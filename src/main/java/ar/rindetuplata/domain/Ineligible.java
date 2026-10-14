package ar.rindetuplata.domain;

/** The instrument cannot be used for the requested amount or term; never silently dropped. */
public record Ineligible(Reason reason, String detail) implements Evaluation {

    public enum Reason {
        MINIMUM_TERM,
        MINIMUM_AMOUNT
    }
}
