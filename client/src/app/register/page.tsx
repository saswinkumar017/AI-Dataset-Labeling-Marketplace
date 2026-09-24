"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { friendlyAuthError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";

const RESEND_COOLDOWN_SECONDS = 60;

export default function RegisterPage() {
  const { requestOtp, verifyOtp } = useAuth();
  const router = useRouter();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [step, setStep] = useState<"details" | "otp">("details");
  const [error, setError] = useState("");
  const [info, setInfo] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [expiresIn, setExpiresIn] = useState(0);
  const [resendIn, setResendIn] = useState(0);
  const [showPassword, setShowPassword] = useState(false);
  useEffect(() => {
    if (step !== "otp") return;
    if (expiresIn <= 0 && resendIn <= 0) return;
    const timer = setTimeout(() => {
      if (expiresIn > 0) setExpiresIn((s) => s - 1);
      if (resendIn > 0) setResendIn((s) => s - 1);
    }, 1000);
    return () => clearTimeout(timer);
  }, [step, expiresIn, resendIn]);

  function formatCountdown(total: number) {
    const m = Math.floor(Math.max(0, total) / 60);
    const s = Math.max(0, total) % 60;
    return `${m}:${String(s).padStart(2, "0")}`;
  }

  function passwordProblem(pw: string): string | null {
    if (pw.length < 8) return "Password must be at least 8 characters.";
    if (!/[a-z]/.test(pw) || !/[A-Z]/.test(pw))
      return "Password must include both uppercase and lowercase letters.";
    if (!/\d/.test(pw)) return "Password must include a number.";
    if (!/[^A-Za-z0-9]/.test(pw)) return "Password must include a special character (e.g. !@#$).";
    return null;
  }

  async function onRequestOtp(e: React.FormEvent) {
    e.preventDefault();
    const pwProblem = passwordProblem(password);
    if (!name || !email.includes("@") || pwProblem) {
      setError(
        !name || !email.includes("@")
          ? "Please fill in your name and a valid email."
          : (pwProblem as string)
      );
      return;
    }
    setError("");
    setInfo("");
    setSubmitting(true);
    try {
      const expires = await requestOtp(name, email, password);
      setExpiresIn(expires);
      setResendIn(RESEND_COOLDOWN_SECONDS);
      setStep("otp");
      setInfo(`We emailed a 6-digit code to ${email}. Enter it below to finish creating your account.`);
    } catch (err) {
      setError(friendlyAuthError(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function onVerifyOtp(e: React.FormEvent) {
    e.preventDefault();
    if (!/^\d{6}$/.test(code)) {
      setError("Enter the 6-digit code from your email.");
      return;
    }
    setError("");
    setSubmitting(true);
    try {
      await verifyOtp(email, code);
      router.push("/login?registered=1");
    } catch (err) {
      setError(friendlyAuthError(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function onResend() {
    if (resendIn > 0 || submitting) return;
    setError("");
    setSubmitting(true);
    try {
      const expires = await requestOtp(name, email, password);
      setExpiresIn(expires);
      setResendIn(RESEND_COOLDOWN_SECONDS);
      setInfo(`A new code was sent to ${email}.`);
    } catch (err) {
      setError(friendlyAuthError(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-md px-6 py-12">
      <div className="rounded-2xl border border-zinc-200 bg-white p-6">
        <h1 className="text-lg font-semibold text-zinc-900">Create your account</h1>
        <p className="mt-1 text-sm text-zinc-500">
          {step === "details"
            ? "Join LabelMate and start labeling."
            : "Check your email for the verification code."}
        </p>

        {step === "details" ? (
          <form onSubmit={onRequestOtp} className="mt-6 space-y-4">
            <div>
              <label className="text-sm font-medium text-zinc-700">Name</label>
              <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Your Name" autoComplete="name" className="mt-1 w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-sm outline-none focus:border-zinc-900" />
            </div>
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

              <p className="mt-1 text-xs text-zinc-500">
                8+ characters with uppercase, lowercase, number and special character.
              </p>
            </div>
            {error && <div className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700 ring-1 ring-amber-200">{error}</div>}
            <button type="submit" disabled={submitting} className={`w-full rounded-full py-2.5 text-sm font-medium text-white ${submitting ? "bg-zinc-400" : "bg-zinc-900 hover:bg-zinc-800"}`}>
              {submitting ? "Sending code…" : "Send verification code"}
            </button>
          </form>
        ) : (
          <form onSubmit={onVerifyOtp} className="mt-6 space-y-4">
            {info && <div className="rounded-lg bg-emerald-50 px-3 py-2 text-xs text-emerald-700 ring-1 ring-emerald-200">{info}</div>}
            <div>
              <label className="text-sm font-medium text-zinc-700">6-digit code</label>
              <input
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
                inputMode="numeric"
                placeholder="123456"
                autoComplete="one-time-code"
                className="mt-1 w-full rounded-lg border border-zinc-200 bg-white px-3 py-2 text-center text-lg tracking-[0.5em] outline-none focus:border-zinc-900"
              />
              <p className="mt-1 text-xs text-zinc-500">
                {expiresIn > 0 ? `Code expires in ${formatCountdown(expiresIn)}.` : "Code expired — request a new one."}
              </p>
            </div>
            {error && <div className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700 ring-1 ring-amber-200">{error}</div>}
            <button type="submit" disabled={submitting} className={`w-full rounded-full py-2.5 text-sm font-medium text-white ${submitting ? "bg-zinc-400" : "bg-zinc-900 hover:bg-zinc-800"}`}>
              {submitting ? "Verifying…" : "Verify and create account"}
            </button>
            <div className="flex items-center justify-between text-sm">
              <button type="button" onClick={() => setStep("details")} className="text-zinc-600 underline">
                Edit details
              </button>
              <button
                type="button"
                onClick={onResend}
                disabled={resendIn > 0 || submitting}
                className={`font-medium ${resendIn > 0 ? "text-zinc-400" : "text-zinc-900 underline"}`}
              >
                {resendIn > 0 ? `Resend in ${resendIn}s` : "Resend code"}
              </button>
            </div>
          </form>
        )}

        <div className="mt-4 text-center text-sm text-zinc-600">
          Already have an account? <Link href="/login" className="font-medium text-zinc-900 underline">Sign in</Link>
        </div>
      </div>
    </div>
  );
}
