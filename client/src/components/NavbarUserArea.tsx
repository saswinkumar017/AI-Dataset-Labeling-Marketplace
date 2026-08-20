"use client";

import Link from "next/link";
import { useAuth } from "@/lib/auth-context";

export default function NavbarUserArea() {
  const { user, loading, logout } = useAuth();

  if (loading) return <div className="text-sm text-zinc-400">…</div>;

  if (!user) {
    return (
      <div className="flex items-center gap-2">
        <Link href="/login" className="rounded-full px-4 py-1.5 text-sm font-medium text-zinc-600 hover:bg-zinc-100">Login</Link>
        <Link href="/register" className="rounded-full bg-zinc-900 px-4 py-1.5 text-sm font-medium text-white hover:bg-zinc-800">Get Started</Link>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-3">
      <Link href="/dashboard" className="hidden text-sm text-zinc-600 hover:text-zinc-900 md:block">Dashboard</Link>
      <Link href="/datasets" className="hidden text-sm font-medium text-zinc-900 hover:text-zinc-700 md:block">Datasets</Link>
      <Link href="/projects" className="hidden text-sm text-zinc-600 hover:text-zinc-900 md:block">Projects</Link>
      <Link href="/annotate" className="hidden text-sm text-zinc-600 hover:text-zinc-900 md:block">Annotate</Link>
      <span className="hidden rounded-full bg-zinc-100 px-3 py-1 text-xs font-medium text-zinc-700 sm:block">
        {user.username} · {user.role}
      </span>
      <button onClick={logout} className="rounded-full border border-zinc-200 px-4 py-1.5 text-sm font-medium hover:bg-zinc-50">
        Logout
      </button>
    </div>
  );
}
