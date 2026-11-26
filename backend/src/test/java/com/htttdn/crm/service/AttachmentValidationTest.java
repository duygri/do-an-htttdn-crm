package com.htttdn.crm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.htttdn.crm.entity.*;
import com.htttdn.crm.exception.ApiException;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class AttachmentValidationTest {
    static byte[] image(String format)throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),format,out);return out.toByteArray();}
    @Test void decodesPngAndJpegAndRejectsSpoofedMagic()throws Exception{
        for(String format:new String[]{"png","jpg"}){var bytes=image(format);assertEquals(format,AttachmentService.detect(bytes));assertDoesNotThrow(()->AttachmentImageValidator.validate(bytes,format));}
        assertThrows(ApiException.class,()->AttachmentService.detect("<svg></svg>".getBytes()));
        byte[] png={(byte)137,80,78,71,13,10,26,10,0,0,0};assertEquals("png",AttachmentService.detect(png));assertThrows(ApiException.class,()->AttachmentImageValidator.validate(png,"png"));
        assertThrows(ApiException.class,()->AttachmentImageValidator.validate(new byte[]{(byte)255,(byte)216,(byte)255},"jpg"));
        assertThrows(ApiException.class,()->AttachmentImageValidator.validate("RIFF0000WEBPinvalid".getBytes(),"webp"));
    }
    @Test void rejectsImageDimensionsBeforeLargeDecode()throws Exception{
        byte[] png=image("png");png[16]=0x7f;png[17]=(byte)255;png[18]=(byte)255;png[19]=(byte)255;assertThrows(ApiException.class,()->AttachmentImageValidator.validate(png,"png"));
    }
    @Test void entitiesNeverSerializePrivateLedgerOrRawReturnMetadata()throws Exception{
        var mapper=new ObjectMapper().findAndRegisterModules();var user=new User();user.pendingAttachments="INTERNAL_SECRET_PUBLIC_ID";
        var order=new Order();order.returnAttachments="INTERNAL_SECRET_PUBLIC_ID";order.setCustomer(user);
        String userJson=mapper.writeValueAsString(user),orderJson=mapper.writeValueAsString(order);
        assertFalse(userJson.contains("pendingAttachments"));assertFalse(userJson.contains("INTERNAL_SECRET_PUBLIC_ID"));assertFalse(orderJson.contains("returnAttachments"));assertFalse(orderJson.contains("INTERNAL_SECRET_PUBLIC_ID"));
    }
    @Test void multipartLimitAllowsFiveMbWithoutChangingYaml(){var config=new com.htttdn.crm.config.AttachmentMultipartConfig().multipartConfigElement();assertEquals(5*1024*1024,config.getMaxFileSize());assertEquals(6*1024*1024,config.getMaxRequestSize());}
}
