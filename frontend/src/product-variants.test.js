import {describe,it,expect} from 'vitest';
import {availableStock,firstAvailableVariant,catalogStock} from './product-variants';
describe('variant availability',()=>{
 it('does not advertise inactive stock as available in the catalog',()=>{
  expect(catalogStock({stock:20,variantsAllocated:true,variants:[{stock:20,active:false},{stock:0,active:true}]})).toBe(0);
 });
 it('selects an active in-stock pair rather than the first sold-out pair',()=>{
  expect(firstAvailableVariant({variantsAllocated:true,variants:[{id:1,size:'M',color:'Black',stock:0},{id:2,size:'L',color:'Blue',stock:2}]})).toEqual({variantId:2,size:'L',color:'Blue'});
 });
 it('keeps legacy common stock',()=>expect(availableStock({stock:8},'M','Black')).toBe(8));
 it('uses normalized selected pair and rejects missing or inactive variants',()=>{const p={stock:20,variantsAllocated:true,variants:[{size:'M',color:'Black',stock:3},{size:'L',color:'Blue',stock:7,active:false}]};expect(availableStock(p,' m ',' black ')).toBe(3);expect(availableStock(p,'M','Blue')).toBe(0);expect(availableStock(p,'L','Blue')).toBe(0);});
});
