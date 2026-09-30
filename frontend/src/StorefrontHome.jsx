import React from 'react';
import {
  ArrowUpRight,
  ArrowRight,
  CircleCheck,
  Shirt,
  Sparkles,
  Layers,
  Tag,
} from 'lucide-react';

const COLLECTIONS = [
  {
    code: '01',
    title: 'Áo Polo',
    category: 'Áo polo',
    description: 'Daily Uniform · Phom đứng thanh lịch, chất vải dệt tổ ong thoáng mát.',
    icon: Shirt,
  },
  {
    code: '02',
    title: 'Áo Khoác',
    category: 'Áo khoác',
    description: 'Outerwear · Thiết kế cản gió nhẹ, phom bomber & jacket hiện đại.',
    icon: Layers,
  },
  {
    code: '03',
    title: 'Áo Thun',
    category: 'Áo thun',
    description: 'Essential Tees · 100% Cotton định lượng cao, không bai dão sau giặt.',
    icon: Tag,
  },
  {
    code: '04',
    title: 'Quần Nam',
    category: 'Quần',
    description: 'Bottoms · Quần tây & kaki co giãn nhẹ, phom chuẩn cho mọi dịp.',
    icon: Sparkles,
  },
];

// Static collection artwork from Figma 4:3896. Product cards remain API-driven.
export default function StorefrontHome({ onCatalog }) {
  return (
    <>
      <section className="shop-editorial" aria-label="Khám phá phong cách Anh Lớn Shop">
        <div className="shop-editorial-copy">
          <p className="shop-eyebrow">BỘ SƯU TẬP / DAILY UNIFORM</p>
          <h1>Mặc đơn giản.<br/><em>Chất riêng</em><br/>không đơn điệu.</h1>
          <p className="shop-editorial-description">Những phom dáng chuẩn chỉnh, chất liệu tốt và bảng màu dễ phối — xây tủ đồ nam hiện đại cho mọi ngày.</p>
          <div className="shop-editorial-actions">
            <button className="button shop-primary" onClick={() => onCatalog('')}>Khám phá hàng mới <ArrowUpRight size={16}/></button>
            <button className="button button-light" onClick={() => onCatalog('Áo polo')}>Xem Daily Uniform</button>
          </div>
          <div className="shop-editorial-foot">
            <span><CircleCheck size={14}/> Thiết kế dễ phối</span>
            <span><CircleCheck size={14}/> Đổi size trong 30 ngày</span>
            <span><CircleCheck size={14}/> Giao hàng toàn quốc</span>
          </div>
        </div>
        <div className="shop-editorial-visual">
          <img className="shop-editorial-image" src="/images/figma/daily-uniform.png" alt="Bộ sưu tập Daily Uniform — áo polo màu san hô" fetchPriority="high"/>
          <button className="shop-featured-link" type="button" aria-label="Khám phá bộ sưu tập Daily Uniform" onClick={() => onCatalog('Áo polo')}>
            <span><small>THẾ HỆ MỚI</small><b>Daily Uniform.</b></span>
            <span className="shop-collection-cta"><ArrowRight size={18} aria-hidden="true"/></span>
          </button>
        </div>
      </section>

      {/* Featured Collections / Categories Grid */}
      <section className="shop-collections" aria-label="Bộ sưu tập nổi bật">
        <div className="shop-section-title">
          <div>
            <p className="shop-eyebrow">DANH MỤC NỔI BẬT</p>
            <h2>Chọn phong cách <i>cho bạn.</i></h2>
          </div>
          <button
            type="button"
            className="button button-light"
            onClick={() => onCatalog('')}
          >
            Xem tất cả danh mục <ArrowUpRight size={16} />
          </button>
        </div>
        <div className="shop-collection-grid">
          {COLLECTIONS.map((col) => {
            const Icon = col.icon;
            return (
              <button
                key={col.category}
                type="button"
                className="shop-collection-tile"
                onClick={() => onCatalog(col.category)}
              >
                <div className="shop-collection-top">
                  <span>{col.code}</span>
                  <Icon size={22} />
                </div>
                <h3>{col.title}</h3>
                <p>{col.description}</p>
                <span className="shop-collection-arrow" aria-hidden="true">
                  <ArrowUpRight size={18} />
                </span>
              </button>
            );
          })}
        </div>
      </section>
    </>
  );
}
