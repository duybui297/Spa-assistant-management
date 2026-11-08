import React from 'react';
import { api } from '../lib/api';
import { money } from '../lib/format';
import { Bars, ErrorBox, Loading, Page, Table, useLoad } from '../components/kit';
import type { Tab } from '../App';

export interface MonthPnl { month: string; sales: number; refunds: number; commissions: number; expenses: Record<string, number>; expenseTotal: number; profit: number; partial: boolean }
export interface ProfitData { months: MonthPnl[]; waterfall: { label: string; delta: number }[]; compareLabel: string; notes: string[]; salesByCategory: Record<string, number> }

const mLabel = (m: string) => `T${Number(m.slice(5))}/${m.slice(2, 4)}`;

export default function ProfitScreen({ go }: { go: (t: Tab) => void }) {
  const d = useLoad(() => api<ProfitData>('/assistant/profit', { query: { months: 6 } }));
  if (d.loading) return <div className="screen"><Loading /></div>;
  if (d.error || !d.data) return <div className="screen"><ErrorBox error={d.error ?? ''} retry={d.reload} /></div>;
  const p = d.data;
  const wf = p.waterfall;
  const start = wf[0];
  const end = wf[wf.length - 1];
  const drivers = wf.slice(1, -1).filter((s) => Math.abs(s.delta) > 0).sort((a, b) => Math.abs(b.delta) - Math.abs(a.delta));
  const maxAbs = Math.max(1, ...drivers.map((x) => Math.abs(x.delta)));
  const noExpense = p.months.some((m) => !m.partial && m.expenseTotal === 0);
  const cats = Object.entries(p.salesByCategory).sort((a, b) => b[1] - a[1]);
  const catTotal = cats.reduce((a, c) => a + c[1], 0);

  return (
    <Page kicker="Kết quả kinh doanh" title="Lợi nhuận"
      lead={<>Lãi = doanh số − hoàn tiền − hoa hồng − chi phí nhập tay. <b>{p.compareLabel}</b>: lãi {money(start.delta)} → <b>{money(end.delta)}</b>.</>}
      right={<button className="btn sm ghost" onClick={() => go('expenses')}>Nhập chi phí</button>}>
      {noExpense && <div className="callout bad small">Có tháng chưa nhập chi phí, lãi của tháng đó đang bị tính cao hơn thực tế.</div>}
      <section className="card pad">
        <div className="card-t">Lãi theo tháng (tháng cuối chưa hết tháng)</div>
        <Bars data={p.months.map((m) => ({ label: mLabel(m.month), value: m.profit, ghost: m.partial }))}
          colorOf={(x) => (x.value < 0 ? 'var(--bad)' : x.ghost ? 'rgba(52,211,153,.4)' : 'var(--good)')}
          line={p.months.map((m) => m.sales)} height={240} />
        <div className="legend muted small"><span className="lg good" /> Lãi <span className="lg brand2" /> Doanh số</div>
      </section>
      <div className="grid-2">
        <section className="card pad">
          <div className="card-t">Vì sao lãi thay đổi ({p.compareLabel})</div>
          {drivers.length === 0 && <div className="muted">Không có thay đổi.</div>}
          <div className="drivers">
            {drivers.map((s) => (
              <div key={s.label} className="drv">
                <span>{s.label}</span>
                <div className="drv-bar"><i className={s.delta >= 0 ? 'up' : 'down'} style={{ width: `${(Math.abs(s.delta) / maxAbs) * 100}%` }} /></div>
                <b className={s.delta >= 0 ? 'good-t' : 'bad-t'}>{money(s.delta, { sign: true })}</b>
              </div>
            ))}
          </div>
        </section>
        <section className="card pad">
          <div className="card-t">Doanh số theo nhóm dịch vụ (tháng trước)</div>
          <div className="drivers">
            {cats.map(([k, v]) => (
              <div key={k} className="drv">
                <span>{k}</span>
                <div className="drv-bar"><i className="up" style={{ width: `${catTotal ? (v / catTotal) * 100 : 0}%` }} /></div>
                <b>{money(v)}</b>
              </div>
            ))}
          </div>
        </section>
      </div>
      <section className="card">
        <Table rows={p.months} cols={[
          { key: 'month', label: 'Tháng', render: (m) => mLabel(m.month) + (m.partial ? ' (đang chạy)' : '') },
          { key: 'sales', label: 'Doanh số', type: 'money' }, { key: 'refunds', label: 'Hoàn tiền', type: 'money' },
          { key: 'commissions', label: 'Hoa hồng', type: 'money' }, { key: 'expenseTotal', label: 'Chi phí', type: 'money' },
          { key: 'profit', label: 'Lãi', align: 'right', render: (m) => <b className={m.profit < 0 ? 'bad-t' : 'good-t'}>{money(m.profit, { full: true })}</b> }]} />
      </section>
      <ul className="notes">{p.notes.map((n, i) => <li key={i}>{n}</li>)}</ul>
    </Page>
  );
}
