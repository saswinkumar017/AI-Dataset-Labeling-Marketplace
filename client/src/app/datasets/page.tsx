"use client";
import { useEffect, useState } from "react";
import AppShell from "@/components/AppShell";
import Card from "@/components/Card";
import Badge from "@/components/Badge";
import RequireAuth from "@/components/RequireAuth";
import { createDataset, deleteDataset, friendlyDatasetError, listDatasets, updateDataset, type DatasetResponse } from "@/lib/api";

type FormState = {
  name: string;
  description: string;
  fileName: string;
  filePath: string;
  fileSizeBytes: string;
  checksumSha256: string;
};

const emptyForm: FormState = { name: "", description: "", fileName: "", filePath: "", fileSizeBytes: "", checksumSha256: "" };

export default function DatasetsPage() {
  const [datasets, setDatasets] = useState<DatasetResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [showMeta, setShowMeta] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editForm, setEditForm] = useState<FormState>(emptyForm);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [csvError, setCsvError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const data = await listDatasets();
      setDatasets(data);
    } catch (err) {
      setError(friendlyDatasetError(err));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  function handleCsvFile(e: React.ChangeEvent<HTMLInputElement>, target: "create" | "edit") {
    const file = e.target.files?.[0];
    if (!file) return;
    setCsvError(null);
    if (!file.name.toLowerCase().endsWith(".csv")) {
      setCsvError("Please select a CSV file (.csv).");
      return;
    }
    if (file.size > 50 * 1024 * 1024) {
      setCsvError("CSV too large — max 50 MB.");
      return;
    }
    const fileName = file.name;
    const filePath = `uploads/${fileName}`;
    const fileSize = file.size.toString();
    if (target === "create") {
      setForm((prev) => ({ ...prev, fileName, filePath, fileSizeBytes: fileSize }));
      setShowMeta(true);
    } else {
      setEditForm((prev) => ({ ...prev, fileName, filePath, fileSizeBytes: fileSize }));
    }
  }

  function toPayload(formState: FormState) {
    return {
      name: formState.name.trim(),
      description: formState.description.trim() || null,
      fileName: formState.fileName.trim() || null,
      filePath: formState.filePath.trim() || null,
      fileSizeBytes: formState.fileSizeBytes.trim() ? Number(formState.fileSizeBytes) : null,
      checksumSha256: formState.checksumSha256.trim() || null,
    };
  }

  async function onCreate(e: React.FormEvent) {
    e.preventDefault();
    if (form.name.trim().length < 2 || form.name.trim().length > 150) {
      setError("Name must be between 2 and 150 characters.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await createDataset(toPayload(form));
      setForm(emptyForm);
      setShowMeta(false);
      setCsvError(null);
      await load();
    } catch (err) {
      setError(friendlyDatasetError(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function onUpdate(id: number) {
    if (editForm.name.trim().length < 2) {
      setError("Name must be between 2 and 150 characters.");
      return;
    }
    setError(null);
    try {
      await updateDataset(id, toPayload(editForm));
      setEditingId(null);
      await load();
    } catch (err) {
      setError(friendlyDatasetError(err));
    }
  }

  async function onDelete(id: number) {
    if (!confirm("Delete this dataset? This cannot be undone.")) return;
    setError(null);
    try {
      await deleteDataset(id);
      await load();
    } catch (err) {
      setError(friendlyDatasetError(err));
    }
  }

  return (
    <RequireAuth>
      <AppShell>
        <div className="mb-6">
          <h1 className="text-xl font-semibold text-zinc-900">Datasets</h1>
          <p className="text-sm text-zinc-500">Create a dataset — upload a CSV file or enter details manually.</p>
        </div>

        <Card>
          <h2 className="text-sm font-semibold text-zinc-900">Create dataset</h2>
          <form onSubmit={onCreate} className="mt-4 space-y-3">
            <div>
              <label className="text-xs font-medium text-zinc-700">Name *</label>
              <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Product Reviews" className="mt-1 w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-900" maxLength={150} />
            </div>
            <div>
              <label className="text-xs font-medium text-zinc-700">Description</label>
              <textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="Optional — what this dataset is for" rows={2} className="mt-1 w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-900" maxLength={2000} />
            </div>

            <div className="rounded-lg border border-dashed border-zinc-300 bg-zinc-50 p-3">
              <label className="text-xs font-medium text-zinc-700">Upload CSV file (optional)</label>
              <input type="file" accept=".csv" onChange={(e) => handleCsvFile(e, "create")} className="mt-1 block w-full text-sm text-zinc-600 file:mr-3 file:rounded-full file:border file:border-zinc-200 file:bg-white file:px-3 file:py-1 file:text-xs file:font-medium hover:file:bg-zinc-50" />
              <div className="mt-1 text-xs text-zinc-500">CSV only · Max 50 MB · Selecting a file auto-fills fileName, filePath, and fileSizeBytes below.</div>
              {csvError && <div className="mt-2 text-xs text-red-600">{csvError}</div>}
            </div>

            <button type="button" onClick={() => setShowMeta((v) => !v)} className="text-xs font-medium text-zinc-600 hover:text-zinc-900">
              {showMeta ? "Hide" : "Show"} file details — fileName, filePath, fileSizeBytes, checksumSha256
            </button>
            {showMeta && (
              <div className="grid gap-3 rounded-lg border border-zinc-200 bg-zinc-50 p-3 md:grid-cols-2">
                <div>
                  <label className="text-xs font-medium text-zinc-700">fileName</label>
                  <input value={form.fileName} onChange={(e) => setForm({ ...form, fileName: e.target.value })} placeholder="reviews.csv (no slash)" className="mt-1 w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm outline-none focus:border-zinc-900" maxLength={255} />
                  <div className="mt-1 text-xs text-zinc-500">Plain file name, no / or \</div>
                </div>
                <div>
                  <label className="text-xs font-medium text-zinc-700">filePath</label>
                  <input value={form.filePath} onChange={(e) => setForm({ ...form, filePath: e.target.value })} placeholder="uploads/reviews.csv (relative, no ..)" className="mt-1 w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm outline-none focus:border-zinc-900" maxLength={500} />
                  <div className="mt-1 text-xs text-zinc-500">Relative path, no parent refs</div>
                </div>
                <div>
                  <label className="text-xs font-medium text-zinc-700">fileSizeBytes</label>
                  <input value={form.fileSizeBytes} onChange={(e) => setForm({ ...form, fileSizeBytes: e.target.value })} placeholder="1024" type="number" min="0" className="mt-1 w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm outline-none focus:border-zinc-900" />
                </div>
                <div>
                  <label className="text-xs font-medium text-zinc-700">checksumSha256</label>
                  <input value={form.checksumSha256} onChange={(e) => setForm({ ...form, checksumSha256: e.target.value })} placeholder="64 lowercase hex" className="mt-1 w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm outline-none focus:border-zinc-900" maxLength={64} />
                  <div className="mt-1 text-xs text-zinc-500">64 lowercase hex chars</div>
                </div>
              </div>
            )}
            <button type="submit" disabled={submitting} className={`rounded-full px-5 py-2 text-sm font-medium text-white ${submitting ? "bg-zinc-400" : "bg-zinc-900 hover:bg-zinc-800"}`}>
              {submitting ? "Creating…" : "Create dataset"}
            </button>
          </form>
        </Card>

        {error && <div className="mt-4 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700 ring-1 ring-amber-200">{error}</div>}

        <div className="mt-6">
          {loading ? (
            <div className="text-sm text-zinc-500">Loading datasets…</div>
          ) : datasets.length === 0 ? (
            <Card>
              <div className="text-sm font-medium text-zinc-900">No datasets yet</div>
              <p className="mt-1 text-sm text-zinc-500">Upload a CSV above or create your first dataset to get started.</p>
            </Card>
          ) : (
            <div className="grid gap-4 md:grid-cols-2">
              {datasets.map((d) => (
                <Card key={d.id}>
                  <div className="flex items-start justify-between">
                    <div>
                      <div className="text-sm font-semibold text-zinc-900">{d.name}</div>
                      <div className="text-xs text-zinc-500">{d.status} · {new Date(d.createdAt).toLocaleDateString()}</div>
                      {d.description && <div className="mt-1 text-xs text-zinc-600 line-clamp-2">{d.description}</div>}
                    </div>
                    <Badge tone="emerald">{d.status}</Badge>
                  </div>
                  {(d.fileName || d.filePath || d.fileSizeBytes !== null || d.checksumSha256) && (
                    <div className="mt-3 rounded-lg bg-zinc-50 p-2 text-xs text-zinc-600">
                      {d.fileName && <div>fileName: {d.fileName}</div>}
                      {d.filePath && <div>filePath: {d.filePath}</div>}
                      {d.fileSizeBytes !== null && <div>fileSizeBytes: {d.fileSizeBytes}</div>}
                      {d.checksumSha256 && <div className="break-all">checksum: {d.checksumSha256}</div>}
                    </div>
                  )}
                  <div className="mt-3 flex flex-wrap gap-2">
                    <button onClick={() => setExpandedId(expandedId === d.id ? null : d.id)} className="rounded-full border border-zinc-200 px-3 py-1 text-xs hover:bg-zinc-50">
                      {expandedId === d.id ? "Hide" : "View"}
                    </button>
                    <button
                      onClick={() => {
                        setEditingId(d.id);
                        setEditForm({
                          name: d.name,
                          description: d.description ?? "",
                          fileName: d.fileName ?? "",
                          filePath: d.filePath ?? "",
                          fileSizeBytes: d.fileSizeBytes?.toString() ?? "",
                          checksumSha256: d.checksumSha256 ?? "",
                        });
                      }}
                      className="rounded-full border border-zinc-200 px-3 py-1 text-xs hover:bg-zinc-50"
                    >
                      Edit
                    </button>
                    <button onClick={() => onDelete(d.id)} className="rounded-full border border-red-200 bg-white px-3 py-1 text-xs text-red-600 hover:bg-red-50">
                      Delete
                    </button>
                  </div>
                  {expandedId === d.id && (
                    <div className="mt-3 rounded-lg border border-zinc-200 bg-zinc-50 p-3 text-xs">
                      <div>ID: {d.id}</div>
                      <div>Created: {d.createdAt}</div>
                      {d.updatedAt && <div>Updated: {d.updatedAt}</div>}
                    </div>
                  )}
                  {editingId === d.id && (
                    <div className="mt-3 space-y-2 rounded-lg border border-zinc-200 p-3">
                      <input value={editForm.name} onChange={(e) => setEditForm({ ...editForm, name: e.target.value })} className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm" placeholder="Name" />
                      <textarea value={editForm.description} onChange={(e) => setEditForm({ ...editForm, description: e.target.value })} className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm" placeholder="Description" rows={2} />
                      <div className="rounded-lg border border-dashed border-zinc-300 bg-zinc-50 p-2">
                        <label className="text-xs font-medium text-zinc-700">Replace CSV file (optional)</label>
                        <input type="file" accept=".csv" onChange={(e) => handleCsvFile(e, "edit")} className="mt-1 block w-full text-sm text-zinc-600 file:mr-2 file:rounded-full file:border file:border-zinc-200 file:bg-white file:px-2 file:py-1 file:text-xs" />
                        {csvError && <div className="mt-1 text-xs text-red-600">{csvError}</div>}
                      </div>
                      <input value={editForm.fileName} onChange={(e) => setEditForm({ ...editForm, fileName: e.target.value })} className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm" placeholder="fileName" />
                      <input value={editForm.filePath} onChange={(e) => setEditForm({ ...editForm, filePath: e.target.value })} className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm" placeholder="filePath" />
                      <input value={editForm.fileSizeBytes} onChange={(e) => setEditForm({ ...editForm, fileSizeBytes: e.target.value })} className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm" placeholder="fileSizeBytes" type="number" />
                      <input value={editForm.checksumSha256} onChange={(e) => setEditForm({ ...editForm, checksumSha256: e.target.value })} className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm" placeholder="checksumSha256" />
                      <div className="flex gap-2">
                        <button onClick={() => onUpdate(d.id)} className="rounded-full bg-zinc-900 px-4 py-1.5 text-xs font-medium text-white hover:bg-zinc-800">Save</button>
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
