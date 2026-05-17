import { C, bg, kicker, title, note, footer, box } from "./common.mjs";

export async function slide11(presentation, ctx) {
  const slide = presentation.slides.add();
  bg(slide, ctx);
  kicker(slide, ctx, "CLASSIFICATION LAYER");
  title(slide, ctx, "Stage 4 makes classification explainable instead of black-box.");
  const stack = [
    ["Clone type", "T1 / T2 / T3 / T4_WEAK / NON_CLONE / INCONCLUSIVE", C.blue],
    ["Scope", "FULL / PARTIAL / MIXED / UNKNOWN", C.green],
    ["Confidence", "evidence strength x consistency x reliability", C.amber],
    ["Evidence chain", "supporting evidence, opposing evidence, warnings", C.red],
  ];
  stack.forEach((s, i) => {
    const y = 226 + i * 80;
    ctx.addShape(slide, { x: 112, y, width: 760, height: 54, fill: C.white, line: { style: "solid", fill: C.line, width: 1 } });
    ctx.addShape(slide, { x: 112, y, width: 7, height: 54, fill: s[2] });
    ctx.addText(slide, { text: s[0], x: 138, y: y + 13, width: 180, height: 28, fontSize: 18, bold: true, color: C.ink });
    ctx.addText(slide, { text: s[1], x: 336, y: y + 16, width: 490, height: 24, fontSize: 15, color: C.charcoal, typeface: ctx.fonts.mono });
  });
  ctx.addShape(slide, { x: 936, y: 252, width: 170, height: 170, fill: C.ink });
  ctx.addText(slide, { text: "T3", x: 960, y: 280, width: 120, height: 60, fontSize: 54, bold: true, color: C.amber, align: "center", typeface: ctx.fonts.title });
  ctx.addText(slide, { text: "partial\nmedium confidence", x: 958, y: 358, width: 126, height: 52, fontSize: 16, color: C.white, align: "center" });
  note(slide, ctx, "Important boundary: Stage 4 consumes diagnostics; it does not compute new similarity scores.", 138, 570, 820, 34, false, 18);
  footer(slide, ctx, "Stage 4 classification", 11);
  return slide;
}
