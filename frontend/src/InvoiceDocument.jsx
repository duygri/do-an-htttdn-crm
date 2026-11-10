import React from 'react';
import './invoice-document.css';

const money = value => new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND',maximumFractionDigits:0}).format(Number(value||0));
const date = value => value ? new Date(value).toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh'}) : '—';
const paymentLabels = {PAYOS:'PayOS / VietQR',COD:'Thanh toán khi nhận hàng',PAID:'Đã thanh toán',PENDING:'Chưa thanh toán',CANCELLED:'Đã hủy',EXPIRED:'Hết hạn',FAILED:'Thất bại'};

export default function InvoiceDocument({invoice}){
  if(!invoice)return null;
  return <article className="invoice-document" aria-label={`Hóa đơn ${invoice.invoiceNumber}`}>
    <header className="invoice-head">
      <div><h2>{invoice.sellerName}</h2>{invoice.sellerAddress&&<p>{invoice.sellerAddress}</p>}{invoice.sellerPhone&&<p>Điện thoại: {invoice.sellerPhone}</p>}{invoice.sellerTaxCode&&<p>MST: {invoice.sellerTaxCode}</p>}</div>
      <div className="invoice-title"><h1>HÓA ĐƠN BÁN HÀNG</h1><p>Số: <b>{invoice.invoiceNumber}</b></p><p>Ngày lập: {date(invoice.issuedAt)}</p><p>Đơn hàng: #{invoice.orderCode}</p></div>
    </header>
    <section className="invoice-parties">
      <div><h3>Người mua</h3><p><b>{invoice.buyerName}</b></p>{invoice.buyerPhone&&<p>Điện thoại: {invoice.buyerPhone}</p>}{invoice.buyerEmail&&<p>Email: {invoice.buyerEmail}</p>}{invoice.deliveryAddress&&<p>Địa chỉ: {invoice.deliveryAddress}</p>}</div>
      <div><h3>Thanh toán</h3><p>Phương thức: {paymentLabels[invoice.paymentMethod]||invoice.paymentMethod||'—'}</p><p>Trạng thái: {paymentLabels[invoice.paymentStatus]||invoice.paymentStatus||'—'}</p></div>
    </section>
    <table className="invoice-table">
      <thead><tr><th>STT</th><th>Sản phẩm</th><th>Size / Màu</th><th>SL</th><th>Đơn giá</th><th>Thành tiền</th></tr></thead>
      <tbody>{(invoice.items||[]).map((item,index)=><tr key={`${item.productName}-${index}`}><td>{index+1}</td><td>{item.productName}</td><td>{[item.size,item.color].filter(Boolean).join(' / ')||'—'}</td><td>{item.quantity}</td><td>{money(item.unitPrice)}</td><td>{money(item.lineTotal)}</td></tr>)}</tbody>
    </table>
    <div className="invoice-totals">
      <p><span>Tạm tính</span><b>{money(invoice.subtotal)}</b></p>
      {Number(invoice.discountAmount)>0&&<p><span>Giảm giá</span><b>-{money(invoice.discountAmount)}</b></p>}
      <p className="invoice-grand"><span>Tổng cộng</span><b>{money(invoice.totalAmount)}</b></p>
    </div>
    {invoice.note&&<p className="invoice-note"><b>Ghi chú:</b> {invoice.note}</p>}
    <footer className="invoice-signatures"><div><span>Người mua</span><small>Ký, ghi rõ họ tên</small></div><div><span>Người bán</span><small>Ký, ghi rõ họ tên</small></div></footer>
    <p className="invoice-actions no-print"><button type="button" className="admin-primary" onClick={()=>window.print()}>In hóa đơn</button></p>
  </article>;
}
