export type UserRole = "annotator" | "admin";

export function getRole(): UserRole | null {
  if (typeof window === "undefined") return null;
  return (localStorage.getItem("labelmate_role") as UserRole | null);
}

export function setRole(role: UserRole) {
  localStorage.setItem("labelmate_role", role);
  localStorage.setItem("labelmate_auth", "1");
}

export function isAuthed(): boolean {
  if (typeof window === "undefined") return false;
  return localStorage.getItem("labelmate_auth") === "1";
}

export function logout() {
  localStorage.removeItem("labelmate_auth");
  localStorage.removeItem("labelmate_role");
}
