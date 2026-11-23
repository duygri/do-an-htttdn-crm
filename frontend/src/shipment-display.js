export const SHIPMENT_LABELS = {
  CREATED: 'Đã tạo vận đơn',
  PICKED_UP: 'Đã lấy hàng',
  IN_TRANSIT: 'Đang trung chuyển',
  OUT_FOR_DELIVERY: 'Đang giao tới khách',
  DELIVERED: 'Đã giao',
  FAILED: 'Giao thất bại',
  RETURNED: 'Hoàn hàng',
};
export const EVENT_OPTIONS = ['PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED', 'RETURNED'];
export const SHIPMENT_TONE = {
  CREATED: 'neutral',
  PICKED_UP: 'info',
  IN_TRANSIT: 'info',
  OUT_FOR_DELIVERY: 'warning',
  DELIVERED: 'success',
  FAILED: 'danger',
  RETURNED: 'danger',
};
/** Happy-path order. FAILED/RETURNED are side branches available from any open shipment. */
const PROGRESSION = ['CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED'];
const BRANCHES = ['FAILED', 'RETURNED'];
/** Shipment states the backend refuses to mutate. */
const FROZEN_SHIPMENT_STATUSES = ['DELIVERED', 'RETURNED'];
/** Order states the backend refuses to mutate. */
const CLOSED_ORDER_STATUSES = ['CANCELLED', 'COMPLETED', 'DELIVERED'];
const RETURN_BLOCKS_CREATE = status => Boolean(status) && status !== 'NONE';
const RETURN_BLOCKS_EVENT = status => ['APPROVED', 'COMPLETED'].includes(status);

export const shipmentTone = status => SHIPMENT_TONE[status] || 'neutral';

/** Forward-only options: never offer a step already reached, except DELIVERED itself. */
export const nextEventOptions = status => {
  const index = PROGRESSION.indexOf(status);
  if (index < 0) return EVENT_OPTIONS.slice();
  return [...PROGRESSION.slice(index + 1), ...BRANCHES];
};

/**
 * Mirrors ShipmentService rules so the manager UI never offers a rejected action.
 * `canCreate`: only CONFIRMED/PREPARING orders, no return in progress, no shipment yet.
 * `canRecordEvent`: shipment exists, not frozen, order not closed, return not approved/completed.
 */
export const shipmentActions = detail => {
  const shipment = detail?.shipment || null;
  return {
    canCreate: Boolean(detail)
      && !shipment
      && ['CONFIRMED', 'PREPARING'].includes(detail.status)
      && !RETURN_BLOCKS_CREATE(detail.returnStatus),
    canRecordEvent: Boolean(shipment)
      && !FROZEN_SHIPMENT_STATUSES.includes(shipment.status)
      && !CLOSED_ORDER_STATUSES.includes(detail?.status)
      && !RETURN_BLOCKS_EVENT(detail?.returnStatus),
  };
};

export const shipmentDate = value => value ? new Date(value).toLocaleString('vi-VN') : '—';
