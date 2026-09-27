"use client";
import { useEffect, useMemo, useState } from "react";
import { AdminShell } from "../AdminShell";
import Badge from "@/components/Badge";
import Card from "@/components/Card";
import { friendlyAdminError, listAdminUsers, updateAdminUserRole, type BackendUser } from "@/lib/api";

export default function AdminUsersPage() {
  const [users, setUsers] = useState<BackendUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [roleFilter, setRoleFilter] = useState<"ALL" | "ADMIN" | "ANNOTATOR">("ALL");
  const [actionError, setActionError] = useState<string | null>(null);
  const [actingId, setActingId] = useState<number | null>(null);

  useEffect(() => {
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

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return users.filter((u) => {
      if (roleFilter !== "ALL" && u.role !== roleFilter) return false;
      if (!q) return true;
      return u.email.toLowerCase().includes(q) || u.username.toLowerCase().includes(q);
    });
  }, [users, query, roleFilter]);

  async function changeRole(u: BackendUser, role: "ADMIN" | "ANNOTATOR") {
    setActionError(null);
    setActingId(u.id);
    try {
      const updated = await updateAdminUserRole(u.id, role);
      setUsers((prev) => prev.map((p) => (p.id === u.id ? updated : p)));
    } catch (err) {
      setActionError(friendlyAdminError(err));
    } finally {
      setActingId(null);
    }
  }

  return (
    <AdminShell title="Users" subtitle="Every account on the platform — promote admins here. No public admin registration.">
      <div className="mb-3 flex flex-wrap gap-2">
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search email or username…"
          className="w-full max-w-xs rounded-full border border-zinc-200 bg-white px-4 py-2 text-xs outline-none focus:border-zinc-400"
        />
        {(["ALL", "ADMIN", "ANNOTATOR"] as const).map((r) => (
          <button
            key={r}
            onClick={() => setRoleFilter(r)}
            className={`rounded-full border px-3 py-1 text-xs ${roleFilter === r ? "border-zinc-900 bg-zinc-900 text-white" : "border-zinc-200 hover:bg-zinc-50"}`}
          >
            {r === "ALL" ? "All" : r === "ADMIN" ? "Admins" : "Annotators"}
          </button>
        ))}
      </div>
      {actionError && (
        <Card className="mb-3">
          <p className="text-sm text-red-600">{actionError}</p>
        </Card>
      )}
      {loading ? (
        <div className="text-sm text-zinc-500">Loading users…</div>
      ) : error ? (
        <Card>
          <div className="text-sm font-medium text-zinc-900">Users unavailable</div>
          <p className="mt-1 text-sm text-zinc-500">{error}</p>
        </Card>
      ) : filtered.length === 0 ? (
        <Card>
          <div className="text-sm font-medium text-zinc-900">No users match</div>
          <p className="mt-1 text-sm text-zinc-500">Accounts appear here as soon as people register.</p>
        </Card>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-zinc-200 bg-white">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-zinc-200 bg-zinc-50 text-[11px] uppercase tracking-wider text-zinc-500">
                <th className="px-4 py-3">User</th>
                <th className="px-4 py-3">Email</th>
                <th className="px-4 py-3">Role</th>
                <th className="px-4 py-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-100">
              {filtered.map((u) => (
                <tr key={u.id} className="hover:bg-zinc-50">
                  <td className="px-4 py-2 font-medium text-zinc-900">{u.username}</td>
                  <td className="px-4 py-2 text-xs text-zinc-500">{u.email}</td>
                  <td className="px-4 py-2">
                    <Badge tone={u.role === "ADMIN" ? "emerald" : "zinc"}>{u.role}</Badge>
                  </td>
                  <td className="px-4 py-2 text-right">
                    {u.role === "ADMIN" ? (
                      <button
                        disabled={actingId === u.id}
                        onClick={() => changeRole(u, "ANNOTATOR")}
                        className="rounded-full border border-zinc-200 px-3 py-1 text-xs hover:bg-zinc-50 disabled:opacity-50"
                      >
                        Demote
                      </button>
                    ) : (
                      <button
                        disabled={actingId === u.id}
                        onClick={() => changeRole(u, "ADMIN")}
                        className="rounded-full bg-zinc-900 px-3 py-1 text-xs font-medium text-white hover:bg-zinc-800 disabled:opacity-50"
                      >
                        Make admin
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </AdminShell>
  );
}
