package ar.rindetuplata.source.macro;

/** Macro series the product uses, with their BCRA monetary-statistics variable id. */
public enum MacroVariable {
    /** INDEC monthly inflation, for history (S10). */
    MONTHLY_INFLATION(27),
    /** REM expected 12-month inflation, for today's ranking and the simulator (S10). */
    EXPECTED_INFLATION_12M(29),
    /** Average TNA of remunerated demand deposits at payment-service providers: the wallet benchmark (S12). */
    PSP_BENCHMARK_TNA(1196);

    private final int bcraId;

    MacroVariable(int bcraId) {
        this.bcraId = bcraId;
    }

    public int bcraId() {
        return bcraId;
    }

    static MacroVariable ofBcraId(int id) {
        for (MacroVariable variable : values()) {
            if (variable.bcraId == id) {
                return variable;
            }
        }
        throw new IllegalArgumentException("Unknown BCRA variable id " + id);
    }
}
