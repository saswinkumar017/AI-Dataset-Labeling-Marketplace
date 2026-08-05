import Link from "next/link";

export default function Sidebar() {
  return (
    <div className="rounded-lg border border-zinc-200 bg-white p-3 text-xs text-zinc-500">
      Annotator Panel · <Link href="/annotate" className="font-medium text-zinc-900 underline">Go to Annotation</Link>
    </div>
  );
}
