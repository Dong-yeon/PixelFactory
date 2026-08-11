import { useState } from 'react';
import type { FormEvent } from 'react';
import { api, ApiError } from '../api';
import type { EquipmentResponse, WorkOrder, WorkOrderStatus } from '../types';

const STATUS_LABEL: Record<WorkOrderStatus, string> = {
  ASSIGNED: '배정됨',
  READY: '준비',
  IN_PROGRESS: '진행 중',
  INSPECTION_WAITING: '검사 대기',
  ON_HOLD: '중단',
  COMPLETED: '완료',
};

interface Props {
  workOrders: WorkOrder[];
  equipments: EquipmentResponse[];
  onChanged: () => void;
}

/** 작업지시 목록 + 상태 조작 + 생성 — 조작 결과는 이벤트/OEE push로 즉시 대시보드에 반영된다. */
export function WorkOrderPanel({ workOrders, equipments, onChanged }: Props) {
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [completingId, setCompletingId] = useState<number | null>(null);
  const [producedQty, setProducedQty] = useState('');
  const [defectQty, setDefectQty] = useState('0');

  const run = async (id: number, action: () => Promise<unknown>) => {
    setBusyId(id);
    setError(null);
    try {
      await action();
      setCompletingId(null);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '요청에 실패했습니다.');
    } finally {
      setBusyId(null);
    }
  };

  const equipmentCode = (equipmentId: number) =>
    equipments.find((eq) => eq.id === equipmentId)?.equipmentCode ?? `#${equipmentId}`;

  return (
    <section className="card">
      <h2>작업지시</h2>
      <p className="subtitle">시작/중단하면 설비 상태와 OEE가 실시간으로 반응합니다</p>
      {error && <span className="error-text">{error}</span>}

      <table className="wo-table">
        <thead>
          <tr>
            <th>지시번호</th>
            <th>LOT</th>
            <th>설비</th>
            <th>상태</th>
            <th>수량</th>
            <th>조작</th>
          </tr>
        </thead>
        <tbody>
          {workOrders.length === 0 && (
            <tr>
              <td colSpan={6}>
                <div className="empty-note">작업지시가 없습니다. 아래에서 새로 만들어 보세요.</div>
              </td>
            </tr>
          )}
          {workOrders.map((wo) => {
            const busy = busyId === wo.id;
            return (
              <tr key={wo.id}>
                <td>{wo.workOrderNo}</td>
                <td>{wo.lotNo}</td>
                <td>{equipmentCode(wo.equipmentId)}</td>
                <td>{STATUS_LABEL[wo.status]}</td>
                <td className="qty">
                  {wo.producedQty}/{wo.plannedQty}
                  {wo.defectQty > 0 && ` (불량 ${wo.defectQty})`}
                </td>
                <td>
                  <div className="wo-actions">
                    {(wo.status === 'ASSIGNED' || wo.status === 'READY' || wo.status === 'ON_HOLD') && (
                      <button
                        className="btn primary"
                        disabled={busy}
                        onClick={() => run(wo.id, () => api.startWorkOrder(wo.id))}
                      >
                        {wo.status === 'ON_HOLD' ? '재시작' : '시작'}
                      </button>
                    )}
                    {wo.status === 'IN_PROGRESS' &&
                      (completingId === wo.id ? (
                        <span className="inline-complete">
                          <input
                            type="number"
                            min={0}
                            placeholder="생산수"
                            value={producedQty}
                            onChange={(e) => setProducedQty(e.target.value)}
                          />
                          <input
                            type="number"
                            min={0}
                            placeholder="불량수"
                            value={defectQty}
                            onChange={(e) => setDefectQty(e.target.value)}
                          />
                          <button
                            className="btn primary"
                            disabled={busy || producedQty === ''}
                            onClick={() =>
                              run(wo.id, () =>
                                api.completeProduction(wo.id, Number(producedQty), Number(defectQty || 0)),
                              )
                            }
                          >
                            확정
                          </button>
                          <button className="btn" onClick={() => setCompletingId(null)}>
                            취소
                          </button>
                        </span>
                      ) : (
                        <button
                          className="btn"
                          disabled={busy}
                          onClick={() => {
                            setCompletingId(wo.id);
                            setProducedQty(String(wo.plannedQty));
                            setDefectQty('0');
                          }}
                        >
                          생산완료
                        </button>
                      ))}
                    {(wo.status === 'ASSIGNED' ||
                      wo.status === 'READY' ||
                      wo.status === 'IN_PROGRESS' ||
                      wo.status === 'INSPECTION_WAITING') && (
                      <button
                        className="btn"
                        disabled={busy}
                        onClick={() => {
                          const reason = window.prompt('중단 사유를 입력하세요.', '품질 이상 확인');
                          if (reason) {
                            void run(wo.id, () => api.holdWorkOrder(wo.id, reason));
                          }
                        }}
                      >
                        중단
                      </button>
                    )}
                    {(wo.status === 'INSPECTION_WAITING' || wo.status === 'ON_HOLD') && (
                      <button
                        className="btn"
                        disabled={busy}
                        onClick={() => run(wo.id, () => api.closeWorkOrder(wo.id))}
                      >
                        종료
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>

      <CreateForm equipments={equipments} onCreated={onChanged} />
    </section>
  );
}

function CreateForm({
  equipments,
  onCreated,
}: {
  equipments: EquipmentResponse[];
  onCreated: () => void;
}) {
  const [workOrderNo, setWorkOrderNo] = useState('');
  const [lotNo, setLotNo] = useState('');
  const [equipmentId, setEquipmentId] = useState('');
  const [plannedQty, setPlannedQty] = useState('100');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);

    // 서버는 LocalDateTime(타임존 없음)을 기대하므로 로컬 시각으로 포맷한다.
    // toISOString()은 UTC라 KST에서 9시간 과거가 되어 @FutureOrPresent 검증에 걸린다.
    const pad = (n: number) => String(n).padStart(2, '0');
    const iso = (d: Date) =>
      `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
      `T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
    const now = new Date(Date.now() + 60_000);
    const end = new Date(now.getTime() + 8 * 3600_000);

    try {
      await api.createWorkOrder({
        workOrderNo,
        lotNo,
        equipmentId: Number(equipmentId || equipments[0]?.id),
        plannedQty: Number(plannedQty),
        // 데모 마스터: 품목/공정/담당자는 시드 데이터 1번 고정.
        itemId: 1,
        processId: 1,
        assignedUserId: 1,
        plannedStartAt: iso(now),
        plannedEndAt: iso(end),
      });
      setWorkOrderNo('');
      setLotNo('');
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '생성에 실패했습니다.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <details className="wo-create">
      <summary>+ 새 작업지시</summary>
      <form onSubmit={submit}>
        <div className="form-grid">
          <div className="field">
            <label htmlFor="wo-no">지시번호</label>
            <input
              id="wo-no"
              value={workOrderNo}
              onChange={(e) => setWorkOrderNo(e.target.value)}
              placeholder="WO-2026-001"
              required
            />
          </div>
          <div className="field">
            <label htmlFor="wo-lot">LOT 번호</label>
            <input
              id="wo-lot"
              value={lotNo}
              onChange={(e) => setLotNo(e.target.value)}
              placeholder="LOT-A-001"
              required
            />
          </div>
          <div className="field">
            <label htmlFor="wo-eq">설비</label>
            <select id="wo-eq" value={equipmentId} onChange={(e) => setEquipmentId(e.target.value)}>
              {equipments.map((eq) => (
                <option key={eq.id} value={eq.id}>
                  {eq.equipmentCode} · {eq.name}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="wo-qty">계획 수량</label>
            <input
              id="wo-qty"
              type="number"
              min={1}
              value={plannedQty}
              onChange={(e) => setPlannedQty(e.target.value)}
              required
            />
          </div>
        </div>
        <div className="form-footer">
          <button className="btn primary" type="submit" disabled={busy}>
            {busy ? '생성 중…' : '생성'}
          </button>
          {error && <span className="error-text">{error}</span>}
        </div>
      </form>
    </details>
  );
}
