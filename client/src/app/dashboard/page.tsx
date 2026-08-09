"use client";
import AppShell from "@/components/AppShell";
import Card from "@/components/Card";
import RequireAuth from "@/components/RequireAuth";
import { useAuth } from "@/lib/auth-context";

export default function DashboardPage() {
  const { user } = useAuth();
  return (
    <RequireAuth>
      <AppShell>
        <div className="mb-6">
          <h1 className="text-xl font-semibold text-zinc-900">Welcome, {user?.username ?? "labeler"}</h1>
          <p className="text-sm text-zinc-500">Signed in as {user?.email} ({user?.role}).</p>
        </div>
        <Card>
          <div className="text-sm font-medium text-zinc-900">Nothing here yet</div>
          <p className="mt-1 text-sm text-zinc-500">Your datasets and progress will appear here once you create your first dataset.</p>
        </Card>
      </AppShell>
    </RequireAuth>
  );
}
