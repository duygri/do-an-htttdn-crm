export const GROUP_LABELS = { day: 'ngày', month: 'tháng', quarter: 'quý', year: 'năm' };

export const CHART_PALETTE = ['#16a34a', '#ea580c', '#2563eb', '#7c3aed', '#d97706', '#0f766e', '#64748b'];

const money = (value) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(
    Number(value || 0)
  );

export function compactVnd(value) {
  const n = Number(value || 0);
  const abs = Math.abs(n);
  const sign = n < 0 ? '-' : '';
  const fmt = (x, unit) => `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(x)}${unit}`;
  if (abs >= 1e9) return sign + fmt(abs / 1e9, ' tỷ');
  if (abs >= 1e6) return sign + fmt(abs / 1e6, ' tr');
  if (abs >= 1e3) return sign + fmt(abs / 1e3, 'k');
  return sign + fmt(abs, '');
}

export function niceScale(rawMax) {
  if (!(rawMax > 0)) return { max: 0, ticks: [0] };
  const rough = rawMax / 4;
  const power = 10 ** Math.floor(Math.log10(rough));
  const norm = rough / power;
  const step = (norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 2.5 ? 2.5 : norm <= 5 ? 5 : 10) * power;
  const max = Math.ceil(rawMax / step) * step;
  const ticks = [];
  for (let value = 0; value <= max + step * 1e-6; value += step) ticks.push(Math.round(value * 100) / 100);
  return { max, ticks };
}

export function shortPeriod(period, groupBy) {
  if (!period) return '—';
  if (groupBy === 'day') return `${period.slice(8, 10)}/${period.slice(5, 7)}`;
  if (groupBy === 'month') return `${period.slice(5, 7)}/${period.slice(2, 4)}`;
  if (groupBy === 'quarter') {
    const [year, quarter] = period.split('-Q');
    return `Q${quarter}/${year.slice(2)}`;
  }
  return period;
}

export function moneyText(value) {
  return money(value);
}

const vnDate = (date) => {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date);
  const read = (type) => parts.find((part) => part.type === type)?.value;
  return { year: Number(read('year')), month: read('month'), day: read('day') };
};

/** Returns ISO instants for the backend report endpoint. `year` spans five calendar years. */
export function periodRange(preset = 'month', now = new Date()) {
  const { year, month, day } = vnDate(now);
  const startYear = preset === 'year' ? year - 4 : year;
  const from = new Date(`${startYear}-01-01T00:00:00+07:00`).toISOString();
  const today = new Date(`${year}-${month}-${day}T00:00:00+07:00`);
  const to = new Date(today.getTime() + 86400000).toISOString();
  const groupBy = ['month', 'quarter', 'year'].includes(preset) ? preset : 'month';
  return { from, to, groupBy };
}

export function reportQuery(range) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(range || {})) {
    if (value !== '' && value !== null && value !== undefined) query.set(key, String(value));
  }
  return query.toString();
}
