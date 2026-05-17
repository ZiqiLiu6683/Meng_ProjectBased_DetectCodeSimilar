import { C, bg, kicker, title, note, footer, box } from "./common.mjs";

export async function slide09(presentation, ctx) {
  const slide = presentation.slides.add();
  bg(slide, ctx);
  kicker(slide, ctx, "MEASUREMENT LAYER");
  title(slide, ctx, "Stage 1 and Stage 2 create evidence without making the final call.");
  box(slide, ctx, { x: 78, y: 238, w: 315, h: 250, head: "Stage 1 raw signals", body: "S1 non-method context\nS2 subtree overlap\nS3 token winnowing\nS4 tree edit distance\nS5 API vocabulary\nexact normalized match", accent: C.blue });
  box(slide, ctx, { x: 482, y: 238, w: 315, h: 250, head: "Method pair matrix", body: "Every method in A is compared with every method in B first. Candidate filtering is delayed so early pruning does not erase partial or one-to-many evidence.", accent: C.green });
  box(slide, ctx, { x: 886, y: 238, w: 315, h: 250, head: "Stage 2 features", body: "magnitude\nstructural exactness\ntoken exact gap\nspread\nsize ratio\ncontainment A/B", accent: C.amber });
  ctx.addShape(slide, { x: 393, y: 358, width: 88, height: 3, fill: C.line });
  ctx.addShape(slide, { x: 797, y: 358, width: 88, height: 3, fill: C.line });
  note(slide, ctx, "Assumption: a complete method-pair matrix is safer than early top-k selection because real reuse patterns are often asymmetric.", 148, 566, 980, 42, false, 18);
  footer(slide, ctx, "Stage 1-2 evidence generation", 9);
  return slide;
}
