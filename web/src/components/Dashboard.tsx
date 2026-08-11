import { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiError } from '../api';
import type { EquipmentResponse, FactoryEvent, OeeSummary, WorkOrder } from '../types';
import { useRealtime } from '../useRealtime';
import { EventTimeline } from './EventTimeline';
import { LineCard } from './LineCard';
import { WorkOrderPanel } from './WorkOrderPanel';

const MAX_TIMELINE_EVENTS = 80;

interface Props {
  user: { username: string; name: string; role: string };
  onLogout: () => void;
}

export function Dashboard({ user, onLogout }: Props) {
  const [summary, setSummary] = useState<OeeSummary | null>(null);
  const [events, setEvents] = useState<FactoryEvent[]>([]);
  const [workOrders, setWorkOrders] = useState<WorkOrder[]>([]);
  const [equipments, setEquipments] = useState<EquipmentResponse[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);

  const refreshWorkOrders = useCallback(() => {
    api.workOrders().then(setWorkOrders).catch(() => undefined);
  }, []);

  // 401(토큰 만료)이면 로그인으로 되돌린다.
  const guard = useCallback(
    (err: unknown) => {
      if (err instanceof ApiError && err.status === 401) {
        onLogout();
        return;
      }
      setLoadError(err instanceof Error ? err.message : '데이터를 불러오지 못했습니다.');
    },
    [onLogout],
  );

  useEffect(() => {
    api.oeeSummary().then(setSummary).catch(guard);
    api.recentEvents().then(setEvents).catch(guard);
    api.workOrders().then(setWorkOrders).catch(guard);
    api.equipments().then(setEquipments).catch(guard);
  }, [guard]);

  // 작업지시 이벤트가 흐르면 목록도 갱신 — 다른 사용자의 조작도 반영된다.
  const refreshTimer = useRef<number | null>(null);
  const handleFactoryEvent = useCallback(
    (event: FactoryEvent) => {
      setEvents((prev) => [event, ...prev].slice(0, MAX_TIMELINE_EVENTS));
      if (event.sourceType === 'WORK_ORDER' && refreshTimer.current === null) {
        refreshTimer.current = window.setTimeout(() => {
          refreshTimer.current = null;
          refreshWorkOrders();
        }, 300);
      }
    },
    [refreshWorkOrders],
  );

  const connected = useRealtime(setSummary, handleFactoryEvent);

  const shift = summary?.window.shift;
  const windowFrom = summary ? new Date(summary.window.from) : null;

  return (
    <>
      <header className="app-header">
        <h1>PixelFactory OEE</h1>
        {summary && (
          <span className="shift-info">
            {shift ? `${shift} 시프트` : '조회 구간'} ·{' '}
            {windowFrom?.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })} ~ 현재
          </span>
        )}
        <div className="spacer" />
        <span className={connected ? 'live-indicator connected' : 'live-indicator'}>
          <span className="dot" aria-hidden />
          {connected ? '실시간 연결됨' : '연결 대기 중'}
        </span>
        <span className="user-chip">
          {user.name} ({user.role})
        </span>
        <button className="btn" onClick={onLogout}>
          로그아웃
        </button>
      </header>

      <main className="layout">
        <div className="col">
          {loadError && (
            <div className="card">
              <span className="error-text">{loadError}</span>
            </div>
          )}
          {!summary && !loadError && <div className="card empty-note">OEE 데이터를 불러오는 중…</div>}
          {summary?.lines.map((line) => <LineCard key={line.lineId} line={line} />)}
          <WorkOrderPanel
            workOrders={workOrders}
            equipments={equipments}
            onChanged={refreshWorkOrders}
          />
        </div>
        <div className="col">
          <EventTimeline events={events} />
        </div>
      </main>
    </>
  );
}
