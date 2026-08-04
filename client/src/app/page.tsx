export default function Home() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-zinc-50 px-6 py-16 dark:bg-zinc-950">
      <main className="flex w-full max-w-2xl flex-col items-center gap-6 rounded-2xl bg-white px-8 py-12 shadow-sm ring-1 ring-zinc-200 dark:bg-zinc-900 dark:ring-zinc-800">
        <div className="flex flex-col items-center gap-2 text-center">
          <h1 className="text-4xl font-bold tracking-tight text-zinc-900 dark:text-zinc-50">
            LabelMate
          </h1>
          <p className="text-lg font-medium text-zinc-600 dark:text-zinc-400">
            AI Dataset Labeling Marketplace
          </p>
        </div>
        <div className="h-px w-full bg-zinc-200 dark:bg-zinc-800" />
        <p className="rounded-full bg-emerald-50 px-4 py-2 text-sm font-medium text-emerald-700 ring-1 ring-emerald-200 dark:bg-emerald-950/30 dark:text-emerald-300 dark:ring-emerald-900">
          Frontend initialized successfully.
        </p>
        <p className="max-w-md text-center text-sm leading-6 text-zinc-500 dark:text-zinc-400">
          Next.js 16 · React 19 · TypeScript · Tailwind CSS 4 · Axios
          <br />
          Minimal runnable frontend — product UI will be built on top of this foundation.
        </p>
      </main>
    </div>
  );
}
