import axios from "axios";

/**
 * Backend base URL. It must come from the environment (client/.env.local,
 * see client/.env.example) — there is intentionally no hardcoded fallback,
 * so a misconfigured deployment fails with a clear message instead of
 * silently calling the wrong server. The check runs per request (not at
 * module load) so unit tests importing these helpers need no live backend.
 */
function resolveBaseUrl(): string {
  const url = process.env.NEXT_PUBLIC_API_URL;
  if (!url) {
    throw new Error(
      "NEXT_PUBLIC_API_URL is not set. Copy client/.env.example to client/.env.local and set it (e.g. http://localhost:8080)."
    );
  }
  return url;
}

/** Absolute backend origin, for building links the browser fetches directly (e.g. stored images). */
export function apiBaseUrl(): string {
  return resolveBaseUrl().replace(/\/$/, "");
}

export const api = axios.create({
  headers: { "Content-Type": "application/json" },
  timeout: 10000,
});

api.interceptors.request.use((config) => {
  config.baseURL = resolveBaseUrl();
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
  columns: string[];
  itemCount: number;
  createdAt: string;
  updatedAt: string | null;
};

export type ProjectResponse = {
  id: number;
  datasetId: number;
  name: string;
  instructions: string | null;
  labelType: string | null;
  labels: string[];
  status: "DRAFT" | "IN_PROGRESS" | "COMPLETED";
  totalTasks: number;
  pendingTasks: number;
  submittedTasks: number;
  approvedTasks: number;
  rejectedTasks: number;
  createdAt: string;
  updatedAt: string | null;
};

export type ProjectPayload = {
  datasetId: number;
  name: string;
  instructions?: string | null;
  labelType?: string | null;
  labels?: string[] | null;
};

export async function registerRequest(username: string, email: string, password: string) {
  const res = await api.post<BackendUser>("/api/auth/register", { username, email, password });
  return res.data;
}

export type OtpSendResponse = {
  message: string;
  expiresInSeconds: number;
};

export async function requestRegistrationOtp(username: string, email: string, password: string) {
  const res = await api.post<OtpSendResponse>("/api/auth/register/request-otp", {
    username,
    email,
    password,
  });
  return res.data;
}

export async function verifyRegistrationOtp(email: string, code: string) {
  const res = await api.post<BackendUser>("/api/auth/register/verify-otp", { email, code });
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

export type FileUploadResponse = {
  fileName: string;
  filePath: string;
  fileSizeBytes: number;
  checksumSha256: string;
};

export async function uploadDatasetFile(file: File) {
  const form = new FormData();
  form.append("file", file);
  const res = await api.post<FileUploadResponse>("/api/datasets/upload", form, {
    headers: { "Content-Type": "multipart/form-data" },
    timeout: 60000,
  });
  return res.data;
}

export type DatasetItemResponse = {
  id: number;
  datasetId: number;
  content: string;
  rowData: Record<string, string>;
  imageUrl: string | null;
  mediaType: string | null;
  createdAt: string;
};

export type TableIngestResult = {
  datasetId: number;
  columns: string[];
  inserted: number;
  totalItems: number;
};

export type PagedResponse<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export async function addDatasetItems(datasetId: number, contents: string[]) {
  const res = await api.post<DatasetItemResponse[]>(`/api/datasets/${datasetId}/items`, { contents }, {
    timeout: 120000,
  });
  return res.data;
}

export async function listDatasetItems(datasetId: number) {
  const res = await api.get<DatasetItemResponse[]>(`/api/datasets/${datasetId}/items`);
  return res.data;
}

export async function listDatasetItemsPaged(datasetId: number, page: number, size: number) {
  const res = await api.get<PagedResponse<DatasetItemResponse>>(`/api/datasets/${datasetId}/items/paged`, {
    params: { page, size },
  });
  return res.data;
}

export async function getDatasetColumns(datasetId: number) {
  const res = await api.get<{ datasetId: number; columns: string[] }>(
    `/api/datasets/${datasetId}/columns`
  );
  return res.data;
}

export async function addTableRows(
  datasetId: number,
  data: { columns: string[]; rows: Record<string, string>[] }
) {
  const res = await api.post<TableIngestResult>(`/api/datasets/${datasetId}/table-rows`, data, {
    timeout: 120000,
  });
  return res.data;
}

export async function uploadTableCsv(datasetId: number, file: File) {
  const form = new FormData();
  form.append("file", file);
  const res = await api.post<TableIngestResult>(`/api/datasets/${datasetId}/table-upload`, form, {
    headers: { "Content-Type": "multipart/form-data" },
    timeout: 120000,
  });
  return res.data;
}

export async function uploadDatasetImages(datasetId: number, files: File[], captions?: string[]) {
  const form = new FormData();
  files.forEach((file) => form.append("files", file));
  (captions ?? []).forEach((caption) => form.append("captions", caption));
  const res = await api.post<DatasetItemResponse[]>(`/api/datasets/${datasetId}/images`, form, {
    headers: { "Content-Type": "multipart/form-data" },
    timeout: 120000,
  });
  return res.data;
}

export async function listProjects() {
  const res = await api.get<ProjectResponse[]>("/api/projects");
  return res.data;
}

export async function createProject(data: ProjectPayload) {
  const res = await api.post<ProjectResponse>("/api/projects", data);
  return res.data;
}

export async function getProject(id: number) {
  const res = await api.get<ProjectResponse>(`/api/projects/${id}`);
  return res.data;
}

export async function updateProject(id: number, data: ProjectPayload) {
  const res = await api.put<ProjectResponse>(`/api/projects/${id}`, data);
  return res.data;
}

export async function deleteProject(id: number) {
  await api.delete(`/api/projects/${id}`);
}

export function friendlyAuthError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    const backendMessage = (error.response?.data as { error?: string } | undefined)?.error;
    if (status === 409) return "An account with this email already exists. Try signing in.";
    if (status === 401) return "Incorrect email or password. Please try again.";
    if (status === 429) return backendMessage ?? "Too many attempts. Please wait a minute and try again.";
    if (status === 400) return backendMessage ?? "Please check your details and try again.";
    if (error.code === "ECONNABORTED") return "Request timed out. Please try again.";
    if (error.message === "Network Error") return "Cannot reach the server. Is the backend running and is this page origin allowed (CORS)?";
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
    if (error.message === "Network Error") return "Cannot reach the server. Is the backend running and is this page origin allowed (CORS)?";
    return backendMessage ?? "Something went wrong. Please try again.";
  }
  return "Something went wrong. Please try again.";
}

