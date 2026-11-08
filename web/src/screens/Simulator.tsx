import React, { useState } from 'react';
import { api } from '../lib/api';
import { money } from '../lib/format';
import { ErrorBox, Loading, Page, Stat, useLoad } from '../components/kit';
import type { ProfitData } from './Profit';

/** Mô phỏng "nếu… thì…" trên số của tháng đủ ngày gần nhất. Tính hoàn toàn bằng công thức ở trình duyệt. */
export default function Simulator() {
  const d = useLoad(() => api<ProfitData>('/assistant/profit', { query: { months: 3 } }));
  const [price, setPrice] = useState(0);
  const [visits, setVisits] = useState(0);
  const [comm, setComm] = useState(0);
  const [cost, setCost] = useState(0);
  const [newCost, setNewCost] = useState(0);
  if (d.loading) return <div className="screen"><Loading /></div>;
  if (d.error || !d.data) return <div className="screen"><ErrorBox error={d.error ?? ''} retry={d.reload} /></div>;
  const base = d.data.months.filter((m) => !m.partial).at(-1) ?? d.data.months[0];
  const sales = base.sales * (1 + price / 100) * (1 + visits / 100);
  const refunds = base.refunds * (1 + visits / 100);
  const commissions = base.commissions * (1 + price / 100) * (1 + visits / 100) * (1 + comm / 100);
  const expenses = base.expenseTotal * (1 + cost / 100) + newCost * 1e6;
  const profit = sales - refunds - commissions - expenses;
  const delta = profit - base.profit;

  const S = ({ label, v, set, min, max, unit = '%', step = 1 }: { label: string; v: number; set: (n: number) => void; min: number; max: number; unit?: string; step?: number }) => (
    <label className="sl">
      <div className="sl-h"><span>{label}</span><b>{v > 0 ? '+' : ''}{v}{unit}</b></div>
      <input type="range" min={min} max={max} step={step} value={v} onChange={(e) => set(Number(e.target.value))} />
    </label>
  );

  return (
    <Page kicker={`Dựa trên tháng ${base.month}`} title="Mô phỏng"
      lead="Kéo thanh trượt để xem lãi thay đổi thế nào. Chi phí lấy từ phần nhập tay; chưa nhập thì kết quả chưa phản ánh đúng.">
      <div className="grid-2">
        <section className="card pad sliders">
          {S({ label: "Tăng / giảm giá dịch vụ", v: price, set: setPrice, min: -30, max: 30 })}
          {S({ label: "Tăng / giảm lượt khách", v: visits, set: setVisits, min: -50, max: 50 })}
          {S({ label: "Thay đổi tỷ lệ hoa hồng", v: comm, set: setComm, min: -50, max: 50 })}
          {S({ label: "Thay đổi chi phí hiện có", v: cost, set: setCost, min: -30, max: 50 })}
          {S({ label: "Thêm chi phí mới mỗi tháng", v: newCost, set: setNewCost, min: 0, max: 200, step: 5, unit: " tr" })}
          <button className="btn sm ghost" onClick={() => { setPrice(0); setVisits(0); setComm(0); setCost(0); setNewCost(0); }}>Đặt lại</button>
        </section>
        <section className="sim-out">
          <div className="stats one">
            <Stat label="Lãi tháng sau mô phỏng" value={money(profit)} tone={profit < 0 ? 'bad' : 'good'} sub={`Hiện tại ${money(base.profit)} · ${delta >= 0 ? 'tăng' : 'giảm'} ${money(Math.abs(delta))}`} />
            <Stat label="Doanh số" value={money(sales)} sub={`Hiện tại ${money(base.sales)}`} />
            <Stat label="Hoa hồng" value={money(commissions)} sub={`Hiện tại ${money(base.commissions)}`} />
            <Stat label="Chi phí" value={money(expenses)} sub={`Hiện tại ${money(base.expenseTotal)}`} />
          </div>
          <p className="muted small">Giả định: hoa hồng và hoàn tiền thay đổi cùng tỷ lệ với doanh số; chi phí không đổi theo lượt khách.</p>
        </section>
      </div>
    </Page>
  );
}
