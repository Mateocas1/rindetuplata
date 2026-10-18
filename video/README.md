# rindetuplata — motion piece

A 15-second, 60 fps motion-graphics film for rindetuplata. One continuous camera take through a single 3D
room: no cuts, six beats, Spanish (es-AR), sound design only — no music, no voice.

Built with [Remotion](https://remotion.dev) so the whole film is a function of time in TypeScript, and so the
same composition can be rendered at 180 fps and blended down for real motion blur.

**Output:** `out/rindetuplata.mp4` — 1920×1080, 60 fps, H.264 + AAC, 15.0 s.

## The six beats

| t (s) | Beat | What happens |
|---|---|---|
| 0.00–2.35 | **Hook** | The product's own question. "plata" morphs from the display face into the serif accent. |
| 2.35–5.45 | **Thesis** | A 30-day return split into the part inflation eats and the part left over — and the camera ends pushed into that 0.38% sliver. |
| 5.45–8.35 | **Benefits** | The real "best option of today" plate, with the money counting up. |
| 8.35–11.35 | **Features** | Four product surfaces trucked past the lens; the panel nearest the centre is the only sharp one. |
| 11.35–13.30 | **Positioning** | "Sin comisiones de nadie." Sources as chips. |
| 13.30–15.00 | **Outro** | The wordmark as one word, serif on "plata". |

Every figure on screen is real, from the recorded fixtures in `../web/fixtures/`: REBA, plazo fijo, 24,00 % TNA,
ARS 1.019.726,03 in 30 days on ARS 1.000.000, expected inflation 1,59 % (REM BCRA), real return +0,38 %.

## How the "single take" is built

- **One camera, one spline.** `src/lib/camera.ts` interpolates every camera channel with monotone cubic
  Hermite (Fritsch–Carlson), which is C1 continuous end to end and never overshoots. The camera therefore
  never stops dead at a beat boundary, which is what would otherwise read as an edit.
- **Beats are places, not shots.** Each beat sits at `index × 950` units of depth in one 3D stage. The next
  beat is already faintly there behind the current one.
- **Beats leave by moving.** As a beat falls behind the lens it scales up, drops out of frame and blurs; the
  incoming one arrives undersized and grows. Two full-screen type layouts cross-dissolving at the same scale
  would double-expose, so the exit is physical.
- **Copy runs ahead of the lens.** Each beat's own clock is 0.45 s ahead of the wall clock (`SceneLead`
  context), so compositions resolve *during* the move and the arrival is a settle rather than a reveal.
- **The transit is an event.** `SpeedRush` derives radial light streaks from the camera's actual velocity, so
  the gap between two beats is motion instead of an empty room — and it can never drift out of sync.
- **Depth of field is real.** Far beats blur with distance, and incoming ones pull into focus.
- **Morphs**: the word "plata" changes face in place (`TypeMorph`); the thesis crop of a nominal return becomes
  a stacked bar; the counted money lands with a green pulse.

## Rendering

```bash
npm install

node still.mjs 2.3 7.4 12.4      # single frames -> out/stills/ (fast, for design review)
node preview.mjs                 # 960x540 preview with the audio mix -> out/preview.mp4
node mix.mjs                     # build out/mix.wav from the cue sheet
node master.mjs                  # full master -> out/rindetuplata.mp4
```

`master.mjs` renders the composition at **180 fps** (2700 subframes) and then:

```bash
ffmpeg -framerate 180 -start_number 0 -i element-%d.png \
  -vf "tmix=frames=3:weights='1 1 1',select='eq(mod(n\,3)\,2)',setpts=N/(60*TB),noise=c0s=7:c0f=t+u,format=yuv420p" \
  -r 60 -c:v libx264 -crf 16 ...
```

Three aligned subframes span 1/60 s, which is a 180° shutter: the motion blur a real camera would have produced
at 60 fps. Text that is not moving is identical across the triple, so it stays perfectly sharp. Grain is added
after the blend, so it belongs to the print rather than to the exposure. This is why every animation here is
written as a function of seconds and never of a frame count.

Set `CONCURRENCY` to taste (default 4); at 1080p it is roughly 0.8 s per subframe on 12 cores.

## Sound

`src/../mix.mjs` holds the cue sheet: `[seconds, file, gain dB, fade in, fade out]`. The 14 effects were
generated with ElevenLabs `text-to-sound-effects` (model `eleven_text_to_sound_v2`) and are committed under
`public/sfx/`. No music and no voice, by request. The mix is levelled to about −14 LUFS integrated with a
limiter, which is a normal level for web playback.

To regenerate one effect:

```bash
elevenlabs text-to-sound-effects convert \
  --json '{"text":"fast air whooshing past, cinematic transition","duration_seconds":0.9,"prompt_influence":0.75,"model_id":"eleven_text_to_sound_v2"}' \
  -o public/sfx/whoosh-a.mp3
```

## Layout

```
src/
  theme.ts          brand tokens, copied from web/src/styles/tokens.css (plus alpha())
  fonts.ts          self-hosted OFL fonts, loaded before the first frame
  lib/camera.ts     beat map + one continuous camera spline + depth helpers
  lib/math.ts       easing, monotone cubic, impulse, Argentine formatting
  world/
    Stage.tsx       the 3D stage and the Zone (enter, hold, leave)
    World.tsx       floor, dust, drifting data field, sky, lens
    SpeedRush.tsx   velocity-driven transit streaks
    Type.tsx        every reveal in the film comes from here
  ui/parts.tsx      the product's own components (plate, card, row, field, toggle, sparkline, chip)
  scenes/           the six beats
  Video.tsx         the film
public/sfx/         the generated sound design
```

## Notes

- Rendering needs a Chrome binary. `still.mjs`, `preview.mjs` and `master.mjs` point at
  `/home/picala/.local/bin/google-chrome`; change `CHROME` in those three files for another machine.
- Colour is authored in `oklch()` (the site's own tokens). Never concatenate an alpha suffix onto a token —
  use `alpha(color, a)` from `theme.ts`, or the paint is silently dropped.
- `out/` is not committed.

## Licensing

The four typefaces bundled in `public/fonts/` (Space Grotesk, Archivo, Geist Mono, Instrument Serif) are
self-hosted copies of the OFL-1.1 files shipped by `@fontsource` in `../web/node_modules`, so the render
works offline and matches the site exactly. Remotion is free for individuals and companies with three or
fewer employees.
