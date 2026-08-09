"use client";
import { useState } from "react";
import AppShell from "@/components/AppShell";
import Badge from "@/components/Badge";
import Card from "@/components/Card";
import RequireAuth from "@/components/RequireAuth";
import { mockReviews } from "@/lib/mockData";

export default function ReviewPage() {
  const [items, setItems] = useState(mockReviews);
  const done = items.filter((i) => i.status === "APPROVED").length;

  function decide(id: string, decision: "APPROVED" | "REJECTED") {
    setItems((prev) => prev.map((r) => (r.id === id ? { ...r, status: decision } : r)));
  }

  return (
    <RequireAuth>
      <AppShell>
        <div className="mb-4">
          <h1 className="text-xl font-semibold text-zinc-900">Review queue</h1>
          <p className="text-sm text-zinc-500">Your judgment decides what becomes final — mock queue, real auth.</p>
          <div className="mt-2 text-xs text-zinc-500">{done} approved · {items.filter((i) => i.status === "PENDING").length} waiting</div>
        </div>
        <div className="space-y-4">
          {items.map((r) => (
            <Card key={r.id}>
              <div className="text-sm leading-6 text-zinc-900">“{r.text}”</div>
              <div className="mt-2 flex flex-wrap gap-2 text-xs">
                <span className="rounded-full bg-blue-50 px-2.5 py-1 text-blue-700 ring-1 ring-blue-200">Annotator: {r.annotator}</span>
                <span className="rounded-full bg-amber-50 px-2.5 py-1 text-amber-700 ring-1 ring-amber-200">AI: {r.aiSuggestion} {(r.aiConfidence * 100).toFixed(0)}%</span>
                <span className="rounded-full bg-zinc-900 px-2.5 py-1 text-white">Submitted: {r.submittedLabel}</span>
              </div>
              <div className="mt-3 flex items-center gap-2">
                <Badge tone={r.status === "APPROVED" ? "emerald" : r.status === "REJECTED" ? "amber" : "zinc"}>{r.status}</Badge>
                {r.status === "PENDING" && (
                  <>
                    <button onClick={() => decide(r.id, "APPROVED")} className="rounded-full bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-700">Approve</button>
                    <button onClick={() => decide(r.id, "REJECTED")} className="rounded-full border border-zinc-200 px-3 py-1.5 text-xs hover:bg-zinc-50">Reject</button>
                  </>
                )}
              </div>
            </Card>
          ))}
        </div>
      </AppShell>
    </RequireAuth>
  );
}
