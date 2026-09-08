"use client";
import { useEffect, useState } from "react";
import { AdminShell } from "../AdminShell";
import Badge from "@/components/Badge";
import Card from "@/components/Card";
import { friendlyAdminError, listAdminUsers, type BackendUser } from "@/lib/api";

export default function AdminUsersPage() {
  const [users, setUsers] = useState<BackendUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // State updates live in promise callbacks (not in the effect body) so the
    // initial fetch complies with react-hooks/set-state-in-effect.
    let cancelled = false;
    listAdminUsers()
      .then((data) => {
        if (cancelled) return;
        setUsers(data);
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

  return (
    <AdminShell title="Users" subtitle="Every account on the platform — identity and role only.">
      {loading ? (
        <div className="text-sm text-zinc-500">Loading users…</div>
      ) : error ? (
        <Card>
          <div className="text-sm font-medium text-zinc-900">Users unavailable</div>
          <p className="mt-1 text-sm text-zinc-500">{error}</p>
        </Card>
      ) : users.length === 0 ? (
        <Card>
          <div className="text-sm font-medium text-zinc-900">No users found</div>
          <p className="mt-1 text-sm text-zinc-500">Accounts appear here as soon as people register.</p>
        </Card>
      ) : (
        <Card>
          <ul className="divide-y divide-zinc-100">
            {users.map((u) => (
              <li key={u.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
                <span>
                  <span className="font-medium text-zinc-900">{u.username}</span>
                  <span className="ml-2 text-xs text-zinc-500">{u.email}</span>
                </span>
                <Badge tone={u.role === "ADMIN" ? "emerald" : "zinc"}>{u.role}</Badge>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </AdminShell>
  );
}