export function friendlyProjectError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    const backendMessage = (error.response?.data as { error?: string } | undefined)?.error;
    if (status === 401) return "Session expired. Please sign in again.";
    if (status === 404) return "Project not found or you do not have access.";
    if (status === 400) return backendMessage ?? "Please check the project details.";
    if (error.code === "ECONNABORTED") return "Request timed out. Please try again.";
    if (error.message === "Network Error") return "Cannot reach the server. Is the backend running and is this page origin allowed (CORS)?";
    return backendMessage ?? "Something went wrong. Please try again.";
  }
  return "Something went wrong. Please try again.";
}

export type TaskStatus =
  | "PENDING"
  | "ASSIGNED"
  | "IN_PROGRESS"
  | "SUBMITTED"
  | "APPROVED"
  | "REJECTED";

export type TaskResponse = {
  id: number;
  projectId: number;
  datasetId: number;
  itemIndex: number | null;
  itemData: string | null;
  status: TaskStatus;
  assignedToId: number | null;
  assignedToEmail: string | null;
  projectName: string | null;
  item: DatasetItemResponse | null;
  createdAt: string;
  updatedAt: string | null;
};

export type AnnotationResponse = {
  id: number;
  taskId: number;
  projectId: number;
  label: string;
  labelId: number | null;
  source: "HUMAN" | "AI" | "HUMAN_APPROVED";
  confidence: number | null;
  content: string | null;
  createdAt: string;
  updatedAt: string | null;
};

export async function listProjectTasks(projectId: number, status?: TaskStatus) {
  const res = await api.get<TaskResponse[]>(`/api/projects/${projectId}/tasks`, {
    params: status ? { status } : {},
  });
  return res.data;
}

export async function createTask(projectId: number, data: { itemData?: string | null; itemIndex?: number | null }) {
  const res = await api.post<TaskResponse>(`/api/projects/${projectId}/tasks`, data);
  return res.data;
}

