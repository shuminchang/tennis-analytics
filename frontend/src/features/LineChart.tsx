import { chartPath } from "./chartMath";
import type { Datum } from "./chartMath";
export default function LineChart({ data, label, inverted = false }: { data: Datum[]; label: string; inverted?: boolean }) {
  const values = data.filter(p => p.value !== null);
  return <figure><svg className="line-chart" viewBox="0 0 740 230" role="img" aria-label={label}><title>{label}</title><path d={chartPath(data, inverted)} fill="none" stroke="currentColor" strokeWidth="2"/><text x="50" textAnchor="end" y="20">{values.length ? (inverted ? Math.round(Math.min(...values.map(p => p.value!))) : Math.round(Math.max(...values.map(p => p.value!)))) : ""}</text><text x="50" textAnchor="end" y="200">{values.length ? (inverted ? Math.round(Math.max(...values.map(p => p.value!))) : Math.round(Math.min(...values.map(p => p.value!)))) : ""}</text>{values.length === 1 && <circle cx="60" cy="20" r="4" fill="currentColor"/>}<line x1="60" y1="210" x2="710" y2="210" stroke="currentColor" opacity=".25"/><text x="60" y="227">{data[0]?.date}</text><text x="710" y="227" textAnchor="end">{data.at(-1)?.date}</text></svg><figcaption>{label} · {values.length} / {data.length}</figcaption></figure>;
}
