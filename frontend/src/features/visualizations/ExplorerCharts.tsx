import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { init, use as registerCharts, type EChartsCoreOption, type EChartsType, type ECElementEvent } from "echarts/core";
import { ScatterChart, BarChart } from "echarts/charts";
import { GridComponent, TooltipComponent, LegendComponent, AriaComponent, DataZoomSliderComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
import { groupLabel, interval, labels, type Scatter, type Histogram, type Point } from "./model";
registerCharts([ScatterChart, BarChart, GridComponent, TooltipComponent, LegendComponent, AriaComponent, DataZoomSliderComponent, CanvasRenderer]);

function Canvas({ option, label, onHover, onOpen }: { option: EChartsCoreOption; label: string; onHover?: (p: Point) => void; onOpen?: (p: Point) => void }) {
  const element = useRef<HTMLDivElement>(null);const chart = useRef<EChartsType | null>(null);
  useEffect(() => {
    const instance = init(element.current!, null, { renderer: "canvas" });chart.current = instance;
    instance.on("finished", () => element.current?.setAttribute("aria-busy", "false"));
    void document.fonts?.ready.then(() => { if (!instance.isDisposed()) instance.resize(); });
    const resize = new ResizeObserver(() => instance.resize());resize.observe(element.current!);
    return () => { resize.disconnect();instance.dispose();chart.current = null; };
  }, []);
  useEffect(() => {
    const instance = chart.current!;element.current?.setAttribute("aria-busy", "true");instance.setOption(option, { notMerge: true });
    const hover = (event: ECElementEvent) => { const p = (event.data as { point?: Point })?.point;if (p) onHover?.(p); };
    const open = (event: ECElementEvent) => { const p = (event.data as { point?: Point })?.point;if (p) onOpen?.(p); };
    instance.on("mouseover", hover);instance.on("click", open);
    return () => { instance.off("mouseover", hover);instance.off("click", open); };
  }, [option, onHover, onOpen]);
  return <div ref={element} className="explorer-canvas" aria-busy="true" role="img" aria-label={label} />;
}
const colors: Record<string, string> = { R: "#244f3a", L: "#a2502a", U: "#386e83", A: "#755586", UNKNOWN: "#6b7369", ALL: "#244f3a" };
const symbols: Record<string, string> = { R: "circle", L: "triangle", U: "diamond", A: "rect", UNKNOWN: "pin", ALL: "circle" };
const base = () => ({ animation: !window.matchMedia?.("(prefers-reduced-motion: reduce)").matches, animationDuration: 300, textStyle: { fontFamily: '"DM Sans Variable", "Noto Sans TC Variable", sans-serif', color: "#1e3529" } });
export default function ExplorerCharts({ scatter, histogram, en }: { scatter: Scatter; histogram: Histogram; en: boolean }) {
  const navigate = useNavigate();const [hovered, setHovered] = useState<Point | null>(null);const names = labels(en);
  const open = useCallback((p: Point) => { void navigate(`/players/${p.tour}/${p.playerId}`); }, [navigate]);
  const scatterLabel = `${names[scatter.axes.x.key]} × ${names[scatter.axes.y.key]} · ${en ? "scatter plot; use the data table for keyboard navigation" : "散布圖；鍵盤操作請使用下方資料表"}`;
  const scatterOption = useMemo<EChartsCoreOption>(() => ({
    ...base(), aria: { enabled: true, label: { description: scatterLabel } },
    grid: { top: 50, left: 58, right: 24, bottom: 84 },
    tooltip: { renderMode: "richText", confine: true, formatter: (event: { data?: { point?: Point } }) => { const p = event.data?.point;return p ? `${p.displayName}\n${p.tour} #${p.playerId} · ${groupLabel(p.group, en)}\n${labels(en)[scatter.axes.x.key]}: ${p.x}\n${labels(en)[scatter.axes.y.key]}: ${p.y}\nn = ${scatter.sample.valid}` : ""; } },
    legend: { top: 8, textStyle: { fontSize: 11 }, itemWidth: 10, itemHeight: 10, selectedMode: false },
    xAxis: { type: "value", name: labels(en)[scatter.axes.x.key], nameLocation: "middle", nameGap: 30, inverse: scatter.axes.x.key === "rank", min: scatter.axes.x.key === "rank" ? 1 : undefined, scale: true, splitLine: { lineStyle: { color: "#e4e5dc" } } },
    yAxis: { type: "value", name: labels(en)[scatter.axes.y.key], nameLocation: "middle", nameGap: 40, nameRotate: 90, inverse: scatter.axes.y.key === "rank", min: scatter.axes.y.key === "rank" ? 1 : undefined, scale: true, splitLine: { lineStyle: { color: "#e4e5dc" } } },
    dataZoom: [{ type: "slider", xAxisIndex: 0, bottom: 8, height: 14, showDetail: false, brushSelect: false, borderColor: "#d6dbce", fillerColor: "#d9ec8666" }],
    series: [...new Set(scatter.items.map(p => p.group))].map(group => ({ type: "scatter", name: groupLabel(group, en), symbol: symbols[group] ?? "circle", symbolSize: 10, itemStyle: { color: colors[group] ?? colors.UNKNOWN, opacity: .8 }, emphasis: { scale: 1.6 }, data: scatter.items.filter(p => p.group === group).map(p => ({ name: p.displayName, value: [p.x, p.y], point: p })) })),
  }), [scatter, en, scatterLabel]);
  const histogramOption = useMemo<EChartsCoreOption>(() => ({
    ...base(), aria: { enabled: true, label: { description: en ? "Height distribution; bin counts also available in the data table" : "身高分布；各箱計數可在下方資料表查閱" } },
    grid: { top: 50, left: 48, right: 20, bottom: 84 },
    tooltip: { renderMode: "richText", confine: true, formatter: (event: { dataIndex: number }) => { const bin = histogram.bins[event.dataIndex];return `${interval(bin)} cm\n${en ? "Players" : "球員數"}: ${bin.count}`; } },
    xAxis: { type: "category", data: histogram.bins.map(interval), name: en ? "Height (cm)" : "身高（cm）", nameLocation: "middle", nameGap: 40, axisLabel: { interval: 0, fontSize: 10, formatter: (_v: string, index: number) => index === 0 ? String(histogram.bins[0].lower) : index === histogram.bins.length - 1 ? String(histogram.bins[index].upper) : "" } },
    yAxis: { type: "value", name: en ? "Players" : "球員數", minInterval: 1, splitLine: { lineStyle: { color: "#e4e5dc" } } },
    series: [{ type: "bar", barCategoryGap: "4%", itemStyle: { color: "#a2502a", borderRadius: [3, 3, 0, 0] }, data: histogram.bins.map(bin => bin.count) }],
  }), [histogram, en]);
  return <div className="explorer-chart-grid"><div><p className="explorer-plot-label">{names[scatter.axes.x.key]} × {names[scatter.axes.y.key]}</p>{scatter.sample.valid === 0 ? <div className="explorer-state">{en ? "No valid pairs for these axes." : "這組座標沒有有效配對。"}</div> : <Canvas option={scatterOption} label={scatterLabel} onHover={setHovered} onOpen={open}/>}<p className="explorer-hover" role="status" aria-live="polite">{hovered ? `${hovered.displayName} · ${hovered.tour} #${hovered.playerId} · ${names[scatter.axes.x.key]} ${hovered.x} / ${names[scatter.axes.y.key]} ${hovered.y} · ${groupLabel(hovered.group, en)}` : en ? "Hover for details; click a point to open the player. Zoom changes the plot only." : "移到散點查看數值，點選進入球員頁；縮放僅影響圖形。"}</p></div><div><p className="explorer-plot-label">{en ? "Height distribution · cm" : "身高分布 · cm"}</p><Canvas option={histogramOption} label={en ? "Height histogram" : "身高直方圖"}/><p className="explorer-hover">{en ? "Equal-width bins; the last bin includes its upper edge." : "採等寬分箱；最後一箱包含右端點。"}</p></div></div>;
}
