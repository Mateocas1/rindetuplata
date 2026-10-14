package ar.rindetuplata.source.macro;

import ar.rindetuplata.source.SourceException;
import java.util.List;

/**
 * Primary macro source with a secondary behind it (S5). The observations keep their own {@code source}, so a
 * consumer can tell the secondary answered.
 */
public class FallbackMacroSource implements MacroSource {

    private final MacroSource primary;
    private final MacroSource secondary;

    public FallbackMacroSource(MacroSource primary, MacroSource secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    @Override
    public boolean supports(MacroVariable variable) {
        return primary.supports(variable) || secondary.supports(variable);
    }

    @Override
    public List<MacroObservation> fetch(MacroVariable variable) {
        try {
            return primary.fetch(variable);
        } catch (SourceException primaryFailure) {
            if (!secondary.supports(variable)) {
                throw primaryFailure;
            }
            try {
                return secondary.fetch(variable);
            } catch (SourceException secondaryFailure) {
                primaryFailure.addSuppressed(secondaryFailure);
                throw primaryFailure;
            }
        }
    }
}
