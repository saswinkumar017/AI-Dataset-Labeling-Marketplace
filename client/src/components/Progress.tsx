export default function Progress({ value }: { value: number }) {
  return (
    <div className="h-2 w-full overflow-hidden rounded-full bg-zinc-100">
      <div className="h-full bg-zinc-900" style={{ width: `${Math.min(100, Math.max(0, value))}%` }} />
    </div>
  );
}
