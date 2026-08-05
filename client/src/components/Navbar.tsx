import Link from "next/link";

export default function Navbar() {
  return (
    <header className="sticky top-0 z-10 border-b border-zinc-200 bg-white">
      <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-6">
        <Link href="/" className="flex items-center gap-2 font-semibold tracking-tight text-zinc-900">
          <span className="flex h-7 w-7 items-center justify-center rounded bg-zinc-900 text-sm font-bold text-white">L</span>
          LabelMate
        </Link>
        <div className="flex items-center gap-2">
          <Link href="/login" className="rounded-full px-4 py-1.5 text-sm font-medium text-zinc-600 hover:bg-zinc-100">Login</Link>
          <Link href="/register" className="rounded-full bg-zinc-900 px-4 py-1.5 text-sm font-medium text-white hover:bg-zinc-800">Get Started</Link>
        </div>
      </div>
    </header>
  );
}
