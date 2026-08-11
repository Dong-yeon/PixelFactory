import type {
  ApiEnvelope,
  EquipmentResponse,
  FactoryEvent,
  LoginResponse,
  OeeSummary,
  WorkOrder,
  WorkOrderCreateRequest,
} from './types';

// 배포 시 VITE_API_BASE_URL로 API 오리진 주입, 로컬 개발은 vite proxy('')를 쓴다.
const API_BASE: string = import.meta.env.VITE_API_BASE_URL ?? '';

const TOKEN_KEY = 'pixelfactory.token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null): void {
  if (token === null) {
    localStorage.removeItem(TOKEN_KEY);
  } else {
    localStorage.setItem(TOKEN_KEY, token);
  }
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE}${path}`, { ...init, headers });

  let envelope: ApiEnvelope<T> | null = null;
  try {
    envelope = (await response.json()) as ApiEnvelope<T>;
  } catch {
    // Non-JSON error body — fall through to the status-based error below.
  }

  if (!response.ok || !envelope?.success) {
    if (response.status === 401) {
      setToken(null);
    }
    throw new ApiError(
      response.status,
      envelope?.error?.code ?? 'UNKNOWN',
      envelope?.error?.message ?? `요청 실패 (HTTP ${response.status})`,
    );
  }

  return envelope.data as T;
}

export const api = {
  login(username: string, password: string): Promise<LoginResponse> {
    return request('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    });
  },

  oeeSummary(): Promise<OeeSummary> {
    return request('/api/oee/summary');
  },

  recentEvents(limit = 50): Promise<FactoryEvent[]> {
    return request(`/api/events/recent?limit=${limit}`);
  },

  equipments(): Promise<EquipmentResponse[]> {
    return request('/api/equipments');
  },

  workOrders(): Promise<WorkOrder[]> {
    return request('/api/work-orders');
  },

  createWorkOrder(body: WorkOrderCreateRequest): Promise<WorkOrder> {
    return request('/api/work-orders', { method: 'POST', body: JSON.stringify(body) });
  },

  startWorkOrder(id: number): Promise<WorkOrder> {
    return request(`/api/work-orders/${id}/start`, { method: 'PATCH' });
  },

  holdWorkOrder(id: number, reason: string): Promise<WorkOrder> {
    return request(`/api/work-orders/${id}/hold`, {
      method: 'PATCH',
      body: JSON.stringify({ reason }),
    });
  },

  completeProduction(id: number, producedQty: number, defectQty: number): Promise<WorkOrder> {
    return request(`/api/work-orders/${id}/complete-production`, {
      method: 'PATCH',
      body: JSON.stringify({ producedQty, defectQty }),
    });
  },

  closeWorkOrder(id: number): Promise<WorkOrder> {
    return request(`/api/work-orders/${id}/close`, { method: 'PATCH' });
  },
};
