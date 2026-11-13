# UI design notes

Derived from the reference recording, not invented. The rules below are the ones
that actually constrain the code.

## Colour

One accent, used once per screen. `Tokens.Accent` appears on the composer button and
nowhere else; if a second blue element shows up, the screen is wrong. Everything
else is a neutral: near-black canvas, `#2A2A2A` user bubbles, three steps of grey
text.

The navy wash (`WashMid`, `WashBottom`) exists only behind an empty conversation. It
fades out the moment there is content, because a gradient behind body text is a
contrast bug waiting to be reported.

## Shape

* Composer: 28dp radius, full width, 56dp tall. One shape, no variation.
* User bubble: 24dp radius, max 300dp wide.
* Assistant: no container at all.
* Sheets: 28dp top corners.

## Type

Body 16sp / 25sp line height. That is larger than a chat app and smaller than a
document, which is the right compromise for text that is half prose and half code.
Code blocks are 13sp monospace on `#141414`.

## Motion

Three animations, each with a job:

1. **Shimmer** while waiting for the first token. Replaces a spinner because it
   occupies the shape the answer will take.
2. **Pulsing avatar** while streaming. Tells you the connection is alive without a
   progress bar.
3. **Composer button morph.** Mic → send → stop in the same circle. One control,
   three meanings, zero layout shift.

Nothing else animates. Screen transitions are a 150ms crossfade; there is no shared
element transition because the composer never moves.

## Accessibility

* Every icon button has a `contentDescription`.
* Touch targets are 44dp minimum (`Tokens.touchTarget`), even where the glyph is
  20dp.
* Text contrast on the canvas is 12.6:1 for primary, 5.2:1 for secondary. The
  tertiary grey is used only for disclaimers and is deliberately not load-bearing.
* The app never relies on colour alone: queue rows carry a word ("done", "failed")
  next to the dot.
