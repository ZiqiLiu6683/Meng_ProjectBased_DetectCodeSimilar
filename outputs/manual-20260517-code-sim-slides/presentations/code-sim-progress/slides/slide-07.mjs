import { C, bg, kicker, title, note, footer } from "./common.mjs";

export async function slide07(presentation, ctx) {
  const slide = presentation.slides.add();
  bg(slide, ctx);
  kicker(slide, ctx, "DESIGN LESSON");
  title(slide, ctx, "The redesign separates measurement, correspondence, and classification.");
  ctx.addText(slide, { text: "Before", x: 118, y: 242, width: 160, height: 32, fontSize: 24, bold: true, color: C.red, typeface: ctx.fonts.title });
  ctx.addShape(slide, { x: 120, y: 310, width: 360, height: 84, fill: C.white, line: { style: "solid", fill: C.line, width: 1 } });
  ctx.addText(slide, { text: "Compute scores", x: 142, y: 330, width: 300, height: 22, fontSize: 18, bold: true, color: C.ink });
  ctx.addText(slide, { text: "Collapse into one result too early", x: 142, y: 360, width: 300, height: 22, fontSize: 15, color: C.slate });
  ctx.addShape(slide, { x: 500, y: 350, width: 90, height: 3, fill: C.line });
  ctx.addText(slide, { text: "Now", x: 674, y: 242, width: 160, height: 32, fontSize: 24, bold: true, color: C.green, typeface: ctx.fonts.title });
  const steps = ["Raw signals", "Method-pair features", "Correspondence", "Clone classification", "Structured report"];
  steps.forEach((s, i) => {
    const x = 650 + (i % 2) * 260;
    const y = 300 + Math.floor(i / 2) * 76;
    ctx.addShape(slide, { x, y, width: 220, height: 50, fill: C.white, line: { style: "solid", fill: C.line, width: 1 } });
    ctx.addText(slide, { text: s, x: x + 14, y: y + 14, width: 190, height: 22, fontSize: 16, bold: true, color: C.ink });
  });
  note(slide, ctx, "This also creates a cleaner place for explicit statuses like NOT_APPLICABLE instead of pretending missing evidence is zero.", 168, 548, 900, 44, false, 18);
  footer(slide, ctx, "Design pivot", 7);
  return slide;
}
