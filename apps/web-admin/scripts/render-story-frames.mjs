#!/usr/bin/env node
/**
 * Renders the demo frame sequence for /story.
 *
 * The scroll component only needs a folder of same-size images; this script
 * exists so the demo has a real sequence without shipping a video file. It draws
 * one canvas scene (a thali spinning on the brand's violet plane) at 120 evenly
 * spaced values of `t`, and writes each as a WebP frame.
 *
 *   node scripts/render-story-frames.mjs [--count 120] [--width 960]
 *                                       [--height 540] [--quality 0.72]
 *                                       [--out public/sequence/plate]
 *
 * Replace the output folder with frames exported from a real video (ezgif
 * "Video to JPG", or ffmpeg) and the component plays that instead — same file
 * naming, same dimensions. See docs/scroll-story.md.
 */
import { mkdir, readdir, rm, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { chromium } from 'playwright';

const argv = process.argv.slice(2);
const option = (name, fallback) => {
  const index = argv.indexOf(`--${name}`);
  return index >= 0 && argv[index + 1] ? argv[index + 1] : fallback;
};

const count = Number(option('count', 120));
const width = Number(option('width', 960));
const height = Number(option('height', 540));
const quality = Number(option('quality', 0.72));
const outDir = resolve(process.cwd(), option('out', 'public/sequence/plate'));

const SCENE = String.raw`
const canvas = document.getElementById('scene');
const ctx = canvas.getContext('2d', { alpha: false });

const TAU = Math.PI * 2;

function ellipse(x, y, rx, ry, fill) {
  ctx.beginPath();
  ctx.ellipse(x, y, rx, ry, 0, 0, TAU);
  if (fill) { ctx.fillStyle = fill; ctx.fill(); }
}

/** Seven katoris, unevenly coloured so the rotation is unmistakable. */
const KATORIS = [
  { ink: '#F6E4B4', deep: '#C99736', size: 0.115 }, // paneer butter masala
  { ink: '#E7A63F', deep: '#9C5A12', size: 0.105 }, // dal
  { ink: '#8FB03F', deep: '#456A18', size: 0.100 }, // saag / greens
  { ink: '#E06A41', deep: '#8F2614', size: 0.095 }, // tandoori red
  { ink: '#FFF7E6', deep: '#DCC79A', size: 0.110 }, // rice
  { ink: '#C1834A', deep: '#6E3E16', size: 0.098 }, // gravy
  { ink: '#F2F6FC', deep: '#BCC9DA', size: 0.092 }, // raita
];

function drawBackdrop(W, H) {
  const glass = ctx.createRadialGradient(W * 0.5, H * 0.4, 0, W * 0.5, H * 0.4, Math.hypot(W, H) * 0.62);
  glass.addColorStop(0, '#3B1B8C');
  glass.addColorStop(0.48, '#271055');
  glass.addColorStop(1, '#0F0620');
  ctx.fillStyle = glass;
  ctx.fillRect(0, 0, W, H);

  // Table light pooling under the plate.
  const pool = ctx.createRadialGradient(W * 0.5, H * 0.78, 0, W * 0.5, H * 0.78, W * 0.34);
  pool.addColorStop(0, 'rgba(236, 140, 23, 0.20)');
  pool.addColorStop(1, 'rgba(236, 140, 23, 0)');
  ctx.fillStyle = pool;
  ctx.fillRect(0, 0, W, H);
}

function drawPlate(cx, cy, rx, ry, spin) {
  // Contact shadow, then the gold rim.
  const shadow = ctx.createRadialGradient(cx, cy + ry * 1.05, rx * 0.1, cx, cy + ry * 1.05, rx * 1.2);
  shadow.addColorStop(0, 'rgba(4, 1, 10, 0.65)');
  shadow.addColorStop(1, 'rgba(4, 1, 10, 0)');
  ctx.fillStyle = shadow;
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  const rim = ctx.createLinearGradient(cx - rx, cy - ry, cx + rx, cy + ry);
  rim.addColorStop(0, '#FCEBB4');
  rim.addColorStop(0.28, '#E1B24A');
  rim.addColorStop(0.55, '#A2700F');
  rim.addColorStop(0.8, '#D8AE4E');
  rim.addColorStop(1, '#8A5B06');
  ellipse(cx, cy, rx, ry, rim);

  // The well: brushed steel, so the food sits on something cooler than the rim.
  const well = ctx.createLinearGradient(cx - rx, cy - ry, cx + rx, cy + ry);
  well.addColorStop(0, '#241733');
  well.addColorStop(0.5, '#150C22');
  well.addColorStop(1, '#2A1B3C');
  ellipse(cx, cy, rx * 0.87, ry * 0.87, well);

  // Rotation notches on the rim: without them a symmetric plate looks static.
  ctx.save();
  for (let i = 0; i < 28; i++) {
    const a = spin + (i / 28) * TAU;
    const near = Math.sin(a);
    ctx.globalAlpha = 0.35 + 0.35 * ((near + 1) / 2);
    ellipse(
      cx + Math.cos(a) * rx * 0.945,
      cy + Math.sin(a) * ry * 0.945,
      rx * 0.012,
      ry * 0.012 * 1.6,
      '#FFF2C8'
    );
  }
  ctx.restore();

  // Specular sweep across the well.
  const sheen = ctx.createLinearGradient(cx - rx * 0.9, cy - ry, cx + rx * 0.5, cy + ry);
  sheen.addColorStop(0, 'rgba(255, 255, 255, 0)');
  sheen.addColorStop(0.45, 'rgba(255, 255, 255, 0.10)');
  sheen.addColorStop(0.62, 'rgba(255, 255, 255, 0.03)');
  sheen.addColorStop(1, 'rgba(255, 255, 255, 0)');
  ellipse(cx, cy, rx * 0.87, ry * 0.87, sheen);
}

function drawKatoris(cx, cy, rx, ry, spin) {
  const orbit = rx * 0.575;
  const items = KATORIS.map((katori, index) => {
    const a = spin + (index / KATORIS.length) * TAU;
    return { katori, a, depth: Math.sin(a) };
  }).sort((left, right) => left.depth - right.depth);

  for (const { katori, a, depth } of items) {
    // Nearer bowls are drawn last and slightly larger; the vertical squash is
    // what puts them on the plate rather than floating above it.
    const scale = 1 + depth * 0.16;
    const x = cx + Math.cos(a) * orbit;
    const y = cy + depth * ry * 0.62;
    const r = rx * katori.size * scale;

    ctx.save();
    ctx.beginPath();
    ctx.ellipse(x, y + r * 0.1, r * 1.05, r * 0.62, 0, 0, TAU);
    ctx.fillStyle = 'rgba(3, 1, 8, 0.35)';
    ctx.fill();

    const bowl = ctx.createRadialGradient(x - r * 0.35, y - r * 0.3, r * 0.1, x, y, r);
    bowl.addColorStop(0, katori.ink);
    bowl.addColorStop(0.72, katori.ink);
    bowl.addColorStop(1, katori.deep);
    ctx.beginPath();
    ctx.ellipse(x, y, r, r * 0.58, 0, 0, TAU);
    ctx.fillStyle = bowl;
    ctx.fill();

    ctx.beginPath();
    ctx.ellipse(x, y, r, r * 0.58, 0, 0, TAU);
    ctx.strokeStyle = 'rgba(255, 240, 205, ' + (0.30 + depth * 0.15) + ')';
    ctx.lineWidth = Math.max(1, rx * 0.006);
    ctx.stroke();

    // A little gloss on the near edge keeps the metal from reading as flat.
    ctx.beginPath();
    ctx.ellipse(x - r * 0.2, y - r * 0.15, r * 0.45, r * 0.2, -0.4, 0, TAU);
    ctx.fillStyle = 'rgba(255, 255, 255, 0.20)';
    ctx.fill();
    ctx.restore();
  }
}

function drawSteam(cx, cy, rx, ry, t) {
  ctx.save();
  ctx.lineCap = 'round';
  // Soft edges: a hard stroke at this size reads as a scratch on the background.
  ctx.filter = 'blur(' + Math.max(1, rx * 0.012) + 'px)';
  for (let i = 0; i < 3; i++) {
    const phase = TAU * (t + i / 3);
    const baseX = cx + (i - 1) * rx * 0.34;
    const baseY = cy - ry * 0.1;
    const rise = ry * (1.2 + 0.25 * Math.sin(phase * 0.7));
    const drift = Math.sin(phase) * rx * 0.14;
    const alpha = 0.05 + 0.05 * ((Math.sin(phase) + 1) / 2);

    const plume = ctx.createLinearGradient(baseX, baseY, baseX + drift, baseY - rise);
    plume.addColorStop(0, 'rgba(255, 244, 226, ' + alpha + ')');
    plume.addColorStop(1, 'rgba(255, 244, 226, 0)');
    ctx.strokeStyle = plume;
    ctx.lineWidth = rx * 0.022;
    ctx.beginPath();
    ctx.moveTo(baseX, baseY);
    ctx.bezierCurveTo(
      baseX + drift * 1.4, baseY - rise * 0.35,
      baseX - drift * 1.2, baseY - rise * 0.7,
      baseX + drift * 0.6, baseY - rise
    );
    ctx.stroke();
  }
  ctx.restore();
}

function drawVignette(W, H) {
  const vignette = ctx.createRadialGradient(W * 0.5, H * 0.5, Math.min(W, H) * 0.28, W * 0.5, H * 0.5, Math.max(W, H) * 0.74);
  vignette.addColorStop(0, 'rgba(8, 3, 18, 0)');
  vignette.addColorStop(1, 'rgba(8, 3, 18, 0.88)');
  ctx.fillStyle = vignette;
  ctx.fillRect(0, 0, W, H);
}

/** t: 0 at the first frame, 1 at the last. */
window.renderFrame = function renderFrame(t) {
  const W = canvas.width;
  const H = canvas.height;
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  drawBackdrop(W, H);

  const cx = W * 0.5;
  const cy = H * 0.545;
  const rx = Math.min(W * 0.37, H * 0.66);
  const ry = rx * 0.35;
  // Slow push-in: the plate grows about 9% over the sequence, so the scrub has a
  // second motion cue besides the spin.
  const zoom = 1.02 + 0.09 * t;

  ctx.save();
  ctx.translate(cx, cy);
  ctx.scale(zoom, zoom);
  ctx.translate(-cx, -cy);
  drawPlate(cx, cy, rx, ry, t * TAU);
  drawKatoris(cx, cy, rx, ry, t * TAU);
  drawSteam(cx, cy, rx, ry, t);
  ctx.restore();

  drawVignette(W, H);
};

window.renderFrame(0);
`;

const html = `<!doctype html>
<html>
  <head><meta charset="utf-8"><style>html,body{margin:0;background:#0F0620}canvas{display:block}</style></head>
  <body><canvas id="scene" width="${width}" height="${height}"></canvas><script>${SCENE}</script></body>
</html>`;

async function main() {
  if (!Number.isFinite(count) || count < 1) {
    throw new Error('--count must be a positive number');
  }

  await mkdir(outDir, { recursive: true });

  // Drop frames from an earlier, longer run so the folder always matches `count`.
  for (const name of await readdir(outDir)) {
    if (/^frame-\d+\.webp$/.test(name)) {
      await rm(join(outDir, name));
    }
  }

  const browser = await chromium.launch();
  let bytes = 0;

  try {
    const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: 1 });
    await page.setContent(html, { waitUntil: 'load' });

    for (let i = 0; i < count; i++) {
      const progress = count === 1 ? 0 : i / (count - 1);
      const dataUrl = await page.evaluate(
        ([value, imageQuality]) => {
          window.renderFrame(value);
          return document.getElementById('scene').toDataURL('image/webp', imageQuality);
        },
        [progress, quality]
      );

      const buffer = Buffer.from(dataUrl.slice(dataUrl.indexOf(',') + 1), 'base64');
      const name = `frame-${String(i + 1).padStart(3, '0')}.webp`;
      await writeFile(join(outDir, name), buffer);
      bytes += buffer.length;

      if ((i + 1) % 20 === 0 || i === count - 1) {
        console.log(`  ${i + 1}/${count} frames  (${(bytes / 1024).toFixed(0)} KB so far)`);
      }
    }
  } finally {
    await browser.close();
  }

  console.log(`\n${count} frames · ${width}x${height} · ${(bytes / (1024 * 1024)).toFixed(2)} MB`);
  console.log(`Written to ${outDir}`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
