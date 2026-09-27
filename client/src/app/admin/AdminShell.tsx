"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
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
          This area is limited to administrators. Admin status is granted by an
          existing admin — there is no public registration for it.
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

const NAV = [
  { href: "/admin", label: "Overview" },
  { href: "/admin/users", label: "Users" },
  { href: "/admin/settings", label: "AI settings" },
];

export function AdminShell({ title, subtitle, children }: { title: string; subtitle: string; children: React.ReactNode }) {
  const pathname = usePathname();
  return (
    <RequireAuth roles={["ADMIN"]}>
      <div className="min-h-[calc(100vh-56px)] bg-zinc-50 px-6 py-6">
        <div className="mx-auto flex max-w-6xl flex-col gap-4 md:flex-row">
          <aside className="w-full shrink-0 rounded-2xl border border-zinc-200 bg-white p-4 md:w-56">
            <div className="px-2 text-[11px] font-bold uppercase tracking-widest text-zinc-400">Admin console</div>
            <nav className="mt-3 space-y-1">
              {NAV.map((n) => {
                const active = pathname === n.href;
                return (
                  <Link
                    key={n.href}
                    href={n.href}
                    className={`block rounded-lg px-3 py-2 text-sm ${active ? "bg-zinc-900 font-semibold text-white" : "text-zinc-600 hover:bg-zinc-100"}`}
                  >
                    {n.label}
                  </Link>
                );
              })}
            </nav>
            <Link href="/dashboard" className="mt-4 block px-3 text-xs text-zinc-500 hover:text-zinc-900">
              ← Back to workspace
            </Link>
          </aside>
          <div className="min-w-0 flex-1 rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm">
            <div className="mb-4 border-b border-zinc-200 pb-3">
              <h1 className="text-xl font-bold text-zinc-950">{title}</h1>
              <p className="text-sm text-zinc-500">{subtitle}</p>
            </div>
            <AdminGate>{children}</AdminGate>
          </div>
        </div>
      </div>
    </RequireAuth>
  );
}
