import React, { useId, useState, useRef, useEffect, useMemo } from 'react';
import { ChevronDown, Search, Check, X } from 'lucide-react';
import provinces from './data/vietnam-addresses.json';

export function normalizeSearchText(text) {
  return String(text || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
    .trim();
}

const normalized = text => String(text || '').normalize('NFC').trim().replace(/\s+/g, ' ').toLocaleLowerCase('vi');
const unique = (items, name) => { const matches = items.filter(item => normalized(item.name) === normalized(name)); return matches.length === 1 ? matches[0] : null; };
export const phoneDigits = value => String(value || '').replace(/[^0-9]/g, '');
export const validPhone = value => /^0[0-9]{9}$/.test(value || '');
export function matchAddress(address) {
  const province = unique(provinces, address.province);
  const ward = province ? unique(province.wards, address.ward) : null;
  return { province, ward };
}
export function prepareAddress(address) {
  const { province, ward } = matchAddress(address);
  return { ...address, district: '', province: province?.name || '', ward: ward?.name || '' };
}
export function shippingErrors(form) {
  const errors = {};
  if (!validPhone(form.phone)) errors.phone = 'Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.';
  const { province, ward } = matchAddress(form);
  if (!province || !ward) errors.location = 'Vui lòng chọn Tỉnh/Thành phố và Phường/Xã hợp lệ.';
  if (!form.recipientName?.trim() || !form.addressLine?.trim()) errors.details = 'Vui lòng nhập người nhận và số nhà, tên đường.';
  return errors;
}
export function PhoneField({ value, onChange, error, disabled }) {
  const id = useId();
  return <div className="shop-shipping-field"><label htmlFor={id}>SỐ ĐIỆN THOẠI</label><input id={id} type="tel" inputMode="numeric" autoComplete="tel-national" required pattern="0[0-9]{9}" value={value ?? ''} disabled={disabled} aria-invalid={!!error} aria-describedby={error ? `${id}-error` : undefined} onChange={event => onChange(phoneDigits(event.target.value))} placeholder="Ví dụ: 0912345678"/>{error && <p className="shop-field-error" id={`${id}-error`} role="alert">{error}</p>}</div>;
}

function SearchableSelect({
  id,
  error,
  describedBy,
  placeholder,
  searchPlaceholder,
  items = [],
  selectedCode = '',
  disabled = false,
  onSelect,
}) {
  const [open, setOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [activeIndex, setActiveIndex] = useState(0);
  const containerRef = useRef(null);
  const inputRef = useRef(null);
  const triggerRef = useRef(null);
  const close = (restoreFocus = false) => {
    setOpen(false);
    if (restoreFocus) triggerRef.current?.focus();
  };
  const choose = item => { if (item) { onSelect(item); close(true); } };

  const selectedItem = items.find(item => String(item.code) === String(selectedCode));

  useEffect(() => {
    if (!open) {
      setSearchQuery('');
      return;
    }
    inputRef.current?.focus();

    const handleClickOutside = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('pointerdown', handleClickOutside);
    return () => {
      document.removeEventListener('pointerdown', handleClickOutside);
    };
  }, [open]);
  useEffect(() => { if (disabled) setOpen(false); }, [disabled]);

  const filteredItems = useMemo(() => {
    if (!searchQuery.trim()) return items;
    const query = normalizeSearchText(searchQuery);
    return items.filter(item => normalizeSearchText(item.name).includes(query));
  }, [items, searchQuery]);
  useEffect(() => {
    if (open) document.getElementById(`${id}-option-${activeIndex}`)?.scrollIntoView?.({ block: 'nearest' });
  }, [open, activeIndex, id, filteredItems]);

  return (
    <div
      className={`shop-searchable-select ${disabled ? 'disabled' : ''} ${open ? 'is-open' : ''}`}
      ref={containerRef}
      onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget)) close(); }}
      onKeyDown={event => {
        if (event.key === 'Escape' && open) { event.preventDefault(); event.stopPropagation(); close(true); }
      }}
    >
      <button
        type="button"
        id={id}
        ref={triggerRef}
        className="shop-searchable-trigger"
        disabled={disabled}
        onClick={() => { setActiveIndex(0); setOpen(prev => !prev); }}
        onKeyDown={event => {
          if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault(); setActiveIndex(event.key === 'ArrowUp' ? items.length - 1 : 0); setOpen(true);
          }
        }}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={`${id}-list`}
        aria-labelledby={`${id}-label ${id}-value`}
        aria-invalid={!!error}
        aria-describedby={describedBy}
      >
        <span id={`${id}-value`} className={selectedItem ? 'shop-searchable-val' : 'shop-searchable-ph'}>
          {selectedItem ? selectedItem.name : placeholder}
        </span>
        <ChevronDown size={17} className={`shop-searchable-arrow ${open ? 'rotated' : ''}`} />
      </button>

      {open && !disabled && (
        <div className="shop-searchable-popover">
          <div className="shop-searchable-search-wrap">
            <Search size={15} className="shop-searchable-search-icon" />
            <input
              ref={inputRef}
              type="text"
              role="combobox"
              aria-label={searchPlaceholder}
              aria-expanded="true"
              aria-autocomplete="list"
              aria-controls={`${id}-list`}
              aria-activedescendant={filteredItems[activeIndex] ? `${id}-option-${activeIndex}` : undefined}
              className="shop-searchable-input"
              value={searchQuery}
              onChange={e => { setSearchQuery(e.target.value); setActiveIndex(0); }}
              onKeyDown={event => {
                if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                  event.preventDefault();
                  setActiveIndex(current => filteredItems.length ? (current + (event.key === 'ArrowDown' ? 1 : -1) + filteredItems.length) % filteredItems.length : 0);
                } else if (event.key === 'Enter') { event.preventDefault(); choose(filteredItems[activeIndex]); }
              }}
              placeholder={searchPlaceholder}
              onClick={e => e.stopPropagation()}
            />
            {searchQuery && (
              <button
                type="button"
                className="shop-searchable-clear"
                onClick={(e) => {
                  e.stopPropagation();
                  setSearchQuery('');
                  setActiveIndex(0);
                  inputRef.current?.focus();
                }}
                aria-label="Xóa tìm kiếm"
              >
                <X size={14} />
              </button>
            )}
          </div>

          <div className="shop-searchable-options" role="listbox" id={`${id}-list`} aria-labelledby={`${id}-label`}>
            {filteredItems.length === 0 ? (
              <div className="shop-searchable-empty" role="status">
                Không tìm thấy kết quả phù hợp
              </div>
            ) : (
              filteredItems.map((item, index) => {
                const isSelected = String(item.code) === String(selectedCode);
                return (
                  <button
                    key={item.code}
                    type="button"
                    id={`${id}-option-${index}`}
                    tabIndex={-1}
                    className={`shop-searchable-option ${isSelected ? 'is-selected' : ''} ${index === activeIndex ? 'is-active' : ''}`}
                    onPointerDown={event => event.preventDefault()}
                    onClick={() => choose(item)}
                    role="option"
                    aria-selected={isSelected}
                  >
                    <span>{item.name}</span>
                    {isSelected && <Check size={16} className="shop-searchable-check" />}
                  </button>
                );
              })
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export default function ShippingFields({ form, onChange, error, legacy, disabled }) {
  const id = useId();
  const { province, ward } = matchAddress(form);
  return <div className="shop-shipping-location">
    {legacy && <p className="shop-address-note">Địa chỉ đã lưu: {legacy}. Vui lòng kiểm tra và chọn lại tỉnh/phường theo danh mục mới; địa chỉ đã lưu chỉ thay đổi khi anh/chị bấm lưu.</p>}
    <div className="form-two-columns">
      <div className="shop-shipping-field">
        <label id={`${id}-province-label`} htmlFor={`${id}-province`}>TỈNH/THÀNH PHỐ</label>
        <SearchableSelect
          id={`${id}-province`}
          error={error}
          describedBy={error ? `${id}-error` : undefined}
          placeholder="Chọn Tỉnh/Thành phố"
          searchPlaceholder="Tìm kiếm tỉnh, thành phố..."
          items={provinces}
          selectedCode={province?.code || ''}
          disabled={disabled}
          onSelect={selected => {
            onChange({ province: selected?.name || '', ward: '', district: '' });
          }}
        />
      </div>
      <div className="shop-shipping-field">
        <label id={`${id}-ward-label`} htmlFor={`${id}-ward`}>PHƯỜNG/XÃ</label>
        <SearchableSelect
          id={`${id}-ward`}
          error={error}
          describedBy={error ? `${id}-error` : undefined}
          placeholder={province ? 'Chọn Phường/Xã' : 'Chọn tỉnh trước'}
          searchPlaceholder="Tìm kiếm phường, xã..."
          items={province?.wards || []}
          selectedCode={ward?.code || ''}
          disabled={disabled || !province}
          onSelect={selected => {
            onChange({ ward: selected?.name || '', district: '' });
          }}
        />
      </div>
    </div>
    {error && <p className="shop-field-error" id={`${id}-error`} role="alert">{error}</p>}
  </div>;
}
