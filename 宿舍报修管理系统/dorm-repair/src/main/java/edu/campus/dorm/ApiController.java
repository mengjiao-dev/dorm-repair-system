package edu.campus.dorm;
import java.util.*;import java.nio.file.*;import java.time.*;import java.io.*;
import javax.imageio.ImageIO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;import org.springframework.core.io.FileSystemResource;
import org.springframework.security.crypto.password.PasswordEncoder;
@RestController @RequestMapping("/api")
public class ApiController {
 final RepairService service;final JdbcTemplate db;final PasswordEncoder encoder;final Dictionary dictionaries;final Path uploads;final boolean demo;
 public ApiController(RepairService s,JdbcTemplate d,PasswordEncoder e,Dictionary dictionaries,@Value("${app.uploads}")String path,@Value("${app.demo}")boolean demo)throws IOException{service=s;db=d;encoder=e;this.dictionaries=dictionaries;uploads=Path.of(path).toAbsolutePath().normalize();Files.createDirectories(uploads);this.demo=demo;}
 Actor who(Authentication a){return service.actor(a.getName());}
 @GetMapping("/csrf") Map<String,String> csrf(CsrfToken t){return Map.of("token",t.getToken(),"headerName",t.getHeaderName());}
 @GetMapping("/meta") Map<String,Object> meta(){return Map.of("demo",demo,"name","宿舍报修管理系统");}
 @GetMapping("/me") Actor me(Authentication a){return who(a);}
 @GetMapping("/categories") List<Map<String,Object>> categories(){return dictionaries.categories();}
 @GetMapping("/workers") List<Map<String,Object>> workers(Authentication auth){Actor a=who(auth);a.require("MANAGER");return db.queryForList("SELECT u.id,u.display_name,u.skill,(SELECT COUNT(*) FROM repair_order o WHERE o.assignee_id=u.id AND o.status IN ('ASSIGNED','REPAIRING','PENDING_VERIFY')) AS workload FROM app_user u WHERE u.role='WORKER' AND u.enabled=TRUE AND u.building=?",a.building());}
 @GetMapping("/repairs") Map<String,Object> list(Authentication a,@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="10")int size){return service.list(who(a),status,search,page,size);}
 @GetMapping("/repairs/{id}") Map<String,Object> detail(Authentication a,@PathVariable long id){return service.detail(who(a),id);}
 @PostMapping("/repairs") Map<String,Long> create(Authentication a,@Valid @RequestBody Contracts.Create c){return Map.of("id",service.create(who(a),c));}
 @PostMapping("/repairs/{id}/{action}") Map<String,Boolean> act(Authentication a,@PathVariable long id,@PathVariable String action,@Valid @RequestBody Contracts.Action c){service.action(who(a),id,action,c);return Map.of("ok",true);}
 @GetMapping("/stats") Map<String,Object> stats(Authentication a){return service.stats(who(a));}
 @GetMapping("/notifications") List<Map<String,Object>> notifications(Authentication a){return db.queryForList("SELECT * FROM notification WHERE user_id=? ORDER BY created_at DESC LIMIT 50",who(a).id());}
 @PostMapping("/notifications/read") Map<String,Boolean> read(Authentication a){db.update("UPDATE notification SET is_read=TRUE WHERE user_id=?",who(a).id());return Map.of("ok",true);}
 @PostMapping("/repairs/{id}/attachments") Map<String,Object> upload(Authentication auth,@PathVariable long id,@RequestParam("file")MultipartFile file)throws IOException {
  Actor a=who(auth);var o=service.scoped(a,id);if(!(a.is("STUDENT")&&Set.of("PENDING_REVIEW","PENDING_ASSIGN").contains(o.status)||a.is("WORKER")&&o.status.equals("REPAIRING")))throw new Problem(403,"当前状态不允许上传图片");
  if(file.isEmpty()||file.getSize()>5*1024*1024)throw Problem.bad("图片大小需为 1 字节至 5 MB");
  byte[] bytes=file.getBytes();String type;
  if(bytes.length>8&&bytes[0]==(byte)0x89&&bytes[1]==0x50&&bytes[2]==0x4e&&bytes[3]==0x47)type="png";
  else if(bytes.length>3&&bytes[0]==(byte)0xff&&bytes[1]==(byte)0xd8&&bytes[2]==(byte)0xff)type="jpg";
  else throw Problem.bad("仅支持真实的 PNG 或 JPG 图片");
  try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw Problem.bad("图片无法解码");var reader=readers.next();try{reader.setInput(input);if((long)reader.getWidth(0)*reader.getHeight(0)>16000000L)throw Problem.bad("图片像素不能超过 1600 万");}finally{reader.dispose();}}
  var img=ImageIO.read(new ByteArrayInputStream(bytes));if(img==null)throw Problem.bad("图片无法解码");
  if(db.queryForObject("SELECT COUNT(*) FROM attachment WHERE order_id=?",Integer.class,id)>=6)throw Problem.bad("每张工单最多 6 张图片");
  String key=UUID.randomUUID()+"."+type;Path dest=uploads.resolve(key);ImageIO.write(img,type,dest.toFile());
  String name=file.getOriginalFilename()==null?"图片":Path.of(file.getOriginalFilename()).getFileName().toString();if(name.length()>150)name=name.substring(name.length()-150);
  try{db.update("INSERT INTO attachment(order_id,uploader_id,storage_key,media_type,original_name,created_at) VALUES(?,?,?,?,?,?)",id,a.id(),key,type.equals("png")?"image/png":"image/jpeg",name,LocalDateTime.now());}catch(RuntimeException e){Files.deleteIfExists(dest);throw e;}
  return Map.of("ok",true);
 }
 @GetMapping("/attachments/{id}") ResponseEntity<?> attachment(Authentication a,@PathVariable long id){var rows=db.queryForList("SELECT * FROM attachment WHERE id=?",id);if(rows.isEmpty())throw new Problem(404,"图片不存在");var r=rows.get(0);service.scoped(who(a),((Number)r.get("order_id")).longValue());Path path=uploads.resolve((String)r.get("storage_key")).normalize();if(!path.startsWith(uploads)||!Files.exists(path))throw new Problem(404,"图片不存在");return ResponseEntity.ok().contentType(MediaType.parseMediaType((String)r.get("media_type"))).header("Cache-Control","no-store").body(new FileSystemResource(path));}
 @GetMapping("/admin/users") List<Map<String,Object>> users(Authentication a){who(a).require("ADMIN");return db.queryForList("SELECT id,username,display_name,role,building,room,skill,enabled FROM app_user ORDER BY id");}
 @PostMapping("/admin/users") Map<String,Boolean> user(Authentication auth,@Valid @RequestBody Contracts.NewUser u){who(auth).require("ADMIN");if(u.role().equals("STUDENT")&&(u.room()==null||u.room().isBlank()))throw Problem.bad("学生必须绑定房间");if(u.role().equals("WORKER")&&(u.skill()==null||u.skill().isBlank()))throw Problem.bad("维修人员必须设置工种");db.update("INSERT INTO app_user(username,password_hash,display_name,role,building,room,skill) VALUES(?,?,?,?,?,?,?)",u.username(),encoder.encode(u.password()),u.name(),u.role(),u.building(),u.room(),u.skill());return Map.of("ok",true);}
 @PostMapping("/admin/users/{id}/toggle") Map<String,Boolean> toggle(Authentication auth,@PathVariable long id){who(auth).require("ADMIN");db.update("UPDATE app_user SET enabled=NOT enabled WHERE id=? AND role<>'ADMIN'",id);return Map.of("ok",true);}
 @GetMapping("/admin/events") List<Map<String,Object>> events(Authentication a){who(a).require("ADMIN");return db.queryForList("SELECT id,order_id,state,attempts,last_error,created_at FROM outbox_event ORDER BY created_at DESC LIMIT 100");}
 @PostMapping("/admin/events/{id}/retry") Map<String,Boolean> retry(Authentication a,@PathVariable String id){who(a).require("ADMIN");db.update("UPDATE outbox_event SET state='PENDING',attempts=0,next_retry_at=CURRENT_TIMESTAMP WHERE id=? AND state='FAILED'",id);return Map.of("ok",true);}
 @PostMapping("/admin/categories") Map<String,Boolean> category(Authentication a,@RequestBody Map<String,String> body){who(a).require("ADMIN");String name=body.getOrDefault("name","").trim();if(name.isBlank()||name.length()>40)throw Problem.bad("类别名称需为 1 至 40 字");db.update("INSERT INTO category(name) VALUES(?)",name);dictionaries.evict();return Map.of("ok",true);}
 @PostMapping("/admin/categories/{id}/toggle") Map<String,Boolean> toggleCategory(Authentication a,@PathVariable long id){who(a).require("ADMIN");db.update("UPDATE category SET enabled=NOT enabled WHERE id=?",id);dictionaries.evict();return Map.of("ok",true);}
 @GetMapping("/admin/categories") List<Map<String,Object>> allCategories(Authentication a){who(a).require("ADMIN");return db.queryForList("SELECT * FROM category ORDER BY id");}
}
