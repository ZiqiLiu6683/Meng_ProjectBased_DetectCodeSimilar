import { C, bg, kicker, title, note, footer } from "./common.mjs";

export async function slide03(presentation, ctx) {
  const slide = presentation.slides.add();
  bg(slide, ctx);
  kicker(slide, ctx, "STRATEGY REVIEW");
  title(slide, ctx, "The early strategies were useful, but each explained only one slice of similarity.");
  const headers = ["Strategy", "What it sees well", "Where it breaks"];
  const xs = [70, 330, 760];
  const ws = [230, 380, 390];
  headers.forEach((h, i) => ctx.addText(slide, { text: h, x: xs[i], y: 222, width: ws[i], height: 28, fontSize: 14, bold: true, color: C.slate, typeface: ctx.fonts.mono }));
  const rows = [
    ["Token / Winnowing", "Fast local overlap; robust to whitespace and comments.", "Sensitive to reordering, extraction, and structural edits."],
    ["AST / Subtree", "Structural reuse and Type-2-like similarity.", "Can overvalue wrapper context or boilerplate."],
    ["Method-level matrix", "Real reuse patterns across methods.", "Needs careful correspondence policy; one-to-many matters."],
    ["API vocabulary", "Weak semantic hints from called libraries.", "Auxiliary only; API density can mislead."],
  ];
  rows.forEach((r, i) => {
    const y = 270 + i * 82;
    ctx.addShape(slide, { x: 60, y: y - 12, width: 1100, height: 1, fill: C.line });
    ctx.addText(slide, { text: r[0], x: 70, y, width: 230, height: 44, fontSize: 18, bold: true, color: C.ink });
    ctx.addText(slide, { text: r[1], x: 330, y, width: 360, height: 48, fontSize: 15, color: C.charcoal });
    ctx.addText(slide, { text: r[2], x: 760, y, width: 380, height: 48, fontSize: 15, color: C.charcoal });
  });
  note(slide, ctx, "Review outcome: the question became how to organize signals into a reliable decision process.", 70, 616, 760, 28, false, 17);
  footer(slide, ctx, "Earlier strategy review", 3);
  return slide;
}
