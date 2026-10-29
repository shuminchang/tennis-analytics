import type { ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { get } from "./api";
export default function Resource<T>({ path, children }: { path: string; children: (data: T) => ReactNode }) {
  const { i18n } = useTranslation();const en = i18n.language === "en";
  const query = useQuery({ queryKey: ["resource", path], queryFn: ({ signal }) => get<T>(path, signal), retry: false });
  if (query.isPending) return <p role="status">{en ? "Loading…" : "載入中…"}</p>;
  if (query.isError) return <div role="alert"><p>{en ? "Unavailable or access denied. Please sign in if required." : "暫時無法取得資料或沒有權限；受保護功能請先登入。"}</p><button className="secondary" onClick={() => void query.refetch()}>{en ? "Retry" : "重試"}</button></div>;
  return <>{children(query.data)}</>;
}
