import { APIRequestContext, expect } from '@playwright/test';
import { BFF_BASE, API_BASE, TestUser } from '../fixtures/users';

export class ApiClient {
  constructor(private request: APIRequestContext) {}

  async getToken(user: TestUser): Promise<string> {
    const res = await this.request.post(`${BFF_BASE}/api/v1/auth/token`, {
      data: { username: user.email, password: user.password },
    });
    if (!res.ok()) {
      throw new Error(`Login failed for ${user.email}: ${res.status()}`);
    }
    const body = await res.json();
    return body.access_token as string;
  }

  authHeaders(token: string) {
    return { Authorization: `Bearer ${token}` };
  }

  async get(path: string, token: string) {
    return this.request.get(`${BFF_BASE}${path}`, {
      headers: this.authHeaders(token),
    });
  }

  async post(path: string, token: string, data: unknown) {
    return this.request.post(`${BFF_BASE}${path}`, {
      headers: this.authHeaders(token),
      data: data as Record<string, unknown>,
    });
  }

  async put(path: string, token: string, data: unknown) {
    return this.request.put(`${BFF_BASE}${path}`, {
      headers: this.authHeaders(token),
      data: data as Record<string, unknown>,
    });
  }

  async delete(path: string, token: string) {
    return this.request.delete(`${BFF_BASE}${path}`, {
      headers: this.authHeaders(token),
    });
  }

  // ── FastAPI (backend Python) ────────────────────────────────────────────────

  async apiGet(path: string, token?: string) {
    return this.request.get(`${API_BASE}${path}`, {
      headers: token ? this.authHeaders(token) : {},
    });
  }

  async apiPost(path: string, token: string, data: unknown) {
    return this.request.post(`${API_BASE}${path}`, {
      headers: this.authHeaders(token),
      data: data as Record<string, unknown>,
    });
  }
}
