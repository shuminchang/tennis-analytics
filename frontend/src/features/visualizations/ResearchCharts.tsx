import { useMemo } from "react";
import { use as registerCharts, type EChartsCoreOption } from "echarts/core";
import { LineChart } from "echarts/charts";
import { GridComponent, TooltipComponent, LegendComponent, AriaComponent } from "echarts/components";
import Canvas from "./ChartCanvas";
import { percent, type Ages } from "./researchModel";
registerCharts([LineChart, GridComponent, TooltipComponent, LegendComponent, AriaComponent]);
const palette = ["#244f3a", "#a2502a", "#386e83", "#755586", "#6b7369"];
export default function ResearchCharts({ data, en }: { data: Ages; en: boolean }) {
  const label = en ? "Age and observed win rate, Wilson 95% intervals" : "年齡與來源勝率，Wilson 95% 區間";
  const option = useMemo<EChartsCoreOption>(() => {
    const groups = [...new Set(data.items.map(r => r.surface))];const start = Math.min(...data.items.map(r => r.lowerAge)), end = Math.max(...data.items.map(r => r.lowerAge));
    return { animation: !window.matchMedia?.("(prefers-reduced-motion: reduce)").matches, aria: { enabled: true, label: { description: label } },
      textStyle: { fontFamily: '"DM Sans Variable", "Noto Sans TC Variable", sans-serif' }, grid: { left: 60, right: 30, top: 55, bottom: 50 },
      legend: { top: 0, data: groups },tooltip: { trigger: "axis", renderMode: "richText", confine: true, formatter: (events: { data: number[] }[]) => { const x = events[0]?.data[0];return data.items.filter(r => r.lowerAge === x).map(r => `${r.surface}: ${r.lowerAge}–<${r.upperAge}\n${percent(r.winRate)} [${percent(r.lower95)}, ${percent(r.upper95)}] · n=${r.matches}`).join("\n"); } },
      xAxis: { type: "value", name: en ? "Age at tournament (years)" : "來源賽事年齡（歲）", nameLocation: "middle", nameGap: 30, scale: true },
      yAxis: { type: "value", min: 0, max: 1, axisLabel: { formatter: (v: number) => `${Math.round(v * 100)}%` }, name: en ? "Observed win rate" : "觀測勝率" },
      series: groups.flatMap((group, i) => { const rows = data.items.filter(r => r.surface === group);const byAge = new Map(rows.map(r => [r.lowerAge, r]));
        const ages = Array.from({ length: Math.round((end - start) / data.binWidth) + 1 }, (_, j) => start + j * data.binWidth);return [
        { type: "line", name: `${group}-lower`, stack: group, symbol: "none", silent: true, lineStyle: { opacity: 0 }, areaStyle: { opacity: 0 }, data: ages.map(age => [age, byAge.get(age)?.lower95 ?? null]), tooltip: { show: false } },
        { type: "line", name: `${group}-interval`, stack: group, symbol: "none", silent: true, lineStyle: { opacity: 0 }, areaStyle: { color: palette[i % palette.length], opacity: .12 }, data: ages.map(age => { const r = byAge.get(age);return [age, r ? r.upper95 - r.lower95 : null]; }), tooltip: { show: false } },
        { type: "line", name: group, symbolSize: 7, connectNulls: false, itemStyle: { color: palette[i % palette.length] }, data: ages.map(age => [age, byAge.get(age)?.winRate ?? null]) },
      ]; }),
    };
  }, [data, en, label]);
  return <Canvas option={option} label={label}/>;
}
