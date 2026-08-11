import type { LineOee } from '../types';
import { StatusBadge } from './badges';
import { Meter, pct } from './Meter';

function formatDuration(ms: number): string {
  const totalMinutes = Math.floor(ms / 60_000);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return hours > 0 ? `${hours}시간 ${minutes}분` : `${minutes}분`;
}

/** 라인 OEE 히어로 + A/P/Q 미터 + 설비 카드 그리드. */
export function LineCard({ line }: { line: LineOee }) {
  const m = line.metrics;

  return (
    <section className="card">
      <div className="line-head">
        <h2>
          {line.lineCode} · {line.name}
        </h2>
      </div>

      <div className="line-grid">
        <div>
          <div className="hero-figure">{pct(m.oee)}</div>
          <div className="hero-label">라인 OEE (A × P × Q)</div>

          <Meter label="가동률 (A)" value={m.availability} />
          <Meter label="성능 (P)" value={m.performance} />
          <Meter label="품질 (Q)" value={m.quality} />

          <div className="line-stats">
            <span>
              생산 <strong>{m.cycleCount}</strong>
            </span>
            <span>
              불량 <strong>{m.defectCount}</strong>
            </span>
            <span>
              가동(설비 합계) <strong>{formatDuration(m.runtimeMs)}</strong> /{' '}
              {formatDuration(m.plannedTimeMs)}
            </span>
          </div>
        </div>

        <div className="equipment-grid">
          {line.equipments.map((eq) => (
            <div className="equipment-card" key={eq.equipmentId}>
              <div className="eq-head">
                <div>
                  <div className="eq-code">{eq.equipmentCode}</div>
                  <div className="eq-name">{eq.name}</div>
                </div>
                <StatusBadge status={eq.status} />
              </div>
              <div>
                <span className="eq-oee">{pct(eq.metrics.oee)}</span>{' '}
                <span className="hero-label">OEE</span>
              </div>
              <Meter small label="A" value={eq.metrics.availability} />
              <Meter small label="P" value={eq.metrics.performance} />
              <Meter small label="Q" value={eq.metrics.quality} />
              <div className="eq-counts">
                생산 {eq.metrics.cycleCount} · 불량 {eq.metrics.defectCount}
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
