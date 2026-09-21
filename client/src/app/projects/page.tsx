"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import AppShell from "@/components/AppShell";
import Card from "@/components/Card";
import Badge from "@/components/Badge";
import RequireAuth from "@/components/RequireAuth";
import { assignTask, autoLabelTask, createProject, deleteProject, exportProject, friendlyDatasetError, friendlyExportError, friendlyProjectError, friendlyTaskError, generateProjectTasks, listDatasets, listProjectTasks, listProjects, updateProject, type DatasetResponse, type ProjectResponse, type TaskResponse } from "@/lib/api";

type FormState = {
  datasetId: string;
  name: string;
  instructions: string;
  labelType: string;
  labels: string;
};

const emptyForm: FormState = { datasetId: "", name: "", instructions: "", labelType: "", labels: "" };

function parseLabels(raw: string): string[] {
  return Array.from(
    new Set(
      raw
        .split(",")
        .map((part) => part.trim())
        .filter((part) => part.length > 0)
    )
  ).slice(0, 50);
}

function statusTone(status: ProjectResponse["status"]): "zinc" | "emerald" | "blue" {
  if (status === "COMPLETED") return "emerald";
  if (status === "IN_PROGRESS") return "blue";
  return "zinc";
}

export default function ProjectsPage() {
  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const [datasets, setDatasets] = useState<DatasetResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [datasetsLoading, setDatasetsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [datasetsError, setDatasetsError] = useState<string | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [submitting, setSubmitting] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [exportingId, setExportingId] = useState<number | null>(null);
  const [editForm, setEditForm] = useState<FormState>(emptyForm);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [tasksOpenId, setTasksOpenId] = useState<number | null>(null);
  const [tasksByProject, setTasksByProject] = useState<Record<number, TaskResponse[]>>({});
  const [tasksLoadingId, setTasksLoadingId] = useState<number | null>(null);
  const [assignInputs, setAssignInputs] = useState<Record<number, string>>({});
  const [assigningId, setAssigningId] = useState<number | null>(null);
  const [generatingId, setGeneratingId] = useState<number | null>(null);
  const [taskNotice, setTaskNotice] = useState<string | null>(null);
  const [bulkAiProjectId, setBulkAiProjectId] = useState<number | null>(null);
  const [bulkAiProgress, setBulkAiProgress] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      setProjects(await listProjects());
    } catch (err) {
      setError(friendlyProjectError(err));
    } finally {
      setLoading(false);
    }
    // Datasets are reloaded separately so a dataset failure never masks
    // the project list (and vice versa).
    try {
      setDatasets(await listDatasets());
      setDatasetsError(null);
    } catch (err) {
      setDatasetsError(friendlyDatasetError(err));
    } finally {
      setDatasetsLoading(false);
    }
  }

  function datasetName(id: number): string {
    return datasets.find((d) => d.id === id)?.name ?? `Dataset #${id}`;
  }

  useEffect(() => {
    // State updates live in promise callbacks (not in the effect body) so the
    // initial fetch complies with react-hooks/set-state-in-effect.
    let cancelled = false;
    listProjects()
      .then((data) => {
        if (cancelled) return;
        setProjects(data);
        setLoading(false);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(friendlyProjectError(err));
        setLoading(false);
      });
    listDatasets()
      .then((data) => {
        if (cancelled) return;
        setDatasets(data);
        setDatasetsLoading(false);
      })
      .catch((err) => {
        if (cancelled) return;
        setDatasetsError(friendlyDatasetError(err));
        setDatasetsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  function toPayload(formState: FormState) {
    const labels = parseLabels(formState.labels);
    return {
      datasetId: Number(formState.datasetId),
      name: formState.name.trim(),
      instructions: formState.instructions.trim() || null,
      labelType: formState.labelType.trim() || null,
      labels: labels.length > 0 ? labels : null,
    };
  }

  function validate(formState: FormState): string | null {
    if (!formState.datasetId.trim() || !Number.isInteger(Number(formState.datasetId)) || Number(formState.datasetId) <= 0) {
      return "Please select one of your datasets.";
    }
    if (formState.name.trim().length < 2 || formState.name.trim().length > 150) {
      return "Name must be between 2 and 150 characters.";
    }
    if (formState.instructions.trim().length > 2000) {
      return "Instructions must be at most 2000 characters.";
    }
    if (formState.labelType.trim().length > 50) {
      return "Label type must be at most 50 characters.";
    }
    const labels = parseLabels(formState.labels);
    if (labels.length < 1) {
      return "Add at least one label — an annotation project needs possible labels.";
    }
    if (labels.length > 50) {
      return "At most 50 labels are allowed.";
    }
    const tooLong = labels.find((label) => label.length > 100);
    if (tooLong) {
      return `Label "${tooLong.slice(0, 30)}" is too long — at most 100 characters each.`;
    }
    return null;
  }

  async function onCreate(e: React.FormEvent) {
    e.preventDefault();
    const problem = validate(form);
    if (problem) {
      setError(problem);
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await createProject(toPayload(form));
      setForm(emptyForm);
      await load();
    } catch (err) {
      setError(friendlyProjectError(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function onUpdate(id: number, datasetId: number) {
    const problem = validate({ ...editForm, datasetId: String(datasetId) });
    if (problem) {
      setError(problem);
      return;
    }
    setError(null);
    try {
      await updateProject(id, toPayload({ ...editForm, datasetId: String(datasetId) }));
      setEditingId(null);
      await load();
    } catch (err) {
      setError(friendlyProjectError(err));
    }
  }

  async function onExport(id: number, format: "json" | "csv") {
    if (exportingId !== null) return;
    setExportingId(id);
    setError(null);
    try {
      const blob = await exportProject(id, format);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `project-${id}-export.${format}`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      setError(friendlyExportError(err));
    } finally {
      setExportingId(null);
    }
  }

  async function onGenerate(projectId: number) {
    if (generatingId !== null) return;
    setGeneratingId(projectId);
    setError(null);
    setTaskNotice(null);
    try {
      const created = await generateProjectTasks(projectId);
      setTaskNotice(
        created.length === 0
          ? "Queue is already complete — every dataset item has a task."
          : `${created.length} task${created.length === 1 ? "" : "s"} generated from dataset items.`
      );
      setProjects(await listProjects());
      if (tasksOpenId === projectId) {
        const queue = await listProjectTasks(projectId);
        setTasksByProject((prev) => ({ ...prev, [projectId]: queue }));
      }
    } catch (err) {
      setError(friendlyTaskError(err));
    } finally {
      setGeneratingId(null);
    }
  }

  async function onBulkAiLabel(projectId: number) {
    if (bulkAiProjectId !== null) return;
    const queue = tasksByProject[projectId] ?? [];
    const candidates = queue.filter((t) =>
      t.status === "PENDING" || t.status === "ASSIGNED" || t.status === "IN_PROGRESS" || t.status === "REJECTED"
    );
    if (candidates.length === 0) {
      setTaskNotice("Nothing to label — every task is already submitted or approved.");
      return;
    }
    setBulkAiProjectId(projectId);
    setError(null);
    setTaskNotice(null);
    let done = 0;
    let failed = 0;
    for (const task of candidates) {
      setBulkAiProgress(`AI labeling ${done + 1} of ${candidates.length}…`);
      try {
        await autoLabelTask(task.id);
        done++;
      } catch {
        // One task failing (no text, AI down) never aborts the rest.
        failed++;
        done++;
      }
    }
    setBulkAiProjectId(null);
    setBulkAiProgress(null);
    setTaskNotice(
      failed === 0
        ? `${done} task${done === 1 ? "" : "s"} AI-labeled — each is now waiting for human review.`
        : `${done - failed} labeled, ${failed} skipped (see review queue for details).`
    );
    try {
      const queue = await listProjectTasks(projectId);
      setTasksByProject((prev) => ({ ...prev, [projectId]: queue }));
      setProjects(await listProjects());
    } catch (err) {
      setError(friendlyTaskError(err));
    }
  }

  async function onDelete(id: number) {
    if (!confirm("Delete this project? This cannot be undone.")) return;
    setError(null);
    try {
      await deleteProject(id);
      await load();
    } catch (err) {
      setError(friendlyProjectError(err));
    }
  }

  async function toggleTasks(projectId: number) {
    if (tasksOpenId === projectId) {
      setTasksOpenId(null);
      return;
    }
    setTasksOpenId(projectId);
    setTasksLoadingId(projectId);
    setError(null);
    try {
      const queue = await listProjectTasks(projectId);
      setTasksByProject((prev) => ({ ...prev, [projectId]: queue }));
    } catch (err) {
      setError(friendlyTaskError(err));
    } finally {
      setTasksLoadingId(null);
    }
  }

  async function onAssign(taskId: number, projectId: number) {
    const email = (assignInputs[taskId] ?? "").trim();
    if (!email || assigningId !== null) return;
    setAssigningId(taskId);
    setError(null);
    try {
      const updated = await assignTask(taskId, email);
      setTasksByProject((prev) => ({
        ...prev,
        [projectId]: (prev[projectId] ?? []).map((t) => (t.id === taskId ? updated : t)),
      }));
      setAssignInputs((prev) => ({ ...prev, [taskId]: "" }));
    } catch (err) {
      setError(friendlyTaskError(err));
    } finally {
      setAssigningId(null);
    }
  }

  return (
    <RequireAuth>
      <AppShell>
        <div className="mb-6">
          <h1 className="text-xl font-semibold text-zinc-900">Projects</h1>
          <p className="text-sm text-zinc-500">Create an annotation project over one of your datasets.</p>
        </div>

        <Card>
          <h2 className="text-sm font-semibold text-zinc-900">Create project</h2>
          <form onSubmit={onCreate} className="mt-4 space-y-3">
            <div>
              <label className="text-xs font-medium text-zinc-700">Dataset *</label>
              {datasetsLoading ? (
                <div className="mt-1 text-xs text-zinc-500">Loading your datasets…</div>
              ) : datasetsError ? (
                <div className="mt-1 text-xs text-red-600">{datasetsError}</div>
              ) : datasets.length === 0 ? (
                <div className="mt-1 text-xs text-zinc-500">No datasets yet — create one on the Datasets page first.</div>
              ) : (
                <select value={form.datasetId} onChange={(e) => setForm({ ...form, datasetId: e.target.value })} className="mt-1 w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm outline-none focus:border-zinc-900">
                  <option value="">Select a dataset…</option>
                  {datasets.map((d) => (
                    <option key={d.id} value={d.id}>{d.name} (#{d.id})</option>
                  ))}
                </select>
              )}
            </div>
            <div>
              <label className="text-xs font-medium text-zinc-700">Name *</label>
              <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Image Classification v1" className="mt-1 w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-900" maxLength={150} />
            </div>
            <div>
              <label className="text-xs font-medium text-zinc-700">Instructions</label>
              <textarea value={form.instructions} onChange={(e) => setForm({ ...form, instructions: e.target.value })} placeholder="Optional — guidelines shown to annotators" rows={2} className="mt-1 w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-900" maxLength={2000} />
            </div>
            <div>
              <label className="text-xs font-medium text-zinc-700">Label type</label>
              <input value={form.labelType} onChange={(e) => setForm({ ...form, labelType: e.target.value })} placeholder="CLASSIFICATION" className="mt-1 w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-900" maxLength={50} />
            </div>
            <div>
              <label className="text-xs font-medium text-zinc-700">Labels *</label>
              <input value={form.labels} onChange={(e) => setForm({ ...form, labels: e.target.value })} placeholder="Positive, Negative, Neutral" className="mt-1 w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-900" />
              <p className="mt-1 text-xs text-zinc-400">Comma-separated options annotators can pick from.</p>
            </div>
            <button type="submit" disabled={submitting || datasets.length === 0} className={`rounded-full px-5 py-2 text-sm font-medium text-white ${submitting || datasets.length === 0 ? "bg-zinc-400" : "bg-zinc-900 hover:bg-zinc-800"}`}>
              {submitting ? "Creating…" : "Create project"}
            </button>
          </form>
        </Card>

        {error && <div className="mt-4 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700 ring-1 ring-amber-200">{error}</div>}

        <div className="mt-6">
          {loading ? (
            <div className="text-sm text-zinc-500">Loading projects…</div>
          ) : projects.length === 0 ? (
            <Card>
              <div className="text-sm font-medium text-zinc-900">No projects yet</div>
              <p className="mt-1 text-sm text-zinc-500">Create your first project above to start labeling.</p>
            </Card>
          ) : (
            <div className="grid gap-4 md:grid-cols-2">
              {projects.map((p) => (
                <Card key={p.id}>
                  <div className="flex items-start justify-between">
                    <div>
                      <div className="text-sm font-semibold text-zinc-900">{p.name}</div>
                      <div className="text-xs text-zinc-500">{p.status} · {datasetName(p.datasetId)} · {new Date(p.createdAt).toLocaleDateString()}</div>
                      {p.instructions && <div className="mt-1 text-xs text-zinc-600 line-clamp-2">{p.instructions}</div>}
                    </div>
                    <Badge tone={statusTone(p.status)}>{p.status}</Badge>
                  </div>
                  {p.labelType && (
                    <div className="mt-3 rounded-lg bg-zinc-50 p-2 text-xs text-zinc-600">
                      <div>labelType: {p.labelType}</div>
                    </div>
                  )}
                  {p.labels && p.labels.length > 0 && (
                    <div className="mt-2 flex flex-wrap gap-1.5">
                      {p.labels.map((label) => (
                        <span key={label} className="rounded-full bg-blue-50 px-2.5 py-1 text-xs text-blue-700 ring-1 ring-blue-200">{label}</span>
                      ))}
                    </div>
                  )}
                  <div className="mt-2 text-xs text-zinc-500">
                    {p.totalTasks} task{p.totalTasks === 1 ? "" : "s"} · {p.pendingTasks} pending · {p.submittedTasks} submitted · {p.approvedTasks} approved
                    {p.rejectedTasks > 0 && <span> · {p.rejectedTasks} needs rework</span>}
                  </div>
                  {taskNotice && tasksOpenId === p.id && (
                    <div role="status" className="mt-2 rounded-lg bg-emerald-50 px-3 py-2 text-xs text-emerald-700 ring-1 ring-emerald-200">
                      {taskNotice}
                    </div>
                  )}
                  <div className="mt-3 flex flex-wrap gap-2">
                    <Link href={`/annotate?projectId=${p.id}`} className="rounded-full bg-zinc-900 px-3 py-1 text-xs font-medium text-white hover:bg-zinc-800">
                      Annotate
                    </Link>
                    <Link href={`/projects/${p.id}`} className="rounded-full border border-zinc-200 px-3 py-1 text-xs hover:bg-zinc-50">
                      Detail
                    </Link>
                    <Link href={`/review?projectId=${p.id}`} className="rounded-full border border-zinc-200 px-3 py-1 text-xs hover:bg-zinc-50">
                      Review
                    </Link>
                    <button onClick={() => toggleTasks(p.id)} className="rounded-full border border-zinc-200 px-3 py-1 text-xs hover:bg-zinc-50">
                      {tasksOpenId === p.id ? "Hide tasks" : "Tasks & assign"}
                    </button>
                    <button
                      onClick={() => onGenerate(p.id)}
                      disabled={generatingId === p.id}
                      className="rounded-full border border-zinc-200 px-3 py-1 text-xs hover:bg-zinc-50 disabled:opacity-40"
                    >
                      {generatingId === p.id ? "Generating…" : "Generate tasks"}
                    </button>
                    <button onClick={() => setExpandedId(expandedId === p.id ? null : p.id)} className="rounded-full border border-zinc-200 px-3 py-1 text-xs hover:bg-zinc-50">
                      {expandedId === p.id ? "Hide" : "View"}
                    </button>
                    <button
                      onClick={() => {
                        setEditingId(p.id);
                        setEditForm({ datasetId: String(p.datasetId), name: p.name, instructions: p.instructions ?? "", labelType: p.labelType ?? "", labels: (p.labels ?? []).join(", ") });
                      }}
                      className="rounded-full border border-zinc-200 px-3 py-1 text-xs hover:bg-zinc-50"
                    >
                      Edit
                    </button>
                    <button onClick={() => onExport(p.id, "json")} disabled={exportingId === p.id} className="rounded-full border border-zinc-200 px-3 py-1 text-xs hover:bg-zinc-50 disabled:opacity-40">
                      {exportingId === p.id ? "Exporting…" : "Export JSON"}
                    </button>
                    <button onClick={() => onExport(p.id, "csv")} disabled={exportingId === p.id} className="rounded-full border border-zinc-200 px-3 py-1 text-xs hover:bg-zinc-50 disabled:opacity-40">
                      {exportingId === p.id ? "Exporting…" : "Export CSV"}
                    </button>
                    <button onClick={() => onDelete(p.id)} className="rounded-full border border-red-200 bg-white px-3 py-1 text-xs text-red-600 hover:bg-red-50">
                      Delete
                    </button>
                  </div>
                  {tasksOpenId === p.id && (
                    <div className="mt-3 rounded-lg border border-zinc-200 bg-zinc-50 p-3 text-xs">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="font-semibold text-zinc-900">Tasks & assignment</div>
                        <button
                          onClick={() => onBulkAiLabel(p.id)}
                          disabled={bulkAiProjectId !== null}
                          className="rounded-full border border-indigo-200 bg-indigo-50 px-3 py-1 text-xs text-indigo-700 hover:bg-indigo-100 disabled:opacity-40"
                        >
                          {bulkAiProjectId === p.id ? (bulkAiProgress ?? "Labeling…") : "AI label all pending"}
                        </button>
                      </div>
                      {tasksLoadingId === p.id ? (
                        <div className="mt-2 text-zinc-500">Loading tasks…</div>
                      ) : (tasksByProject[p.id] ?? []).length === 0 ? (
                        (() => {
                          const datasetItems =
                            datasets.find((d) => d.id === p.datasetId)?.itemCount ?? 0;
                          return datasetItems > 0 ? (
                            <div className="mt-2 text-zinc-500">
                              This project’s dataset has {datasetItems} item{datasetItems === 1 ? "" : "s"} but no tasks yet.
                              <button
                                onClick={() => onGenerate(p.id)}
                                disabled={generatingId === p.id}
                                className="ml-2 rounded-full bg-zinc-900 px-3 py-1 text-xs font-medium text-white hover:bg-zinc-800 disabled:opacity-40"
                              >
                                {generatingId === p.id ? "Generating…" : `Generate ${datasetItems} tasks`}
                              </button>
                            </div>
                          ) : (
                            <div className="mt-2 text-zinc-500">
                              No tasks yet — add items from the <Link href={`/annotate?projectId=${p.id}`} className="underline">annotation workspace</Link>.
                            </div>
                          );
                        })()
                      ) : (
                        <ul className="mt-2 space-y-2">
                          {(tasksByProject[p.id] ?? []).map((t) => (
                            <li key={t.id} className="rounded-lg border border-zinc-200 bg-white p-2">
                              <div className="flex flex-wrap items-center justify-between gap-2">
                                <span className="font-medium text-zinc-900">
                                  Task #{t.id} · {t.status}
                                  <span className="ml-2 font-normal text-zinc-500">
                                    {t.assignedToEmail ? `→ ${t.assignedToEmail}` : "→ unassigned"}
                                  </span>
                                </span>
                                <Link href={`/annotate?taskId=${t.id}`} className="underline text-zinc-600 hover:text-zinc-900">
                                  Open
                                </Link>
                              </div>
                              <div className="mt-1 truncate text-zinc-500">{t.itemData || "(empty item)"}</div>
                              <div className="mt-2 flex gap-2">
                                <input
                                  value={assignInputs[t.id] ?? ""}
                                  onChange={(e) => setAssignInputs((prev) => ({ ...prev, [t.id]: e.target.value }))}
                                  placeholder="annotator email"
                                  type="email"
                                  maxLength={255}
                                  className="w-full rounded-lg border border-zinc-200 px-2 py-1 text-xs outline-none focus:border-zinc-900"
                                />
                                <button
                                  onClick={() => onAssign(t.id, p.id)}
                                  disabled={assigningId === t.id}
                                  className={`shrink-0 rounded-full px-3 py-1 text-xs font-medium text-white ${assigningId === t.id ? "bg-zinc-400" : "bg-zinc-900 hover:bg-zinc-800"}`}
                                >
                                  {assigningId === t.id ? "Assigning…" : "Assign"}
                                </button>
                              </div>
                            </li>
                          ))}
                        </ul>
                      )}
                    </div>
                  )}
                  {expandedId === p.id && (
                    <div className="mt-3 rounded-lg border border-zinc-200 bg-zinc-50 p-3 text-xs">
                      <div>ID: {p.id}</div>
                      <div>Dataset: {datasetName(p.datasetId)} (#{p.datasetId})</div>
                      <div>Status: {p.status}</div>
                      {p.labelType && <div>Label type: {p.labelType}</div>}
                      {p.instructions && <div className="mt-1">Instructions: {p.instructions}</div>}
                      <div>Created: {p.createdAt}</div>
                      {p.updatedAt && <div>Updated: {p.updatedAt}</div>}
                    </div>
                  )}
                  {editingId === p.id && (
                    <div className="mt-3 space-y-2 rounded-lg border border-zinc-200 p-3">
                      <div className="text-xs text-zinc-500">Dataset: {datasetName(p.datasetId)} (#{p.datasetId}, cannot be changed)</div>
                      <input value={editForm.name} onChange={(e) => setEditForm({ ...editForm, name: e.target.value })} className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm" placeholder="Name" maxLength={150} />
                      <textarea value={editForm.instructions} onChange={(e) => setEditForm({ ...editForm, instructions: e.target.value })} className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm" placeholder="Instructions" rows={2} maxLength={2000} />
                      <input value={editForm.labelType} onChange={(e) => setEditForm({ ...editForm, labelType: e.target.value })} className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm" placeholder="Label type" maxLength={50} />
                      <input value={editForm.labels} onChange={(e) => setEditForm({ ...editForm, labels: e.target.value })} className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm" placeholder="Labels, comma-separated (adds new ones)" />
                      <div className="flex gap-2">
                        <button onClick={() => onUpdate(p.id, p.datasetId)} className="rounded-full bg-zinc-900 px-4 py-1.5 text-xs font-medium text-white hover:bg-zinc-800">Save</button>
                        <button onClick={() => setEditingId(null)} className="rounded-full border border-zinc-200 px-4 py-1.5 text-xs hover:bg-zinc-50">Cancel</button>
                      </div>
                    </div>
                  )}
                </Card>
              ))}
            </div>
          )}
        </div>
      </AppShell>
    </RequireAuth>
  );
}
