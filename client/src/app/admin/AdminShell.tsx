"use client";
import Link from "next/link";
import AppShell from "@/components/AppShell";
import Card from "@/components/Card";
import RequireAuth from "@/components/RequireAuth";
import { useAuth } from "@/lib/auth-context";

function AdminGate({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) {
    return <div className="text-sm text-zinc-500">Checking access…</div>;
  }
  if (!user || user.role !== "ADMIN") {
    return (
      <Card>
        <div className="text-sm font-medium text-zinc-900">Admin access required</div>
        <p className="mt-1 text-sm text-zinc-500">
          This area is limited to administrators. Admin status is granted directly in the
          database — there is no public registration for it.
        </p>
        <Link
          href="/dashboard"
          className="mt-3 inline-block rounded-full border border-zinc-200 px-4 py-2 text-xs hover:bg-zinc-50"
        >
          Back to dashboard
        </Link>
      </Card>
    );
  }
  return <>{children}</>;
}

export function AdminShell({ title, subtitle, children }: { title: string; subtitle: string; children: React.ReactNode }) {
  return (
    <RequireAuth>
      <AppShell>
        <div className="mb-4">
          <div className="text-xs font-semibold uppercase tracking-wide text-zinc-400">Administration</div>
          <h1 className="text-xl font-semibold text-zinc-900">{title}</h1>
          <p className="text-sm text-zinc-500">{subtitle}</p>
        </div>
        <div className="mb-4 flex gap-2 text-xs">
          <Link href="/admin" className="rounded-full border border-zinc-200 px-3 py-1 hover:bg-zinc-50">
            Overview
          </Link>
          <Link href="/admin/users" className="rounded-full border border-zinc-200 px-3 py-1 hover:bg-zinc-50">
            Users
          </Link>
        </div>
        <AdminGate>{children}</AdminGate>
      </AppShell>
    </RequireAuth>
  );
}