export async function createTasksBulk(projectId: number, items: string[]) {
  const res = await api.post<TaskResponse[]>(`/api/projects/${projectId}/tasks/bulk`, { items });
  return res.data;
}

export async function generateProjectTasks(projectId: number) {
  const res = await api.post<TaskResponse[]>(`/api/projects/${projectId}/tasks/generate`, {}, {
    timeout: 120000,
  });
  return res.data;
}

export async function listAssignedTasks() {
  const res = await api.get<TaskResponse[]>("/api/tasks/assigned");
  return res.data;
}

export async function getTask(taskId: number) {
  const res = await api.get<TaskResponse>(`/api/tasks/${taskId}`);
  return res.data;
}

export async function assignTask(taskId: number, assigneeEmail: string) {
  const res = await api.post<TaskResponse>(`/api/tasks/${taskId}/assign`, { assigneeEmail });
  return res.data;
}

export async function startTask(taskId: number) {
  const res = await api.post<TaskResponse>(`/api/tasks/${taskId}/start`, {});
  return res.data;
}

export async function submitTask(taskId: number) {
  const res = await api.post<TaskResponse>(`/api/tasks/${taskId}/submit`, {});
  return res.data;
}

export async function listTaskAnnotations(taskId: number) {
  const res = await api.get<AnnotationResponse[]>("/api/annotations", { params: { taskId } });
  return res.data;
}

export async function listProjectAnnotations(projectId: number) {
  const res = await api.get<AnnotationResponse[]>("/api/annotations", { params: { projectId } });
  return res.data;
}

export async function createAnnotation(data: { taskId: number; label: string }) {
  const res = await api.post<AnnotationResponse>("/api/annotations", data);
  return res.data;
}

export async function updateAnnotation(id: number, data: { label: string }) {
  const res = await api.put<AnnotationResponse>(`/api/annotations/${id}`, data);
  return res.data;
}

export async function deleteAnnotation(id: number) {
  await api.delete(`/api/annotations/${id}`);
}

export function friendlyTaskError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    const backendMessage = (error.response?.data as { error?: string } | undefined)?.error;
    if (status === 401) return "Session expired. Please sign in again.";
    if (status === 404) return "Task or project not found, or you do not have access.";
    if (status === 400) return backendMessage ?? "Please check the task details.";
    if (error.code === "ECONNABORTED") return "Request timed out. Please try again.";
    if (error.message === "Network Error") return "Cannot reach the server. Is the backend running and is this page origin allowed (CORS)?";
    return backendMessage ?? "Something went wrong. Please try again.";
  }
  return "Something went wrong. Please try again.";
}

export function friendlyAnnotationError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    const backendMessage = (error.response?.data as { error?: string } | undefined)?.error;
    if (status === 401) return "Session expired. Please sign in again.";
    if (status === 404) return "Annotation or task not found, or you do not have access.";
    if (status === 409) return backendMessage ?? "This item can no longer be annotated.";
    if (status === 400) return backendMessage ?? "Please check the annotation and try again.";
    if (error.code === "ECONNABORTED") return "Request timed out. Please try again.";
    if (error.message === "Network Error") return "Cannot reach the server. Is the backend running and is this page origin allowed (CORS)?";
    return backendMessage ?? "Something went wrong. Please try again.";
  }
  return "Something went wrong. Please try again.";
}

export type ReviewDecision = "APPROVED" | "REJECTED";

export type ReviewResponse = {
  id: number;
  annotationId: number;
  taskId: number;
  projectId: number;
  reviewer: string;
  decision: ReviewDecision;
  comment: string | null;
  reviewedAt: string;
};

export async function listAnnotationReviews(annotationId: number) {
  const res = await api.get<ReviewResponse[]>(`/api/annotations/${annotationId}/reviews`);
  return res.data;
}

export async function listProjectReviews(projectId: number) {
  const res = await api.get<ReviewResponse[]>(`/api/projects/${projectId}/reviews`);
  return res.data;
}

export async function submitReview(annotationId: number, data: { decision: ReviewDecision; comment?: string | null }) {
  const res = await api.post<ReviewResponse>(`/api/annotations/${annotationId}/reviews`, data);
  return res.data;
}

