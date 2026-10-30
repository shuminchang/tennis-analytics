import { useEffect, useRef, useState } from "react";
import { init, use as registerCharts, type EChartsCoreOption, type EChartsType, type ECElementEvent } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import type { Point } from "./model";
registerCharts([CanvasRenderer]);
export default function ChartCanvas({ option, label, onHover, onOpen, exportName, exportLabel }: { option: EChartsCoreOption; label: string; onHover?: (p: Point) => void; onOpen?: (p: Point) => void; exportName?: string; exportLabel?: string }) {
  const [ready, setReady] = useState(false);
  const element = useRef<HTMLDivElement>(null);const chart = useRef<EChartsType | null>(null);
  useEffect(() => {
    const instance = init(element.current!, null, { renderer: "canvas" });chart.current = instance;
    instance.on("finished", () => { element.current?.setAttribute("aria-busy", "false");setReady(true); });
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
  return <div><div ref={element} className="explorer-canvas" aria-busy="true" role="img" aria-label={label} />{exportName && <button className="explorer-quiet" disabled={!ready} onClick={() => { const image=chart.current?.getDataURL({type:"png",pixelRatio:2,backgroundColor:"#f6f4ec"});if(image) { const a=document.createElement("a");a.href=image;a.download=exportName;a.click(); } }}>{exportLabel ?? "PNG"}</button>}</div>;
}
