"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import AppShell from "@/components/AppShell";
import Card from "@/components/Card";
import RequireAuth from "@/components/RequireAuth";
import { useAuth } from "@/lib/auth-context";
import { dashboardSummary, listAssignedTasks, listProjects, type DashboardSummary, type ProjectResponse, type TaskResponse } from "@/lib/api";

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
      <div className="text-2xl font-semibold text-zinc-900">{value}</div>
      <div className="mt-1 text-xs text-zinc-500">{label}</div>
    </div>
  );
}

export default function DashboardPage() {
  const { user } = useAuth();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const [assigned, setAssigned] = useState<TaskResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // State updates live in promise callbacks (not in the effect body) so the
    // initial fetch complies with react-hooks/set-state-in-effect.
    let cancelled = false;
    Promise.all([
      dashboardSummary(),
      listProjects().catch(() => [] as ProjectResponse[]),
      listAssignedTasks().catch(() => [] as TaskResponse[]),
    ])
      .then(([data, owned, mine]) => {
        if (cancelled) return;
        setSummary(data);
        setProjects(owned.slice(0, 5));
        setAssigned(mine);
        setLoading(false);
      })
      .catch(() => {
        if (cancelled) return;
        setError("Could not load your dashboard. Is the backend running?");
        setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <RequireAuth>
      <AppShell>
        <div className="mb-6">
          <h1 className="text-xl font-semibold text-zinc-900">Welcome, {user?.username ?? "labeler"}</h1>
          <p className="text-sm text-zinc-500">Signed in as {user?.email} ({user?.role}).</p>
        </div>

        {loading ? (
          <div className="text-sm text-zinc-500">Loading your workflow…</div>
        ) : error ? (
          <Card>
            <div className="text-sm font-medium text-zinc-900">Dashboard unavailable</div>
            <p className="mt-1 text-sm text-zinc-500">{error}</p>
          </Card>
        ) : summary ? (
          <>
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              <Stat label="Datasets" value={summary.datasetCount} />
              <Stat label="Projects" value={summary.projectCount} />
              <Stat label="Items to label" value={summary.tasksPending} />
              <Stat label="Waiting for review" value={summary.pendingReviews} />
              <Stat label="Submitted annotations" value={summary.annotationCount} />
              <Stat label="Approved final labels" value={summary.reviewsApproved} />
              <Stat label="Rejected, needs rework" value={summary.tasksRejected} />
              <Stat label="Finished items" value={summary.tasksApproved} />
              <Stat label="Assigned to me" value={summary.assignedToMe} />
              <Stat label="My action needed" value={summary.assignedNeedsAction} />
            </div>

            {assigned.length > 0 && (
              <Card className="mt-6">
                <div className="text-sm font-semibold text-zinc-900">My assigned tasks</div>
                <p className="mt-1 text-xs text-zinc-500">
                  Work assigned to you by project owners — open a task to label it.
                </p>
                <ul className="mt-3 space-y-2">
                  {assigned.slice(0, 8).map((t) => (
                    <li key={t.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-zinc-100 px-3 py-2 text-sm">
                      <span className="font-medium text-zinc-900">
                        {t.projectName ?? `Project #${t.projectId}`} · Task #{t.id}
                        <span className="ml-2 text-xs font-normal text-zinc-500">{t.status}</span>
                      </span>
                      <Link href={`/annotate?taskId=${t.id}`} className="rounded-full bg-zinc-900 px-3 py-1 text-xs font-medium text-white hover:bg-zinc-800">
                        Open task
                      </Link>
                    </li>
                  ))}
                </ul>
                {assigned.length > 8 && (
                  <div className="mt-2 text-xs text-zinc-500">+ {assigned.length - 8} more — open the workspace to see all.</div>
                )}
                <div className="mt-3 text-xs">
                  <Link href="/annotate?assigned=1" className="text-zinc-600 underline hover:text-zinc-900">Open all assigned in workspace</Link>
                </div>
              </Card>
            )}

            {summary.projectCount === 0 ? (
              <Card className="mt-6">
                <div className="text-sm font-medium text-zinc-900">Start your first labeling run</div>
                <p className="mt-1 text-sm text-zinc-500">
                  Create a dataset, open a project over it, then annotate and review.
                </p>
                <div className="mt-3 flex gap-2">
                  <Link href="/datasets" className="rounded-full bg-zinc-900 px-4 py-2 text-xs font-medium text-white hover:bg-zinc-800">
                    Create dataset
                  </Link>
                  <Link href="/projects" className="rounded-full border border-zinc-200 px-4 py-2 text-xs hover:bg-zinc-50">
                    View projects
                  </Link>
                </div>
              </Card>
            ) : (
              <Card className="mt-6">
                <div className="text-sm font-semibold text-zinc-900">Continue where you left off</div>
                <ul className="mt-3 space-y-2">
                  {projects.map((p) => (
                    <li key={p.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-zinc-100 px-3 py-2 text-sm">
                      <span className="font-medium text-zinc-900">{p.name}</span>
                      <span className="flex gap-2">
                        <Link href={`/annotate?projectId=${p.id}`} className="rounded-full bg-zinc-900 px-3 py-1 text-xs font-medium text-white hover:bg-zinc-800">
                          Annotate
                        </Link>
                        <Link href={`/review?projectId=${p.id}`} className="rounded-full border border-zinc-200 px-3 py-1 text-xs hover:bg-zinc-50">
                          Review
                        </Link>
                      </span>
                    </li>
                  ))}
                </ul>
                <div className="mt-3 flex gap-2 text-xs">
                  <Link href="/annotate" className="text-zinc-600 underline hover:text-zinc-900">Annotation workspace</Link>
                  <Link href="/review" className="text-zinc-600 underline hover:text-zinc-900">Review queue</Link>
                </div>
              </Card>
            )}
          </>
        ) : null}
      </AppShell>
    </RequireAuth>
  );
}
