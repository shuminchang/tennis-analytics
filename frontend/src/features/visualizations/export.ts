export function csvCell(value: string | number | null | undefined): string {
  if (value == null) return '""';let text=String(value);
  if (typeof value === "string" && (/^[=+@\t\r\n-]/.test(text) || /^\s+[=+@-]/.test(text))) text="'"+text;
  return `"${text.replaceAll('"','""')}"`;
}
export function csvContent(rows: (string | number | null | undefined)[][]): string { return "\uFEFF"+rows.map(row=>row.map(csvCell).join(",")).join("\r\n"); }
export function downloadCsv(name: string, rows: (string | number | null | undefined)[][]) {
  const url=URL.createObjectURL(new Blob([csvContent(rows)],{type:"text/csv;charset=utf-8"}));const a=document.createElement("a");a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
