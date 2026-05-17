import { C, bg, kicker, title, note, footer } from "./common.mjs";

export async function slide14(presentation, ctx) {
  const slide = presentation.slides.add();
  bg(slide, ctx, C.mist);
  kicker(slide, ctx, "NEXT STEPS", C.amber);
  title(slide, ctx, "The next phase should calibrate the detector and extend evidence beyond static structure.");
  const phases = [
    ["Now", "Benchmark calibration\nmore edge-case tests\nperformance guardrails", C.blue],
    ["Next", "Control/data-flow features\nstronger partial clone handling\nclearer report language", C.green],
    ["Later", "Dynamic monitoring\nexecution traces\nhybrid static + runtime evidence", C.amber],
  ];
  phases.forEach((p, i) => {
    const x = 104 + i * 370;
    ctx.addShape(slide, { x, y: 260, width: 290, height: 230, fill: C.white, line: { style: "solid", fill: C.line, width: 1 } });
    ctx.addShape(slide, { x, y: 260, width: 290, height: 8, fill: p[2] });
    ctx.addText(slide, { text: p[0], x: x + 22, y: 298, width: 110, height: 42, fontSize: 32, bold: true, color: p[2], typeface: ctx.fonts.title });
    ctx.addText(slide, { text: p[1], x: x + 22, y: 366, width: 230, height: 92, fontSize: 17, color: C.charcoal, typeface: ctx.fonts.mono });
  });
  ctx.addShape(slide, { x: 394, y: 374, width: 80, height: 3, fill: C.slate });
  ctx.addShape(slide, { x: 764, y: 374, width: 80, height: 3, fill: C.slate });
  note(slide, ctx, "Dynamic monitoring is not a replacement for static detection; it can become a second evidence channel for behavior-level similarity.", 160, 572, 940, 42, false, 18);
  footer(slide, ctx, "Roadmap", 14);
  return slide;
}
