"use client";
import AppShell from "@/components/AppShell";
import Card from "@/components/Card";
import RequireAuth from "@/components/RequireAuth";

export default function ProjectsPage() {
  return (
    <RequireAuth>
      <AppShell>
        <div className="mb-6">
          <h1 className="text-xl font-semibold text-zinc-900">Projects</h1>
          <p className="text-sm text-zinc-500">No projects yet.</p>
        </div>
        <Card>
          <div className="text-sm font-medium text-zinc-900">Nothing here yet</div>
          <p className="mt-1 text-sm text-zinc-500">Create your first project to start labeling.</p>
        </Card>
      </AppShell>
    </RequireAuth>
  );
}
