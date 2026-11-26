package com.htttdn.crm.service;

import com.htttdn.crm.exception.ApiException;
import org.springframework.http.HttpStatus;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

/** Decode JPEG/PNG after inspecting dimensions; WebP is container-validated without an optional native codec. */
final class AttachmentImageValidator {
    private AttachmentImageValidator(){}
    static void validate(byte[] bytes,String format){
        try {
            if("webp".equals(format)){webp(bytes);return;}
            try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
                var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IllegalArgumentException();var reader=readers.next();
                try {reader.setInput(stream,true,true);dimensions(reader.getWidth(0),reader.getHeight(0));if(reader.read(0)==null)throw new IllegalArgumentException();}
                finally{reader.dispose();}
            }
            if("jpg".equals(format)&&((bytes[bytes.length-2]&255)!=255||(bytes[bytes.length-1]&255)!=217))throw new IllegalArgumentException();
        }catch(Exception e){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ATTACHMENT","Ảnh không hợp lệ hoặc kích thước vượt quá 8192 px / 16 triệu điểm ảnh.");}
    }
    private static void dimensions(long w,long h){if(w<1||h<1||w>8192||h>8192||w*h>16_000_000)throw new IllegalArgumentException();}
    private static long le(byte[] b,int p,int n){if(p+n>b.length)throw new IllegalArgumentException();long v=0;for(int i=0;i<n;i++)v|=(long)(b[p+i]&255)<<(8*i);return v;}
    private static String tag(byte[] b,int p){if(p+4>b.length)throw new IllegalArgumentException();return new String(b,p,4,java.nio.charset.StandardCharsets.US_ASCII);}
    private static void webp(byte[] b){
        if(b.length<26||le(b,4,4)+8!=b.length)throw new IllegalArgumentException();boolean image=false;int at=12;long canvasW=0,canvasH=0;
        while(at<b.length){if(at+8>b.length)throw new IllegalArgumentException();String type=tag(b,at);long size=le(b,at+4,4);int start=at+8;if(size>b.length-start)throw new IllegalArgumentException();
            if(type.equals("ANIM")||type.equals("ANMF"))throw new IllegalArgumentException();
            if(type.equals("VP8X")){if(at!=12||size!=10||(b[start]&2)!=0)throw new IllegalArgumentException();canvasW=1+le(b,start+4,3);canvasH=1+le(b,start+7,3);dimensions(canvasW,canvasH);}
            if(type.equals("VP8 ")){if(image||size<11||(b[start]&1)!=0||le(b,start+3,3)!=0x2a019d)throw new IllegalArgumentException();long w=le(b,start+6,2)&0x3fff,h=le(b,start+8,2)&0x3fff;dimensions(w,h);if(canvasW!=0&&(w!=canvasW||h!=canvasH))throw new IllegalArgumentException();image=true;}
            if(type.equals("VP8L")){if(image||size<6||(b[start]&255)!=0x2f)throw new IllegalArgumentException();long bits=le(b,start+1,4);if((bits>>>29)!=0)throw new IllegalArgumentException();long w=(bits&0x3fff)+1,h=((bits>>>14)&0x3fff)+1;dimensions(w,h);if(canvasW!=0&&(w!=canvasW||h!=canvasH))throw new IllegalArgumentException();image=true;}
            at=start+(int)size+((int)size&1);if(at>b.length)throw new IllegalArgumentException();
        }if(!image)throw new IllegalArgumentException();
    }
}
