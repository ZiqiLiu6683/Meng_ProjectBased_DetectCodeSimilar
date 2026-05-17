import { C, bg, kicker, title, note, footer } from "./common.mjs";

export async function slide05(presentation, ctx) {
  const slide = presentation.slides.add();
  bg(slide, ctx);
  kicker(slide, ctx, "STRUCTURE SIGNAL");
  title(slide, ctx, "AST and method-level comparison exposed the matching problem.", false, 86, 40);
  const left = ["A.scoreOrder", "A.quantityRisk", "A.emailRisk", "A.normalize"];
  const right = ["B.calculateRisk", "B.itemRiskScore", "B.tempEmail", "B.cleanRegion", "B.keepLargeOrders"];
  left.forEach((t, i) => {
    const y = 238 + i * 78;
    ctx.addShape(slide, { x: 112, y, width: 250, height: 42, fill: C.white, line: { style: "solid", fill: C.line, width: 1 } });
    ctx.addText(slide, { text: t, x: 128, y: y + 10, width: 220, height: 22, fontSize: 16, color: C.ink, typeface: ctx.fonts.mono });
  });
  right.forEach((t, i) => {
    const y = 220 + i * 68;
    ctx.addShape(slide, { x: 865, y, width: 270, height: 42, fill: C.white, line: { style: "solid", fill: C.line, width: 1 } });
    ctx.addText(slide, { text: t, x: 881, y: y + 10, width: 238, height: 22, fontSize: 16, color: C.ink, typeface: ctx.fonts.mono });
  });
  const pairs = [[362,259,865,241,C.blue], [362,337,865,309,C.green], [362,415,865,377,C.amber], [362,493,865,445,C.blue], [362,259,865,513,C.red]];
  pairs.forEach(p => {
    const [x1,y1,x2,y2,c] = p;
    ctx.addShape(slide, { x: x1, y: y1, width: x2 - x1, height: 2, fill: c });
  });
  ctx.addText(slide, { text: "Do not force one-to-one matching.", x: 442, y: 304, width: 360, height: 34, fontSize: 24, bold: true, color: C.ink, typeface: ctx.fonts.title, align: "center" });
  note(slide, ctx, "Partial clones, extracted helpers, and added methods are evidence, not noise.", 430, 356, 380, 64, false, 18);
  footer(slide, ctx, "Strategy B/C: AST + method level", 5);
  return slide;
}
