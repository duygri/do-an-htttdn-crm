import React from 'react';
import { SHIPMENT_LABELS, shipmentDate, shipmentTone } from './shipment-display';
import './order-display.css';

export default function ShipmentTimeline({ shipment, trackingCode }) {
  if (!shipment) return trackingCode ? <p className="order-address"><b>Mã theo dõi</b><br />{trackingCode}</p> : null;
  return (
    <section className="order-address" aria-label="Theo dõi vận chuyển">
      <b>Theo dõi vận chuyển</b>
      {/* Status stays in the same text node as the separator: the brief's test looks it up by exact text and it repeats as an event label below. */}
      <p data-tone={shipmentTone(shipment.status)}><span>{shipment.carrierName}</span> · {SHIPMENT_LABELS[shipment.status] || shipment.status}</p>
      <p>Mã vận đơn: {shipment.trackingCode}</p>
      <ol className="return-history">
        {(shipment.events || []).map((event, index) => (
          <li key={`${event.status}-${index}`} data-tone={shipmentTone(event.status)}>
            <b>{SHIPMENT_LABELS[event.status] || event.status}</b>
            <small>{shipmentDate(event.occurredAt)}</small>
            {event.note && <p>{event.note}</p>}
          </li>
        ))}
      </ol>
    </section>
  );
}
