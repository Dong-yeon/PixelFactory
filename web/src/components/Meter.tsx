interface Props {
  label: string;
  value: number; // 0.0 ~ 1.0
  small?: boolean;
}

export function pct(value: number): string {
  return `${(value * 100).toFixed(1)}%`;
}

/** A/P/Q 미터 — 채움은 액센트, 트랙은 같은 램프의 밝은 스텝. */
export function Meter({ label, value, small }: Props) {
  const width = Math.max(0, Math.min(1, value)) * 100;
  return (
    <div className={small ? 'meter small' : 'meter'}>
      <div className="meter-head">
        <span>{label}</span>
        <span className="meter-value">{pct(value)}</span>
      </div>
      <div className="track" role="img" aria-label={`${label} ${pct(value)}`}>
        <div className="fill" style={{ width: `${width}%` }} />
      </div>
    </div>
  );
}
