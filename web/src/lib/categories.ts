/**
 * Category landing pages (T7c, S14).
 *
 * One page per ranked kind. The header nav, the category pages and the
 * instrument breadcrumbs all read this map, so the copy and the URLs stay in one
 * place. Spanish copy for Argentine savers, voseo, informational only (S8 L-G).
 */
import type { RankedKind } from './types';

export interface Category {
  slug: string;
  kind: RankedKind;
  path: string;
  navLabel: string;
  title: string;
  h1: string;
  description: string;
  intro: string;
}

export const CATEGORIES: Category[] = [
  {
    slug: 'plazo-fijo',
    kind: 'PLAZO_FIJO',
    path: '/plazo-fijo/',
    navLabel: 'Plazos fijos',
    h1: 'Plazos fijos por banco',
    title: 'Plazos fijos: tasas por banco y cuánto ganás | rindetuplata',
    description:
      'Tasas de plazo fijo por banco ordenadas por los pesos que ganás con tu monto y tu plazo, con la tasa para no clientes, la fuente y la fecha de cada dato.',
    intro:
      'Cuánto paga cada banco por un plazo fijo, ordenado por lo que ganás en pesos con tu monto y tu plazo. La tasa que ves es la de no clientes: si sos cliente de alguno, marcalo en el ranking para usar su tasa preferencial.',
  },
  {
    slug: 'billeteras',
    kind: 'WALLET',
    path: '/billeteras/',
    navLabel: 'Billeteras',
    h1: 'Billeteras remuneradas',
    title: 'Billeteras remuneradas: tasas y cuánto rinden | rindetuplata',
    description:
      'Billeteras y cuentas remuneradas que pagan interés diario sobre tu saldo, con su tasa base, las condiciones de cada una y el rendimiento real contra la inflación.',
    intro:
      'Cuentas y billeteras que acreditan interés todos los días sobre el saldo. Algunas tasas dependen de condiciones como sumar plata o gastar con la tarjeta: mostramos la tasa base y, cuando existe, el "hasta" con su condición.',
  },
  {
    slug: 'fondos',
    kind: 'FUND',
    path: '/fondos/',
    navLabel: 'Fondos',
    h1: 'Fondos money market',
    title: 'Fondos money market: rendimiento por clase | rindetuplata',
    description:
      'Fondos de dinero (FCI) ordenados por lo que rinden en pesos, con la TNA equivalente que sale de la variación del valor de cuotaparte entre dos días.',
    intro:
      'Fondos de dinero (FCI) que rescatan en el día. El rendimiento sale de la variación del valor de cuotaparte entre dos ruedas, así que cada clase muestra su TNA equivalente y la fecha de sus cuotapartes.',
  },
  {
    slug: 'cripto',
    kind: 'CRYPTO',
    path: '/cripto/',
    navLabel: 'Cripto',
    h1: 'Cripto y fintech en pesos',
    title: 'Cripto y fintech: rendimientos en pesos | rindetuplata',
    description:
      'Plataformas de cripto y fintech que pagan interés en pesos, con el APY convertido a TNA equivalente para compararlo con un plazo fijo o una billetera.',
    intro:
      'Plataformas de cripto y fintech que pagan interés sobre tus pesos. El APY que publican se convierte a TNA equivalente para que puedas compararlo, en la misma escala, con un plazo fijo o una billetera.',
  },
];

export function categoryForKind(kind: string): Category | null {
  return CATEGORIES.find((category) => category.kind === kind) ?? null;
}
