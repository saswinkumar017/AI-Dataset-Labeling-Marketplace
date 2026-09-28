"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useAuth } from "@/lib/auth-context";

type Role = "ADMIN" | "ANNOTATOR";

export default function RequireAuth({ children, roles }: { children: React.ReactNode; roles?: Role[] }) {
  const { user, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (loading) return;
    if (!user) {
      router.replace("/login");
      return;
    }
    if (roles && !roles.includes(user.role as Role)) {
      router.replace(user.role === "ADMIN" ? "/admin" : "/dashboard");
    }
  }, [loading, user, router, roles]);

  if (loading) {
    return <div className="mx-auto max-w-4xl px-6 py-16 text-sm text-zinc-500">Checking your session…</div>;
  }
  if (!user) return null;
  if (roles && !roles.includes(user.role as Role)) return null;
  return <>{children}</>;
}
