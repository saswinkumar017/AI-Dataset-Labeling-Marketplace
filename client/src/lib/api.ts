import axios from "axios";

const baseURL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export const api = axios.create({
  baseURL,
  headers: { "Content-Type": "application/json" },
  timeout: 10000,
});

api.interceptors.request.use((config) => {
  if (typeof window !== "undefined") {
    const token = localStorage.getItem("labelmate_token");
    if (token) {
      config.headers = config.headers ?? {};
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401 && typeof window !== "undefined") {
      const url: string = error?.config?.url ?? "";
      if (!url.includes("/api/auth/login") && !url.includes("/api/auth/register")) {
        localStorage.removeItem("labelmate_token");
        localStorage.removeItem("labelmate_user");
        if (window.location.pathname !== "/login") {
          window.location.href = "/login";
        }
      }
    }
    return Promise.reject(error);
  }
);

export type BackendUser = {
  id: number;
  email: string;
  username: string;
  role: "ADMIN" | "ANNOTATOR";
  createdAt: string;
};

export type AuthResponse = {
  token: string;
  tokenType: string;
  user: BackendUser;
};

export type DatasetResponse = {
  id: number;
  name: string;
  description: string | null;
  status: "UPLOADING" | "READY" | "FAILED";
  fileName: string | null;
  filePath: string | null;
  fileSizeBytes: number | null;
  checksumSha256: string | null;
  createdAt: string;
  updatedAt: string | null;
};

export async function registerRequest(username: string, email: string, password: string) {
  const res = await api.post<BackendUser>("/api/auth/register", { username, email, password });
  return res.data;
}

export async function loginRequest(email: string, password: string) {
  const res = await api.post<AuthResponse>("/api/auth/login", { email, password });
  return res.data;
}

export async function meRequest() {
  const res = await api.get<BackendUser>("/api/auth/me");
  return res.data;
}

export async function listDatasets() {
  const res = await api.get<DatasetResponse[]>("/api/datasets");
  return res.data;
}

export async function createDataset(data: {
  name: string;
  description?: string | null;
  fileName?: string | null;
  filePath?: string | null;
  fileSizeBytes?: number | null;
  checksumSha256?: string | null;
}) {
  const res = await api.post<DatasetResponse>("/api/datasets", data);
  return res.data;
}

export async function getDataset(id: number) {
  const res = await api.get<DatasetResponse>(`/api/datasets/${id}`);
  return res.data;
}

export async function updateDataset(
  id: number,
  data: {
    name: string;
    description?: string | null;
    fileName?: string | null;
    filePath?: string | null;
    fileSizeBytes?: number | null;
    checksumSha256?: string | null;
  }
) {
  const res = await api.put<DatasetResponse>(`/api/datasets/${id}`, data);
  return res.data;
}

export async function deleteDataset(id: number) {
  await api.delete(`/api/datasets/${id}`);
}

export function friendlyAuthError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    const backendMessage = (error.response?.data as { error?: string } | undefined)?.error;
    if (status === 409) return "An account with this email already exists. Try signing in.";
    if (status === 401) return "Incorrect email or password. Please try again.";
    if (status === 400) return backendMessage ?? "Please check your details and try again.";
    if (error.code === "ECONNABORTED") return "Request timed out. Please try again.";
    if (error.message === "Network Error") return "Cannot reach the server. Is the backend running on port 8080?";
    return backendMessage ?? "Something went wrong. Please try again.";
  }
  return "Something went wrong. Please try again.";
}

export function friendlyDatasetError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    const backendMessage = (error.response?.data as { error?: string } | undefined)?.error;
    if (status === 401) return "Session expired. Please sign in again.";
    if (status === 404) return "Dataset not found or you do not have access.";
    if (status === 400) return backendMessage ?? "Please check the dataset details.";
    if (error.code === "ECONNABORTED") return "Request timed out. Please try again.";
    if (error.message === "Network Error") return "Cannot reach the server. Is the backend running on port 8080?";
    return backendMessage ?? "Something went wrong. Please try again.";
  }
  return "Something went wrong. Please try again.";
}
