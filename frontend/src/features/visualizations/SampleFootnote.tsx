import { labels, type Sample } from "./model";
export default function SampleFootnote({ sample, en }: { sample: Sample; en: boolean }) {
  const names = labels(en);const reasons: Record<string, string> = en ? { MISSING: "missing", QUALITY_BLOCKED: "quality flag", OUT_OF_RANGE: "out of range", INSUFFICIENT_SAMPLE: "insufficient sample" } : { MISSING: "缺值", QUALITY_BLOCKED: "品質標記", OUT_OF_RANGE: "超出範圍", INSUFFICIENT_SAMPLE: "樣本不足" };
  return <div className="sample-footnote"><p>{en ? `Population ${sample.population} · Valid ${sample.valid} · Excluded ${sample.excluded}` : `母體 ${sample.population} · 有效 ${sample.valid} · 排除 ${sample.excluded}`}</p>
    {!!sample.primaryReasons.length && <ul>{sample.primaryReasons.map(r => <li key={`${r.metric}-${r.code}`}>{names[r.metric]}：{reasons[r.code] ?? r.code} {r.count}</li>)}</ul>}
    {sample.valid < 5 && <p className="explorer-warning">{en ? "Fewer than 5 valid observations. Too little data for a relationship claim." : "有效觀測不足 5 筆，不宜據此判斷關聯。"}</p>}
  </div>;
}
