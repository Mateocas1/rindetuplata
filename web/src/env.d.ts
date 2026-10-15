/// <reference types="astro/client" />

interface ImportMetaEnv {
  /** Base URL of the rindetuplata API. Empty (default) means same origin. */
  readonly PUBLIC_API_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
