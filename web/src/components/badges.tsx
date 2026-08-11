import type { EquipmentStatus, EventSeverity } from '../types';

// 색은 아이콘에만 싣고 라벨 텍스트는 잉크 토큰 유지 — 색상 단독으로 의미를 전달하지 않는다.

const STATUS_META: Record<EquipmentStatus, { icon: string; label: string }> = {
  RUNNING: { icon: '▶', label: '가동' },
  IDLE: { icon: '⏸', label: '대기' },
  DOWN: { icon: '✕', label: '고장' },
  QUALITY_HOLD: { icon: '▲', label: '품질보류' },
};

export function StatusBadge({ status }: { status: EquipmentStatus }) {
  const meta = STATUS_META[status];
  return (
    <span className={`badge st-${status}`}>
      <span className="icon" aria-hidden>
        {meta.icon}
      </span>
      {meta.label}
    </span>
  );
}

const SEVERITY_META: Record<EventSeverity, { icon: string; label: string }> = {
  INFO: { icon: '●', label: 'INFO' },
  SUCCESS: { icon: '✓', label: 'OK' },
  WARNING: { icon: '▲', label: 'WARN' },
  ERROR: { icon: '✕', label: 'ERROR' },
};

export function SeverityBadge({ severity }: { severity: EventSeverity }) {
  const meta = SEVERITY_META[severity];
  return (
    <span className={`badge sv-${severity}`}>
      <span className="icon" aria-hidden>
        {meta.icon}
      </span>
      {meta.label}
    </span>
  );
}
