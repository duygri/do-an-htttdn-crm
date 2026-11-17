import React, { useCallback, useMemo, useState } from 'react';
import { adminApi } from './manager-api';
import { useManagerResource } from './useManagerResource';
import { periodRange, reportQuery } from './charts/chart-math';
import StatsPanel from './charts/StatsPanel';

/** Dashboard preview: one request per period, kept fresh by the shared 5s polling hook. */
export default function ManagerDashboardStatistics({ go }) {
  const [period, setPeriod] = useState('month');
  const range = useMemo(() => periodRange(period), [period]);
  const fetcher = useCallback(
    (signal) => adminApi('/api/manager/reports/revenue?' + reportQuery(range), { signal }),
    [range]
  );
  const { data, loading, error, backgroundError, load } = useManagerResource(fetcher, {
    keepPreviousData: true,
  });

  return (
    <section className="admin-panel manager-statistics-preview">
      <StatsPanel
        title="Thống kê doanh thu"
        eyebrow="TỔNG QUAN TÀI CHÍNH"
        period={period}
        onPeriodChange={setPeriod}
        data={data}
        loading={loading}
        error={error}
        backgroundError={backgroundError}
        onRetry={() => load(true)}
        onOpenFull={() => go('statistics')}
        variant="dashboard"
      />
    </section>
  );
}
