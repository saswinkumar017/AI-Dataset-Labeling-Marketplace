"use client";
import { useEffect, useState } from "react";
import AppShell from "@/components/AppShell";
import Card from "@/components/Card";
import Badge from "@/components/Badge";
import RequireAuth from "@/components/RequireAuth";
import { createProject, deleteProject, friendlyProjectError, listProjects, updateProject, type ProjectResponse } from "@/lib/api";

type FormState = {
  datasetId: string;
  name: string;
  instructions: string;
  labelType: string;
};

const emptyForm: FormState = { datasetId: "", name: "", instructions: "", labelType: "" };

function statusTone(status: ProjectResponse["status"]): "zinc" | "emerald" | "blue" {
  if (status === "COMPLETED") return "emerald";
  if (status === "IN_PROGRESS") return "blue";
  return "zinc";
}

export default function ProjectsPage() {
  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [submitting, setSubmitting] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editForm, setEditForm] = useState<FormState>(emptyForm);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const data = await listProjects();
      setProjects(data);
    } catch (err) {
      setError(friendlyProjectError(err));
    } finally {
      setLoading(false);
    }
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
    return () => {
      cancelled = true;
    };
  }, []);

  function toPayload(formState: FormState) {
    return {
      datasetId: Number(formState.datasetId),
      name: formState.name.trim(),
      instructions: formState.instructions.trim() || null,
      labelType: formState.labelType.trim() || null,
    };
  }

  function validate(formState: FormState): string | null {
    if (!formState.datasetId.trim() || !Number.isInteger(Number(formState.datasetId)) || Number(formState.datasetId) <= 0) {
      return "Dataset ID must be a positive whole number. Find it on the Datasets page.";
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
              <label className="text-xs font-medium text-zinc-700">Dataset ID *</label>
              <input value={form.datasetId} onChange={(e) => setForm({ ...form, datasetId: e.target.value })} placeholder="e.g. 1" type="number" min="1" step="1" className="mt-1 w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-900" />
              <div className="mt-1 text-xs text-zinc-500">The ID of one of your datasets — see the Datasets page.</div>
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
            <button type="submit" disabled={submitting} className={`rounded-full px-5 py-2 text-sm font-medium text-white ${submitting ? "bg-zinc-400" : "bg-zinc-900 hover:bg-zinc-800"}`}>
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
                      <div className="text-xs text-zinc-500">{p.status} · Dataset #{p.datasetId} · {new Date(p.createdAt).toLocaleDateString()}</div>
                      {p.instructions && <div className="mt-1 text-xs text-zinc-600 line-clamp-2">{p.instructions}</div>}
                    </div>
                    <Badge tone={statusTone(p.status)}>{p.status}</Badge>
                  </div>
                  {p.labelType && (
                    <div className="mt-3 rounded-lg bg-zinc-50 p-2 text-xs text-zinc-600">
                      <div>labelType: {p.labelType}</div>
                    </div>
                  )}
                  <div className="mt-3 flex flex-wrap gap-2">
                    <button onClick={() => setExpandedId(expandedId === p.id ? null : p.id)} className="rounded-full border border-zinc-200 px-3 py-1 text-xs hover:bg-zinc-50">
                      {expandedId === p.id ? "Hide" : "View"}
                    </button>
                    <button
                      onClick={() => {
                        setEditingId(p.id);
                        setEditForm({ datasetId: String(p.datasetId), name: p.name, instructions: p.instructions ?? "", labelType: p.labelType ?? "" });
                      }}
                      className="rounded-full border border-zinc-200 px-3 py-1 text-xs hover:bg-zinc-50"
                    >
                      Edit
                    </button>
                    <button onClick={() => onDelete(p.id)} className="rounded-full border border-red-200 bg-white px-3 py-1 text-xs text-red-600 hover:bg-red-50">
                      Delete
                    </button>
                  </div>
                  {expandedId === p.id && (
                    <div className="mt-3 rounded-lg border border-zinc-200 bg-zinc-50 p-3 text-xs">
                      <div>ID: {p.id}</div>
                      <div>Dataset ID: {p.datasetId}</div>
                      <div>Status: {p.status}</div>
                      {p.labelType && <div>Label type: {p.labelType}</div>}
                      {p.instructions && <div className="mt-1">Instructions: {p.instructions}</div>}
                      <div>Created: {p.createdAt}</div>
                      {p.updatedAt && <div>Updated: {p.updatedAt}</div>}
                    </div>
                  )}
                  {editingId === p.id && (
                    <div className="mt-3 space-y-2 rounded-lg border border-zinc-200 p-3">
                      <div className="text-xs text-zinc-500">Dataset ID: {p.datasetId} (cannot be changed)</div>
                      <input value={editForm.name} onChange={(e) => setEditForm({ ...editForm, name: e.target.value })} className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm" placeholder="Name" maxLength={150} />
                      <textarea value={editForm.instructions} onChange={(e) => setEditForm({ ...editForm, instructions: e.target.value })} className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm" placeholder="Instructions" rows={2} maxLength={2000} />
                      <input value={editForm.labelType} onChange={(e) => setEditForm({ ...editForm, labelType: e.target.value })} className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm" placeholder="Label type" maxLength={50} />
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
