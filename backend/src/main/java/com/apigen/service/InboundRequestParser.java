package com.apigen.service;

import com.apigen.config.XmlMapperProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Analyse le corps des requêtes entrantes selon son Content-Type :
 * JSON, XML, texte, formulaire, multipart (fichiers) ou binaire.
 *
 * <p>Les fichiers et les contenus binaires sont exposés en base64 dans le contexte
 * {@code request.files.<nom>.base64} / {@code request.body}.</p>
 */
@Service
public class InboundRequestParser {

    private static final int MAX_BODY = 20 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final XmlMapper xmlMapper;

    public InboundRequestParser(ObjectMapper objectMapper, XmlMapperProvider xmlMapperProvider) {
        this.objectMapper = objectMapper;
        this.xmlMapper = xmlMapperProvider.mapper();
    }

    public void enrich(HttpServletRequest request, Map<String, Object> context) {
        String contentType = request.getContentType();
        context.put("contentType", contentType == null ? "" : contentType);
        try {
            String lower = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
            if (lower.startsWith("multipart/form-data")) {
                parseMultipart(request, context);
            } else {
                parseBody(request, context, lower);
            }
            String link = request.getParameter("url");
            if (link != null && !link.isBlank()) {
                context.put("link", link);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Corps de requête illisible : "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        }
    }

    private void parseBody(HttpServletRequest request, Map<String, Object> context, String contentType) throws Exception {
        byte[] bytes = readBytes(request);
        if (bytes.length == 0) {
            context.put("body", null);
            context.put("rawBody", "");
            return;
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        context.put("rawBody", text);
        if (contentType.contains("xml")) {
            context.put("body", xmlMapper.readValue(bytes, Object.class));
        } else if (contentType.contains("json")) {
            context.put("body", objectMapper.readValue(bytes, Object.class));
        } else if (contentType.contains("urlencoded")) {
            context.put("body", parseForm(text));
        } else if (contentType.startsWith("text/")) {
            context.put("body", text);
            if (isLink(text)) {
                context.put("link", text);
            }
        } else {
            // application/octet-stream, image/*, etc.
            context.put("body", Base64.getEncoder().encodeToString(bytes));
            context.put("binary", true);
            context.put("size", bytes.length);
        }
    }

    private void parseMultipart(HttpServletRequest request, Map<String, Object> context) throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        Map<String, Object> files = new LinkedHashMap<>();
        for (Part part : request.getParts()) {
            byte[] bytes = readAll(part.getInputStream());
            if (part.getSubmittedFileName() != null) {
                Map<String, Object> file = new LinkedHashMap<>();
                file.put("filename", part.getSubmittedFileName());
                file.put("contentType", part.getContentType());
                file.put("size", bytes.length);
                file.put("base64", Base64.getEncoder().encodeToString(bytes));
                files.put(part.getName(), file);
            } else {
                fields.put(part.getName(), new String(bytes, StandardCharsets.UTF_8));
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fields", fields);
        body.put("files", files);
        context.put("body", body);
        context.put("files", files);
    }

    private Map<String, Object> parseForm(String text) {
        Map<String, Object> form = new LinkedHashMap<>();
        for (String pair : text.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int index = pair.indexOf('=');
            String key = index < 0 ? pair : pair.substring(0, index);
            String value = index < 0 ? "" : pair.substring(index + 1);
            form.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return form;
    }

    private boolean isLink(String text) {
        String value = text.trim();
        return (value.startsWith("http://") || value.startsWith("https://")) && !value.contains(" ");
    }

    private byte[] readBytes(HttpServletRequest request) throws Exception {
        return readAll(request.getInputStream());
    }

    private byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BODY) {
                throw new IllegalArgumentException("Corps de requête trop volumineux (20 Mo maximum)");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