export function friendlyReviewError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    const backendMessage = (error.response?.data as { error?: string } | undefined)?.error;
    if (status === 401) return "Session expired. Please sign in again.";
    if (status === 403) return "You submitted this annotation yourself — a different reviewer must decide.";
    if (status === 404) return "Annotation not found or you do not have access.";
    if (status === 409) return backendMessage ?? "This annotation has already been reviewed.";
    if (status === 400) return backendMessage ?? "Please check the review and try again.";
    if (error.code === "ECONNABORTED") return "Request timed out. Please try again.";
    if (error.message === "Network Error") return "Cannot reach the server. Is the backend running and is this page origin allowed (CORS)?";
    return backendMessage ?? "Something went wrong. Please try again.";
  }
  return "Something went wrong. Please try again.";
}

export type DashboardSummary = {
  datasetCount: number;
  projectCount: number;
  taskCount: number;
  tasksPending: number;
  tasksSubmitted: number;
  tasksApproved: number;
  tasksRejected: number;
  annotationCount: number;
  reviewsApproved: number;
  reviewsRejected: number;
  pendingReviews: number;
  assignedToMe: number;
  assignedNeedsAction: number;
};

export async function dashboardSummary() {
  const res = await api.get<DashboardSummary>("/api/dashboard/summary");
  return res.data;
}

export type SuggestionResponse = {
  id: number;
  taskId: number;
  suggestedLabel: string;
  confidence: number | null;
  model: string;
  createdAt: string;
};

export async function suggestLabel(taskId: number, labels: string[]) {
  const res = await api.post<SuggestionResponse>(`/api/tasks/${taskId}/suggest`, { labels });
  return res.data;
}

export async function autoLabelTask(taskId: number, confidence?: number | null) {
  const res = await api.post<AnnotationResponse>(`/api/ai/tasks/${taskId}/auto-label`, {
    confidence: confidence ?? null,
  });
  return res.data;
}

export function friendlyAiError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    const backendMessage = (error.response?.data as { error?: string } | undefined)?.error;
    if (status === 401) return "Session expired. Please sign in again.";
    if (status === 404) return "Task not found or you do not have access.";
    if (status === 400) return backendMessage ?? "Add candidate labels (1-50) and try again.";
    if (status === 503 || status === 502 || status === 504)
      return "AI assistance is unavailable right now — you can still label manually.";
    if (error.code === "ECONNABORTED") return "Request timed out. Please try again.";
    if (error.message === "Network Error") return "Cannot reach the server. Is the backend running and is this page origin allowed (CORS)?";
    return backendMessage ?? "Something went wrong. Please try again.";
  }
  return "Something went wrong. Please try again.";
}

export async function exportProject(projectId: number, format: "json" | "csv") {
  const res = await api.get(`/api/projects/${projectId}/export`, {
    params: { format },
    responseType: "blob",
  });
  return res.data as Blob;
}

export function friendlyExportError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    if (status === 401) return "Session expired. Please sign in again.";
    if (status === 404) return "Project not found or you do not have access.";
    if (status === 400) return "Only verified annotations can be exported in json or csv format.";
    if (error.code === "ECONNABORTED") return "Request timed out. Please try again.";
    if (error.message === "Network Error") return "Cannot reach the server. Is the backend running and is this page origin allowed (CORS)?";
    return "Export failed. Please try again.";
  }
  return "Export failed. Please try again.";
}

export type AdminOverview = {
  userCount: number;
  datasetCount: number;
  projectCount: number;
  taskCount: number;
  annotationCount: number;
  reviewCount: number;
  reviewsApproved: number;
  reviewsRejected: number;
  suggestionCount: number;
};

export async function adminOverview() {
  const res = await api.get<AdminOverview>("/api/admin/overview");
  return res.data;
}

export async function listAdminUsers() {
  const res = await api.get<BackendUser[]>("/api/admin/users");
  return res.data;
}

export function friendlyAdminError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    const backendMessage = (error.response?.data as { error?: string } | undefined)?.error;
    if (status === 401) return "Session expired. Please sign in again.";
    if (status === 403) return "Admin access required. This area is limited to administrators.";
    if (status === 404) return backendMessage ?? "Resource not found.";
    if (error.code === "ECONNABORTED") return "Request timed out. Please try again.";
    if (error.message === "Network Error") return "Cannot reach the server. Is the backend running and is this page origin allowed (CORS)?";
    return backendMessage ?? "Something went wrong. Please try again.";
  }
  return "Something went wrong. Please try again.";
}
