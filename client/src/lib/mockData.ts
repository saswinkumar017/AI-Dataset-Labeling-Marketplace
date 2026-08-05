export type DatasetStatus = "READY" | "UPLOADING" | "FAILED";
export type ProjectStatus = "DRAFT" | "IN_PROGRESS" | "COMPLETED";
export type TaskStatus = "PENDING" | "ASSIGNED" | "IN_PROGRESS" | "SUBMITTED" | "APPROVED" | "REJECTED";

export type Dataset = {
  id: string;
  name: string;
  type: "IMAGE" | "TEXT" | "CSV";
  items: number;
  status: DatasetStatus;
  owner: string;
  createdAt: string;
  project?: string;
  progress: number;
};

export type Project = {
  id: string;
  name: string;
  dataset: string;
  datasetId: string;
  status: ProjectStatus;
  progress: number;
  totalTasks: number;
  annotated: number;
  reviewed: number;
  labels: string[];
};

export type AnnotationItem = {
  id: string;
  text: string;
  image?: string;
  labels: string[];
  aiSuggestion: string;
  aiConfidence: number;
  currentLabel: string | null;
};

export type ReviewItem = {
  id: string;
  taskId: string;
  text: string;
  annotator: string;
  aiSuggestion: string;
  aiConfidence: number;
  submittedLabel: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
};

export const mockDatasets: Dataset[] = [
  { id: "ds-1", name: "Product Reviews – Sentiment", type: "TEXT", items: 1240, status: "READY", owner: "you", createdAt: "2026-07-28", project: "Sentiment v1", progress: 68 },
  { id: "ds-2", name: "Street Scenes – Objects", type: "IMAGE", items: 860, status: "READY", owner: "you", createdAt: "2026-07-30", project: "Object Detection", progress: 42 },
  { id: "ds-3", name: "Support Tickets – CSV", type: "CSV", items: 540, status: "READY", owner: "you", createdAt: "2026-08-01", progress: 91 },
];

export const mockProjects: Project[] = [
  { id: "pr-1", name: "Sentiment v1", dataset: "Product Reviews – Sentiment", datasetId: "ds-1", status: "IN_PROGRESS", progress: 68, totalTasks: 1240, annotated: 842, reviewed: 610, labels: ["Positive", "Negative", "Neutral"] },
  { id: "pr-2", name: "Object Detection", dataset: "Street Scenes – Objects", datasetId: "ds-2", status: "IN_PROGRESS", progress: 42, totalTasks: 860, annotated: 361, reviewed: 210, labels: ["Car", "Bike", "Person", "Sign"] },
  { id: "pr-3", name: "Ticket Triage", dataset: "Support Tickets – CSV", datasetId: "ds-3", status: "DRAFT", progress: 12, totalTasks: 540, annotated: 65, reviewed: 12, labels: ["Bug", "Feature", "Question"] },
];

export const mockAnnotationItems: AnnotationItem[] = [
  { id: "t-101", text: "I love the battery life, but the screen is dim in sunlight.", labels: ["Positive", "Negative", "Neutral"], aiSuggestion: "Neutral", aiConfidence: 0.87, currentLabel: null },
  { id: "t-102", text: "Delivery was delayed by three days, support was unhelpful.", labels: ["Positive", "Negative", "Neutral"], aiSuggestion: "Negative", aiConfidence: 0.94, currentLabel: null },
  { id: "t-103", text: "Great value for money, will buy again!", labels: ["Positive", "Negative", "Neutral"], aiSuggestion: "Positive", aiConfidence: 0.92, currentLabel: null },
  { id: "t-104", text: "The app crashes when I try to upload a photo.", labels: ["Positive", "Negative", "Neutral"], aiSuggestion: "Negative", aiConfidence: 0.88, currentLabel: null },
  { id: "t-105", text: "Packaging was excellent, product arrived safely.", labels: ["Positive", "Negative", "Neutral"], aiSuggestion: "Positive", aiConfidence: 0.81, currentLabel: null },
];

export const mockReviews: ReviewItem[] = [
  { id: "r-1", taskId: "t-201", text: "The dataset is clean and well formatted.", annotator: "aisha", aiSuggestion: "Positive", aiConfidence: 0.89, submittedLabel: "Positive", status: "PENDING" },
  { id: "r-2", taskId: "t-202", text: "This image shows a busy intersection with multiple cars.", annotator: "rahul", aiSuggestion: "Car", aiConfidence: 0.91, submittedLabel: "Car", status: "PENDING" },
  { id: "r-3", taskId: "t-203", text: "Refund process took too long.", annotator: "meera", aiSuggestion: "Negative", aiConfidence: 0.93, submittedLabel: "Negative", status: "PENDING" },
];

export const dashboardStats = {
  totalDatasets: 3,
  activeProjects: 2,
  totalTasks: 2640,
  annotated: 1268,
  pendingReviews: 89,
  completed: 832,
};
