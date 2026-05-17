import { C, bg, kicker, title, note, footer, box } from "./common.mjs";

export async function slide06(presentation, ctx) {
  const slide = presentation.slides.add();
  bg(slide, ctx, C.ink);
  kicker(slide, ctx, "SEMANTIC HINTS", C.green, true);
  title(slide, ctx, "API vocabulary can support a semantic story, but it cannot carry the verdict alone.", true);
  const levels = [
    ["Primary evidence", "method structure + token behavior", C.blue],
    ["Scope evidence", "coverage + containment + correspondence", C.green],
    ["Auxiliary evidence", "API vocabulary / S5", C.amber],
    ["Warnings", "weak context, boilerplate, low evidence", C.red],
  ];
  levels.forEach((l, i) => {
    const y = 244 + i * 82;
    ctx.addShape(slide, { x: 128, y, width: 900, height: 52, fill: "#1B2534", line: { style: "solid", fill: "#405168", width: 1 } });
    ctx.addShape(slide, { x: 128, y, width: 8, height: 52, fill: l[2] });
    ctx.addText(slide, { text: l[0], x: 154, y: y + 10, width: 240, height: 28, fontSize: 19, bold: true, color: C.white });
    ctx.addText(slide, { text: l[1], x: 430, y: y + 13, width: 520, height: 24, fontSize: 16, color: "#D9E4EA", typeface: ctx.fonts.mono });
  });
  note(slide, ctx, "Design assumption: weak semantic signals should raise or lower confidence only when other evidence is interpretable.", 128, 590, 850, 38, true, 17);
  footer(slide, ctx, "Strategy D: API signal", 6, true);
  return slide;
}
