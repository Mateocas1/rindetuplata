package ar.rindetuplata.domain;

/** Outcome of evaluating an instrument for an amount and term. */
public sealed interface Evaluation permits Simulation, Ineligible {
}
