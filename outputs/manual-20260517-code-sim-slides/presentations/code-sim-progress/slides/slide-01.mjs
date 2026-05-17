import { C, bg, kicker, title, note, footer, stageBlock } from "./common.mjs";

export async function slide01(presentation, ctx) {
  const slide = presentation.slides.add();
  bg(slide, ctx, C.ink);
  kicker(slide, ctx, "PROJECT PROGRESS", C.amber, true);
  title(slide, ctx, "From similarity scores to an explainable staged detector.", true, 92, 48);
  note(slide, ctx, "A review of earlier strategies, the current stage-based design, the web report prototype, and the next research steps.", 60, 232, 650, 82, true, 20);
  const stages = ["Profile", "Measure", "Feature", "Match", "Classify", "Report"];
  stages.forEach((s, i) => {
    const x = 62 + i * 194;
    ctx.addShape(slide, { x, y: 408, width: 150, height: 86, fill: i % 2 ? "#233044" : "#1B2534", line: { style: "solid", fill: "#405168", width: 1 } });
    ctx.addText(slide, { text: `0${i}`, x: x + 16, y: 426, width: 38, height: 24, fontSize: 16, color: C.amber, bold: true, typeface: ctx.fonts.mono });
    ctx.addText(slide, { text: s, x: x + 16, y: 456, width: 110, height: 24, fontSize: 18, color: C.white, bold: true });
  });
  note(slide, ctx, "Core shift: the project now explains why a result is likely, not only how high a score is.", 60, 548, 840, 36, true, 18);
  footer(slide, ctx, "Code Similarity Detection / Progress Deck", 1, true);
  return slide;
}
