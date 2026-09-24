"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { friendlyAuthError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";

export default function LoginPage() {
  const { login } = useAuth();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!email.includes("@") || password.length < 6) {
      setError("Please check your email and password — password needs at least 6 characters.");
      return;
    }
    setError("");
    setSubmitting(true);
    try {
      await login(email, password);
      router.push("/dashboard");
    } catch (err) {
      setError(friendlyAuthError(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-md px-6 py-12">
      <div className="rounded-2xl border border-zinc-200 bg-white p-6">
        <h1 className="text-lg font-semibold text-zinc-900">Welcome back</h1>
        <p className="mt-1 text-sm text-zinc-500">Sign in to continue your labeling work.</p>
        <form onSubmit={onSubmit} className="mt-6 space-y-4">
          <div>
            <label className="text-sm font-medium text-zinc-700">Email</label>
            <input value={email} onChange={(e) => setEmail(e.target.value)} type="email" placeholder="you@example.com" autoComplete="email" className="mt-1 w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm outline-none focus:border-zinc-900" />
          </div>
          <div>
            <label className="text-sm font-medium text-zinc-700">
              Password
            </label>

            <input
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              type={showPassword ? "text" : "password"}
              placeholder="••••••••"
              autoComplete="new-password"
              className="mt-1 w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm outline-none focus:border-zinc-900"
            />

            <label className="mt-2 flex items-center gap-2 text-xs text-zinc-600">
              <input
                type="checkbox"
                checked={showPassword}
                onChange={(e) => setShowPassword(e.target.checked)}
                className="h-4 w-4 rounded border-zinc-300"
              />
              Show password
            </label>

          </div>
          {error && <div className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700 ring-1 ring-amber-200">{error}</div>}
          <button type="submit" disabled={submitting} className={`w-full rounded-full py-2.5 text-sm font-medium text-white ${submitting ? "bg-zinc-400" : "bg-zinc-900 hover:bg-zinc-800"}`}>
            {submitting ? "Signing in…" : "Sign in"}
          </button>
        </form>
        <div className="mt-4 text-center text-sm text-zinc-600">
          New here? <Link href="/register" className="font-medium text-zinc-900 underline">Create an account</Link>
        </div>
      </div>
    </div>
  );
}
