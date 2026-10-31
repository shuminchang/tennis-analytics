import { Children, cloneElement, isValidElement, useState, type ReactElement, type ReactNode } from "react";
import { useTranslation } from "react-i18next";

type Element = ReactElement<{ children?: ReactNode; "data-sort-value"?: string | number | null }>;
function content(node: ReactNode): string {
  return Children.toArray(node).map(child => isValidElement(child) ? content((child as Element).props.children) : String(child ?? "")).join("");
}
function value(cell: ReactNode): string | number | null {
  if (!isValidElement(cell)) return null;
  const props = (cell as Element).props;
  if ("data-sort-value" in props) return props["data-sort-value"] === "" ? null : props["data-sort-value"] ?? null;
  const text = content(props.children).trim();
  if (["", "—", "未提供", "Not provided", "Name not provided", "Name unavailable", "姓名未提供"].includes(text)) return null;
  if (/^\d{4}[-/]\d{1,2}[-/]\d{1,2}$/.test(text)) return Date.parse(text);
  if (/^-?\d{1,3}(,\d{3})+(\.\d+)?%?$/.test(text)) return Number(text.replaceAll(",", "").replace("%", ""));
  const number = text.match(/^\[?(-?\d+(?:\.\d+)?)(?:%|\s*\/|\s*[,·（(]|$)/);
  return number ? Number(number[1]) : text;
}
// Sort React rows, retaining links/markup. Remote pages explicitly sort only their loaded page;
// bounded client cohorts pass all rows, so sorting precedes the optional client pagination.
export default function SortableTable({ children, scope = "all", page, pageSize, onSort }: { children: ReactNode; scope?: "all" | "page"; page?: number; pageSize?: number; onSort?: () => void }) {
  const { i18n } = useTranslation();const en = i18n.language === "en";
  const [sort, setSort] = useState<{ column: number; descending: boolean } | null>(null);
  const nodes = Children.toArray(children);
  const label = en ? (scope === "page" ? "Sort this page" : "Sort loaded observations") : (scope === "page" ? "本頁排序" : "已載入觀測排序");
  return <><p className="table-sort-note">{en ? (scope === "page" ? "Click headings to sort this page; other pages keep source order." : "Click headings to sort loaded observations; missing values stay last.") : (scope === "page" ? "點欄名排序本頁；其他頁仍維持來源順序。" : "點欄名排序已載入觀測；缺值固定置後。")}</p><table>{nodes.map(node => {
    if (!isValidElement(node)) return node;
    const element = node as Element;
    if (element.type === "thead") return cloneElement(element, {}, Children.map(element.props.children, row => {
      if (!isValidElement(row)) return row;
      return cloneElement(row as Element, {}, Children.map((row as Element).props.children, (cell, column) => {
        if (!isValidElement(cell)) return cell;
        const header = cell as Element;const active = sort?.column === column;
        return cloneElement(header, { "aria-sort": active ? (sort.descending ? "descending" : "ascending") : "none" } as object,
          <button type="button" className="table-sort" title={label} onClick={() => { setSort({ column, descending: active ? !sort.descending : false });onSort?.(); }}>{header.props.children}<span aria-hidden="true">{active ? (sort.descending ? " ↓" : " ↑") : " ↕"}</span><span className="sr-only"> · {label}</span></button>);
      }));
    }));
    if (element.type !== "tbody") return node;
    let rows = Children.toArray(element.props.children);
    if (sort) rows = rows.map((row,index) => ({row,index})).sort((a,b) => {
      const av = value(isValidElement(a.row) ? Children.toArray((a.row as Element).props.children)[sort.column] : null);
      const bv = value(isValidElement(b.row) ? Children.toArray((b.row as Element).props.children)[sort.column] : null);
      if (av === null || bv === null) return av === bv ? a.index-b.index : av === null ? 1 : -1;
      const order = typeof av === "number" && typeof bv === "number" ? av-bv : String(av).localeCompare(String(bv), i18n.language, { numeric:true });
      return (sort.descending ? -order : order) || a.index-b.index;
    }).map(item => item.row);
    if (pageSize) rows = rows.slice((page ?? 0)*pageSize,((page ?? 0)+1)*pageSize);
    return cloneElement(element, {}, rows);
  })}</table></>;
}
