import React, { useEffect, useRef, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { ArrowUpRight, RefreshCw } from "lucide-react";
import { api, endpoints } from "./api";
import "./payment-qr.css";

const money = value => Number(value || 0).toLocaleString("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 });

export default function PaymentQr({ orderId, initial = null, onPaid }) {
  const [payment, setPayment] = useState(initial), [error, setError] = useState(""), [loading, setLoading] = useState(!initial);
  const [retrying, setRetrying] = useState(false);
  const [now, setNow] = useState(Date.now());
  const sequence = useRef(0);
  const paidNotified = useRef(initial?.paymentStatus === "PAID");
  const paymentStatusRef = useRef(initial?.paymentStatus);
  const load = async (sync = false) => {
    const request = ++sequence.current;
    try {
      const latest = await api(sync ? endpoints.syncOrderPayment(orderId) : endpoints.orderPayment(orderId), sync ? { method: "POST" } : {});
      if (request === sequence.current) {
        setPayment(latest); paymentStatusRef.current = latest.paymentStatus; setError("");
        if (latest.paymentStatus === "PAID" && !paidNotified.current) {
          paidNotified.current = true;
          onPaid?.();
        }
      }
    } catch (cause) { if (request === sequence.current) setError(cause.message); }
    finally { if (request === sequence.current) setLoading(false); }
  };
  useEffect(() => {
    paidNotified.current = initial?.paymentStatus === "PAID";
    void load();
    const poll = window.setInterval(() => { if (!document.hidden && paymentStatusRef.current !== "PAID") void load(true); }, 10000);
    const tick = window.setInterval(() => setNow(Date.now()), 1000);
    return () => { sequence.current++; window.clearInterval(poll); window.clearInterval(tick); };
  }, [orderId]);

  const retry = async () => {
    if (retrying) return;
    setRetrying(true); setError("");
    try {
      const latest = await api(endpoints.retryOrderPayment(orderId), { method: "POST" });
      setPayment(latest); paymentStatusRef.current = latest.paymentStatus;
      if (latest.paymentStatus === "PAID" && !paidNotified.current) { paidNotified.current = true; onPaid?.(); }
    } catch (cause) { setError(cause.message); }
    finally { setRetrying(false); }
  };

  const expired = payment?.expiresAt && Date.parse(payment.expiresAt) <= now;
  const pending = payment?.paymentStatus === "PENDING" && !expired;
  const status = payment?.paymentStatus === "PAID" ? "Thanh toán thành công" : payment?.paymentStatus === "CANCELLED" ? payment?.retryable ? "Thanh toán chưa thành công" : "Đơn hàng đã hủy" : expired || payment?.paymentStatus === "EXPIRED" ? "Mã thanh toán đã hết hạn" : "Chưa thanh toán";
  return <section className="shop-payment-qr" aria-label="Thanh toán PayOS">
    <div className="shop-payment-qr-head"><div><h3>Quét mã QR thanh toán</h3><p>Đơn #{payment?.orderCode || initial?.orderCode}</p></div><button type="button" onClick={() => load(true)} aria-label="Cập nhật trạng thái thanh toán"><RefreshCw size={16}/></button></div>
    {loading && !payment ? <p role="status">Đang tải mã QR...</p> : payment && <>
      <p className="shop-payment-qr-status" role="status">{status}</p>
      {pending && <>
        {payment.qrCode ? <div className="shop-payment-qr-image"><QRCodeSVG value={payment.qrCode} size={220} marginSize={2} title={`Mã QR thanh toán đơn ${payment.orderCode}`} /></div> : <p>Đơn này chưa lưu mã QR. Bạn có thể mở trang PayOS để thanh toán.</p>}
        <strong>{money(payment.amount)}</strong>
        {payment.expiresAt && <p>Mã có hiệu lực đến {new Date(payment.expiresAt).toLocaleString("vi-VN")}</p>}
        {payment.paymentUrl && <a className="button button-dark" href={payment.paymentUrl} target="_blank" rel="noopener noreferrer">Thanh toán qua PayOS <ArrowUpRight size={16}/></a>}
      </>}
      {!pending && payment.retryable && <button type="button" className="button button-dark" disabled={retrying} onClick={retry}>{retrying ? "Đang tạo mã..." : "Tạo mã QR mới"}</button>}
    </>}
    {error && <p className="shop-inline-error" role="alert">Không cập nhật được trạng thái: {error} <button type="button" onClick={load}>Thử lại</button></p>}
  </section>;
}
