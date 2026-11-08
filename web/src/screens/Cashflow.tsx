import React, { useState } from 'react';
import { api } from '../lib/api';
import { dm, dmy, money } from '../lib/format';
import { Bars, ErrorBox, Loading, Page, Stat, Table, useLoad } from '../components/kit';
import type { Tab } from '../App';

interface Cashflow {
  openingBalance: number;
  days: { date: string; in: number; out: number; balance: number | null; forecast: boolean }[];
  upcoming: { date: string; label: string; amount: number; kind: string }[];
  avgDailyIn: number; firstNegative: string | null; minBalance: number; notes: string[];
}

const KEY = 'spa-ai-opening';

export default function CashflowScreen({ go }: { go: (t: Tab) => void }) {
  const [opening, setOpening] = useState<number>(() => { try { return Number(localStorage.getItem(KEY) ?? 0); } catch { return 0; } });
  const [input, setInput] = useState(String(opening || ''));
  const [days, setDays] = useState(90);
  const d = useLoad(() => api<Cashflow>('/assistant/cashflow', { query: { opening, days } }), [opening, days]);
  const apply = () => {
    const v = Number(input.replace(/\D/g, '')) || 0;
    try { localStorage.setItem(KEY, String(v)); } catch { /* bỏ qua */ }
    setOpening(v);
  };

  return (
    <Page kicker="Dự báo" title="Dòng tiền"
      lead="Tiền vào lấy từ thanh toán thực tế trong phần mềm Spa; tiền ra lấy từ chi phí và nợ nhà cung cấp anh/chị nhập tay."
      right={<div className="seg">{[30, 60, 90].map((n) => <button key={n} className={days === n ? 'on' : ''} onClick={() => setDays(n)}>{n} ngày</button>)}</div>}>
      <div className="card pad toolbar">
        <label className="fld inline"><span>Số dư tiền mặt + tài khoản hiện tại</span>
          <input inputMode="numeric" value={input} onChange={(e) => setInput(e.target.value)} placeholder="Ví dụ 350000000" />
        </label>
        <button className="btn sm" onClick={apply}>Cập nhật</button>
        <button className="btn sm ghost" onClick={() => go('expenses')}>Nhập chi phí / nợ NCC</button>
      </div>
      {d.loading && <Loading />}
      {d.error && <ErrorBox error={d.error} retry={d.reload} />}
      {d.data && (() => {
        const c = d.data;
        const fc = c.days.filter((x) => x.forecast);
        const last = fc.at(-1);
        return (
          <>
            <div className="stats">
              <Stat label="Tiền vào trung bình / ngày" value={money(c.avgDailyIn)} sub="8 tuần gần nhất" />
              <Stat label={`Số dư dự kiến sau ${days} ngày`} value={money(last?.balance ?? 0)} tone={(last?.balance ?? 0) < 0 ? 'bad' : 'good'} />
              <Stat label="Số dư thấp nhất" value={money(c.minBalance)} tone={c.minBalance < 0 ? 'bad' : undefined} />
              <Stat label="Ngày có thể âm tiền" value={c.firstNegative ? dmy(c.firstNegative) : 'Không'} tone={c.firstNegative ? 'bad' : 'good'} />
            </div>
            <section className="card pad">
              <div className="card-t">Tiền vào 30 ngày qua (cột đậm) và dự báo (cột nhạt) · đường xanh = số dư dự kiến</div>
              <Bars data={c.days.map((x) => ({ label: dm(x.date), value: x.in - x.out, ghost: x.forecast }))}
                colorOf={(x) => (x.value < 0 ? 'var(--bad)' : x.ghost ? 'rgba(129,140,248,.35)' : 'var(--brand)')}
                line={c.days.map((x) => x.balance)} height={260} />
            </section>
            <section className="card">
              <div className="card-t pad-t">Khoản chi sắp tới</div>
              <Table rows={c.upcoming} max={15} empty="Chưa có khoản chi nào được nhập" cols={[
                { key: 'date', label: 'Ngày', type: 'date' }, { key: 'label', label: 'Khoản' },
                { key: 'kind', label: 'Loại', render: (r) => (r.kind === 'supplier' ? 'Nợ NCC' : 'Chi phí định kỳ') },
                { key: 'amount', label: 'Số tiền', type: 'money' }]} />
            </section>
            <ul className="notes">{c.notes.map((n, i) => <li key={i}>{n}</li>)}</ul>
          </>
        );
      })()}
    </Page>
  );
}
