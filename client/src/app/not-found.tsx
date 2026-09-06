import Link from "next/link";

export default function NotFound() {
  return (
    <div className="mx-auto max-w-md px-6 py-16">
      <div className="rounded-2xl border border-zinc-200 bg-white p-6 text-center">
        <div className="text-sm font-semibold text-zinc-900">Page not found</div>
        <p className="mt-1 text-sm text-zinc-500">
          This address does not match anything in LabelMate. Head back to your work.
        </p>
        <div className="mt-4 flex justify-center gap-2">
          <Link
            href="/dashboard"
            className="rounded-full bg-zinc-900 px-4 py-2 text-xs font-medium text-white hover:bg-zinc-800"
          >
            Dashboard
          </Link>
          <Link
            href="/projects"
            className="rounded-full border border-zinc-200 px-4 py-2 text-xs hover:bg-zinc-50"
          >
            Projects
          </Link>
        </div>
      </div>
    </div>
  );
}
