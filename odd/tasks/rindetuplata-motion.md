# rindetuplata motion piece

A 15-second, 60 fps motion-graphics film for rindetuplata: one continuous camera take, Spanish (es-AR),
built as a résumé-grade showreel of motion craft. Lives in `video/`, rendered with Remotion against the
brand's own "Cobalto Cristal" identity. Read every section until `## Log` before working on a task.

## Specs

- **S1 Length and subject.** "make a dynamic 15-second motion graphics video of "rindetuplata"."
- **S2 Craft bar.** "shows what an incredible motion designer you are, like it's your showreel for a résumé. go all out."
- **S3 Frame rate.** "I want it to be a smooth video at 60 fps."
- **S4 One take.** "The idea is to use dynamic zooms that “focus” the viewer’s attention, along with angle changes or morphing—in other words, the goal is for everything to feel like “a single take,” so it doesn’t look like a series of slides, but rather a video with no cuts—or as few as possible."
- **S5 Structure.** "Keep the viewer’s attention with a structure similar to this: hook – thesis – benefits – features – positioning – outro."
- **S6 Language.** "It has to be in Spanish."
- **S7 Tooling.** "We’ll probably use Hyperframes or Remotion? I’m not sure. Use whatever gives the best results."
- **S8 Audio.** "maybe u can access to the elevenlabs, should be logged, for the sound effects, only sound effects, not music, not voices"

## Tasks

| ID | Specs | Task | Route | Status | Commit |
|---|---|---|---|---|---|
| M1 | S1, S3, S7 | Render pipeline: Remotion project in `video/`, local Chrome, deterministic self-hosted fonts, 60 fps output, subframe render → ffmpeg motion blur | inline | done | — |
| M2 | S2, S4 | Identity + world: Cobalto Cristal tokens from `web/src/styles/tokens.css`, perspective grid, cobalt bloom, grain, vignette | inline | done | — |
| M3 | S2, S4 | Continuous camera: one spline through every beat, no cuts, handheld micro-motion, velocity-continuous easing | inline | done | — |
| M4 | S1, S5, S6 | The six beats in es-AR: hook, thesis, benefits, features, positioning, outro | inline | done | — |
| M5 | S8 | ElevenLabs SFX only (no music, no voice), placed and ducked in the mix | inline | done | — |
| M6 | S1, S2, S3 | Master: 1920×1080 H.264 60 fps, audio mix, QA pass over extracted frames | inline | done | — |

## Log

- **L1** (owner, verbatim): "make a dynamic 15-second motion graphics video of "rindetuplata". shows what an incredible motion designer you are, like it's your showreel for a résumé. go all out. I want it to be a smooth video at 60 fps. The idea is to use dynamic zooms that “focus” the viewer’s attention, along with angle changes or morphing—in other words, the goal is for everything to feel like “a single take,” so it doesn’t look like a series of slides, but rather a video with no cuts—or as few as possible. Keep the viewer’s attention with a structure similar to this: hook – thesis – benefits – features – positioning – outro. It has to be in Spanish. We’ll probably use Hyperframes or Remotion? I’m not sure. Use whatever gives the best results."
- **L2** (owner, verbatim): "btw, maybe u can access to the elevenlabs, should be logged, for the sound effects, only sound effects, not music, not voices"
- **L3** (decision, M1): Remotion 4.0.536 over Hyperframes. Reason: frame-exact 60 fps, local Chrome 151 already installed, full CSS/SVG control for a continuous 3D camera, and a `renderMedia` path that lets the same timeline render at 180 fps for true 180°-shutter motion blur.

- **L4** (decision, M1): the film is 1920×1080 at 60 fps, but it is *rendered* at 180 fps and blended down
  (`tmix` over aligned triples) so the 60 fps master carries a true 180°-shutter motion blur. Consequence:
  every animation is written as a function of seconds, never of a frame count, and Remotion's `spring()` is
  banned in this project.
- **L5** (decision, M2): the piece inherits `web/src/styles/tokens.css` rather than inventing a palette. One
  accent (cobalt) with one job, near-square 3px panels, mono caps labels, Space Grotesk display, Instrument
  Serif italic for the single accent word ("plata"), Archivo for reading text.
- **L6** (decision, M3): one camera spline for all 15 seconds, monotone cubic (Fritsch–Carlson) per channel,
  so the move is C1 continuous and cannot stop dead at a beat boundary. Beats are depths in one 3D room
  (`index × 950`), never shots.
- **L7** (defect found and fixed, M6): beats were cross-dissolving at full size, which double-exposed two
  full-screen type layouts and read as a mistake. Fixed by making the outgoing beat leave by moving (scale up,
  fall out of frame, blur) and the incoming arrive undersized. The transit also gained `SpeedRush` (streaks
  derived from real camera velocity) because the ~0.35 s between beats was otherwise an empty room.
- **L8** (defect found and fixed, M5): `${C.text}14` is invalid CSS for an `oklch()` token, so every translucent
  surface in the new components was silently transparent. Replaced with an `alpha(color, a)` helper.
- **L9** (defect found and fixed, M4): a per-beat time lead applied only to a scene's local math left the
  `Word`/`Eyebrow`/`FadeUp` primitives (which call `useT()` themselves) arriving late, so the first third of a
  second after every move was empty. Fixed with a `SceneLead` context read by `useSceneT()`.
- **L10** (decision, M5): sound is design, not score. Every cue is tied to a visual event; there is no bed and
  no music, per L2. The cue sheet lives in `mix.mjs` and was retimed once the beat lead (L9) moved content
  0.45 s earlier.
- **L11** (evidence, M6): objective QA rather than eyeballing. `select='gt(scene,0.06)'` finds no cut in the
  master (one whip scores 0.067, which is the move, not an edit); per-frame mean absolute difference averaged
  in 0.5 s windows found the dead-air windows, which are now all above 0.6 except the deliberate final settle.
