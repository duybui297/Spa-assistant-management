// Định dạng số liệu tiền Việt (đơn vị đồng; hiển thị "triệu" cho gọn).
export function money(v: number | null | undefined, opts: { sign?: boolean; full?: boolean } = {}): string {
  const n = Number(v ?? 0);
  const s = opts.sign && n > 0 ? '+' : n < 0 ? '−' : '';
  const a = Math.abs(n);
  if (opts.full) return `${s}${Math.round(a).toLocaleString('vi-VN')} đ`;
  if (a >= 1e9) return `${s}${(a / 1e9).toFixed(2).replace(/\.?0+$/, '').replace('.', ',')} tỷ`;
  if (a >= 1e6) return `${s}${(a / 1e6).toFixed(1).replace(/\.0$/, '').replace('.', ',')} tr`;
  return `${s}${Math.round(a).toLocaleString('vi-VN')} đ`;
}

export const vnd = (v: number | null | undefined) => money(v, { full: true });

export const pct = (v: number | null | undefined, d = 1) =>
  v === null || v === undefined ? '—' : `${v > 0 ? '+' : v < 0 ? '−' : ''}${Math.abs(v).toFixed(d).replace(/\.0$/, '').replace('.', ',')}%`;

export const pctPlain = (v: number | null | undefined) =>
  v === null || v === undefined ? '—' : `${String(Number(v)).replace('.', ',')}%`;

const WD = ['Chủ nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];

const toDate = (d: string | Date) => (d instanceof Date ? d : new Date(d.length === 10 ? d + 'T00:00:00' : d));

export const dmy = (d: string | Date | null | undefined) => {
  if (!d) return '—';
  const x = toDate(d);
  return `${String(x.getDate()).padStart(2, '0')}/${String(x.getMonth() + 1).padStart(2, '0')}/${x.getFullYear()}`;
};
export const dm = (d: string | Date) => {
  const x = toDate(d);
  return `${String(x.getDate()).padStart(2, '0')}/${String(x.getMonth() + 1).padStart(2, '0')}`;
};
export const wdDmy = (d: string | Date) => `${WD[toDate(d).getDay()]}, ${dmy(d)}`;
export const dmyHm = (d: string | null | undefined) => {
  if (!d) return '—';
  const x = toDate(d);
  return `${dmy(x)} ${String(x.getHours()).padStart(2, '0')}:${String(x.getMinutes()).padStart(2, '0')}`;
};

export const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
