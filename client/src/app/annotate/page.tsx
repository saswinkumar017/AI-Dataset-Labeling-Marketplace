"use client";
import { useState } from "react";
import AppShell from "@/components/AppShell";
import Card from "@/components/Card";
import Badge from "@/components/Badge";
import Progress from "@/components/Progress";
import { mockAnnotationItems } from "@/lib/mockData";

export default function AnnotatePage() {
  const [index, setIndex] = useState(0);
  const [selected, setSelected] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);
  const item = mockAnnotationItems[index];

  const ai = item.aiSuggestion;
  const confidence = item.aiConfidence;

  function acceptAi() {
    setSelected(ai);
  }
  function submit() {
    if (!selected) return;
    setSubmitted(true);
    setTimeout(() => {
      setSubmitted(false);
      setSelected(null);
      setIndex((i) => (i + 1) % mockAnnotationItems.length);
    }, 500);
  }

  return (
    <AppShell>
      <div className="mb-4">
        <h1 className="text-xl font-semibold text-zinc-900">Label this item</h1>
        <p className="text-sm text-zinc-500">Read the text, choose the label that fits best. The AI suggestion is just to help.</p>
      </div>

      <div className="mb-4">
        <div className="flex justify-between text-xs text-zinc-500">
          <span>Item {index + 1} of {mockAnnotationItems.length}</span><span>{Math.round(((index) / mockAnnotationItems.length) * 100)}% done</span>
        </div>
        <div className="mt-1"><Progress value={((index) / mockAnnotationItems.length) * 100} /></div>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <Card className="md:col-span-2">
          <div className="text-xs font-medium text-zinc-500">What do you see?</div>
          <div className="mt-2 rounded-lg border border-zinc-200 bg-zinc-50 p-4 text-sm leading-6 text-zinc-900">
            “{item.text}”
          </div>
          <div className="mt-4 flex gap-2">
            <button onClick={() => setIndex((i) => (i - 1 + mockAnnotationItems.length) % mockAnnotationItems.length)} className="rounded-full border border-zinc-200 px-4 py-1.5 text-xs hover:bg-zinc-50">Previous</button>
            <button onClick={() => setIndex((i) => (i + 1) % mockAnnotationItems.length)} className="rounded-full border border-zinc-200 px-4 py-1.5 text-xs hover:bg-zinc-50">Next</button>
          </div>
        </Card>

        <Card>
          <div className="text-xs font-medium text-zinc-500">AI suggestion</div>
          <div className="mt-2 rounded-lg border border-amber-200 bg-amber-50 p-3">
            <div className="text-sm font-medium text-amber-900">{ai} <span className="text-xs font-normal text-amber-700">· {(confidence * 100).toFixed(0)}% sure</span></div>
            <div className="text-xs text-amber-700">It might help, but your call matters most.</div>
          </div>
          <button onClick={acceptAi} className="mt-3 w-full rounded-full border border-amber-200 bg-white px-3 py-1.5 text-xs font-medium hover:bg-amber-50">Use this suggestion</button>
          <div className="mt-4 text-xs font-medium text-zinc-900">Pick a label</div>
          <div className="mt-2 flex flex-wrap gap-2">
            {item.labels.map((l) => (
              <button key={l} onClick={() => setSelected(l)} className={`rounded-full px-3 py-1.5 text-xs font-medium ring-1 ${selected === l ? "bg-zinc-900 text-white ring-zinc-900" : "bg-white text-zinc-700 ring-zinc-200 hover:bg-zinc-50"}`}>
                {l}
              </button>
            ))}
          </div>
          <div className="mt-4">
            <div className="text-xs text-zinc-500">You chose: <span className="font-medium text-zinc-900">{selected ?? "—"}</span></div>
            <button onClick={submit} disabled={!selected} className={`mt-2 w-full rounded-full px-4 py-2 text-sm font-medium ${selected ? "bg-zinc-900 text-white hover:bg-zinc-800" : "bg-zinc-100 text-zinc-400"}`}>
              {submitted ? "Saved" : "Save label"}
            </button>
            <div className="mt-2 text-xs text-zinc-500">Your choice will be reviewed before it counts as final.</div>
          </div>
        </Card>
      </div>

      <div className="mt-4 flex items-center gap-2">
        <Badge tone="emerald">You decide</Badge>
        <span className="text-xs text-zinc-500">The AI helps, but only your label is saved.</span>
      </div>
    </AppShell>
  );
}
