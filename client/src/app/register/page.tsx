"use client";
import Link from "next/link";
import { useState } from "react";
import { setRole } from "@/lib/auth";

export default function RegisterPage() {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!name || !email.includes("@") || password.length < 6) {
      setError("Please fill in your name, a valid email, and a password with at least 6 characters.");
      return;
    }
    setError("");
    setRole("annotator");
    window.location.href = "/annotate";
  }

  return (
    <div className="mx-auto max-w-md px-6 py-12">
      <div className="rounded-2xl border border-zinc-200 bg-white p-6">
        <h1 className="text-lg font-semibold text-zinc-900">Create your account</h1>
        <p className="mt-1 text-sm text-zinc-500">Join LabelMate and start labeling.</p>
        <form onSubmit={onSubmit} className="mt-6 space-y-4">
          <div>
            <label className="text-sm font-medium text-zinc-700">Name</label>
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Asha Kumar" className="mt-1 w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm outline-none focus:border-zinc-900" />
          </div>
          <div>
            <label className="text-sm font-medium text-zinc-700">Email</label>
            <input value={email} onChange={(e) => setEmail(e.target.value)} type="email" placeholder="you@example.com" className="mt-1 w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm outline-none focus:border-zinc-900" />
          </div>
          <div>
            <label className="text-sm font-medium text-zinc-700">Password</label>
            <input value={password} onChange={(e) => setPassword(e.target.value)} type="password" placeholder="••••••••" className="mt-1 w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm outline-none focus:border-zinc-900" />
          </div>
          {error && <div className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700 ring-1 ring-amber-200">{error}</div>}
          <button type="submit" className="w-full rounded-full bg-zinc-900 py-2.5 text-sm font-medium text-white hover:bg-zinc-800">Create account</button>
        </form>
        <div className="mt-4 text-center text-sm text-zinc-600">
          Already have an account? <Link href="/login" className="font-medium text-zinc-900 underline">Sign in</Link>
        </div>
      </div>
    </div>
  );
}
