import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { mutate } from "./api";
import Resource from "./Resource";
import { useMe } from "./useMe";
export default function Account() {
  const { i18n } = useTranslation();const en = i18n.language === "en";const me = useMe();const client = useQueryClient();const [message, setMessage] = useState("");const [busy, setBusy] = useState(false);
  return <section className="feature-page"><h1>{en ? "Account" : "登入與帳號"}</h1><p>{en ? "Accounts are provisioned by an administrator. There is no public sign-up." : "帳號由管理員開通，不提供自由註冊。"}</p>
    {me.isPending ? <p>{en ? "Loading…" : "載入中…"}</p> : me.data?.authenticated ? <><p>{me.data.username} · {me.data.roles?.join(", ")}</p><button disabled={busy} onClick={async () => { setBusy(true);try { await mutate("/auth/logout", "POST");client.clear();window.location.reload();setMessage(""); } catch { setMessage(en ? "Logout failed." : "登出失敗。"); } finally { setBusy(false); } }}>{en ? "Sign out" : "登出"}</button></> : <form className="account-form" onSubmit={async e => {
      e.preventDefault();const form = e.currentTarget;const f = new FormData(form);setBusy(true);setMessage("");
      try { await mutate("/auth/login", "POST", { username: String(f.get("username")), password: String(f.get("password")) }, true);form.reset();client.clear();window.location.reload(); }
      catch { setMessage(en ? "Sign-in failed. Check credentials or retry later." : "登入失敗，請確認帳密或稍後再試。"); }
      finally { setBusy(false); }
    }}><label>{en ? "Username" : "帳號"}<input name="username" autoComplete="username" required maxLength={64}/></label><label>{en ? "Password" : "密碼"}<input name="password" type="password" autoComplete="current-password" required maxLength={72}/></label><button disabled={busy}>{en ? "Sign in" : "登入"}</button></form>}
    {message && <p role="alert">{message}</p>}
    {me.data?.roles?.includes("ADMIN") && <AdminAccounts />}
  </section>;
}
function AdminAccounts() {
  const { i18n } = useTranslation();const en = i18n.language === "en";const client = useQueryClient();const [message, setMessage] = useState("");
  return <section><h2>{en ? "Manage accounts" : "帳號管理"}</h2><form className="account-form" onSubmit={async e => {
    e.preventDefault();const form = e.currentTarget;const f = new FormData(form);
    try { await mutate("/v1/admin/users", "POST", { username: f.get("username"), password: f.get("password"), role: f.get("role") });form.reset();await client.invalidateQueries({ queryKey: ["resource", "/v1/admin/users"] });setMessage(en ? "Created." : "已建立。"); } catch { setMessage(en ? "Unable to create account." : "建立失敗，請檢查帳號及密碼規則。"); }
  }}><label>{en ? "New username" : "新帳號"}<input name="username" pattern="[a-z0-9_.-]{3,64}" required/></label><label>{en ? "New password (12+ characters)" : "新密碼（至少 12 字元）"}<input type="password" name="password" autoComplete="new-password" minLength={12} maxLength={72} required/></label><label>{en ? "Role" : "角色"}<select name="role"><option>MEMBER</option><option>ADVANCED</option><option>ADMIN</option></select></label><button>{en ? "Create" : "建立"}</button></form><p role="status">{message}</p>
    <Resource<{ id: number; username: string; role: string; enabled: boolean }[]> path="/v1/admin/users">{rows => <ul>{rows.map(u => <li key={u.id}>{u.username} · {u.role} · {u.enabled ? (en ? "Enabled" : "啟用") : (en ? "Disabled" : "停用")} <button onClick={async () => { try { await mutate(`/v1/admin/users/${u.id}`, "PATCH", { role: u.role, enabled: !u.enabled });await client.invalidateQueries({ queryKey: ["resource", "/v1/admin/users"] }); } catch { setMessage(en ? "Update rejected." : "更新被拒絕。"); } }}>{en ? "Toggle access" : "切換啟用"}</button></li>)}</ul>}</Resource>
  </section>;
}
