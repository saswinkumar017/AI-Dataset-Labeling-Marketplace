import { AxiosError, AxiosHeaders } from "axios";
import { describe, expect, it } from "vitest";
import {
  friendlyAiError,
  friendlyAnnotationError,
  friendlyAuthError,
  friendlyDatasetError,
  friendlyExportError,
  friendlyProjectError,
  friendlyReviewError,
  friendlyTaskError,
} from "./api";
import type { AxiosResponse } from "axios";

function axiosError(status?: number, backendError?: string, code?: string): AxiosError {
  const error = new AxiosError("Request failed", code);
  if (status !== undefined) {
    error.response = {
      status,
      data: backendError ? { error: backendError } : {},
      headers: new AxiosHeaders(),
      config: undefined,
      statusText: "",
    } as unknown as AxiosResponse;
  }
  return error;
}

describe("friendlyAuthError", () => {
  it("explains duplicate registration", () => {
    expect(friendlyAuthError(axiosError(409))).toContain("already exists");
  });

  it("explains bad credentials", () => {
    expect(friendlyAuthError(axiosError(401))).toContain("Incorrect email");
  });

  it("prefers the backend validation message", () => {
    expect(friendlyAuthError(axiosError(400, "email must be valid"))).toBe("email must be valid");
  });

  it("handles connectivity problems", () => {
    expect(friendlyAuthError(axiosError(undefined, undefined, "ECONNABORTED"))).toContain("timed out");
    const network = axiosError();
    network.message = "Network Error";
    expect(friendlyAuthError(network)).toContain("port 8080");
  });

  it("falls back for unknown input", () => {
    expect(friendlyAuthError(new Error("boom"))).toBe("Something went wrong. Please try again.");
  });
});

describe("friendlyDatasetError", () => {
  it("hides foreign datasets as not found", () => {
    expect(friendlyDatasetError(axiosError(404))).toContain("not have access");
  });

  it("maps expired sessions", () => {
    expect(friendlyDatasetError(axiosError(401))).toContain("sign in again");
  });
});

describe("friendlyProjectError", () => {
  it("hides foreign projects as not found", () => {
    expect(friendlyProjectError(axiosError(404))).toContain("not have access");
  });
});

describe("friendlyTaskError", () => {
  it("hides foreign queues as not found", () => {
    expect(friendlyTaskError(axiosError(404))).toContain("not have access");
  });
});

describe("friendlyAnnotationError", () => {
  it("explains locked items on conflict", () => {
    expect(friendlyAnnotationError(axiosError(409, "Task is already approved"))).toBe(
      "Task is already approved"
    );
  });

  it("falls back on conflict without a message", () => {
    expect(friendlyAnnotationError(axiosError(409))).toContain("no longer be annotated");
  });
});

describe("friendlyReviewError", () => {
  it("explains the self-review rule", () => {
    expect(friendlyReviewError(axiosError(403))).toContain("different reviewer");
  });

  it("explains duplicate reviews", () => {
    expect(friendlyReviewError(axiosError(409))).toContain("already been reviewed");
  });

  it("hides foreign annotations as not found", () => {
    expect(friendlyReviewError(axiosError(404))).toContain("not have access");
  });
});

describe("friendlyAiError", () => {
  it("keeps manual work possible on provider outages", () => {
    for (const status of [502, 503, 504]) {
      expect(friendlyAiError(axiosError(status))).toContain("label manually");
    }
  });

  it("asks for candidate labels on bad requests", () => {
    expect(friendlyAiError(axiosError(400))).toContain("candidate labels");
  });

  it("prefers backend detail when present", () => {
    expect(friendlyAiError(axiosError(400, "Task has no text"))).toBe("Task has no text");
  });
});

describe("friendlyExportError", () => {
  it("hides foreign projects as not found", () => {
    expect(friendlyExportError(axiosError(404))).toContain("not have access");
  });

  it("explains the verified-only rule", () => {
    expect(friendlyExportError(axiosError(400))).toContain("verified");
  });
});
