"use client";
import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import AppShell from "@/components/AppShell";
import Badge from "@/components/Badge";
import Card from "@/components/Card";
import RequireAuth from "@/components/RequireAuth";
import {
  createAnnotation,
  createTask,
  deleteAnnotation,
  friendlyAnnotationError,
  friendlyProjectError,
  friendlyTaskError,
  listProjectReviews,
  listProjectTasks,
  listProjects,
  listTaskAnnotations,
  updateAnnotation,
  type AnnotationResponse,
  type ProjectResponse,
  type ReviewResponse,
  type TaskResponse,
  type TaskStatus,
} from "@/lib/api";

function taskTone(status: TaskStatus): "zinc" | "emerald" | "amber" | "blue" {
  if (status === "APPROVED") return "emerald";
  if (status === "SUBMITTED") return "blue";
  if (status === "REJECTED") return "amber";
  return "zinc";
}

export default function AnnotatePage() {
  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const [activeId, setActiveId] = useState<number | null>(null);
  const [tasks, setTasks] = useState<TaskResponse[]>([]);
  const [index, setIndex] = useState(0);
  const [submitted, setSubmitted] = useState<AnnotationResponse[]>([]);
  const [label, setLabel] = useState("");
  const [newItem, setNewItem] = useState("");
  const [loadingProjects, setLoadingProjects] = useState(true);
  const [loadingTasks, setLoadingTasks] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [addingItem, setAddingItem] = useState(false);
  const [projectReviews, setProjectReviews] = useState<ReviewResponse[]>([]);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editValue, setEditValue] = useState("");
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const active = projects.find((p) => p.id === activeId) ?? null;
  const current = index < tasks.length ? tasks[index] : null;
  const doneCount = tasks.filter((t) => t.status === "SUBMITTED" || t.status === "APPROVED").length;

  const loadQueue = useCallback(async (projectId: number) => {
    setLoadingTasks(true);
    setError(null);
    setNotice(null);
    try {
      const [queue, reviews] = await Promise.all([
        listProjectTasks(projectId),
        listProjectReviews(projectId).catch(() => [] as ReviewResponse[]),
      ]);
      setTasks(queue);
      setProjectReviews(reviews);
      setIndex(0);
      setSubmitted([]);
      setLabel("");
      setNotice(null);
    } catch (err) {
      setError(friendlyTaskError(err));
      setTasks([]);
      setProjectReviews([]);
    } finally {
      setLoadingTasks(false);
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

  useEffect(() => {
    // State updates live in promise callbacks (not in the effect body) so the
    // per-item fetch complies with react-hooks/set-state-in-effect.
    if (!current) return;
    const taskId = current.id;
    let cancelled = false;
    listTaskAnnotations(taskId)
      .then((data) => {
        if (cancelled) return;
        setSubmitted(data);
      })
      .catch(() => {
        if (cancelled) return;
        setSubmitted([]);
      });
    return () => {
      cancelled = true;
    };
  }, [current]);

  function chooseProject(id: number) {
    if (id === activeId) return;
    setActiveId(id);
    setLabel("");
    setNotice(null);
    void loadQueue(id);
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!current || submitting) return;
    const value = label.trim();
    if (value.length < 1 || value.length > 100) {
      setError("Label must be between 1 and 100 characters.");
      return;
    }
    setSubmitting(true);
    setError(null);
    setNotice(null);
    try {
      const saved = await createAnnotation({ taskId: current.id, label: value });
      setSubmitted((prev) => [saved, ...prev]);
      setTasks((prev) => prev.map((t) => (t.id === current.id ? { ...t, status: "SUBMITTED" as TaskStatus } : t)));
      setLabel("");
      setNotice(`Saved “${saved.label}” for item ${index + 1}. It is now waiting for review.`);
    } catch (err) {
      setError(friendlyAnnotationError(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function onAddItem(e: React.FormEvent) {
    e.preventDefault();
    if (!active || addingItem) return;
    const value = newItem.trim();
    if (value.length < 1 || value.length > 5000) {
      setError("Item text must be between 1 and 5000 characters.");
      return;
    }
    setAddingItem(true);
    setError(null);
    try {
      const created = await createTask(active.id, { itemData: value, itemIndex: tasks.length });
      setTasks((prev) => [...prev, created]);
      setNewItem("");
      setNotice("Item added to the queue.");
    } catch (err) {
      setError(friendlyTaskError(err));
    } finally {
      setAddingItem(false);
    }
  }

  async function onSaveEdit(id: number) {
    if (!current || busyId !== null) return;
    const value = editValue.trim();
    if (value.length < 1 || value.length > 100) {
      setError("Label must be between 1 and 100 characters.");
      return;
    }
    setBusyId(id);
    setError(null);
    try {
      const saved = await updateAnnotation(id, { label: value });
      setSubmitted((prev) => prev.map((a) => (a.id === id ? saved : a)));
      setEditingId(null);
      setNotice(`Updated to “${saved.label}”.`);
    } catch (err) {
      setError(friendlyAnnotationError(err));
    } finally {
      setBusyId(null);
    }
  }

  async function onDeleteAnnotation(id: number) {
    if (!current || busyId !== null) return;
    if (!confirm("Delete this annotation? The item returns to the queue when nothing remains.")) return;
    setBusyId(id);
    setError(null);
    try {
      await deleteAnnotation(id);
      const remaining = submitted.filter((a) => a.id !== id);
      setSubmitted(remaining);
      if (remaining.length === 0) {
        setTasks((prev) =>
          prev.map((t) => (t.id === current.id ? { ...t, status: "IN_PROGRESS" as TaskStatus } : t))
        );
      }
      setEditingId(null);
      setNotice("Annotation deleted.");
    } catch (err) {
      setError(friendlyAnnotationError(err));
    } finally {
      setBusyId(null);
    }
  }

  function go(delta: number) {
    setIndex((i) => Math.min(Math.max(i + delta, 0), Math.max(tasks.length - 1, 0)));
    setLabel("");
    setSubmitted([]);
    setEditingId(null);
    setNotice(null);
    setError(null);
  }

  return (
    <RequireAuth>
      <AppShell>
        <div className="mb-4">
          <h1 className="text-xl font-semibold text-zinc-900">Annotation workspace</h1>
          <p className="text-sm text-zinc-500">Open a project, read each item, and submit its label.</p>
        </div>

        {loadingProjects ? (
          <div className="text-sm text-zinc-500">Loading your projects…</div>
        ) : projects.length === 0 ? (
          <Card>
            <div className="text-sm font-medium text-zinc-900">No projects yet</div>
            <p className="mt-1 text-sm text-zinc-500">
              Create an annotation project first, then come back here to label its items.
            </p>
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
              {active && (
                <div className="mt-4 border-t border-zinc-100 pt-3 text-xs text-zinc-500">
                  {active.instructions && <p className="mb-2">Instructions: {active.instructions}</p>}
                  {active.labelType && <p>Label type: {active.labelType}</p>}
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

              {loadingTasks ? (
                <div className="text-sm text-zinc-500">Loading the annotation queue…</div>
              ) : tasks.length === 0 ? (
                <Card>
                  <div className="text-sm font-medium text-zinc-900">Queue is empty</div>
                  <p className="mt-1 text-sm text-zinc-500">
                    This project has no items yet. Add the first item below to start labeling.
                  </p>
                  <form onSubmit={onAddItem} className="mt-3 flex gap-2">
                    <input
                      value={newItem}
                      onChange={(e) => setNewItem(e.target.value)}
                      placeholder="e.g. The delivery was two days late."
                      maxLength={5000}
                      className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-900"
                    />
                    <button
                      type="submit"
                      disabled={addingItem}
                      className={`shrink-0 rounded-full px-4 py-2 text-xs font-medium text-white ${addingItem ? "bg-zinc-400" : "bg-zinc-900 hover:bg-zinc-800"}`}
                    >
                      {addingItem ? "Adding…" : "Add item"}
                    </button>
                  </form>
                </Card>
              ) : current ? (
                <>
                  <div className="mb-3 flex items-center justify-between text-xs text-zinc-500">
                    <span>
                      Item {index + 1} of {tasks.length} · {doneCount} submitted
                    </span>
                    <Badge tone={taskTone(current.status)}>{current.status}</Badge>
                  </div>
                  <Card>
                    <div className="text-xs font-semibold uppercase tracking-wide text-zinc-500">Item to label</div>
                    <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-zinc-900">
                      {current.itemData || "(empty item — no text was stored for this task)"}
                    </p>
                    <form onSubmit={onSubmit} className="mt-4 flex gap-2">
                      <input
                        value={label}
                        onChange={(e) => setLabel(e.target.value)}
                        placeholder="Enter the label, e.g. Positive"
                        maxLength={100}
                        disabled={current.status === "APPROVED"}
                        className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-900 disabled:bg-zinc-50"
                      />
                      <button
                        type="submit"
                        disabled={submitting || current.status === "APPROVED"}
                        className={`shrink-0 rounded-full px-5 py-2 text-sm font-medium text-white ${
                          submitting || current.status === "APPROVED" ? "bg-zinc-400" : "bg-zinc-900 hover:bg-zinc-800"
                        }`}
                      >
                        {submitting ? "Saving…" : "Submit label"}
                      </button>
                    </form>
                    {current.status === "APPROVED" && (
                      <p className="mt-2 text-xs text-emerald-700">Approved by a reviewer — this item is locked.</p>
                    )}
                    <div className="mt-3 flex gap-2">
                      <button
                        type="button"
                        onClick={() => go(-1)}
                        disabled={index === 0}
                        className="rounded-full border border-zinc-200 px-4 py-1.5 text-xs hover:bg-zinc-50 disabled:opacity-40"
                      >
                        ← Previous
                      </button>
                      <button
                        type="button"
                        onClick={() => go(1)}
                        disabled={index >= tasks.length - 1}
                        className="rounded-full border border-zinc-200 px-4 py-1.5 text-xs hover:bg-zinc-50 disabled:opacity-40"
                      >
                        Next →
                      </button>
                    </div>
                  </Card>

                  {submitted.length > 0 && (
                    <Card className="mt-4">
                      <div className="text-xs font-semibold uppercase tracking-wide text-zinc-500">Submitted for this item</div>
                      <ul className="mt-2 space-y-3">
                        {submitted.map((a) => (
                          <li key={a.id} className="rounded-lg border border-zinc-100 p-2 text-sm">
                            <div className="flex items-center justify-between gap-2">
                              <span className="rounded-full bg-zinc-900 px-2.5 py-1 text-xs text-white">{a.label}</span>
                              <Badge tone={a.source === "HUMAN_APPROVED" ? "emerald" : "zinc"}>{a.source}</Badge>
                            </div>
                            {projectReviews
                              .filter((r) => r.annotationId === a.id)
                              .map((r) => (
                                <p key={r.id} className="mt-2 text-xs text-zinc-500">
                                  Reviewer {r.decision.toLowerCase()}
                                  {r.comment ? `: "${r.comment}"` : "."}
                                </p>
                              ))}
                            {current.status !== "APPROVED" &&
                              !projectReviews.some((r) => r.annotationId === a.id) && (
                              editingId === a.id ? (
                                <div className="mt-2 flex gap-2">
                                  <input
                                    value={editValue}
                                    onChange={(e) => setEditValue(e.target.value)}
                                    maxLength={100}
                                    className="w-full rounded-lg border border-zinc-200 px-3 py-1.5 text-sm outline-none focus:border-zinc-900"
                                  />
                                  <button
                                    type="button"
                                    onClick={() => onSaveEdit(a.id)}
                                    disabled={busyId === a.id}
                                    className={`shrink-0 rounded-full px-3 py-1.5 text-xs font-medium text-white ${busyId === a.id ? "bg-zinc-400" : "bg-zinc-900 hover:bg-zinc-800"}`}
                                  >
                                    {busyId === a.id ? "Saving…" : "Save"}
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() => setEditingId(null)}
                                    className="shrink-0 rounded-full border border-zinc-200 px-3 py-1.5 text-xs hover:bg-zinc-50"
                                  >
                                    Cancel
                                  </button>
                                </div>
                              ) : (
                                <div className="mt-2 flex gap-2">
                                  <button
                                    type="button"
                                    onClick={() => {
                                      setEditingId(a.id);
                                      setEditValue(a.label);
                                    }}
                                    className="rounded-full border border-zinc-200 px-3 py-1 text-xs hover:bg-zinc-50"
                                  >
                                    Edit
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() => onDeleteAnnotation(a.id)}
                                    disabled={busyId === a.id}
                                    className="rounded-full border border-red-200 px-3 py-1 text-xs text-red-600 hover:bg-red-50 disabled:opacity-40"
                                  >
                                    {busyId === a.id ? "Deleting…" : "Delete"}
                                  </button>
                                </div>
                              )
                            )}
                          </li>
                        ))}
                      </ul>
                    </Card>
                  )}

                  <Card className="mt-4">
                    <div className="text-xs font-semibold uppercase tracking-wide text-zinc-500">Add another item</div>
                    <form onSubmit={onAddItem} className="mt-2 flex gap-2">
                      <input
                        value={newItem}
                        onChange={(e) => setNewItem(e.target.value)}
                        placeholder="Paste the next text to label…"
                        maxLength={5000}
                        className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-900"
                      />
                      <button
                        type="submit"
                        disabled={addingItem}
                        className={`shrink-0 rounded-full px-4 py-2 text-xs font-medium text-white ${addingItem ? "bg-zinc-400" : "bg-zinc-900 hover:bg-zinc-800"}`}
                      >
                        {addingItem ? "Adding…" : "Add"}
                      </button>
                    </form>
                  </Card>
                </>
              ) : null}
            </div>
          </div>
        )}
      </AppShell>
    </RequireAuth>
  );
}
