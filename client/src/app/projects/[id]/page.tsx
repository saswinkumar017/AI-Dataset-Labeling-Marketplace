"use client";
import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import AppShell from "@/components/AppShell";
import Badge from "@/components/Badge";
import Card from "@/components/Card";
import RequireAuth from "@/components/RequireAuth";
import {
  apiBaseUrl,
  autoLabelTask,
  createAnnotation,
  exportProject,
  friendlyAiError,
  friendlyAnnotationError,
  friendlyExportError,
  friendlyProjectError,
  friendlyReviewError,
  getProject,
  listAssignedTasks,
  listProjectAnnotations,
  listProjectReviews,
  listProjectTasks,
  submitReview,
  type AnnotationResponse,
  type ProjectResponse,
  type ReviewDecision,
  type ReviewResponse,
  type TaskResponse,
  type TaskStatus,
} from "@/lib/api";

type StatusFilter = "ALL" | TaskStatus;

const FILTERS: StatusFilter[] = [
  "ALL",
  "PENDING",
  "ASSIGNED",
  "IN_PROGRESS",
  "SUBMITTED",
  "APPROVED",
  "REJECTED",
];

function taskTone(status: TaskStatus): "zinc" | "emerald" | "amber" | "blue" {
  if (status === "APPROVED") return "emerald";
  if (status === "SUBMITTED") return "blue";
  if (status === "REJECTED") return "amber";
  return "zinc";
}

