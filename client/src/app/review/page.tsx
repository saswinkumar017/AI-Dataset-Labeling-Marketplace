"use client";
import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import AppShell from "@/components/AppShell";
import Badge from "@/components/Badge";
import Card from "@/components/Card";
import RequireAuth from "@/components/RequireAuth";
import {
  friendlyProjectError,
  friendlyReviewError,
  listProjectAnnotations,
  listProjectReviews,
  listProjectTasks,
  listProjects,
  submitReview,
  type AnnotationResponse,
  type ProjectResponse,
  type ReviewDecision,
  type ReviewResponse,
  type TaskResponse,
} from "@/lib/api";

type QueueItem = {
  annotation: AnnotationResponse;
  itemData: string | null;
};

export default function ReviewPage() {
  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const [activeId, setActiveId] = useState<number | null>(null);
  const [pending, setPending] = useState<QueueItem[]>([]);
  const [decided, setDecided] = useState<ReviewResponse[]>([]);
  const [comment, setComment] = useState("");
  const [loadingProjects, setLoadingProjects] = useState(true);
  const [loadingQueue, setLoadingQueue] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const active = projects.find((p) => p.id === activeId) ?? null;

  const loadQueue = useCallback(async (projectId: number) => {
    setLoadingQueue(true);
    setError(null);
    setNotice(null);
    setComment("");
    try {
      const [annotations, reviews, tasks] = await Promise.all([
        listProjectAnnotations(projectId),
        listProjectReviews(projectId),
        listProjectTasks(projectId),
      ]);
      const reviewedIds = new Set(reviews.map((r) => r.annotationId));
      const itemByTask = new Map<number, string | null>(tasks.map((t: TaskResponse) => [t.id, t.itemData]));
      setPending(
        annotations
          .filter((a) => !reviewedIds.has(a.id))
          .map((a) => ({ annotation: a, itemData: itemByTask.get(a.taskId) ?? null }))
      );
      setDecided(reviews);
    } catch (err) {
      setError(friendlyReviewError(err));
      setPending([]);
      setDecided([]);
    } finally {
      setLoadingQueue(false);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    listProjects()
      .then((data) => {
        if (cancelled) return;
        setProjects(data);
        setLoadingProjects(false);
        if (data.length === 0) return;
        const params = new URLSearchParams(window.location.search);
        const wanted = Number(params.get("projectId"));
        const initial =
          Number.isInteger(wanted) && data.some((p) => p.id === wanted) ? wanted : data[0].id;
        setActiveId(initial);
        void loadQueue(initial);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(friendlyProjectError(err));
        setLoadingProjects(false);
      });
    return () => {
      cancelled = true;
    };
  }, [loadQueue]);

  function chooseProject(id: number) {
    if (id === activeId) return;
    setActiveId(id);
    void loadQueue(id);
  }

  async function decide(annotationId: number, decision: ReviewDecision) {
    if (busyId !== null) return;
    setBusyId(annotationId);
    setError(null);
    setNotice(null);
    try {
      const saved = await submitReview(annotationId, {
        decision,
        comment: comment.trim() || null,
      });
      setPending((prev) => prev.filter((q) => q.annotation.id !== annotationId));
      setDecided((prev) => [saved, ...prev]);
      setComment("");
      setNotice(
        decision === "APPROVED"
          ? "Approved — the annotation is now the final label."
          : "Rejected — the item returns to the annotation queue."
      );
    } catch (err) {
      setError(friendlyReviewError(err));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <RequireAuth>
      <AppShell>
        <div className="mb-4">
          <h1 className="text-xl font-semibold text-zinc-900">Review queue</h1>
          <p className="text-sm text-zinc-500">Your judgment decides what becomes final.</p>
          {pending.length + decided.length > 0 && (
            <div className="mt-2 text-xs text-zinc-500">
              {decided.filter((r) => r.decision === "APPROVED").length} approved · {pending.length} waiting
            </div>
          )}
        </div>

        {loadingProjects ? (
          <div className="text-sm text-zinc-500">Loading your projects…</div>
        ) : projects.length === 0 ? (
          <Card>
            <div className="text-sm font-medium text-zinc-900">No projects yet</div>
            <p className="mt-1 text-sm text-zinc-500">Create a project and submit annotations before reviewing.</p>
            <Link href="/projects" className="mt-3 inline-block rounded-full bg-zinc-900 px-4 py-2 text-xs font-medium text-white hover:bg-zinc-800">
              Go to Projects
            </Link>
          </Card>
        ) : (
          <div className="grid gap-4 md:grid-cols-[220px_1fr]">
            <Card className="h-fit">
              <h2 className="text-xs font-semibold uppercase tracking-wide text-zinc-500">Projects</h2>
              <div className="mt-2 space-y-1">
                {projects.map((p) => (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => chooseProject(p.id)}
                    className={`w-full rounded-lg px-3 py-2 text-left text-sm ${
                      p.id === activeId ? "bg-zinc-900 text-white" : "text-zinc-700 hover:bg-zinc-100"
                    }`}
                  >
                    <span className="block truncate font-medium">{p.name}</span>
                    <span className={`block text-xs ${p.id === activeId ? "text-zinc-300" : "text-zinc-400"}`}>#{p.id}</span>
                  </button>
                ))}
              </div>
              {active?.instructions && (
                <div className="mt-4 border-t border-zinc-100 pt-3 text-xs text-zinc-500">
                  Instructions: {active.instructions}
                </div>
              )}
            </Card>

            <div>
              {error && (
                <div role="alert" className="mb-3 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700 ring-1 ring-amber-200">
                  {error}
                </div>
              )}
              {notice && (
                <div role="status" className="mb-3 rounded-lg bg-emerald-50 px-3 py-2 text-xs text-emerald-700 ring-1 ring-emerald-200">
                  {notice}
                </div>
              )}

              {loadingQueue ? (
                <div className="text-sm text-zinc-500">Loading the review queue…</div>
              ) : pending.length === 0 ? (
                <Card>
                  <div className="text-sm font-medium text-zinc-900">Nothing waiting</div>
                  <p className="mt-1 text-sm text-zinc-500">
                    {decided.length === 0
                      ? "No annotations have been submitted for this project yet. Annotate an item first."
                      : "Every submitted annotation in this project has a decision."}
                  </p>
                  <Link href="/annotate" className="mt-3 inline-block rounded-full border border-zinc-200 px-4 py-2 text-xs hover:bg-zinc-50">
                    Open annotation workspace
                  </Link>
                </Card>
              ) : (
                <div className="space-y-4">
                  <Card>
                    <label className="text-xs font-medium text-zinc-700" htmlFor="review-comment">
                      Feedback for the annotator (optional, shared with every decision below)
                    </label>
                    <textarea
                      id="review-comment"
                      value={comment}
                      onChange={(e) => setComment(e.target.value)}
                      rows={2}
                      maxLength={2000}
                      placeholder="e.g. Check sarcasm — this reads negative, not positive."
                      className="mt-1 w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-900"
                    />
                  </Card>
                  {pending.map(({ annotation: a, itemData }) => (
                    <Card key={a.id}>
                      <div className="text-sm leading-6 text-zinc-900">
                        “{itemData || "(empty item — no text was stored for this task)"}”
                      </div>
                      <div className="mt-2 flex flex-wrap gap-2 text-xs">
                        <span className="rounded-full bg-zinc-900 px-2.5 py-1 text-white">
                          Submitted: {a.label}
                        </span>
                        <span className="rounded-full bg-zinc-100 px-2.5 py-1 text-zinc-600 ring-1 ring-zinc-200">
                          Task #{a.taskId} · {a.source}
                        </span>
                      </div>
                      <div className="mt-3 flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => decide(a.id, "APPROVED")}
                          disabled={busyId === a.id}
                          className={`rounded-full px-4 py-1.5 text-xs font-medium text-white ${busyId === a.id ? "bg-zinc-400" : "bg-emerald-600 hover:bg-emerald-700"}`}
                        >
                          {busyId === a.id ? "Saving…" : "Approve"}
                        </button>
                        <button
                          type="button"
                          onClick={() => decide(a.id, "REJECTED")}
                          disabled={busyId === a.id}
                          className="rounded-full border border-zinc-200 px-4 py-1.5 text-xs hover:bg-zinc-50 disabled:opacity-40"
                        >
                          Reject
                        </button>
                      </div>
                    </Card>
                  ))}
                </div>
              )}

              {decided.length > 0 && (
                <div className="mt-6">
                  <h2 className="mb-2 text-xs font-semibold uppercase tracking-wide text-zinc-500">Decided</h2>
                  <div className="space-y-2">
                    {decided.map((r) => (
                      <div key={r.id} className="flex items-center justify-between gap-2 rounded-lg border border-zinc-200 bg-white px-3 py-2 text-xs">
                        <span className="text-zinc-600">
                          Annotation #{r.annotationId} · by {r.reviewer}
                          {r.comment && <span className="text-zinc-400"> — “{r.comment}”</span>}
                        </span>
                        <Badge tone={r.decision === "APPROVED" ? "emerald" : "amber"}>{r.decision}</Badge>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
      </AppShell>
    </RequireAuth>
  );
}
