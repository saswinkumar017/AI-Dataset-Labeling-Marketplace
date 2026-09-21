"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import AppShell from "@/components/AppShell";
import Card from "@/components/Card";
import Badge from "@/components/Badge";
import RequireAuth from "@/components/RequireAuth";
import { addDatasetItems, apiBaseUrl, createDataset, deleteDataset, friendlyDatasetError, getDatasetColumns, listDatasetItemsPaged, listDatasets, updateDataset, uploadDatasetFile, uploadDatasetImages, uploadTableCsv, type DatasetItemResponse, type DatasetResponse, type PagedResponse } from "@/lib/api";

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
  const [uploadingFile, setUploadingFile] = useState(false);
  const [ingestOpenId, setIngestOpenId] = useState<number | null>(null);
  const [ingestText, setIngestText] = useState("");
  const [ingestBusy, setIngestBusy] = useState(false);
  const [ingestNotice, setIngestNotice] = useState<string | null>(null);
  const [ingestColumns, setIngestColumns] = useState<string[]>([]);
  const [itemsPage, setItemsPage] = useState(0);
  const [itemsData, setItemsData] = useState<PagedResponse<DatasetItemResponse> | null>(null);
  const [itemsLoading, setItemsLoading] = useState(false);

  const ITEMS_PAGE_SIZE = 10;

  async function openIngest(dataset: DatasetResponse) {
    if (ingestOpenId === dataset.id) {
      setIngestOpenId(null);
      return;
    }
    setIngestOpenId(dataset.id);
    setIngestText("");
    setIngestNotice(null);
    setItemsPage(0);
    setItemsData(null);
    setIngestColumns(dataset.columns ?? []);
    setError(null);
    setItemsLoading(true);
    try {
      const [page, cols] = await Promise.all([
        listDatasetItemsPaged(dataset.id, 0, ITEMS_PAGE_SIZE),
        getDatasetColumns(dataset.id).catch(() => ({ datasetId: dataset.id, columns: dataset.columns ?? [] })),
      ]);
      setItemsData(page);
      setIngestColumns(cols.columns);
    } catch (err) {
      setError(friendlyDatasetError(err));
    } finally {
      setItemsLoading(false);
    }
  }

  async function gotoItemsPage(datasetId: number, page: number) {
    if (itemsLoading) return;
    setItemsLoading(true);
    try {
      setItemsData(await listDatasetItemsPaged(datasetId, page, ITEMS_PAGE_SIZE));
      setItemsPage(page);
    } catch (err) {
      setError(friendlyDatasetError(err));
    } finally {
      setItemsLoading(false);
    }
  }

  async function refreshIngest(dataset: DatasetResponse) {
    try {
      const [page, cols, all] = await Promise.all([
        listDatasetItemsPaged(dataset.id, itemsPage, ITEMS_PAGE_SIZE).catch(() => null),
        getDatasetColumns(dataset.id).catch(() => null),
        listDatasets().catch(() => null),
      ]);
      if (page) setItemsData(page);
      if (cols) setIngestColumns(cols.columns);
      if (all) {
        setDatasets(all);
        const updated = all.find((d) => d.id === dataset.id);
        if (updated) setIngestColumns(updated.columns ?? cols?.columns ?? []);
      }
    } catch {
      // Best-effort refresh; the explicit notices below carry the outcome.
    }
  }

  async function onAddTextItems(dataset: DatasetResponse) {
    const lines = ingestText.split(/\r?\n/).map((l) => l.trim()).filter((l) => l.length > 0);
    if (lines.length === 0 || ingestBusy) return;
    setIngestBusy(true);
    setIngestNotice(null);
    setError(null);
    try {
      const saved = await addDatasetItems(dataset.id, lines.slice(0, 5000));
      setIngestText("");
      setIngestNotice(`${saved.length} item${saved.length === 1 ? "" : "s"} added.`);
      await refreshIngest(dataset);
    } catch (err) {
      setError(friendlyDatasetError(err));
    } finally {
      setIngestBusy(false);
    }
  }

  async function onTableCsv(dataset: DatasetResponse, file: File | undefined) {
    if (!file || ingestBusy) return;
    setIngestBusy(true);
    setIngestNotice(null);
    setError(null);
    try {
      const result = await uploadTableCsv(dataset.id, file);
      setIngestNotice(
        `${result.inserted} row${result.inserted === 1 ? "" : "s"} added (${result.totalItems} total). Columns: ${result.columns.join(", ")}`
      );
      await refreshIngest(dataset);
    } catch (err) {
      setError(friendlyDatasetError(err));
    } finally {
      setIngestBusy(false);
    }
  }

  async function onImages(dataset: DatasetResponse, files: File[]) {
    if (files.length === 0 || ingestBusy) return;
    setIngestBusy(true);
    setIngestNotice(null);
    setError(null);
    try {
      const saved = await uploadDatasetImages(dataset.id, files.slice(0, 20));
      setIngestNotice(`${saved.length} image${saved.length === 1 ? "" : "s"} added.`);
      await refreshIngest(dataset);
    } catch (err) {
      setError(friendlyDatasetError(err));
    } finally {
      setIngestBusy(false);
    }
  }

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
    // State updates live in promise callbacks (not in the effect body) so the
    // initial fetch complies with react-hooks/set-state-in-effect.
    let cancelled = false;
    listDatasets()
      .then((data) => {
        if (cancelled) return;
        setDatasets(data);
        setLoading(false);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(friendlyDatasetError(err));
        setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function handleCsvFile(e: React.ChangeEvent<HTMLInputElement>, target: "create" | "edit") {
    const file = e.target.files?.[0];
    if (!file) return;
    setCsvError(null);
    const lower = file.name.toLowerCase();
    if (!lower.endsWith(".csv") && !lower.endsWith(".txt")) {
      setCsvError("Please select a .csv or .txt file.");
      e.target.value = "";
      return;
    }
    if (file.size > 50 * 1024 * 1024) {
      setCsvError("File too large — max 50 MB.");
      e.target.value = "";
      return;
    }
    // Store the bytes first: the server validates, saves the file under
    // uploads/, and returns the metadata the dataset record persists. The
    // form keeps working offline-style when the upload fails.
    setUploadingFile(true);
    try {
      const stored = await uploadDatasetFile(file);
      const patch = {
        fileName: stored.fileName,
        filePath: stored.filePath,
        fileSizeBytes: stored.fileSizeBytes.toString(),
        checksumSha256: stored.checksumSha256,
      };
      if (target === "create") {
        setForm((prev) => ({ ...prev, ...patch }));
        setShowMeta(true);
      } else {
        setEditForm((prev) => ({ ...prev, ...patch }));
      }
    } catch {
      setCsvError("Upload failed — is the backend running? You can still enter file details manually.");
      const fallback = {
        fileName: file.name,
        filePath: `uploads/${file.name}`,
        fileSizeBytes: file.size.toString(),
      };
      if (target === "create") {
        setForm((prev) => ({ ...prev, ...fallback }));
        setShowMeta(true);
      } else {
        setEditForm((prev) => ({ ...prev, ...fallback }));
      }
    } finally {
      setUploadingFile(false);
      e.target.value = "";
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
              <label className="text-xs font-medium text-zinc-700">Upload dataset file (optional)</label>
              <input type="file" accept=".csv,.txt" onChange={(e) => handleCsvFile(e, "create")} className="mt-1 block w-full text-sm text-zinc-600 file:mr-3 file:rounded-full file:border file:border-zinc-200 file:bg-white file:px-3 file:py-1 file:text-xs file:font-medium hover:file:bg-zinc-50" />
              <div className="mt-1 text-xs text-zinc-500">CSV or TXT · Max 50 MB · The file is stored on the server; fileName, filePath, fileSizeBytes, and checksum fill in automatically.</div>
              {uploadingFile && <div className="mt-1 text-xs text-zinc-500">Uploading file…</div>}
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
                      <div className="text-xs text-zinc-500">{d.status} · {d.itemCount} item{d.itemCount === 1 ? "" : "s"} · {new Date(d.createdAt).toLocaleDateString()}</div>
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
                    <button onClick={() => openIngest(d)} className="rounded-full border border-zinc-200 px-3 py-1 text-xs hover:bg-zinc-50">
                      {ingestOpenId === d.id ? "Hide data" : "Ingest data"}
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
                  {ingestOpenId === d.id && (
                    <div className="mt-3 space-y-3 rounded-lg border border-zinc-200 bg-zinc-50 p-3 text-xs">
                      {ingestNotice && (
                        <div role="status" className="rounded-lg bg-emerald-50 px-3 py-2 text-xs text-emerald-700 ring-1 ring-emerald-200">
                          {ingestNotice}{" "}
                          <Link href="/projects" className="underline hover:text-emerald-900">
                            Go to Projects to generate labeling tasks →
                          </Link>
                        </div>
                      )}
                      {ingestColumns.length > 0 && (
                        <div>
                          <span className="font-semibold text-zinc-900">Columns: </span>
                          <span className="text-zinc-600">{ingestColumns.join(", ")}</span>
                        </div>
                      )}
                      <div>
                        <div className="font-semibold text-zinc-900">Text items (one per line)</div>
                        <textarea
                          value={ingestText}
                          onChange={(e) => setIngestText(e.target.value)}
                          placeholder={"First item to label\nSecond item to label"}
                          rows={3}
                          maxLength={500000}
                          className="mt-1 w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm outline-none focus:border-zinc-900"
                        />
                        <button
                          onClick={() => onAddTextItems(d)}
                          disabled={ingestBusy || ingestText.trim().length === 0}
                          className={`mt-1 rounded-full px-4 py-1.5 text-xs font-medium text-white ${ingestBusy || ingestText.trim().length === 0 ? "bg-zinc-400" : "bg-zinc-900 hover:bg-zinc-800"}`}
                        >
                          {ingestBusy ? "Adding…" : "Add items"}
                        </button>
                      </div>
                      <div className="flex flex-wrap items-center gap-2">
                        <label className="cursor-pointer rounded-full border border-zinc-200 bg-white px-4 py-1.5 text-xs hover:bg-zinc-50">
                          Upload table CSV
                          <input
                            type="file"
                            accept=".csv,.txt"
                            className="hidden"
                            onChange={(e) => {
                              void onTableCsv(d, e.target.files?.[0]);
                              e.target.value = "";
                            }}
                          />
                        </label>
                        <label className="cursor-pointer rounded-full border border-zinc-200 bg-white px-4 py-1.5 text-xs hover:bg-zinc-50">
                          Upload images
                          <input
                            type="file"
                            accept=".jpg,.jpeg,.png,.webp,.gif"
                            multiple
                            className="hidden"
                            onChange={(e) => {
                              void onImages(d, Array.from(e.target.files ?? []));
                              e.target.value = "";
                            }}
                          />
                        </label>
                        {ingestBusy && <span className="text-zinc-500">Working…</span>}
                      </div>
                      <div>
                        <div className="font-semibold text-zinc-900">
                          Items {itemsData ? `(${itemsData.totalElements})` : ""}
                        </div>
                        {itemsLoading ? (
                          <div className="mt-1 text-zinc-500">Loading items…</div>
                        ) : itemsData && itemsData.content.length > 0 ? (
                          <>
                            <ul className="mt-1 space-y-1">
                              {itemsData.content.map((item) => (
                                <li key={item.id} className="rounded-lg border border-zinc-200 bg-white p-2">
                                  {item.imageUrl && (
                                    <img
                                      src={`${apiBaseUrl()}${item.imageUrl}`}
                                      alt={item.content}
                                      className="mb-1 max-h-24 rounded"
                                      loading="lazy"
                                    />
                                  )}
                                  <div className="truncate text-zinc-900">{item.content}</div>
                                  {Object.keys(item.rowData).length > 0 && (
                                    <div className="mt-1 text-zinc-500">
                                      {Object.entries(item.rowData).slice(0, 4).map(([k, v]) => (
                                        <span key={k} className="mr-2">{k}: {String(v).slice(0, 40)}</span>
                                      ))}
                                    </div>
                                  )}
                                </li>
                              ))}
                            </ul>
                            <div className="mt-2 flex items-center gap-2">
                              <button
                                onClick={() => gotoItemsPage(d.id, itemsPage - 1)}
                                disabled={itemsPage === 0 || itemsLoading}
                                className="rounded-full border border-zinc-200 bg-white px-3 py-1 text-xs hover:bg-zinc-50 disabled:opacity-40"
                              >
                                ← Prev
                              </button>
                              <span className="text-zinc-500">Page {itemsPage + 1} of {Math.max(itemsData.totalPages, 1)}</span>
                              <button
                                onClick={() => gotoItemsPage(d.id, itemsPage + 1)}
                                disabled={itemsPage + 1 >= itemsData.totalPages || itemsLoading}
                                className="rounded-full border border-zinc-200 bg-white px-3 py-1 text-xs hover:bg-zinc-50 disabled:opacity-40"
                              >
                                Next →
                              </button>
                            </div>
                          </>
                        ) : (
                          <div className="mt-1 text-zinc-500">No items yet — ingest above to fill this dataset.</div>
                        )}
                      </div>
                    </div>
                  )}
                  {editingId === d.id && (
                    <div className="mt-3 space-y-2 rounded-lg border border-zinc-200 p-3">
                      <input value={editForm.name} onChange={(e) => setEditForm({ ...editForm, name: e.target.value })} className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm" placeholder="Name" />
                      <textarea value={editForm.description} onChange={(e) => setEditForm({ ...editForm, description: e.target.value })} className="w-full rounded-lg border border-zinc-200 px-3 py-2 text-sm" placeholder="Description" rows={2} />
                      <div className="rounded-lg border border-dashed border-zinc-300 bg-zinc-50 p-2">
                        <label className="text-xs font-medium text-zinc-700">Replace dataset file (optional)</label>
                        <input type="file" accept=".csv,.txt" onChange={(e) => handleCsvFile(e, "edit")} className="mt-1 block w-full text-sm text-zinc-600 file:mr-2 file:rounded-full file:border file:border-zinc-200 file:bg-white file:px-2 file:py-1 file:text-xs" />
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
