/**
 * Guards the motion layer (T12d, `animate` + better-ui): press feedback on the
 * button recipes, one disclosure recipe for every `<details>`, a dimmed busy
 * region, movement only without a reduced-motion preference, and no color
 * transitions anywhere (so an OS theme switch never smears and the zero-JS
 * pages need no suppression script).
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { extname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const webRoot = resolve(fileURLToPath(new URL('.', import.meta.url)), '../..');
const recipes = readFileSync(join(webRoot, 'src/styles/recipes.css'), 'utf8');
const global = readFileSync(join(webRoot, 'src/styles/global.css'), 'utf8');

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) return entry === 'api' ? [] : sourceFiles(full);
    return ['.astro', '.tsx', '.css'].includes(extname(full)) ? [full] : [];
  });
}

const sources = sourceFiles(join(webRoot, 'src')).map((file) => ({
  file: relative(webRoot, file),
  text: readFileSync(file, 'utf8'),
}));

/** The body of one `@utility name { ... }` block, braces balanced. */
function utility(name: string): string {
  const start = recipes.indexOf(`@utility ${name} {`);
  if (start === -1) return '';
  let depth = 0;
  for (let i = recipes.indexOf('{', start); i < recipes.length; i += 1) {
    if (recipes[i] === '{') depth += 1;
    if (recipes[i] === '}') depth -= 1;
    if (depth === 0) return recipes.slice(start, i + 1);
  }
  return '';
}

/** A tag whose class comes from `{name}` gets the value of `const name = '...'` in its file. */
function withClassConstant(file: string, tag: string): string {
  const reference = /class(?:Name)?=\{(\w+)\}/.exec(tag);
  if (!reference) return tag;
  const constant = new RegExp(`const ${reference[1]} = '([^']*)'`).exec(file);
  return constant ? `${tag} ${constant[1]}` : tag;
}

describe('motion layer', () => {
  it.each(['btn-primary', 'btn-secondary'])(
    '%s scales to 0.96 on press over 150ms ease-out, only without reduced motion',
    (name) => {
      const body = utility(name);
      expect(body).toContain('motion-safe:enabled:active:scale-[0.96]');
      expect(body).toMatch(/transition-property:\s*scale;/);
      expect(body).toMatch(/transition-duration:\s*150ms;/);
      expect(body).toMatch(/transition-timing-function:\s*ease-out;/);
    },
  );

  it('styles every <details> with the one disclosure recipe', () => {
    expect(utility('disclosure')).not.toBe('');
    const offenders = sources
      .filter(({ file, text }) => !file.endsWith('.css') && /<details\b/.test(text))
      .flatMap(({ file, text }) =>
        [...text.matchAll(/<details\b[^>]*>/g)]
          .filter((match) => !/\bdisclosure\b/.test(withClassConstant(text, match[0])))
          .map((match) => `${file}: ${match[0]}`),
      );
    expect(offenders).toEqual([]);
  });

  it('rotates the disclosure chevron and grows its height only without reduced motion', () => {
    const body = utility('disclosure');
    const gated = body.slice(body.indexOf('@media (prefers-reduced-motion: no-preference)'));
    expect(body).toContain('@media (prefers-reduced-motion: no-preference)');
    expect(gated).toMatch(/transition:[^;]*rotate/);
    expect(gated).toMatch(/transition:[^;]*block-size/);
    const ungated = body.slice(0, body.indexOf('@media (prefers-reduced-motion: no-preference)'));
    expect(ungated).not.toMatch(/transition[^;]*(rotate|block-size|translate|scale)/);
  });

  it('dims a busy region instead of moving its data', () => {
    expect(global).toMatch(/\[aria-busy='true'\]\s*\{\s*opacity:\s*0\.6;/);
    expect(global).toMatch(/\[aria-busy\]\s*\{\s*transition:\s*opacity 150ms ease-out;/);
  });

  it('never transitions everything or any color', () => {
    const offenders = sources
      .filter(({ text }) =>
        /transition-all|transition:\s*all|transition-colors|transition(-property)?:[^;]*\b(color|background-color|border-color|fill|stroke)\b/.test(
          text,
        ),
      )
      .map(({ file }) => file);
    expect(offenders).toEqual([]);
  });
});
