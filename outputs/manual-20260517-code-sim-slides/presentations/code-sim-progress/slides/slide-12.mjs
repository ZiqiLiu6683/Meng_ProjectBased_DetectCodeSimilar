import { C, bg, kicker, title, note, footer, box } from "./common.mjs";

export async function slide12(presentation, ctx) {
  const slide = presentation.slides.add();
  bg(slide, ctx, C.ink);
  kicker(slide, ctx, "WEB PROTOTYPE", C.green, true);
  title(slide, ctx, "The web interface turns pipeline evidence into a readable report.", true);
  const panels = [
    ["Input", "two editors\ninferred file names\nload sample", C.blue],
    ["Summary", "clone type\nscope\nconfidence", C.green],
    ["Evidence", "matched methods\nwhy this result\nwarnings", C.amber],
    ["Developer JSON", "full staged output\nschemaVersion 1.0", C.red],
  ];
  panels.forEach((p, i) => {
    const x = 82 + i * 292;
    ctx.addShape(slide, { x, y: 270, width: 235, height: 210, fill: "#1B2534", line: { style: "solid", fill: "#405168", width: 1 } });
    ctx.addShape(slide, { x, y: 270, width: 235, height: 7, fill: p[2] });
    ctx.addText(slide, { text: p[0], x: x + 18, y: 300, width: 190, height: 30, fontSize: 22, bold: true, color: C.white, typeface: ctx.fonts.title });
    ctx.addText(slide, { text: p[1], x: x + 18, y: 356, width: 190, height: 76, fontSize: 17, color: "#D9E4EA", typeface: ctx.fonts.mono });
  });
  note(slide, ctx, "UI design principle: the first screen enables a quick comparison; the report explains the decision in layers.", 104, 564, 950, 38, true, 18);
  footer(slide, ctx, "Web report design", 12, true);
  return slide;
}
