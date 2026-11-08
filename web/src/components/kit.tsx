import React, { useCallback, useEffect, useState } from 'react';
import { money, dmy, pctPlain } from '../lib/format';
import { Icon } from './ui';

/* ---------- tải dữ liệu ---------- */
export function useLoad<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const reload = useCallback(() => {
    setLoading(true);
    setError(null);
    fn().then(setData).catch((e) => setError(e.message ?? String(e))).finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  useEffect(() => { reload(); }, [reload]);
  return { data, error, loading, reload, setData };
}

export function Loading({ text = 'Đang tải dữ liệu…' }: { text?: string }) {
  return <div className="card pad muted loading"><span className="spinner" /> {text}</div>;
}

export function ErrorBox({ error, retry }: { error: string; retry?: () => void }) {
  return (
    <div className="callout bad">
      <b>Không tải được dữ liệu.</b> <span className="muted">{error}</span>
      {retry && <div style={{ marginTop: 10 }}><button className="btn sm ghost" onClick={retry}>Thử lại</button></div>}
    </div>
  );
}

export function Page({ kicker, title, lead, right, children }: { kicker?: string; title: string; lead?: React.ReactNode; right?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="screen">
      <header className="hello page-h">
        <div>
          {kicker && <div className="kicker">{kicker}</div>}
          <h1>{title}</h1>
          {lead && <p className="lead">{lead}</p>}
        </div>
        {right && <div className="page-r">{right}</div>}
      </header>
      {children}
    </div>
  );
}

/* ---------- KPI ---------- */
export function Stat({ label, value, sub, tone }: { label: string; value: React.ReactNode; sub?: React.ReactNode; tone?: 'good' | 'bad' | 'warn' }) {
  return (
    <div className="card pad stat">
      <div className="card-t">{label}</div>
      <div className={`stat-v ${tone ?? ''}`}>{value}</div>
      {sub && <div className="muted small">{sub}</div>}
    </div>
  );
}

/* ---------- bảng ---------- */
export type Col<T> = { key: string; label: string; type?: 'money' | 'date' | 'number' | 'percent' | 'text'; render?: (r: T) => React.ReactNode; align?: 'right' | 'left' };

export function fmtCell(v: unknown, type?: string): React.ReactNode {
  if (v === null || v === undefined || v === '') return '—';
  switch (type) {
    case 'money': return money(Number(v), { full: true });
    case 'date': return dmy(String(v));
    case 'percent': return pctPlain(Number(v));
    case 'number': return Number(v).toLocaleString('vi-VN');
    default: return typeof v === 'boolean' ? (v ? 'Có' : 'Không') : String(v);
  }
}

export function Table<T extends Record<string, any>>({ cols, rows, rowClass, onRow, empty = 'Không có dữ liệu', max }: {
  cols: Col<T>[]; rows: T[]; rowClass?: (r: T) => string; onRow?: (r: T) => void; empty?: string; max?: number;
}) {
  const [all, setAll] = useState(false);
  const shown = max && !all ? rows.slice(0, max) : rows;
  return (
    <div className="tbl-wrap">
      <table className="tbl">
        <thead>
          <tr>{cols.map((c) => <th key={c.key} className={c.align === 'right' || ['money', 'number', 'percent'].includes(c.type ?? '') ? 'r' : ''}>{c.label}</th>)}</tr>
        </thead>
        <tbody>
          {shown.length === 0 && <tr><td colSpan={cols.length} className="muted empty">{empty}</td></tr>}
          {shown.map((r, i) => (
            <tr key={i} className={`${rowClass?.(r) ?? ''} ${onRow ? 'click' : ''}`} onClick={onRow ? () => onRow(r) : undefined}>
              {cols.map((c) => (
                <td key={c.key} className={c.align === 'right' || ['money', 'number', 'percent'].includes(c.type ?? '') ? 'r' : ''}>
                  {c.render ? c.render(r) : fmtCell(r[c.key], c.type)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {max && rows.length > max && (
        <button className="btn sm ghost more" onClick={() => setAll(!all)}>{all ? 'Thu gọn' : `Xem tất cả ${rows.length} dòng`}</button>
      )}
    </div>
  );
}

/* ---------- biểu đồ cột / đường đơn giản (SVG, không cần thư viện) ---------- */
export function Bars({ data, height = 220, colorOf, line }: {
  data: { label: string; value: number; ghost?: boolean }[]; height?: number; colorOf?: (d: { value: number; ghost?: boolean }) => string; line?: (number | null)[];
}) {
  const w = 720;
  const pad = { l: 8, r: 8, t: 14, b: 26 };
  // cột và đường dùng 2 thang đo riêng (đường số dư thường lớn hơn cột nhiều lần)
  const vals = data.map((d) => d.value);
  const max = Math.max(1, ...vals);
  const min = Math.min(0, ...vals);
  const span = max - min || 1;
  const lv = (line ?? []).filter((x): x is number => x !== null);
  const lmax = Math.max(1, ...lv);
  const lmin = Math.min(0, ...lv);
  const lspan = lmax - lmin || 1;
  const bw = (w - pad.l - pad.r) / Math.max(1, data.length);
  const inner = height - pad.t - pad.b;
  const y = (v: number) => pad.t + inner * (1 - (v - min) / span);
  const yl = (v: number) => pad.t + inner * (1 - (v - lmin) / lspan);
  const labelEvery = Math.ceil(data.length / 12);
  return (
    <svg className="chart" viewBox={`0 0 ${w} ${height}`} preserveAspectRatio="none" style={{ width: '100%', height }}>
      <line x1={pad.l} x2={w - pad.r} y1={y(0)} y2={y(0)} className="axis-zero" />
      {data.map((d, i) => {
        const x = pad.l + i * bw + bw * 0.15;
        const y0 = y(0);
        const y1 = y(d.value);
        return (
          <g key={i}>
            <rect x={x} width={bw * 0.7} y={Math.min(y0, y1)} height={Math.max(1, Math.abs(y1 - y0))} rx={Math.min(4, bw * 0.2)}
              fill={colorOf ? colorOf(d) : d.ghost ? 'rgba(129,140,248,.35)' : 'var(--brand)'}>
              <title>{d.label}: {money(d.value, { full: true })}</title>
            </rect>
            {i % labelEvery === 0 && <text x={x + bw * 0.35} y={height - 8} textAnchor="middle" className="ax-t">{d.label}</text>}
          </g>
        );
      })}
      {line && (
        <polyline fill="none" stroke="var(--brand2)" strokeWidth={2}
          points={line.map((v, i) => (v === null ? null : `${pad.l + i * bw + bw / 2},${yl(v)}`)).filter(Boolean).join(' ')} />
      )}
    </svg>
  );
}

export function Empty({ text }: { text: string }) {
  return <div className="muted empty-box"><Icon name="check" size={16} /> {text}</div>;
}

export function Tabs<T extends string>({ value, onChange, items }: { value: T; onChange: (v: T) => void; items: { id: T; label: string; badge?: number }[] }) {
  return (
    <div className="seg">
      {items.map((t) => (
        <button key={t.id} className={value === t.id ? 'on' : ''} onClick={() => onChange(t.id)}>
          {t.label}{t.badge ? <i className="badge">{t.badge}</i> : null}
        </button>
      ))}
    </div>
  );
}
