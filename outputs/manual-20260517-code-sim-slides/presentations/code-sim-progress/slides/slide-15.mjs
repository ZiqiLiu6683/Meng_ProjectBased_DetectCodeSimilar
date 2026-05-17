import { C, bg, kicker, title, note, footer } from "./common.mjs";

export async function slide15(presentation, ctx) {
  const slide = presentation.slides.add();
  bg(slide, ctx, C.ink);
  kicker(slide, ctx, "CLOSING", C.amber, true);
  title(slide, ctx, "The project direction is clear: explainability first, stronger evidence next.", true, 110, 48);
  const points = [
    ["Strategy review", "Signals are useful but incomplete alone."],
    ["Stage pipeline", "Measurement, matching, and classification are separated."],
    ["Web report", "The user sees a decision plus supporting evidence."],
    ["Next research", "Calibration and dynamic behavior can strengthen confidence."],
  ];
  points.forEach((p, i) => {
    const x = 92 + (i % 2) * 530;
    const y = 332 + Math.floor(i / 2) * 100;
    ctx.addShape(slide, { x, y, width: 430, height: 70, fill: "#1B2534", line: { style: "solid", fill: "#405168", width: 1 } });
    ctx.addText(slide, { text: p[0], x: x + 20, y: y + 12, width: 180, height: 24, fontSize: 18, bold: true, color: C.white });
    ctx.addText(slide, { text: p[1], x: x + 20, y: y + 40, width: 370, height: 20, fontSize: 14, color: "#D9E4EA" });
  });
  note(slide, ctx, "Suggested oral takeaway: this work is evolving from an algorithm demo into an explainable detection framework.", 92, 584, 850, 34, true, 18);
  footer(slide, ctx, "Summary", 15, true);
  return slide;
}
