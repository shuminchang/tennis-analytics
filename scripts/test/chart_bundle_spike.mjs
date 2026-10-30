// Reproducible ECharts module-vs-full library probe; outputs only under .local.
import { build } from '../../frontend/node_modules/vite/dist/node/index.js';
import { resolve } from 'node:path';
import { gzipSync } from 'node:zlib';
import { readFileSync, writeFileSync, unlinkSync, readdirSync, mkdirSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
const root = resolve(import.meta.dirname, '../..');
const frontend = resolve(root, 'frontend');
const output = resolve(root, '.local/verification');mkdirSync(output, { recursive: true });
const variants = {
  modular: `import {init,use} from 'echarts/core';
import {ScatterChart,BarChart} from 'echarts/charts';
import {GridComponent,TooltipComponent,LegendComponent,AriaComponent,DataZoomSliderComponent} from 'echarts/components';
import {CanvasRenderer} from 'echarts/renderers';
use([ScatterChart,BarChart,GridComponent,TooltipComponent,LegendComponent,AriaComponent,DataZoomSliderComponent,CanvasRenderer]);
export {init};`,
  full: `export {init} from 'echarts';`,
};
const report = { echarts: JSON.parse(readFileSync(resolve(frontend, 'node_modules/echarts/package.json'))).version, format: 'Vite minified ES library probe; not the final application chunk', variants: {} };
for (const [kind, source] of Object.entries(variants)) {
  const entry = resolve(frontend, `.echarts-probe-${randomUUID()}.ts`);
  try {
    writeFileSync(entry, source);
    const outDir = resolve(output, 'echarts-spike-' + kind);
    await build({ configFile: false, root: frontend, publicDir: false, logLevel: 'error', build: { outDir, emptyOutDir: true, minify: true, lib: { entry, formats: ['es'], fileName: 'spike' } } });
    const text = readdirSync(outDir).filter(name => name.endsWith('.js')).map(name => readFileSync(resolve(outDir, name))).join('');
    report.variants[kind] = { bytes: Buffer.byteLength(text), gzipBytes: gzipSync(text).length };
  } finally { unlinkSync(entry); }
}
writeFileSync(resolve(output, 'echarts-spike.json'), JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report, null, 2));
