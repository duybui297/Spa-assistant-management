import React, { useState } from 'react';
import { api } from '../lib/api';
import { iso, money } from '../lib/format';
import { ErrorBox, Loading, Page, Table, useLoad } from '../components/kit';
import { useToast } from '../components/ui';

interface Expense { id: string; expenseDate: string; category: string; description: string | null; amount: number; recurring: boolean }
const CATS = ['Mặt bằng', 'Lương', 'Điện nước', 'Marketing', 'Vật tư', 'Bảo trì máy', 'Thuế phí', 'Khác'];

export default function Expenses() {
  const toast = useToast();
  const list = useLoad(() => api<Expense[]>('/manual/expenses'));
  const [f, setF] = useState<Omit<Expense, 'id'>>({ expenseDate: iso(new Date()), category: CATS[0], description: '', amount: 0, recurring: true });
  const [busy, setBusy] = useState(false);

  const add = async () => {
    setBusy(true);
    try {
      await api('/manual/expenses', { body: f });
      toast('Đã thêm chi phí');
      setF({ ...f, description: '', amount: 0 });
      list.reload();
    } catch (e: any) { toast(e.message, 'info'); } finally { setBusy(false); }
  };
  const [armed, setArmed] = useState<string | null>(null);
  const del = async (id: string) => {
    if (armed !== id) { setArmed(id); setTimeout(() => setArmed((a) => (a === id ? null : a)), 3000); return; }
    setArmed(null);
    try { await api(`/manual/expenses/${id}`, { method: 'DELETE' }); list.reload(); } catch (e: any) { toast(e.message, 'info'); }
  };

  const byMonth = new Map<string, number>();
  (list.data ?? []).forEach((e) => byMonth.set(e.expenseDate.slice(0, 7), (byMonth.get(e.expenseDate.slice(0, 7)) ?? 0) + e.amount));

  return (
    <Page kicker="Dữ liệu ngoài phần mềm Spa" title="Nhập chi phí"
      lead="Phần mềm Spa không có chi phí vận hành. Nhập ở đây để màn Lợi nhuận, Dòng tiền và Mô phỏng có số. Khoản đánh dấu 'Hằng tháng' được dùng để dự báo dòng tiền.">
      <section className="card pad form">
        <div className="row4">
          <label className="fld"><span>Ngày chi</span><input type="date" value={f.expenseDate} onChange={(e) => setF({ ...f, expenseDate: e.target.value })} /></label>
          <label className="fld"><span>Loại</span>
            <input list="cats" value={f.category} onChange={(e) => setF({ ...f, category: e.target.value })} />
            <datalist id="cats">{CATS.map((c) => <option key={c} value={c} />)}</datalist>
          </label>
          <label className="fld"><span>Số tiền (đ)</span><input type="number" value={f.amount || ''} onChange={(e) => setF({ ...f, amount: Number(e.target.value) })} /></label>
          <label className="fld check"><input type="checkbox" checked={f.recurring} onChange={(e) => setF({ ...f, recurring: e.target.checked })} /><span>Hằng tháng</span></label>
        </div>
        <label className="fld"><span>Ghi chú</span><input value={f.description ?? ''} onChange={(e) => setF({ ...f, description: e.target.value })} /></label>
        <button className="btn" disabled={busy || !f.amount || !f.category} onClick={add}>Thêm chi phí</button>
      </section>
      {list.loading && <Loading />}
      {list.error && <ErrorBox error={list.error} retry={list.reload} />}
      {list.data && (
        <>
          <div className="chips">{Array.from(byMonth.entries()).sort().reverse().map(([m, v]) => <span key={m} className="chip">T{Number(m.slice(5))}/{m.slice(2, 4)}: <b>{money(v)}</b></span>)}</div>
          <section className="card">
            <Table rows={list.data} max={40} empty="Chưa nhập chi phí nào" cols={[
              { key: 'expenseDate', label: 'Ngày', type: 'date' }, { key: 'category', label: 'Loại' },
              { key: 'description', label: 'Ghi chú' }, { key: 'recurring', label: 'Hằng tháng', render: (e) => (e.recurring ? 'Có' : '') },
              { key: 'amount', label: 'Số tiền', type: 'money' },
              { key: 'x', label: '', render: (e) => <button className={`btn sm ${armed === e.id ? '' : 'ghost'}`} onClick={() => del(e.id)}>{armed === e.id ? 'Bấm lần nữa để xóa' : 'Xóa'}</button> }]} />
          </section>
        </>
      )}
    </Page>
  );
}
