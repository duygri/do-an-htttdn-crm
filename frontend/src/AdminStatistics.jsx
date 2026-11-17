import React, { useCallback, useEffect, useRef, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { adminApi } from './api';
import StatsPanel from './charts/StatsPanel';
import { periodRange, reportQuery } from './charts/chart-math';
import './admin-statistics.css';

function useAdminStatistics(period, refreshKey = 0) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [backgroundError, setBackgroundError] = useState('');
  const [reload, setReload] = useState(0);
  const dataRef = useRef(null);

  useEffect(() => {
    const controller = new AbortController();
    let alive = true;
    setLoading(true);
    setError('');
    setBackgroundError('');
    const query = reportQuery(periodRange(period));
    adminApi('/api/admin/reports/statistics?' + query, { signal: controller.signal })
      .then((result) => {
        if (!alive) return;
        dataRef.current = result;
        setData(result);
      })
      .catch((failure) => {
        if (!alive || controller.signal.aborted) return;
        if ([401, 403, 423].includes(failure.status)) {
          window.dispatchEvent(new Event('admin-session-expired'));
          return;
        }
        if (dataRef.current) setBackgroundError(failure.message || 'Không tải được số liệu thống kê.');
        else setError(failure.message || 'Không tải được số liệu thống kê.');
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
      controller.abort();
    };
  }, [period, refreshKey, reload]);

  const retry = useCallback(() => setReload((value) => value + 1), []);
  return { data, loading, error, backgroundError, retry };
}

export function AdminStatisticsWidget({ go, refreshKey = 0 }) {
  const [period, setPeriod] = useState('month');
  const { data, loading, error, backgroundError, retry } = useAdminStatistics(period, refreshKey);
  return (
    <div className="internal-section-card admin-statistics-widget">
      <StatsPanel
        title="Thống kê doanh thu"
        eyebrow="DỮ LIỆU TÀI CHÍNH"
        period={period}
        onPeriodChange={setPeriod}
        data={data}
        loading={loading}
        error={error}
        backgroundError={backgroundError}
        onRetry={retry}
        onOpenFull={() => go?.('/admin/statistics')}
        variant="dashboard"
      />
    </div>
  );
}

export default function AdminStatisticsPage() {
  const [period, setPeriod] = useState('month');
  const { data, loading, error, backgroundError, retry } = useAdminStatistics(period);
  return (
    <div className="internal-statistics-page">
      <div className="internal-section-card">
        <StatsPanel
          title="Thống kê doanh thu"
          eyebrow="THỐNG KÊ TOÀN HỆ THỐNG"
          period={period}
          onPeriodChange={setPeriod}
          data={data}
          loading={loading}
          error={error}
          backgroundError={backgroundError}
          onRetry={retry}
          showProducts
        />
      </div>
      <div className="internal-statistics-actions">
        <button type="button" className="internal-sync-btn" onClick={retry} title="Tải lại số liệu mới nhất">
          <RefreshCw size={13} />
          <span>Làm mới số liệu</span>
        </button>
      </div>
    </div>
  );
}
