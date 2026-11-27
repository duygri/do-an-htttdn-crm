import {useEffect,useRef,useState} from 'react';
import {api} from './api';
export default function useMembershipCheckout(cart){
 const [code,setCode]=useState(''),[applied,setApplied]=useState(''),[revision,setRevision]=useState(0);
 const [result,setResult]=useState(null),[error,setError]=useState(''),[pending,setPending]=useState(true);
 const sequence=useRef(0);
 const items=cart.items.map(({productId,quantity,size,color,variantId})=>({productId,quantity,size,color,...(variantId!=null?{variantId}:{})}));
 const key=JSON.stringify([items,cart.subtotal,applied,revision]);
 useEffect(()=>{
  let live=true;const request=++sequence.current;setPending(true);setError('');
  api('/api/orders/preview',{method:'POST',body:{items,voucherCode:applied||undefined}}).then(data=>{if(live&&request===sequence.current)setResult({key,data});}).catch(reason=>{if(live&&request===sequence.current)setError(reason.message);}).finally(()=>{if(live&&request===sequence.current)setPending(false);});
  return()=>{live=false;};
 },[key]);
 const quote=result?.key===key?result.data:null;
 const change=value=>{setCode(value.toUpperCase());setApplied('');setError('');};
 return {code,quote,voucher:quote?.voucher||null,appliedCode:applied,busy:pending||!quote&&!error,error,subtotal:quote?.afterMemberAmount??0,
  change,remove:()=>change(''),apply:()=>{if(!code.trim()){setError('Vui lòng nhập mã giảm giá.');return;}setApplied(code.trim().toUpperCase());setRevision(v=>v+1);},retry:()=>setRevision(v=>v+1)};
}
