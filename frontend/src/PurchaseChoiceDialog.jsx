import React, { useEffect, useRef, useState } from 'react';
import { api, endpoints } from './api';
import { availableStock } from './product-variants';
import './PurchaseChoiceDialog.css';

const norm = value => String(value || '').trim().toLowerCase();
const options = value => [...new Set(String(value || '').split(',').map(v => v.trim()).filter(Boolean))];
const count = value => Math.max(0, Math.floor(Number(value) || 0));

export default function PurchaseChoiceDialog({ productId, buy = false, onClose, onConfirm }) {
  const dialog = useRef(null), lock = useRef(false);
  const [product, setProduct] = useState(null), [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0), [loading, setLoading] = useState(true);
  const [color, setColor] = useState(''), [size, setSize] = useState('');
  const [quantity, setQuantity] = useState(1), [sending, setSending] = useState(false);
  useEffect(() => {
    const previous = document.activeElement, overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const node = dialog.current;
    node.showModal?.();
    // The open attribute also supports environments without the dialog API.
    node.setAttribute('open', '');
    node.querySelector('button')?.focus();
    return () => {
      node.close?.();
      document.body.style.overflow = overflow;
      if (previous?.isConnected) previous.focus();
    };
  }, []);
  useEffect(() => {
    let live = true;
    setLoading(true); setError(''); setProduct(null); setColor(''); setSize(''); setQuantity(1);
    api(endpoints.product(productId)).then(data => {
      if (live) setProduct(data.product || data);
    }).catch(reason => { if (live) setError(reason.message || 'Không tải được sản phẩm.'); })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [productId, attempt]);
  const pairs = (product?.variants || []).filter(v => v.active !== false && count(v.stock) > 0);
  const colors = product?.variantsAllocated ? [...new Set(pairs.map(v => v.color).filter(Boolean))] : options(product?.colors);
  const sizes = product?.variantsAllocated ? [...new Set(pairs.map(v => v.size).filter(Boolean))] : options(product?.sizes);
  const stock = product ? count(availableStock(product, size, color)) : 0;
  const valid = !!color && !!size && stock > 0 && Number.isInteger(quantity) && quantity >= 1 && quantity <= stock;
  const dismiss = () => { if (!lock.current) onClose(); };
  const submit = async event => {
    event.preventDefault();
    if (!valid || lock.current) return;
    lock.current = true; setSending(true); setError('');
    const pair = product.variantsAllocated && pairs.find(v => norm(v.color) === norm(color) && norm(v.size) === norm(size));
    try {
      const result = await onConfirm(product, { color, size, ...(pair ? { variantId: pair.id } : {}) }, quantity);
      if (result) onClose();
      else setError('Chưa thêm được sản phẩm. Vui lòng kiểm tra tồn kho và thử lại.');
    } catch (reason) { setError(reason.message || 'Chưa thêm được sản phẩm. Vui lòng thử lại.'); }
    finally { lock.current = false; setSending(false); }
  };
  return <dialog ref={dialog} className="purchase-choice-dialog" aria-labelledby="purchase-choice-title" aria-modal="true"
    onCancel={event => { event.preventDefault(); dismiss(); }}
    onClick={event => { if (event.target === event.currentTarget) { const box = event.currentTarget.getBoundingClientRect(); if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) dismiss(); } }}
    onKeyDown={event => {
      if (event.key === 'Escape') { event.preventDefault(); dismiss(); }
      if (event.key === 'Tab') {
        const nodes = [...dialog.current.querySelectorAll('button:not(:disabled), input:not(:disabled)')];
        const first = nodes[0], last = nodes.at(-1);
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
      }
    }}>
    <form onSubmit={submit}>
      <header><h2 id="purchase-choice-title">Chọn sản phẩm</h2><button type="button" aria-label="Đóng lựa chọn sản phẩm" disabled={sending} onClick={dismiss}>×</button></header>
      {loading && <p role="status">Đang tải sản phẩm…</p>}
      {error && <p role="alert">{error}</p>}
      {!loading && !product && <button type="button" onClick={() => setAttempt(v => v + 1)}>Thử lại</button>}
      {product && <><h3>{product.name}</h3>
        <fieldset disabled={sending}><legend>Màu sắc</legend><div className="purchase-choice-options">{colors.map(item => <button type="button" key={item} aria-pressed={color === item} onClick={() => {
          setColor(item); setError('');
          if (product.variantsAllocated && availableStock(product, size, item) <= 0) setSize('');
          setQuantity(current => Math.max(1, Math.min(current, count(availableStock(product, size, item)))));
        }}>{item}</button>)}</div></fieldset>
        <fieldset disabled={sending}><legend>Kích thước</legend><div className="purchase-choice-options">{sizes.map(item => <button type="button" key={item} aria-pressed={size === item}
          disabled={product.variantsAllocated && (!color || availableStock(product, item, color) <= 0)} onClick={() => { setSize(item); setError(''); setQuantity(current => Math.max(1, Math.min(current, count(availableStock(product, item, color))))); }}>{item}</button>)}</div></fieldset>
        <label className="purchase-choice-quantity">Số lượng<input aria-label="Số lượng" type="number" min="1" max={stock || 1} step="1" value={quantity} disabled={sending || !size || !color || !stock}
          onChange={event => { const value = Number(event.target.value); setQuantity(Number.isFinite(value) ? Math.max(1, Math.min(stock || 1, Math.floor(value))) : 1); }}/></label>
        <p>{color && size ? `Còn ${stock} sản phẩm trong kho` : 'Vui lòng chọn màu sắc và kích thước.'}</p>
        <button className="purchase-choice-confirm" type="submit" disabled={!valid || sending}>{sending ? 'Đang xử lý…' : buy ? 'Xác nhận mua ngay' : 'Xác nhận thêm vào giỏ'}</button>
      </>}
    </form>
  </dialog>;
}
