// oee-service REST/WebSocket 계약 타입 (서버 DTO 미러)

export interface ApiEnvelope<T> {
  success: boolean;
  data?: T;
  error?: { code: string; message: string };
}

export type EquipmentStatus = 'IDLE' | 'RUNNING' | 'DOWN' | 'QUALITY_HOLD';

export type WorkOrderStatus =
  | 'ASSIGNED'
  | 'READY'
  | 'IN_PROGRESS'
  | 'INSPECTION_WAITING'
  | 'ON_HOLD'
  | 'COMPLETED';

export type EventSeverity = 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR';

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  username: string;
  name: string;
  role: string;
}

export interface EquipmentResponse {
  id: number;
  equipmentCode: string;
  name: string;
  lineId: number;
  idealCycleTimeMs: number;
  status: EquipmentStatus;
}

export interface OeeMetrics {
  availability: number;
  performance: number;
  quality: number;
  oee: number;
  plannedTimeMs: number;
  runtimeMs: number;
  cycleCount: number;
  defectCount: number;
}

export interface EquipmentOee {
  equipmentId: number;
  equipmentCode: string;
  name: string;
  status: EquipmentStatus;
  metrics: OeeMetrics;
}

export interface LineOee {
  lineId: number;
  lineCode: string;
  name: string;
  metrics: OeeMetrics;
  equipments: EquipmentOee[];
}

export interface OeeWindow {
  from: string;
  to: string;
  shift?: string;
}

export interface OeeSummary {
  window: OeeWindow;
  lines: LineOee[];
}

export interface FactoryEvent {
  id: number;
  eventType: string;
  sourceType: string;
  sourceId: number | null;
  targetType: string;
  targetId: number | null;
  workOrderId: number | null;
  lotNo: string | null;
  severity: EventSeverity;
  message: string;
  payloadJson: string | null;
  createdAt: string;
}

export interface WorkOrder {
  id: number;
  workOrderNo: string;
  itemId: number;
  processId: number;
  equipmentId: number;
  assignedUserId: number;
  lotNo: string;
  plannedQty: number;
  producedQty: number;
  defectQty: number;
  status: WorkOrderStatus;
  plannedStartAt: string;
  plannedEndAt: string;
  startedAt: string | null;
  completedAt: string | null;
  holdReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface WorkOrderCreateRequest {
  workOrderNo: string;
  itemId: number;
  processId: number;
  equipmentId: number;
  assignedUserId: number;
  lotNo: string;
  plannedQty: number;
  plannedStartAt: string;
  plannedEndAt: string;
}
