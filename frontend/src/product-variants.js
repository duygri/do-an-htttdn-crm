export function availableStock(product, size, color) {
  if (!product.variantsAllocated) return product.stock;
  return product.variants?.find(v => v.active !== false && String(v.size).trim().toLowerCase() === String(size).trim().toLowerCase() && String(v.color).trim().toLowerCase() === String(color).trim().toLowerCase())?.stock ?? 0;
}

export function firstAvailableVariant(product) {
  if(product.variantsAllocated){
    const match=product.variants?.find(v=>v.active!==false&&v.stock>0);
    return match?{size:match.size,color:match.color,variantId:match.id}:{};
  }
  return {size:product.sizes?.split(',')[0]?.trim(),color:product.colors?.split(',')[0]?.trim()};
}

export function catalogStock(product) {
  return product.variantsAllocated
    ? (product.variants||[]).filter(v=>v.active!==false).reduce((sum,v)=>sum+Number(v.stock||0),0)
    : Number(product.stock||0);
}
