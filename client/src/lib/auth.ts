export type StoredUser = {
  id: number;
  email: string;
  username: string;
  role: "ADMIN" | "ANNOTATOR";
  createdAt: string;
};

const TOKEN_KEY = "labelmate_token";
const USER_KEY = "labelmate_user";

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function getStoredUser(): StoredUser | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as StoredUser) : null;
  } catch {
    return null;
  }
}

export function clearAuthStorage() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}
