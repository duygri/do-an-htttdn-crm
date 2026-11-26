package com.htttdn.crm.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.util.unit.DataSize;

@Configuration
public class AttachmentMultipartConfig {
    @Bean public MultipartConfigElement multipartConfigElement(){
        var factory=new MultipartConfigFactory();factory.setMaxFileSize(DataSize.ofMegabytes(5));factory.setMaxRequestSize(DataSize.ofMegabytes(6));return factory.createMultipartConfig();
    }
}
