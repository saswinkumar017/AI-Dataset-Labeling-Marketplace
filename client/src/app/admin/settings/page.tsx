"use client";
import { useEffect, useState } from "react";
import { AdminShell } from "../AdminShell";
import Card from "@/components/Card";
import { friendlyAdminError, getAdminAiSettings, updateAdminAiSettings } from "@/lib/api";

export default function AdminSettingsPage() {
  const [apiKey, setApiKey] = useState("");
  const [keyPlaceholder, setKeyPlaceholder] = useState("");
  const [model, setModel] = useState("");
  const [baseUrl, setBaseUrl] = useState("");
  const [enabled, setEnabled] = useState(false);
  const [chatModel, setChatModel] = useState("openai");
  const [live, setLive] = useState(false);
  const [beanActive, setBeanActive] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getAdminAiSettings()
      .then((data) => {
        if (cancelled) return;
        setKeyPlaceholder(data.apiKeyMasked);
        setModel(data.model);
        setBaseUrl(data.baseUrl);
        setEnabled(data.enabled);
        setChatModel(data.chatModel);
        setLive(data.live);
        setBeanActive(data.beanActive);
        setLoading(false);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(friendlyAdminError(err));
        setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function save() {
    setNotice(null);
    setError(null);
    setSaving(true);
    try {
      const data = await updateAdminAiSettings({
        apiKey: apiKey.trim() ? apiKey.trim() : undefined,
        model: model.trim(),
        baseUrl: baseUrl.trim(),
        enabled,
        chatModel: chatModel.trim(),
      });
      setKeyPlaceholder(data.apiKeyMasked);
      setApiKey("");
      setLive(data.live);
      setBeanActive(data.beanActive);
      setNotice(
        data.live
          ? "Saved. AI is live — key, model and on/off apply immediately."
          : "Saved. Not live yet — see status above."
      );
    } catch (err) {
      setError(friendlyAdminError(err));
    } finally {
      setSaving(false);
    }
  }

  const inputCls = "w-full rounded-xl border border-zinc-200 bg-white px-3 py-2 text-sm outline-none focus:border-zinc-400";

  return (
    <AdminShell title="AI settings" subtitle="Provider key and model. Key is stored masked and never shown raw.">
      {loading ? (
        <div className="text-sm text-zinc-500">Loading AI settings…</div>
      ) : (
        <Card>
          {error && <p className="mb-3 text-sm text-red-600">{error}</p>}
          {notice && <p className="mb-3 text-sm text-emerald-700">{notice}</p>}
          <div className={`mb-4 rounded-xl px-3 py-2 text-sm ${live ? "bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200" : "bg-amber-50 text-amber-700 ring-1 ring-amber-200"}`}>
            {live
              ? "● AI assistance is live."
              : beanActive
                ? "○ AI bean is up but not live — turn it on and save a key below."
                : "○ AI bean is off — set APP_AI_ENABLED=true and SPRING_AI_MODEL_CHAT=openai in server/.env, then restart the backend once."}
          </div>
          <label className="block text-xs font-medium text-zinc-700">Provider API key</label>
          <input
            type="password"
            value={apiKey}
            onChange={(e) => setApiKey(e.target.value)}
            placeholder={keyPlaceholder || "Not configured"}
            className={`${inputCls} mt-1`}
            autoComplete="off"
          />
          <p className="mt-1 text-xs text-zinc-500">Leave blank to keep the stored key.</p>
          <label className="mt-4 block text-xs font-medium text-zinc-700">Model</label>
          <input value={model} onChange={(e) => setModel(e.target.value)} className={`${inputCls} mt-1`} />
          <label className="mt-4 block text-xs font-medium text-zinc-700">Base URL</label>
          <input value={baseUrl} onChange={(e) => setBaseUrl(e.target.value)} className={`${inputCls} mt-1`} />
          <label className="mt-4 block text-xs font-medium text-zinc-700">Chat backend (needs restart)</label>
          <input value={chatModel} onChange={(e) => setChatModel(e.target.value)} placeholder="openai" className={`${inputCls} mt-1`} />
          <p className="mt-1 text-xs text-zinc-500">Use <span className="font-mono">openai</span> for any OpenAI-compatible provider. <span className="font-mono">none</span> disables the AI bean.</p>
          <label className="mt-4 flex items-center gap-2 text-sm text-zinc-700">
            <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
            AI assistance enabled
          </label>
          <button
            onClick={save}
            disabled={saving}
            className="mt-4 rounded-full bg-zinc-900 px-4 py-2 text-xs font-medium text-white hover:bg-zinc-800 disabled:opacity-50"
          >
            {saving ? "Saving…" : "Save AI settings"}
          </button>
        </Card>
      )}
    </AdminShell>
  );
}
