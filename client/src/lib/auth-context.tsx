"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import {
  loginRequest,
  meRequest,
  registerRequest,
  requestRegistrationOtp,
  verifyRegistrationOtp,
  type BackendUser,
} from "./api";

type AuthState = {
  user: BackendUser | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (username: string, email: string, password: string) => Promise<void>;
  requestOtp: (username: string, email: string, password: string) => Promise<number>;
  verifyOtp: (email: string, code: string) => Promise<void>;
  logout: () => void;
};

const AuthContext = createContext<AuthState | null>(null);

function readStoredUser(): BackendUser | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = localStorage.getItem("labelmate_user");
    return raw ? (JSON.parse(raw) as BackendUser) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<BackendUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // State updates live in promise callbacks (not in the effect body) so the
    // session restore complies with react-hooks/set-state-in-effect.
    let cancelled = false;
    const token = localStorage.getItem("labelmate_token");
    const cached = readStoredUser();
    if (!token) {
      Promise.resolve().then(() => {
        if (!cancelled) setLoading(false);
      });
      return () => {
        cancelled = true;
      };
    }
    if (cached) {
      Promise.resolve(cached).then((stored) => {
        if (!cancelled) setUser(stored);
      });
    }
    meRequest()
      .then((me) => {
        if (cancelled) return;
        setUser(me);
        localStorage.setItem("labelmate_user", JSON.stringify(me));
      })
      .catch(() => {
        if (cancelled) return;
        localStorage.removeItem("labelmate_token");
        localStorage.removeItem("labelmate_user");
        setUser(null);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const res = await loginRequest(email, password);
    localStorage.setItem("labelmate_token", res.token);
    localStorage.setItem("labelmate_user", JSON.stringify(res.user));
    setUser(res.user);
  }, []);

  const register = useCallback(async (username: string, email: string, password: string) => {
    await registerRequest(username, email, password);
  }, []);

  const requestOtp = useCallback(async (username: string, email: string, password: string) => {
    const res = await requestRegistrationOtp(username, email, password);
    return res.expiresInSeconds;
  }, []);

  const verifyOtp = useCallback(async (email: string, code: string) => {
    await verifyRegistrationOtp(email, code);
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem("labelmate_token");
    localStorage.removeItem("labelmate_user");
    setUser(null);
    window.location.href = "/";
  }, []);

  const value = useMemo(
    () => ({ user, loading, login, register, requestOtp, verifyOtp, logout }),
    [user, loading, login, register, requestOtp, verifyOtp, logout]
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
