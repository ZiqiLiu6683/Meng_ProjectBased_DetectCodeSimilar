export const C = {
  ink: "#111827",
  paper: "#F6F1E8",
  mist: "#E6EEF2",
  blue: "#315C8A",
  green: "#3E7C59",
  amber: "#C9792E",
  red: "#B6534C",
  slate: "#64748B",
  line: "#CBD5E1",
  white: "#FFFFFF",
  charcoal: "#1F2937",
};

export function bg(slide, ctx, color = C.paper) {
  ctx.addShape(slide, { x: 0, y: 0, width: ctx.W, height: ctx.H, fill: color });
}

export function kicker(slide, ctx, text, color = C.blue, dark = false) {
  ctx.addShape(slide, { name: "kicker-marker", x: 58, y: 48, width: 34, height: 3, fill: color });
  ctx.addText(slide, {
    name: "kicker-label",
    text,
    x: 104,
    y: 37,
    width: 360,
    height: 26,
    fontSize: 14,
    bold: true,
    color: dark ? C.mist : C.slate,
    typeface: ctx.fonts.mono,
    valign: "middle",
  });
}

export function title(slide, ctx, text, dark = false, y = 86, size = 44) {
  ctx.addText(slide, {
    text,
    x: 58,
    y,
    width: 900,
    height: 110,
    fontSize: size,
    bold: true,
    color: dark ? C.white : C.ink,
    typeface: ctx.fonts.title,
    insets: { left: 0, right: 0, top: 0, bottom: 0 },
  });
}

export function note(slide, ctx, text, x, y, width, height, dark = false, size = 18) {
  ctx.addText(slide, {
    text,
    x,
    y,
    width,
    height,
    fontSize: size,
    color: dark ? "#D9E4EA" : C.charcoal,
    line: ctx.line(),
    insets: { left: 0, right: 0, top: 0, bottom: 0 },
  });
}

export function footer(slide, ctx, section, n, dark = false) {
  ctx.addShape(slide, { x: 58, y: 662, width: 1050, height: 1, fill: dark ? "#334155" : C.line });
  ctx.addText(slide, {
    text: section,
    x: 58,
    y: 672,
    width: 600,
    height: 22,
    fontSize: 12,
    color: dark ? "#AAB7C4" : C.slate,
    typeface: ctx.fonts.mono,
  });
  ctx.addText(slide, {
    text: String(n).padStart(2, "0"),
    x: 1150,
    y: 672,
    width: 70,
    height: 22,
    fontSize: 12,
    color: dark ? "#AAB7C4" : C.slate,
    align: "right",
    typeface: ctx.fonts.mono,
  });
}

export function label(slide, ctx, text, x, y, w, h, color = C.slate, size = 13) {
  ctx.addText(slide, {
    text,
    x,
    y,
    width: w,
    height: h,
    fontSize: size,
    color,
    bold: true,
    typeface: ctx.fonts.mono,
    valign: "middle",
  });
}

export function box(slide, ctx, { x, y, w, h, fill = C.white, line = C.line, head, body, accent = C.blue, dark = false }) {
  ctx.addShape(slide, { x, y, width: w, height: h, fill, line: { style: "solid", fill: line, width: 1 } });
  ctx.addShape(slide, { x, y, width: 5, height: h, fill: accent });
  if (head) {
    ctx.addText(slide, {
      text: head,
      x: x + 18,
      y: y + 16,
      width: w - 32,
      height: 28,
      fontSize: 18,
      bold: true,
      color: dark ? C.white : C.ink,
      valign: "middle",
    });
  }
  if (body) {
    ctx.addText(slide, {
      text: body,
      x: x + 18,
      y: y + 50,
      width: w - 34,
      height: h - 62,
      fontSize: 15,
      color: dark ? "#D9E4EA" : C.charcoal,
      insets: { left: 0, right: 0, top: 0, bottom: 0 },
    });
  }
}

export function stageBlock(slide, ctx, x, y, w, h, stage, body, color = C.blue) {
  ctx.addShape(slide, { x, y, width: w, height: h, fill: C.white, line: { style: "solid", fill: C.line, width: 1 } });
  ctx.addShape(slide, { x, y, width: w, height: 7, fill: color });
  ctx.addText(slide, { text: stage, x: x + 14, y: y + 18, width: w - 28, height: 28, fontSize: 18, bold: true, color: C.ink });
  ctx.addText(slide, { text: body, x: x + 14, y: y + 54, width: w - 28, height: h - 66, fontSize: 14, color: C.charcoal });
}
