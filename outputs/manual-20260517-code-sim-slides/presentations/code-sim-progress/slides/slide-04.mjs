import { C, bg, kicker, title, note, footer, box } from "./common.mjs";

export async function slide04(presentation, ctx) {
  const slide = presentation.slides.add();
  bg(slide, ctx, C.mist);
  kicker(slide, ctx, "TOKEN SIGNAL");
  title(slide, ctx, "Winnowing gave a fast baseline, but its strength is also its boundary.");
  box(slide, ctx, { x: 86, y: 240, w: 440, h: 230, head: "What worked", body: "Normalization, k-grams, rolling hash, and rightmost-min winnowing make local similarity measurable and efficient.", accent: C.green });
  box(slide, ctx, { x: 750, y: 240, w: 440, h: 230, head: "What was missing", body: "A token overlap score cannot reliably tell whether a change is renaming, method extraction, structural modification, or only a shared idiom.", accent: C.red });
  ctx.addShape(slide, { x: 566, y: 310, width: 145, height: 36, fill: C.ink });
  ctx.addText(slide, { text: "score != reason", x: 580, y: 317, width: 120, height: 24, fontSize: 15, bold: true, color: C.white, typeface: ctx.fonts.mono, align: "center" });
  ctx.addShape(slide, { x: 526, y: 334, width: 40, height: 2, fill: C.ink });
  ctx.addShape(slide, { x: 711, y: 334, width: 40, height: 2, fill: C.ink });
  note(slide, ctx, "This became Stage 1 evidence, not the final classifier.", 410, 548, 470, 34, false, 20);
  footer(slide, ctx, "Strategy A: token / winnowing", 4);
  return slide;
}
