package com.htttdn.crm.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Buffers the request body once so a filter can inspect it and the controller can still deserialize it. */
final class CachedBodyRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    CachedBodyRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.body = request.getInputStream().readAllBytes();
    }

    byte[] cachedBody() { return body; }

    @Override
    public ServletInputStream getInputStream() {
        var stream = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override public boolean isFinished() { return stream.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) { }
            @Override public int read() { return stream.read(); }
        };
    }

    @Override
    public BufferedReader getReader() {
        Charset charset = getCharacterEncoding() == null ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding());
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }
}
