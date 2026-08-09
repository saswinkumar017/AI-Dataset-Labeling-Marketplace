"use client";
import AppShell from "@/components/AppShell";
import Card from "@/components/Card";
import RequireAuth from "@/components/RequireAuth";

export default function DatasetsPage() {
  return (
    <RequireAuth>
      <AppShell>
        <div className="mb-6">
          <h1 className="text-xl font-semibold text-zinc-900">Datasets</h1>
          <p className="text-sm text-zinc-500">No datasets yet.</p>
        </div>
        <Card>
          <div className="text-sm font-medium text-zinc-900">Nothing here yet</div>
          <p className="mt-1 text-sm text-zinc-500">Upload your first dataset to get started.</p>
        </Card>
      </AppShell>
    </RequireAuth>
  );
}
