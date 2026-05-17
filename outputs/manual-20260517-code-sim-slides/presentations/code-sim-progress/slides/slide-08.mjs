import { C, bg, kicker, title, note, footer, stageBlock } from "./common.mjs";

export async function slide08(presentation, ctx) {
  const slide = presentation.slides.add();
  bg(slide, ctx, C.ink);
  kicker(slide, ctx, "CURRENT DESIGN", C.blue, true);
  title(slide, ctx, "The staged pipeline routes inputs before it interprets scores.", true, 82, 42);
  const blocks = [
    ["Stage 0", "Input profiling\nparse state, method counts, flags", C.amber],
    ["Stage 1", "Raw measurement\nS1/S2/S3/S4/S5 + exact match", C.blue],
    ["Stage 2", "Pair features\nmagnitude, gaps, containment", C.green],
    ["Stage 3", "Correspondence\nbest matches, coverage, partial signal", C.blue],
    ["Stage 4", "Classification\nclone type, scope, confidence", C.red],
    ["Stage 5", "Report output\nJSON, text, web presentation", C.green],
  ];
  blocks.forEach((b, i) => {
    const x = 70 + (i % 3) * 380;
    const y = 240 + Math.floor(i / 3) * 170;
    ctx.addShape(slide, { x, y, width: 310, height: 122, fill: "#1B2534", line: { style: "solid", fill: "#405168", width: 1 } });
    ctx.addShape(slide, { x, y, width: 310, height: 7, fill: b[2] });
    ctx.addText(slide, { text: b[0], x: x + 18, y: y + 24, width: 120, height: 24, fontSize: 19, bold: true, color: C.white, typeface: ctx.fonts.mono });
    ctx.addText(slide, { text: b[1], x: x + 18, y: y + 58, width: 260, height: 48, fontSize: 15, color: "#D9E4EA" });
  });
  note(slide, ctx, "Pipeline principle: later stages consume evidence; they should not silently recompute or reinterpret earlier signals.", 70, 584, 920, 34, true, 17);
  footer(slide, ctx, "Stage 0-5 architecture", 8, true);
  return slide;
}
