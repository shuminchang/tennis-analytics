import { useQuery } from "@tanstack/react-query";
import { get } from "./api";
export interface Me { authenticated: boolean; username?: string; roles?: string[] }
export function useMe() { return useQuery({ queryKey: ["me"], queryFn: () => get<Me>("/auth/me"), retry: false }); }
