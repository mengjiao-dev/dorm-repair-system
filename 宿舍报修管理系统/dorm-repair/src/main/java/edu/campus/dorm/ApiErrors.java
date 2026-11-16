package edu.campus.dorm;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.dao.DataIntegrityViolationException;
@RestControllerAdvice
public class ApiErrors {
 @ExceptionHandler(Problem.class) ResponseEntity<?> problem(Problem e){return ResponseEntity.status(e.status).body(Map.of("message",e.getMessage()));}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> invalid(MethodArgumentNotValidException e){return ResponseEntity.badRequest().body(Map.of("message",e.getBindingResult().getFieldErrors().stream().findFirst().map(x->x.getField()+": "+x.getDefaultMessage()).orElse("参数不正确")));}
 @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) ResponseEntity<?> malformed(Exception e){return ResponseEntity.badRequest().body(Map.of("message","请求格式不正确"));}
 @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<?> upload(Exception e){return ResponseEntity.status(413).body(Map.of("message","图片不能超过 5 MB"));}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> duplicate(Exception e){return ResponseEntity.status(409).body(Map.of("message","数据冲突，请刷新后重试"));}
}
