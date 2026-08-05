export default function AppShell({ children }: { children: React.ReactNode }) {
  return <div className="min-h-[calc(100vh-56px)] bg-zinc-50 px-6 py-6"><div className="mx-auto max-w-4xl">{children}</div></div>;
}
