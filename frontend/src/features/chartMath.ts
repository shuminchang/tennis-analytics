export interface Datum { date: string; value: number | null }
export function chartPath(data: Datum[], inverted = false): string {
  const values = data.flatMap(p => p.value === null ? [] : [p.value]);if (!values.length) return "";
  const min = Math.min(...values), max = Math.max(...values), first = Date.parse(data[0].date), last = Date.parse(data.at(-1)!.date);
  let pen = false;
  return data.map(p => {
    if (p.value === null) { pen = false;return ""; }
    const x = 60 + (Date.parse(p.date) - first) / (last - first || 1) * 650;
    const y = 20 + (inverted ? (p.value - min) : (max - p.value)) / (max - min || 1) * 180;
    const segment = `${pen ? "L" : "M"}${x.toFixed(2)},${y.toFixed(2)}`;pen = true;return segment;
  }).join(" ");
}