export default function ProjectDetailPage() {
  const params = useParams();
  const projectId = Number(params.id);
  const [project, setProject] = useState<ProjectResponse | null>(null);
  const [tasks, setTasks] = useState<TaskResponse[]>([]);
  const [annotations, setAnnotations] = useState<AnnotationResponse[]>([]);
  const [reviews, setReviews] = useState<ReviewResponse[]>([]);
  const [filter, setFilter] = useState<StatusFilter>("ALL");
  const [labelInputs, setLabelInputs] = useState<Record<number, string>>({});
  const [comment, setComment] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [bulkAi, setBulkAi] = useState(false);
  const [bulkProgress, setBulkProgress] = useState<string | null>(null);
  const [exporting, setExporting] = useState<"json" | "csv" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const validId = Number.isInteger(projectId) && projectId > 0;

  const loadAll = useCallback(async (id: number) => {
    setLoading(true);
    setError(null);
    try {
      const loaded = await getProject(id);
      setProject(loaded);
      // Owners list the whole queue; assignees fall back to their own tasks
      // in this project so the hub stays usable for both roles.
      let queue: TaskResponse[];
      try {
        queue = await listProjectTasks(id);
      } catch {
        queue = (await listAssignedTasks().catch(() => [] as TaskResponse[])).filter(
          (t) => t.projectId === id
        );
      }
      setTasks(queue);
      const [allAnnotations, allReviews] = await Promise.all([
        listProjectAnnotations(id).catch(() => [] as AnnotationResponse[]),
        listProjectReviews(id).catch(() => [] as ReviewResponse[]),
      ]);
      setAnnotations(allAnnotations);
      setReviews(allReviews);
    } catch (err) {
      setError(friendlyProjectError(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // The initial fetch is deferred past the effect body so the first
    // setLoading complies with react-hooks/set-state-in-effect.
    if (!validId) return;
    let cancelled = false;
    void Promise.resolve().then(() => {
      if (!cancelled) void loadAll(projectId);
    });
    return () => {
      cancelled = true;
    };
  }, [projectId, validId, loadAll]);

  async function refresh() {
    if (!validId) return;
    try {
      const [loaded, queue, allAnnotations, allReviews] = await Promise.all([
        getProject(projectId),
        listProjectTasks(projectId).catch(async () =>
          (await listAssignedTasks().catch(() => [] as TaskResponse[])).filter(
            (t) => t.projectId === projectId
          )
        ),
        listProjectAnnotations(projectId).catch(() => [] as AnnotationResponse[]),
        listProjectReviews(projectId).catch(() => [] as ReviewResponse[]),
      ]);
      setProject(loaded);
      setTasks(queue);
      setAnnotations(allAnnotations);
      setReviews(allReviews);
    } catch (err) {
      setError(friendlyProjectError(err));
    }
  }

  const reviewedIds = new Set(reviews.map((r) => r.annotationId));
  const visibleTasks = filter === "ALL" ? tasks : tasks.filter((t) => t.status === filter);
  const verified = annotations.filter((a) => a.source === "HUMAN_APPROVED");
  const approvedRate =
    project && project.totalTasks > 0 ? Math.round((project.approvedTasks / project.totalTasks) * 100) : 0;

  function annotationsFor(taskId: number) {
    return annotations.filter((a) => a.taskId === taskId);
  }

  function isBusy(key: string) {
    return busy === key;
  }

  async function onAnnotate(taskId: number) {
    const value = (labelInputs[taskId] ?? "").trim();
    if (value.length < 1 || value.length > 100 || busy !== null) return;
    setBusy(`annotate-${taskId}`);
    setError(null);
    setNotice(null);
    try {
      await createAnnotation({ taskId, label: value });
      setLabelInputs((prev) => ({ ...prev, [taskId]: "" }));
      setNotice(`Saved “${value}” on task #${taskId} — now waiting for review.`);
      await refresh();
    } catch (err) {
      setError(friendlyAnnotationError(err));
    } finally {
      setBusy(null);
    }
  }

  async function onAutoLabel(taskId: number) {
    if (busy !== null) return;
    setBusy(`ai-${taskId}`);
    setError(null);
    setNotice(null);
    try {
      const saved = await autoLabelTask(taskId);
      setNotice(`AI labeled “${saved.label}” on task #${taskId} — still needs human review.`);
      await refresh();
    } catch (err) {
      setError(friendlyAiError(err));
    } finally {
      setBusy(null);
    }
  }

  async function onBulkAiLabel() {
    if (bulkAi) return;
    const candidates = tasks.filter(
      (t) =>
        t.status === "PENDING" ||
        t.status === "ASSIGNED" ||
        t.status === "IN_PROGRESS" ||
        t.status === "REJECTED"
    );
    if (candidates.length === 0) {
      setNotice("Nothing to label — every task is already submitted or approved.");
      return;
    }
    setBulkAi(true);
    setError(null);
    setNotice(null);
    let labeled = 0;
    let failed = 0;
    for (const task of candidates) {
      setBulkProgress(`AI labeling ${labeled + failed + 1} of ${candidates.length}…`);
      try {
        await autoLabelTask(task.id);
        labeled++;
      } catch {
        // One task failing (no text, AI down) never aborts the rest.
        failed++;
      }
    }
    setBulkAi(false);
    setBulkProgress(null);
    setNotice(
      failed === 0
        ? `${labeled} task${labeled === 1 ? "" : "s"} AI-labeled — each is now waiting for human review.`
        : `${labeled} labeled, ${failed} skipped (AI unavailable or no text).`
    );
    await refresh();
  }

  async function onDecide(annotationId: number, decision: ReviewDecision) {
    if (busy !== null) return;
    setBusy(`review-${annotationId}`);
    setError(null);
    setNotice(null);
    try {
      await submitReview(annotationId, { decision, comment: comment.trim() || null });
      setComment("");
      setNotice(
        decision === "APPROVED"
          ? "Approved — the annotation is now the final label."
          : "Rejected — the item returns to the annotation queue."
      );
      await refresh();
    } catch (err) {
      setError(friendlyReviewError(err));
    } finally {
      setBusy(null);
    }
  }

  async function onExport(format: "json" | "csv") {
    if (exporting !== null || !validId) return;
    setExporting(format);
    setError(null);
    try {
      const blob = await exportProject(projectId, format);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `project-${projectId}-export.${format}`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      setError(friendlyExportError(err));
    } finally {
      setExporting(null);
    }
  }

  return (
    <RequireAuth>
      <AppShell>
        {!validId ? (
          <Card>
            <div className="text-sm font-medium text-zinc-900">Invalid project</div>
            <p className="mt-1 text-sm text-zinc-500">This project link is malformed.</p>
            <Link href="/projects" className="mt-3 inline-block rounded-full bg-zinc-900 px-4 py-2 text-xs font-medium text-white hover:bg-zinc-800">
              Back to Projects
            </Link>
          </Card>
        ) : loading ? (
          <div className="text-sm text-zinc-500">Loading project…</div>
        ) : project ? (
          <>
            <div className="mb-4">
              <div className="text-xs text-zinc-500">
                <Link href="/projects" className="underline hover:text-zinc-900">Projects</Link>
                {" / "}#{project.id}
              </div>
              <h1 className="mt-1 text-xl font-semibold text-zinc-900">{project.name}</h1>
              {project.instructions && <p className="mt-1 text-sm text-zinc-500">{project.instructions}</p>}
              {project.labels.length > 0 && (
                <div className="mt-2 flex flex-wrap gap-1.5">
                  {project.labels.map((label) => (
                    <span key={label} className="rounded-full bg-blue-50 px-2.5 py-1 text-xs text-blue-700 ring-1 ring-blue-200">
                      {label}
                    </span>
                  ))}
                </div>
              )}
            </div>

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

            <Card>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="text-sm font-semibold text-zinc-900">
                  Progress · {project.approvedTasks}/{project.totalTasks} approved ({approvedRate}%)
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => onExport("json")}
                    disabled={exporting !== null}
                    className="rounded-full border border-zinc-200 px-3 py-1 text-xs hover:bg-zinc-50 disabled:opacity-40"
                  >
                    {exporting === "json" ? "Exporting…" : "Export JSON"}
                  </button>
                  <button
                    onClick={() => onExport("csv")}
                    disabled={exporting !== null}
                    className="rounded-full border border-zinc-200 px-3 py-1 text-xs hover:bg-zinc-50 disabled:opacity-40"
                  >
                    {exporting === "csv" ? "Exporting…" : "Export CSV"}
                  </button>
                </div>
              </div>
              <div className="mt-2 h-2 overflow-hidden rounded-full bg-zinc-100">
                <div className="h-full rounded-full bg-emerald-500" style={{ width: `${approvedRate}%` }} />
              </div>
              <div className="mt-2 text-xs text-zinc-500">
                {project.pendingTasks} pending · {project.submittedTasks} submitted · {project.rejectedTasks} needs rework
              </div>
            </Card>

            <Card className="mt-4">
              <label className="text-xs font-medium text-zinc-700" htmlFor="detail-review-comment">
                Feedback for the annotator (optional, attached to your next approve/reject)
              </label>
              <textarea
                id="detail-review-comment"
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                rows={2}
                maxLength={2000}
                placeholder="e.g. Check sarcasm — this reads negative, not positive."
                className="mt-1 w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-900"
              />
            </Card>

            <div className="mt-4 flex flex-wrap items-center gap-2">
              {FILTERS.map((f) => (
                <button
                  key={f}
                  type="button"
                  onClick={() => setFilter(f)}
                  className={`rounded-full px-3 py-1 text-xs ${
                    filter === f ? "bg-zinc-900 text-white" : "border border-zinc-200 hover:bg-zinc-50"
                  }`}
                >
                  {f === "ALL" ? `All (${tasks.length})` : `${f} (${tasks.filter((t) => t.status === f).length})`}
                </button>
              ))}
              <button
                type="button"
                onClick={onBulkAiLabel}
                disabled={bulkAi}
                className="rounded-full border border-indigo-200 bg-indigo-50 px-3 py-1 text-xs text-indigo-700 hover:bg-indigo-100 disabled:opacity-40"
              >
                {bulkAi ? (bulkProgress ?? "Labeling…") : "AI label all pending"}
              </button>
            </div>

            {visibleTasks.length === 0 ? (
              <Card className="mt-4">
                <div className="text-sm font-medium text-zinc-900">No tasks in this view</div>
                <p className="mt-1 text-sm text-zinc-500">
                  {tasks.length === 0
                    ? "This project has no tasks yet — ingest dataset items, then generate the queue."
                    : "No tasks match this filter."}
                </p>
                <div className="mt-3 flex gap-2">
                  <Link href="/datasets" className="rounded-full border border-zinc-200 px-4 py-2 text-xs hover:bg-zinc-50">
                    Go to Datasets
                  </Link>
                  <Link href={`/annotate?projectId=${project.id}`} className="rounded-full bg-zinc-900 px-4 py-2 text-xs font-medium text-white hover:bg-zinc-800">
                    Open workspace
                  </Link>
                </div>
              </Card>
            ) : (
              <div className="mt-4 space-y-4">
                {visibleTasks.map((task) => {
                  const itemAnnotations = annotationsFor(task.id);
                  const locked = task.status === "APPROVED";
                  return (
                    <Card key={task.id}>
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className="text-sm font-medium text-zinc-900">
                          Task #{task.id}
                          {task.assignedToEmail && (
                            <span className="ml-2 text-xs font-normal text-zinc-500">→ {task.assignedToEmail}</span>
                          )}
                        </span>
                        <span className="flex items-center gap-2">
                          <Badge tone={taskTone(task.status)}>{task.status}</Badge>
                          <Link href={`/annotate?taskId=${task.id}`} className="text-xs text-zinc-600 underline hover:text-zinc-900">
                            Open
                          </Link>
                        </span>
                      </div>
                      {task.item?.imageUrl && (
                        <img
                          src={`${apiBaseUrl()}${task.item.imageUrl}`}
                          alt={task.item.content}
                          className="mt-2 max-h-64 rounded-lg ring-1 ring-zinc-200"
                        />
                      )}
                      {task.item && Object.keys(task.item.rowData).length > 0 ? (
                        <dl className="mt-2 space-y-1 text-sm leading-6 text-zinc-900">
                          {Object.entries(task.item.rowData).map(([key, value]) => (
                            <div key={key} className="flex gap-2">
                              <dt className="shrink-0 font-medium text-zinc-500">{key}:</dt>
                              <dd className="whitespace-pre-wrap">{value || "—"}</dd>
                            </div>
                          ))}
                        </dl>
                      ) : (
                        <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-zinc-900">
                          {task.item?.content || task.itemData || "(empty item)"}
                        </p>
                      )}
                      {!locked && (
                        <div className="mt-3 flex gap-2">
                          <input
                            value={labelInputs[task.id] ?? ""}
                            onChange={(e) => setLabelInputs((prev) => ({ ...prev, [task.id]: e.target.value }))}
                            placeholder="Enter the label…"
                            maxLength={100}
                            className="w-full rounded-lg border border-zinc-200 px-3 py-1.5 text-sm outline-none focus:border-zinc-900"
                          />
                          <button
                            type="button"
                            onClick={() => onAnnotate(task.id)}
                            disabled={isBusy(`annotate-${task.id}`) || busy !== null}
                            className={`shrink-0 rounded-full px-4 py-1.5 text-xs font-medium text-white ${
                              busy !== null ? "bg-zinc-400" : "bg-zinc-900 hover:bg-zinc-800"
                            }`}
                          >
                            {isBusy(`annotate-${task.id}`) ? "Saving…" : "Label"}
                          </button>
                          <button
                            type="button"
                            onClick={() => onAutoLabel(task.id)}
                            disabled={busy !== null}
                            className="shrink-0 rounded-full border border-indigo-200 bg-indigo-50 px-4 py-1.5 text-xs text-indigo-700 hover:bg-indigo-100 disabled:opacity-40"
                          >
                            {isBusy(`ai-${task.id}`) ? "Labeling…" : "AI label"}
                          </button>
                        </div>
                      )}
                      {locked && (
                        <p className="mt-2 text-xs text-emerald-700">Approved by a reviewer — this item is locked.</p>
                      )}
                      {itemAnnotations.length > 0 && (
                        <ul className="mt-3 space-y-2">
                          {itemAnnotations.map((a) => (
                            <li key={a.id} className="rounded-lg border border-zinc-100 p-2 text-sm">
                              <div className="flex flex-wrap items-center justify-between gap-2">
                                <span className="rounded-full bg-zinc-900 px-2.5 py-1 text-xs text-white">
                                  {a.label}
                                </span>
                                <Badge tone={a.source === "HUMAN_APPROVED" ? "emerald" : "zinc"}>{a.source}</Badge>
                              </div>
                              {reviews
                                .filter((r) => r.annotationId === a.id)
                                .map((r) => (
                                  <p key={r.id} className="mt-1 text-xs text-zinc-500">
                                    Reviewer {r.decision.toLowerCase()}
                                    {r.comment ? `: “${r.comment}”` : "."}
                                  </p>
                                ))}
                              {!locked && !reviewedIds.has(a.id) && (
                                <div className="mt-2 flex gap-2">
                                  <button
                                    type="button"
                                    onClick={() => onDecide(a.id, "APPROVED")}
                                    disabled={busy !== null}
                                    className={`rounded-full px-3 py-1 text-xs font-medium text-white ${
                                      busy !== null ? "bg-zinc-400" : "bg-emerald-600 hover:bg-emerald-700"
                                    }`}
                                  >
                                    {isBusy(`review-${a.id}`) ? "Saving…" : "Approve"}
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() => onDecide(a.id, "REJECTED")}
                                    disabled={busy !== null}
                                    className="rounded-full border border-zinc-200 px-3 py-1 text-xs hover:bg-zinc-50 disabled:opacity-40"
                                  >
                                    Reject
                                  </button>
                                </div>
                              )}
                            </li>
                          ))}
                        </ul>
                      )}
                    </Card>
                  );
                })}
              </div>
            )}

            {verified.length > 0 && (
              <Card className="mt-6">
                <div className="text-sm font-semibold text-zinc-900">
                  Verified export ({verified.length} approved label{verified.length === 1 ? "" : "s"})
                </div>
                <ul className="mt-2 space-y-1 text-xs text-zinc-600">
                  {verified.slice(0, 10).map((a) => (
                    <li key={a.id}>
                      Task #{a.taskId} → <span className="font-medium text-zinc-900">{a.label}</span>
                    </li>
                  ))}
                </ul>
                {verified.length > 10 && (
                  <div className="mt-1 text-xs text-zinc-500">+ {verified.length - 10} more in the download.</div>
                )}
                <div className="mt-3 flex gap-2">
                  <button
                    onClick={() => onExport("json")}
                    disabled={exporting !== null}
                    className="rounded-full border border-zinc-200 px-4 py-1.5 text-xs hover:bg-zinc-50 disabled:opacity-40"
                  >
                    {exporting === "json" ? "Exporting…" : "Download full JSON"}
                  </button>
                  <button
                    onClick={() => onExport("csv")}
                    disabled={exporting !== null}
                    className="rounded-full border border-zinc-200 px-4 py-1.5 text-xs hover:bg-zinc-50 disabled:opacity-40"
                  >
                    {exporting === "csv" ? "Exporting…" : "Download full CSV"}
                  </button>
                </div>
              </Card>
            )}
          </>
        ) : (
          <Card>
            <div className="text-sm font-medium text-zinc-900">Project not found</div>
            <p className="mt-1 text-sm text-zinc-500">You may not have access to this project.</p>
            <Link href="/projects" className="mt-3 inline-block rounded-full bg-zinc-900 px-4 py-2 text-xs font-medium text-white hover:bg-zinc-800">
              Back to Projects
            </Link>
          </Card>
        )}
      </AppShell>
    </RequireAuth>
  );
}
