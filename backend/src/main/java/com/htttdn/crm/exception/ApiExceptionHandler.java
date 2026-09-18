package com.htttdn.crm.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*; import org.springframework.web.bind.MethodArgumentNotValidException; import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> api(ApiException e){return ResponseEntity.status(e.status()).body(Map.of("code",e.code(),"message",e.getMessage()));}
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<?> notFound(Exception e){return ResponseEntity.status(404).body(Map.of("code","NOT_FOUND","message",Objects.toString(e.getMessage(),"Không tìm thấy dữ liệu.")));}
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> bad(Exception e){return ResponseEntity.badRequest().body(Map.of("code","VALIDATION_ERROR","message",e.getMessage()));}
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> invalid(MethodArgumentNotValidException e){
        Map<String,String> fields=new LinkedHashMap<>(); e.getBindingResult().getFieldErrors().forEach(x->fields.put(x.getField(),x.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("code","VALIDATION_ERROR","message","Dữ liệu chưa hợp lệ.","fields",fields));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(DataIntegrityViolationException e){return ResponseEntity.status(409).body(Map.of("code","CONFLICT","message","Dữ liệu đã tồn tại hoặc vi phạm ràng buộc."));}
}
