"use client";
import { useEffect, useState } from "react";
import { AdminShell } from "./AdminShell";
import Card from "@/components/Card";
import { adminOverview, friendlyAdminError, type AdminOverview } from "@/lib/api";

export default function AdminOverviewPage() {
  const [overview, setOverview] = useState<AdminOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // State updates live in promise callbacks (not in the effect body) so the
    // initial fetch complies with react-hooks/set-state-in-effect.
    let cancelled = false;
    adminOverview()
      .then((data) => {
        if (cancelled) return;
        setOverview(data);
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

  const stats: Array<[string, number]> = overview
    ? [
        ["Users", overview.userCount],
        ["Datasets", overview.datasetCount],
        ["Projects", overview.projectCount],
        ["Tasks", overview.taskCount],
        ["Annotations", overview.annotationCount],
        ["Reviews", overview.reviewCount],
        ["Approved", overview.reviewsApproved],
        ["Rejected", overview.reviewsRejected],
        ["AI suggestions", overview.suggestionCount],
      ]
    : [];

  return (
    <AdminShell title="Platform overview" subtitle="Live totals across the whole LabelMate workflow.">
      {loading ? (
        <div className="text-sm text-zinc-500">Loading platform totals…</div>
      ) : error ? (
        <Card>
          <div className="text-sm font-medium text-zinc-900">Overview unavailable</div>
          <p className="mt-1 text-sm text-zinc-500">{error}</p>
        </Card>
      ) : (
        <div className="grid grid-cols-2 gap-3 md:grid-cols-3">
          {stats.map(([label, value]) => (
            <div key={label} className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm">
              <div className="text-2xl font-semibold text-zinc-900">{value}</div>
              <div className="mt-1 text-xs text-zinc-500">{label}</div>
            </div>
          ))}
        </div>
      )}
    </AdminShell>
  );
}
