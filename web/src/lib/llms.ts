/**
 * `llms.txt` body, in the llmstxt.org format: an H1 with the site name, a blockquote summary and
 * H2 sections of `- [name](url): notes` links, with the `Optional` section last. It names the site and its
 * sections, the public API with its OpenAPI document, and the MCP endpoint of the API.
 *
 * Both hosts come from the build: the site from `SITE_URL` (astro.config.mjs) and the API from
 * `PUBLIC_API_URL`, which falls back to the site origin when the stack is one origin, so a production build never
 * publishes a localhost URL that it was not configured with. The text is Spanish like the site; the tool names
 * are the MCP server's own.
 */
export function llmsTxt(site: URL, api: URL): string {
  const page = (path: string) => new URL(path, site).href;
  const endpoint = (path: string) => new URL(path, api).href;
  return [
    '# rindetuplata',
    '',
    '> Comparador argentino de rendimientos: dónde rinde más tu plata hoy, medido por rendimiento real contra la inflación. Compara plazo fijo por banco, fondos comunes de dinero, billeteras remuneradas y cripto, con historial diario y simulación por monto y plazo.',
    '',
    'Cada dato dice de qué fuente sale y a qué fecha corresponde. Es información, no asesoramiento financiero. La API pública es de lectura abierta y sin cuenta, con un límite de pedidos por dirección; tasas y rendimientos son fracciones (0,05 es 5%). El servidor MCP es de solo lectura sobre el mismo ranking, sin sesión (Streamable HTTP sin estado) y con el mismo límite.',
    '',
    '## Sitio',
    '',
    `- [Ranking de hoy](${page('/')}): los instrumentos ordenados por rendimiento real para tu monto y tu plazo`,
    `- [Plazo fijo](${page('/plazo-fijo/')}): la tasa de cada banco, para clientes y para no clientes`,
    `- [Billeteras](${page('/billeteras/')}): cuentas remuneradas de las billeteras virtuales`,
    `- [Fondos](${page('/fondos/')}): fondos comunes de dinero, con el rendimiento de los últimos 7 días`,
    `- [Cripto](${page('/cripto/')}): rendimientos en pesos de plataformas cripto`,
    `- [Bancos](${page('/bancos/')}): una página por banco, con su tasa de hoy, 90 días de historia y cuántas veces fue la mejor`,
    `- [Informes mensuales](${page('/informes/')}): quién le ganó a la inflación de cada mes cerrado, con el INDEC`,
    `- [Metodología](${page('/metodologia/')}): cómo se calcula el ranking, de dónde sale cada dato y qué dejamos afuera`,
    '',
    '## API pública',
    '',
    `- [Ranking](${endpoint('/api/v1/ranking')}): GET con amount, days, kind, clientOf, asOf, limit y groupFunds`,
    `- [Simulación de un instrumento](${endpoint('/api/v1/simulations')}): GET con instrumentId, amount, days y client`,
    `- [Instrumentos](${endpoint('/api/v1/instruments')}): GET del catálogo con el último dato de cada uno; \`/api/v1/instruments/{id}/history\` trae su historia`,
    `- [Bancos](${endpoint('/api/v1/entities/banks')}): GET de la tasa de cada banco; \`/api/v1/entities/banks/{slug}\` trae un banco`,
    `- [Informes mensuales](${endpoint('/api/v1/reports/monthly')}): GET de los meses con informe; \`/api/v1/reports/monthly/{yyyy-MM}\` trae uno`,
    `- [Documento OpenAPI](${endpoint('/v3/api-docs')}): el contrato completo de la API en JSON`,
    '',
    '## MCP',
    '',
    `- [Servidor MCP](${endpoint('/mcp')}): POST de JSON-RPC; herramientas get_ranking, simulate_instrument, get_instrument_history, list_monthly_reports y get_monthly_report`,
    '',
    '## Optional',
    '',
    `- [Sitemap](${page('/sitemap-index.xml')}): todas las páginas del sitio`,
    '',
  ].join('\n');
}
