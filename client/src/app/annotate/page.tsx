"use client";
import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import AppShell from "@/components/AppShell";
import Badge from "@/components/Badge";
import Card from "@/components/Card";
import RequireAuth from "@/components/RequireAuth";
import {
  apiBaseUrl,
  autoLabelTask,
  createAnnotation,
  createTask,
  createTasksBulk,
  deleteAnnotation,
  friendlyAiError,
  friendlyAnnotationError,
  friendlyProjectError,
  friendlyTaskError,
  generateProjectTasks,
  getProject,
  getTask,
  listAssignedTasks,
  listProjectAnnotations,
  listProjectReviews,
  listProjectTasks,
  listProjects,
  listTaskAnnotations,
  startTask,
  submitTask,
  suggestLabel,
  updateAnnotation,
  type AnnotationResponse,
  type ProjectResponse,
  type ReviewResponse,
  type SuggestionResponse,
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
  const [bulkText, setBulkText] = useState("");
  const [importing, setImporting] = useState(false);
  const [loadingProjects, setLoadingProjects] = useState(true);
  const [loadingTasks, setLoadingTasks] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [addingItem, setAddingItem] = useState(false);
  const [projectReviews, setProjectReviews] = useState<ReviewResponse[]>([]);
  const [candidates, setCandidates] = useState("");
  const [usedLabels, setUsedLabels] = useState<string[]>([]);
  const [suggestion, setSuggestion] = useState<SuggestionResponse | null>(null);
  const [suggesting, setSuggesting] = useState(false);
  const [suggestError, setSuggestError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editValue, setEditValue] = useState("");
  const [busyId, setBusyId] = useState<number | null>(null);
  const [queueMode, setQueueMode] = useState<"project" | "assigned" | "single">("project");
  const [assignedTasks, setAssignedTasks] = useState<TaskResponse[]>([]);
  const [loadingAssigned, setLoadingAssigned] = useState(false);
  const [taskActionBusy, setTaskActionBusy] = useState(false);
  const [autoLabeling, setAutoLabeling] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const active = projects.find((p) => p.id === activeId) ?? null;
  const queue = queueMode === "project" ? tasks : assignedTasks;
  const setQueue = queueMode === "project" ? setTasks : setAssignedTasks;
  const current = index < queue.length ? queue[index] : null;
  const doneCount = queue.filter((t) => t.status === "SUBMITTED" || t.status === "APPROVED").length;
  const revisionNotes = current
    ? projectReviews.filter((r) => submitted.some((a) => a.id === r.annotationId) && r.decision === "REJECTED")
    : [];

  const loadQueue = useCallback(async (projectId: number) => {
    setLoadingTasks(true);
    setError(null);
    setNotice(null);
    try {
      const [queue, reviews, projectAnnotations] = await Promise.all([
        listProjectTasks(projectId),
        listProjectReviews(projectId).catch(() => [] as ReviewResponse[]),
        listProjectAnnotations(projectId).catch(() => [] as AnnotationResponse[]),
      ]);
      setTasks(queue);
      setProjectReviews(reviews);
      setUsedLabels(Array.from(new Set(projectAnnotations.map((a) => a.label))).slice(0, 20));
      setIndex(0);
      setSubmitted([]);
      setLabel("");
      setSuggestion(null);
      setSuggestError(null);
      setNotice(null);
    } catch (err) {
      setError(friendlyTaskError(err));
      setTasks([]);
      setProjectReviews([]);
      setUsedLabels([]);
    } finally {
      setLoadingTasks(false);
    }
  }, []);

  const loadAssignedQueue = useCallback(async () => {
    setLoadingAssigned(true);
    setError(null);
    setNotice(null);
    try {
      const mine = await listAssignedTasks();
      setAssignedTasks(mine);
      setIndex(0);
      setSubmitted([]);
      setLabel("");
      setSuggestion(null);
      setSuggestError(null);
      if (mine.length > 0) {
        // Reviews carry the revision feedback; assignees can read reviews on
        // projects they are assigned to.
        try {
          const reviews = await listProjectReviews(mine[0].projectId);
          setProjectReviews(reviews);
        } catch {
          setProjectReviews([]);
        }
        try {
          const project = await getProject(mine[0].projectId).catch(() => null);
          if (project) {
            setProjects((prev) => (prev.some((p) => p.id === project.id) ? prev : [...prev, project]));
            setActiveId(project.id);
            applyCandidates(project.id, project.labels ?? []);
          }
        } catch {
          // Labels still work via free text when the project is unreachable.
        }
      }
      if (mine.length === 0) setNotice("No tasks are assigned to you right now.");
    } catch (err) {
      setError(friendlyTaskError(err));
      setAssignedTasks([]);
    } finally {
      setLoadingAssigned(false);
    }
  }, []);

  const loadSingleTask = useCallback(async (taskId: number) => {
    setLoadingAssigned(true);
    setError(null);
    setNotice(null);
    try {
      const task = await getTask(taskId);
      setAssignedTasks([task]);
      setIndex(0);
      setSubmitted([]);
      setLabel("");
      setSuggestion(null);
      setSuggestError(null);
      try {
        const reviews = await listProjectReviews(task.projectId).catch(() => [] as ReviewResponse[]);
        setProjectReviews(reviews);
      } catch {
        setProjectReviews([]);
      }
      try {
        const project = await getProject(task.projectId).catch(() => null);
        if (project) {
          setProjects((prev) => (prev.some((p) => p.id === project.id) ? prev : [...prev, project]));
          setActiveId(project.id);
          applyCandidates(project.id, project.labels ?? []);
        }
      } catch {
        // Labels still work via free text when the project is unreachable.
      }
    } catch (err) {
      setError(friendlyTaskError(err));
      setAssignedTasks([]);
    } finally {
      setLoadingAssigned(false);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    listProjects()
      .then((data) => {
        if (cancelled) return;
        setProjects(data);
        setLoadingProjects(false);
        const params = new URLSearchParams(window.location.search);
        const singleTaskId = Number(params.get("taskId"));
        if (Number.isInteger(singleTaskId) && singleTaskId > 0) {
          setQueueMode("single");
          void loadSingleTask(singleTaskId);
          return;
        }
        if (params.get("assigned") === "1") {
          setQueueMode("assigned");
          void loadAssignedQueue();
          return;
        }
        if (data.length === 0) return;
        const wanted = Number(params.get("projectId"));
        const initial =
          Number.isInteger(wanted) && data.some((p) => p.id === wanted) ? wanted : data[0].id;
        setActiveId(initial);
        applyCandidates(initial, data.find((p) => p.id === initial)?.labels ?? []);
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
  }, [loadQueue, loadAssignedQueue, loadSingleTask]);

  useEffect(() => {
    // Number-key shortcuts (1-9) pick the matching project label into the
    // label box. Ignored while typing in an input or when the item is locked.
    function onKey(e: KeyboardEvent) {
      const target = e.target as HTMLElement | null;
      if (target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA")) return;
      if (!current || current.status === "APPROVED") return;
      const scheme = active?.labels ?? [];
      const digit = Number(e.key);
      if (Number.isInteger(digit) && digit >= 1 && digit <= 9 && digit <= scheme.length) {
        setLabel(scheme[digit - 1]);
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [current, active]);

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

  function applyCandidates(projectId: number, scheme: string[]) {
    let stored = "";
    try {
      stored = window.localStorage.getItem(`labelmate_labels_${projectId}`) ?? "";
    } catch {
      stored = "";
    }
    setCandidates(stored || scheme.join(", "));
  }

  function chooseProject(id: number) {
    if (id === activeId && queueMode === "project") return;
    setQueueMode("project");
    setActiveId(id);
    setLabel("");
    setNotice(null);
    applyCandidates(id, projects.find((p) => p.id === id)?.labels ?? []);
    void loadQueue(id);
  }

  function showAssigned() {
    setQueueMode("assigned");
    setIndex(0);
    setSubmitted([]);
    setLabel("");
    setNotice(null);
    void loadAssignedQueue();
  }

  async function onStartTask() {
    if (!current || taskActionBusy) return;
    setTaskActionBusy(true);
    setError(null);
    try {
      const updated = await startTask(current.id);
      setQueue((prev) => prev.map((t) => (t.id === current.id ? updated : t)));
      setNotice(`Task #${current.id} is now in progress.`);
    } catch (err) {
      setError(friendlyTaskError(err));
    } finally {
      setTaskActionBusy(false);
    }
  }

  async function onSubmitTask() {
    if (!current || taskActionBusy) return;
    setTaskActionBusy(true);
    setError(null);
    try {
      const updated = await submitTask(current.id);
      setQueue((prev) => prev.map((t) => (t.id === current.id ? updated : t)));
      setNotice(`Task #${current.id} submitted for review.`);
    } catch (err) {
      setError(friendlyTaskError(err));
    } finally {
      setTaskActionBusy(false);
    }
  }

  async function onAutoLabel() {
    if (!current || autoLabeling || current.status === "APPROVED") return;
    setAutoLabeling(true);
    setError(null);
    setNotice(null);
    try {
      const saved = await autoLabelTask(current.id);
      setSubmitted((prev) => [saved, ...prev]);
      setQueue((prev) => prev.map((t) => (t.id === current.id ? { ...t, status: "SUBMITTED" as TaskStatus } : t)));
      setNotice(`AI labeled “${saved.label}” — review it below, then approve in the review queue or correct it here.`);
    } catch (err) {
      setError(friendlyAiError(err));
    } finally {
      setAutoLabeling(false);
    }
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
      setQueue((prev) => prev.map((t) => (t.id === current.id ? { ...t, status: "SUBMITTED" as TaskStatus } : t)));
      setLabel("");
      setNotice(`Saved “${saved.label}” for item ${index + 1}. It is now waiting for review.`);
    } catch (err) {
      setError(friendlyAnnotationError(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function onGenerateQueue() {
    if (!active || generating) return;
    setGenerating(true);
    setError(null);
    setNotice(null);
    try {
      const created = await generateProjectTasks(active.id);
      if (created.length === 0) {
        setNotice("Dataset has no new items — ingest data first, then generate again.");
      } else {
        setNotice(`${created.length} task${created.length === 1 ? "" : "s"} generated from dataset items.`);
      }
      await loadQueue(active.id);
    } catch (err) {
      setError(friendlyTaskError(err));
    } finally {
      setGenerating(false);
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
        setQueue((prev) =>
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

  function parseCandidates(raw: string): string[] {
    return Array.from(
      new Set(
        raw
          .split(",")
          .map((part) => part.trim())
          .filter((part) => part.length > 0)
      )
    ).slice(0, 50);
  }

  async function onSuggest(e?: React.FormEvent) {
    e?.preventDefault();
    if (!current || suggesting) return;
    const labels = parseCandidates(candidates);
    if (labels.length < 1) {
      setSuggestError("Add at least one candidate label (comma-separated) first.");
      return;
    }
    if (!current.itemData || !current.itemData.trim()) {
      setSuggestError("This item has no text for the AI to read — label it manually.");
      return;
    }
    setSuggesting(true);
    setSuggestError(null);
    try {
      const result = await suggestLabel(current.id, labels);
      setSuggestion(result);
      try {
        window.localStorage.setItem(`labelmate_labels_${activeId}`, candidates);
      } catch {
        // Non-fatal: suggestions work without persisting the label list.
      }
    } catch (err) {
      setSuggestion(null);
      setSuggestError(friendlyAiError(err));
    } finally {
      setSuggesting(false);
    }
  }

  function acceptSuggestion() {
    if (!suggestion) return;
    setLabel(suggestion.suggestedLabel);
    setSuggestion(null);
    setNotice(`AI suggested “${suggestion.suggestedLabel}” — check it and submit when you agree.`);
  }

  function splitCsvLine(line: string): string[] {
    const cells: string[] = [];
    let current = "";
    let quoted = false;
    for (let i = 0; i < line.length; i++) {
      const ch = line[i];
      if (quoted) {
        if (ch === '"') {
          if (line[i + 1] === '"') {
            current += '"';
            i++;
          } else {
            quoted = false;
          }
        } else {
          current += ch;
        }
      } else if (ch === '"') {
        quoted = true;
      } else if (ch === ",") {
        cells.push(current);
        current = "";
      } else {
        current += ch;
      }
    }
    cells.push(current);
    return cells;
  }

  function extractItems(raw: string): string[] {
    const lines = raw.split(/\r?\n/).map((line) => line.trim()).filter((line) => line.length > 0);
    if (lines.length === 0) return [];
    const first = splitCsvLine(lines[0]);
    const start = first.length > 1 && first[0].trim().toLowerCase() === "text" ? 1 : 0;
    return lines
      .slice(start)
      .map((line) => {
        const cells = splitCsvLine(line);
        const cell = cells.length > 1 ? cells[0] : line;
        return cell.trim().replace(/^"(.*)"$/, "$1").replace(/""/g, '"').trim();
      })
      .filter((item) => item.length > 0)
      .slice(0, 500);
  }

  async function onBulkImport(e: React.FormEvent) {
    e.preventDefault();
    if (!active || importing) return;
    const items = extractItems(bulkText);
    if (items.length < 1) {
      setError("Nothing to import — paste one item per line, or pick a CSV file below.");
      return;
    }
    setImporting(true);
    setError(null);
    setNotice(null);
    try {
      const created = await createTasksBulk(active.id, items);
      setBulkText("");
      setNotice(`${created.length} item${created.length === 1 ? "" : "s"} added to the queue.`);
      await loadQueue(active.id);
    } catch (err) {
      setError(friendlyTaskError(err));
    } finally {
      setImporting(false);
    }
  }

  function onBulkFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!file.name.toLowerCase().endsWith(".csv") && !file.name.toLowerCase().endsWith(".txt")) {
      setError("Please pick a .csv or .txt file.");
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setError("File too large for bulk import — max 5 MB.");
      return;
    }
    setError(null);
    const reader = new FileReader();
    reader.onload = () => setBulkText(typeof reader.result === "string" ? reader.result : "");
    reader.onerror = () => setError("Could not read that file.");
    reader.readAsText(file);
    e.target.value = "";
  }

  function go(delta: number) {
    const len = (queueMode === "project" ? tasks : assignedTasks).length;
    setIndex((i) => Math.min(Math.max(i + delta, 0), Math.max(len - 1, 0)));
    setLabel("");
    setSubmitted([]);
    setEditingId(null);
    setSuggestion(null);
    setSuggestError(null);
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
        ) : projects.length === 0 && queueMode === "project" ? (
          <Card>
            <div className="text-sm font-medium text-zinc-900">No projects yet</div>
            <p className="mt-1 text-sm text-zinc-500">
              Create an annotation project first, then come back here to label its items — or check tasks assigned to you.
            </p>
            <div className="mt-3 flex gap-2">
              <Link href="/projects" className="inline-block rounded-full bg-zinc-900 px-4 py-2 text-xs font-medium text-white hover:bg-zinc-800">
                Go to Projects
              </Link>
              <button
                type="button"
                onClick={showAssigned}
                className="inline-block rounded-full border border-zinc-200 px-4 py-2 text-xs hover:bg-zinc-50"
              >
                View assigned tasks
              </button>
            </div>
          </Card>
        ) : (
          <div className="grid gap-4 md:grid-cols-[220px_1fr]">
            <Card className="h-fit">
              <button
                type="button"
                onClick={showAssigned}
                className={`w-full rounded-lg px-3 py-2 text-left text-sm ${
                  queueMode !== "project" ? "bg-zinc-900 text-white" : "text-zinc-700 hover:bg-zinc-100"
                }`}
              >
                <span className="block truncate font-medium">Assigned to me</span>
                <span className={`block text-xs ${queueMode !== "project" ? "text-zinc-300" : "text-zinc-400"}`}>Tasks from other owners</span>
              </button>
              <h2 className="mt-3 text-xs font-semibold uppercase tracking-wide text-zinc-500">Projects</h2>
              <div className="mt-2 space-y-1">
                {projects.map((p) => (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => chooseProject(p.id)}
                    className={`w-full rounded-lg px-3 py-2 text-left text-sm ${
                      p.id === activeId && queueMode === "project" ? "bg-zinc-900 text-white" : "text-zinc-700 hover:bg-zinc-100"
                    }`}
                  >
                    <span className="block truncate font-medium">{p.name}</span>
                    <span className={`block text-xs ${p.id === activeId && queueMode === "project" ? "text-zinc-300" : "text-zinc-400"}`}>#{p.id}</span>
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

              {loadingTasks || loadingAssigned ? (
                <div className="text-sm text-zinc-500">Loading the annotation queue…</div>
              ) : queue.length === 0 ? (
                <Card>
                  <div className="text-sm font-medium text-zinc-900">
                    {queueMode === "project" ? "Queue is empty" : "No assigned tasks"}
                  </div>
                  <p className="mt-1 text-sm text-zinc-500">
                    {queueMode === "project"
                      ? "This project has no tasks yet. If its dataset already has items, generate the queue — or add the first item below to start labeling."
                      : "No tasks are assigned to you right now. Ask a project owner to assign you a task."}
                  </p>
                  {queueMode === "project" && active && (
                  <>
                  <button
                    type="button"
                    onClick={onGenerateQueue}
                    disabled={generating}
                    className={`mt-3 rounded-full px-4 py-2 text-xs font-medium text-white ${generating ? "bg-zinc-400" : "bg-zinc-900 hover:bg-zinc-800"}`}
                  >
                    {generating ? "Generating…" : "Generate tasks from dataset"}
                  </button>
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
                  </>
                  )}
                </Card>
              ) : current ? (
                <>
                  <div className="mb-3 flex items-center justify-between text-xs text-zinc-500">
                    <span>
                      Item {index + 1} of {queue.length} · {doneCount} submitted
                      {current.assignedToEmail && <span> · assigned to {current.assignedToEmail}</span>}
                    </span>
                    <Badge tone={taskTone(current.status)}>{current.status}</Badge>
                  </div>
                  {current.status === "REJECTED" && (
                    <div role="alert" className="mb-3 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700 ring-1 ring-amber-200">
                      Reviewer requested changes
                      {revisionNotes.length > 0
                        ? `: “${revisionNotes[0].comment ?? "please check the label and resubmit"}” — correct the label below and submit again.`
                        : " — correct the label below and submit again."}
                    </div>
                  )}
                  <Card>
                    <div className="text-xs font-semibold uppercase tracking-wide text-zinc-500">Item to label</div>
                    {current.item?.imageUrl && (
                      <img
                        src={`${apiBaseUrl()}${current.item.imageUrl}`}
                        alt={current.item.content}
                        className="mt-2 max-h-80 rounded-lg ring-1 ring-zinc-200"
                      />
                    )}
                    {current.item && Object.keys(current.item.rowData).length > 0 ? (
                      <dl className="mt-2 space-y-1 text-sm leading-6 text-zinc-900">
                        {Object.entries(current.item.rowData).map(([key, value]) => (
                          <div key={key} className="flex gap-2">
                            <dt className="shrink-0 font-medium text-zinc-500">{key}:</dt>
                            <dd className="whitespace-pre-wrap">{value || "—"}</dd>
                          </div>
                        ))}
                      </dl>
                    ) : (
                      <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-zinc-900">
                        {current.item?.content || current.itemData || "(empty item — no text was stored for this task)"}
                      </p>
                    )}
                    {active && active.labels.length > 0 && (
                      <div className="mt-3 flex flex-wrap gap-1.5">
                        {active.labels.slice(0, 9).map((option, i) => (
                          <button
                            key={option}
                            type="button"
                            onClick={() => setLabel(option)}
                            disabled={current.status === "APPROVED"}
                            title={`Shortcut: ${i + 1}`}
                            className={`rounded-full border px-2.5 py-1 text-xs ${
                              label === option
                                ? "border-zinc-900 bg-zinc-900 text-white"
                                : "border-zinc-200 text-zinc-700 hover:bg-zinc-50"
                            } disabled:opacity-40`}
                          >
                            {i + 1} · {option}
                          </button>
                        ))}
                      </div>
                    )}
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
                    <div className="mt-3 flex flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={onStartTask}
                        disabled={taskActionBusy || current.status === "APPROVED" || current.status === "SUBMITTED"}
                        className="rounded-full border border-zinc-200 px-4 py-1.5 text-xs hover:bg-zinc-50 disabled:opacity-40"
                      >
                        {taskActionBusy ? "Working…" : "Start task"}
                      </button>
                      <button
                        type="button"
                        onClick={onSubmitTask}
                        disabled={taskActionBusy || current.status === "APPROVED" || current.status === "SUBMITTED"}
                        className="rounded-full border border-zinc-200 px-4 py-1.5 text-xs hover:bg-zinc-50 disabled:opacity-40"
                      >
                        {taskActionBusy ? "Working…" : "Submit for review"}
                      </button>
                      <button
                        type="button"
                        onClick={onAutoLabel}
                        disabled={autoLabeling || current.status === "APPROVED"}
                        className="rounded-full border border-indigo-200 bg-indigo-50 px-4 py-1.5 text-xs text-indigo-700 hover:bg-indigo-100 disabled:opacity-40"
                      >
                        {autoLabeling ? "Labeling…" : "AI auto-label"}
                      </button>
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
                        disabled={index >= queue.length - 1}
                        className="rounded-full border border-zinc-200 px-4 py-1.5 text-xs hover:bg-zinc-50 disabled:opacity-40"
                      >
                        Next →
                      </button>
                    </div>
                  </Card>

                  <Card className="mt-4">
                    <div className="text-xs font-semibold uppercase tracking-wide text-zinc-500">
                      AI assistance <span className="font-normal normal-case">(optional — never submits for you)</span>
                    </div>
                    {usedLabels.length > 0 && (
                      <div className="mt-2 flex flex-wrap gap-1.5">
                        {usedLabels.map((used) => (
                          <button
                            key={used}
                            type="button"
                            onClick={() =>
                              setCandidates((prev) =>
                                parseCandidates(prev).includes(used) ? prev : prev ? `${prev}, ${used}` : used
                              )
                            }
                            className="rounded-full border border-zinc-200 px-2.5 py-1 text-xs text-zinc-600 hover:bg-zinc-50"
                          >
                            {used}
                          </button>
                        ))}
                      </div>
                    )}
                    <form onSubmit={onSuggest} className="mt-2 flex gap-2">
                      <input
                        value={candidates}
                        onChange={(e) => setCandidates(e.target.value)}
                        placeholder="Candidate labels, comma-separated — e.g. Positive, Negative"
                        maxLength={2000}
                        className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-900"
                      />
                      <button
                        type="submit"
                        disabled={suggesting || current.status === "APPROVED"}
                        className={`shrink-0 rounded-full px-4 py-2 text-xs font-medium text-white ${
                          suggesting || current.status === "APPROVED" ? "bg-zinc-400" : "bg-indigo-600 hover:bg-indigo-700"
                        }`}
                      >
                        {suggesting ? "Thinking…" : "Suggest label"}
                      </button>
                    </form>
                    {suggestError && (
                      <div className="mt-2 flex flex-wrap items-center gap-2">
                        <p role="alert" className="text-xs text-amber-700">{suggestError}</p>
                        <button
                          type="button"
                          onClick={() => onSuggest()}
                          disabled={suggesting}
                          className="rounded-full border border-zinc-200 px-3 py-1 text-xs hover:bg-zinc-50 disabled:opacity-40"
                        >
                          Retry
                        </button>
                      </div>
                    )}
                    {suggestion && (
                      <div className="mt-3 rounded-lg bg-indigo-50 p-3 ring-1 ring-indigo-200">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <span className="rounded-full bg-indigo-600 px-2.5 py-1 text-xs text-white">
                            AI suggests: {suggestion.suggestedLabel}
                          </span>
                          <span className="text-xs text-indigo-700">
                            {suggestion.confidence !== null ? `${Number(suggestion.confidence).toFixed(0)}% confident` : "no confidence given"} · {suggestion.model}
                          </span>
                        </div>
                        <p className="mt-2 text-xs text-indigo-700">
                          Check it against the item — accept to fill the label box, then edit and submit only if you agree.
                        </p>
                        <div className="mt-2 flex gap-2">
                          <button
                            type="button"
                            onClick={acceptSuggestion}
                            className="rounded-full bg-zinc-900 px-4 py-1.5 text-xs font-medium text-white hover:bg-zinc-800"
                          >
                            Accept into label box
                          </button>
                          <button
                            type="button"
                            onClick={() => setSuggestion(null)}
                            className="rounded-full border border-indigo-200 bg-white px-4 py-1.5 text-xs hover:bg-indigo-100"
                          >
                            Dismiss
                          </button>
                        </div>
                      </div>
                    )}
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

                  {queueMode === "project" && (
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
                  )}

                  {queueMode === "project" && (
                  <Card className="mt-4">
                    <div className="text-xs font-semibold uppercase tracking-wide text-zinc-500">Bulk import items</div>
                    <p className="mt-1 text-xs text-zinc-500">
                      Paste one item per line, or pick a CSV file (first column is used, header row skipped).
                      Up to 500 items per import.
                    </p>
                    <form onSubmit={onBulkImport} className="mt-2 space-y-2">
                      <textarea
                        value={bulkText}
                        onChange={(e) => setBulkText(e.target.value)}
                        placeholder={"First item to label\nSecond item to label\n..."}
                        rows={3}
                        maxLength={250000}
                        className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-900"
                      />
                      <div className="flex flex-wrap items-center gap-2">
                        <label className="cursor-pointer rounded-full border border-zinc-200 px-4 py-2 text-xs hover:bg-zinc-50">
                          Choose CSV file
                          <input type="file" accept=".csv,.txt" onChange={onBulkFile} className="hidden" />
                        </label>
                        <button
                          type="submit"
                          disabled={importing}
                          className={`rounded-full px-4 py-2 text-xs font-medium text-white ${importing ? "bg-zinc-400" : "bg-zinc-900 hover:bg-zinc-800"}`}
                        >
                          {importing ? "Importing..." : "Import items"}
                        </button>
                      </div>
                    </form>
                  </Card>
                  )}
                </>
              ) : null}
            </div>
          </div>
        )}
      </AppShell>
    </RequireAuth>
  );
}
