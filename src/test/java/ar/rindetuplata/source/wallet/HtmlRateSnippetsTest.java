package ar.rindetuplata.source.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.source.Fixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class HtmlRateSnippetsTest {

    @Test
    void matchesWhatCaptureExtractRatesPyProducesOnItsOwnFixture() {
        String html = new String(Fixtures.bytes("uala/capture-sample.html"), java.nio.charset.StandardCharsets.UTF_8);

        assertThat(HtmlRateSnippets.extract(html)).containsExactly(
                "Tu cuenta remunerada rinde hasta 24% TNA",
                "Si tenés entre $10.000 y $1.000.000 en tu cuenta rinden 19% TNA sin hacer nada.",
                "Si acumulás $50.000 en ciertas operaciones este mes, tu tasa sube hasta 20% TNA.");
    }

    @Test
    void ignoresScriptStyleCommentAndSvgContentAndDecodesEntities() {
        List<String> snippets = HtmlRateSnippets.extract(Fixtures.text("uala/cuenta-remunerada.html"));

        assertThat(snippets).containsExactly(
                "¡Tus pesos rinden hasta 24% TNA!",
                "Tu cuenta remunerada rinde hasta 24% TNA",
                "Si tenés entre $10.000 y $1.000.000 en tu cuenta rinden 19% TNA sin hacer nada.",
                "Si acumulás $50.000 en ciertas operaciones este mes, tu tasa sube hasta 20% TNA.",
                "Si sumás $500.000 sube hasta 24% TNA.",
                "Simulá cuánto rinde tu plata con una tasa de hasta 24% TNA.");
    }

    @Test
    void inlineTagsDoNotSplitASnippetButBlockTagsDo() {
        assertThat(HtmlRateSnippets.extract("<div>rinde <b>19</b>% TNA</div><div>otro 5 % tna</div>"))
                .containsExactly("rinde 19% TNA", "otro 5 % tna");
    }

    @Test
    void pageWithoutRatesGivesNoSnippets() {
        assertThat(HtmlRateSnippets.extract("<html><body><p>Nada por aquí</p></body></html>")).isEmpty();
        assertThat(HtmlRateSnippets.extract("")).isEmpty();
    }

    @Test
    void acceptsDecimalRatesWithCommaOrDot() {
        assertThat(HtmlRateSnippets.extract("<p>rinde 19,5% TNA</p><p>rinde 20.25 % TNA</p>"))
                .containsExactly("rinde 19,5% TNA", "rinde 20.25 % TNA");
    }
}
