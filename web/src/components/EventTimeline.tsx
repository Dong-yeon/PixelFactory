import type { FactoryEvent } from '../types';
import { SeverityBadge } from './badges';

function formatTime(iso: string): string {
  const date = new Date(iso);
  const hh = String(date.getHours()).padStart(2, '0');
  const mm = String(date.getMinutes()).padStart(2, '0');
  const ss = String(date.getSeconds()).padStart(2, '0');
  return `${hh}:${mm}:${ss}`;
}

export function EventTimeline({ events }: { events: FactoryEvent[] }) {
  return (
    <section className="card">
      <h2>이벤트 타임라인</h2>
      <p className="subtitle">FactoryEvent 실시간 스트림 (/topic/events)</p>
      <div className="timeline">
        {events.length === 0 && (
          <div className="empty-note">아직 수신된 이벤트가 없습니다. 시뮬레이터를 실행해 보세요.</div>
        )}
        {events.map((event) => (
          <div className="timeline-row" key={event.id}>
            <span className="time">{formatTime(event.createdAt)}</span>
            <SeverityBadge severity={event.severity} />
            <div>
              <div className="message">{event.message}</div>
              {event.lotNo && <span className="lot">LOT {event.lotNo}</span>}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
