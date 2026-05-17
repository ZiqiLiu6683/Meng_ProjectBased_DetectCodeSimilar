import { C, bg, kicker, title, note, footer, label } from "./common.mjs";

export async function slide02(presentation, ctx) {
  const slide = presentation.slides.add();
  bg(slide, ctx);
  kicker(slide, ctx, "PROBLEM FRAME");
  title(slide, ctx, "Code similarity is not one behavior, so one score hides the real question.");
  note(slide, ctx, "The detector needs to distinguish exact copies, renamed code, edited code, and weak semantic similarity while also explaining scope and confidence.", 60, 204, 850, 56);
  const items = [
    ["T1", "Exact / formatting", C.green, "comments, whitespace"],
    ["T2", "Renamed but same structure", C.blue, "identifiers, literals"],
    ["T3", "Modified clone", C.amber, "insertions, deletions"],
    ["T4", "Semantic similarity", C.red, "different structure"],
  ];
  items.forEach((it, i) => {
    const x = 92 + i * 285;
    ctx.addShape(slide, { x, y: 342, width: 220, height: 18, fill: it[2] });
    ctx.addText(slide, { text: it[0], x, y: 286, width: 72, height: 42, fontSize: 34, bold: true, color: it[2], typeface: ctx.fonts.title });
    ctx.addText(slide, { text: it[1], x, y: 374, width: 230, height: 30, fontSize: 20, bold: true, color: C.ink });
    ctx.addText(slide, { text: it[3], x, y: 412, width: 220, height: 42, fontSize: 15, color: C.slate });
  });
  ctx.addShape(slide, { x: 92, y: 351, width: 1066, height: 2, fill: C.line });
  label(slide, ctx, "textual", 92, 490, 120, 22);
  label(slide, ctx, "structural", 520, 490, 120, 22);
  label(slide, ctx, "semantic", 1040, 490, 120, 22);
  note(slide, ctx, "Design implication: the system should produce a decision with evidence, warnings, and scope, not just a similarity percentage.", 180, 570, 860, 44);
  footer(slide, ctx, "Why the problem needs stages", 2);
  return slide;
}
