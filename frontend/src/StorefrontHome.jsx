import React from 'react';
import { ArrowUpRight, ArrowRight, Shirt, Layers, MoveUpRight } from 'lucide-react';

export default function StorefrontHome({ products, onCatalog, onProduct }) {
  const featured = products.find(product => product.imageUrl) || null;
  return <>
    <section className="shop-editorial" aria-label="Khám phá phong cách Anh Lớn Shop">
      <div className="shop-editorial-copy">
        <p className="shop-eyebrow"><span/> EVERYDAY ESSENTIALS / ANH LỚN SHOP</p>
        <h1>Mặc đơn giản.<br/><em>Chất riêng</em><br/>không đơn điệu.</h1>
        <p className="shop-editorial-description">Một tủ đồ dễ phối. Một phong cách của riêng bạn.<br/>Khám phá những thiết kế dành cho nhịp sống mỗi ngày.</p>
        <div className="shop-editorial-actions"><button className="button shop-primary" onClick={() => onCatalog('')}>Khám phá sản phẩm <ArrowUpRight size={19}/></button><button className="shop-text-link" onClick={() => onCatalog('Áo thun')}>Bắt đầu với áo thun <ArrowRight size={17}/></button></div>
        <div className="shop-editorial-foot"><span>01 / THE EVERYDAY EDIT</span><span>Ít cầu kỳ. Nhiều cá tính.</span></div>
      </div>
      <div className="shop-editorial-visual">
        {featured ? <img className="shop-editorial-image" src={featured.imageUrl} alt={featured.name} fetchPriority="high" onError={event => { event.currentTarget.style.opacity = '0'; }}/> : <div className="shop-editorial-placeholder"><Shirt size={96} strokeWidth={1}/><span>ANH LỚN / EVERYDAY WEAR</span></div>}
        <span className="shop-editorial-stamp">CHỌN CHẤT RIÊNG<br/><b>ANH LỚN</b></span>
        <div className="shop-editorial-caption"><span>THE NEW<br/><strong>DAILY UNIFORM.</strong></span><MoveUpRight size={36} strokeWidth={1}/></div>
        {featured && <button className="shop-featured-link" type="button" aria-label={`KHÁM PHÁ THIẾT KẾ: ${featured.name}`} title={`Xem ${featured.name}`} onClick={() => onProduct(featured)}><span><small>KHÁM PHÁ THIẾT KẾ</small><b>{featured.name}</b></span><ArrowUpRight size={22} aria-hidden="true"/></button>}
      </div>
    </section>
    <div className="shop-style-line" aria-hidden="true"><span>ĐƠN GIẢN TRONG THIẾT KẾ</span><i>✳</i><span>TỰ TIN TRONG PHONG CÁCH</span><i>✳</i><span>LINH HOẠT MỖI NGÀY</span></div>
    <section className="shop-collections" aria-labelledby="shop-collection-title">
      <div className="shop-section-title"><div><p className="shop-eyebrow">TÌM PHONG CÁCH CỦA BẠN</p><h2 id="shop-collection-title">Tủ đồ, theo cách của bạn.</h2></div><button className="shop-text-link" onClick={() => onCatalog('')}>Tất cả danh mục <ArrowUpRight size={18}/></button></div>
      <div className="shop-collection-grid">{[
        ['Áo thun','Dễ mặc. Dễ là chính mình.','01',Shirt],
        ['Áo khoác','Thêm một lớp cá tính.','02',Layers],
        ['Áo polo','Chỉn chu, không gò bó.','03',Shirt],
        ['Quần','Thoải mái theo từng bước.','04',Layers],
      ].map(([name, subtitle, number, Icon]) => <button className="shop-collection-tile" key={name} onClick={() => onCatalog(name)}><div className="shop-collection-top"><span>{number}</span><Icon size={30} strokeWidth={1.3}/></div><div><h3>{name}</h3><p>{subtitle}</p></div><ArrowUpRight className="shop-collection-arrow" size={22}/></button>)}</div>
    </section>
  </>;
}
