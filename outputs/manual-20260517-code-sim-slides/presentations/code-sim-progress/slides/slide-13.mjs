import { C, bg, kicker, title, note, footer } from "./common.mjs";

export async function slide13(presentation, ctx) {
  const slide = presentation.slides.add();
  bg(slide, ctx);
  kicker(slide, ctx, "CURRENT LIMITATIONS", C.red);
  title(slide, ctx, "The prototype works, but the open risks are still research-shaped.");
  const risks = [
    ["Static only", "No runtime behavior, path coverage, or execution trace evidence yet."],
    ["Heuristic weights", "Stage 4 weights and cutoffs need benchmark calibration."],
    ["Weak Type-4", "API vocabulary is a hint, not proof of semantic equivalence."],
    ["Scale pressure", "Tree edit distance can become expensive for large methods."],
    ["Evaluation depth", "Need per-type metrics, not only a demo-level success case."],
  ];
  risks.forEach((r, i) => {
    const x = 92 + (i % 2) * 540;
    const y = 230 + Math.floor(i / 2) * 112;
    const w = i === 4 ? 820 : 440;
    ctx.addShape(slide, { x, y, width: w, height: 76, fill: C.white, line: { style: "solid", fill: C.line, width: 1 } });
    ctx.addShape(slide, { x, y, width: 6, height: 76, fill: i < 3 ? C.amber : C.red });
    ctx.addText(slide, { text: r[0], x: x + 18, y: y + 13, width: 170, height: 24, fontSize: 18, bold: true, color: C.ink });
    ctx.addText(slide, { text: r[1], x: x + 18, y: y + 42, width: w - 36, height: 24, fontSize: 14, color: C.charcoal });
  });
  note(slide, ctx, "This is useful for the presentation: the limitations naturally define the next work packages.", 92, 590, 820, 32, false, 18);
  footer(slide, ctx, "What remains unresolved", 13);
  return slide;
}
