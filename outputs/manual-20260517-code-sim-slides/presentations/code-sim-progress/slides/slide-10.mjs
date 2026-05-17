import { C, bg, kicker, title, note, footer, label } from "./common.mjs";

export async function slide10(presentation, ctx) {
  const slide = presentation.slides.add();
  bg(slide, ctx, C.mist);
  kicker(slide, ctx, "CORRESPONDENCE LAYER");
  title(slide, ctx, "Stage 3 turns pairwise evidence into file-level diagnostics.");
  const cols = [
    ["Directional best matches", "A -> best B\nB -> best A", C.blue],
    ["Merged pairs", "bidirectional\nA-only / B-only", C.green],
    ["Diagnostics", "coverage\npartial signal\nvariance", C.amber],
  ];
  cols.forEach((c, i) => {
    const x = 90 + i * 390;
    ctx.addShape(slide, { x, y: 240, width: 300, height: 245, fill: C.white, line: { style: "solid", fill: C.line, width: 1 } });
    ctx.addShape(slide, { x, y: 240, width: 300, height: 7, fill: c[2] });
    ctx.addText(slide, { text: c[0], x: x + 22, y: 268, width: 250, height: 32, fontSize: 21, bold: true, color: C.ink, typeface: ctx.fonts.title });
    ctx.addText(slide, { text: c[1], x: x + 22, y: 330, width: 240, height: 76, fontSize: 22, color: C.charcoal, typeface: ctx.fonts.mono });
  });
  ctx.addShape(slide, { x: 390, y: 356, width: 90, height: 3, fill: C.slate });
  ctx.addShape(slide, { x: 780, y: 356, width: 90, height: 3, fill: C.slate });
  note(slide, ctx, "This is the stage where one-to-many, partial clone, and least-covered methods become visible instead of being averaged away.", 160, 548, 930, 46, false, 19);
  footer(slide, ctx, "Stage 3 method correspondence", 10);
  return slide;
}
