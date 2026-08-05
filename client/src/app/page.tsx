import Link from "next/link";

export default function LandingPage() {
  return (
    <div className="bg-white">
      <section className="mx-auto max-w-5xl px-6 py-20">
        <div className="mx-auto max-w-2xl text-center">
          <h1 className="text-4xl font-bold tracking-tight text-zinc-900 md:text-5xl">
            Label data with confidence.
          </h1>
          <p className="mx-auto mt-4 max-w-xl text-base leading-6 text-zinc-600">
            LabelMate helps you turn raw text into high-quality labeled data. Read each item, pick the best label, and know that your judgment shapes the final dataset.
          </p>
          <div className="mt-8 flex justify-center gap-3">
            <Link href="/register" className="rounded-full bg-zinc-900 px-6 py-2.5 text-sm font-medium text-white hover:bg-zinc-800">Get started</Link>
            <Link href="/login" className="rounded-full border border-zinc-200 px-6 py-2.5 text-sm font-medium hover:bg-zinc-50">Sign in</Link>
          </div>
        </div>
      </section>

      <section className="border-y border-zinc-200 bg-zinc-50 py-10">
        <div className="mx-auto max-w-5xl px-6">
          <div className="grid gap-3 md:grid-cols-3">
            <div className="rounded-xl border border-zinc-200 bg-white p-4 text-center">
              <div className="text-sm font-semibold text-zinc-900">Read</div>
              <div className="text-xs text-zinc-500">Each task shows one item to label</div>
            </div>
            <div className="rounded-xl border border-zinc-200 bg-white p-4 text-center">
              <div className="text-sm font-semibold text-zinc-900">Choose</div>
              <div className="text-xs text-zinc-500">Pick the label that fits best</div>
            </div>
            <div className="rounded-xl border border-zinc-200 bg-white p-4 text-center">
              <div className="text-sm font-semibold text-zinc-900">Help from AI</div>
              <div className="text-xs text-zinc-500">Suggestions are just a starting point</div>
            </div>
          </div>
          <div className="mt-3 text-center text-xs text-zinc-500">You are always the final decision maker.</div>
        </div>
      </section>

      <section className="mx-auto max-w-5xl px-6 py-10">
        <div className="rounded-2xl border border-zinc-200 bg-white p-6">
          <h3 className="text-sm font-semibold text-zinc-900">How it works</h3>
          <p className="mt-2 text-sm leading-6 text-zinc-600">Open an item, see what the AI suggests, and decide. If the suggestion looks right, accept it. If not, choose a better label. Your work directly improves the dataset.</p>
          <div className="mt-4 flex flex-wrap gap-2">
            <span className="rounded-full bg-zinc-100 px-3 py-1 text-xs text-zinc-700 ring-1 ring-zinc-200">Human choice</span>
            <span className="rounded-full bg-amber-50 px-3 py-1 text-xs text-amber-700 ring-1 ring-amber-200">AI suggestion</span>
            <span className="rounded-full bg-emerald-50 px-3 py-1 text-xs text-emerald-700 ring-1 ring-emerald-200">Verified</span>
          </div>
        </div>
      </section>
    </div>
  );
}
