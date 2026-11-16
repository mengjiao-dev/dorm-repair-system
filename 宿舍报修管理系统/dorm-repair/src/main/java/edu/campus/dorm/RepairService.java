package edu.campus.dorm;
import java.time.*;import java.util.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.HexFormat;
import org.springframework.stereotype.Service;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.annotation.Transactional;import org.springframework.dao.DuplicateKeyException;
@Service
public class RepairService {
 final JdbcTemplate db; final RepairMapper mapper;
 public RepairService(JdbcTemplate db,RepairMapper mapper){this.db=db;this.mapper=mapper;}
 public Actor actor(String username){var rows=db.queryForList("SELECT * FROM app_user WHERE username=? AND enabled=TRUE",username);if(rows.isEmpty())throw new Problem(401,"账号不可用");return Actor.from(rows.get(0));}
 public void scope(Actor a,RepairEntity o){
  if(o==null)throw new Problem(404,"工单不存在或无权访问");
  boolean ok=a.is("STUDENT")&&o.studentId==a.id() || a.is("MANAGER")&&Objects.equals(a.building(),o.building) || a.is("WORKER")&&Objects.equals(o.assigneeId,a.id());
  if(!ok)throw new Problem(404,"工单不存在或无权访问");
 }
 public RepairEntity scoped(Actor a,long id){var o=mapper.selectById(id);scope(a,o);return o;}
 String where(Actor a,List<Object> args){
  if(a.is("STUDENT")){args.add(a.id());return "o.student_id=?";}
  if(a.is("MANAGER")){args.add(a.building());return "o.building=?";}
  if(a.is("WORKER")){args.add(a.id());return "o.assignee_id=?";}
  return "1=0";
 }
 static final String VIEW="SELECT o.*, s.display_name AS student_name,w.display_name AS worker_name FROM repair_order o JOIN app_user s ON s.id=o.student_id LEFT JOIN app_user w ON w.id=o.assignee_id ";
 public Map<String,Object> list(Actor a,String status,String search,int page,int size){
  List<Object> args=new ArrayList<>();String w=where(a,args);
  if(status!=null&&!status.isBlank()){w+=" AND o.status=?";args.add(status);}
  if(search!=null&&!search.isBlank()){w+=" AND (o.title LIKE ? OR o.order_no LIKE ? OR o.room LIKE ?)";String s="%"+search+"%";args.add(s);args.add(s);args.add(s);}
  long total=db.queryForObject("SELECT COUNT(*) FROM repair_order o WHERE "+w,Long.class,args.toArray());
  int limit=Math.max(1,Math.min(size,50));args.add(limit);args.add((Math.max(1,page)-1)*limit);
  return Map.of("items",db.queryForList(VIEW+" WHERE "+w+" ORDER BY o.created_at DESC,o.id DESC LIMIT ? OFFSET ?",args.toArray()),"total",total,"page",Math.max(1,page),"pageSize",limit);
 }
 public Map<String,Object> detail(Actor a,long id){scoped(a,id);var o=new LinkedHashMap<>(db.queryForMap(VIEW+" WHERE o.id=?",id));
  o.put("logs",db.queryForList("SELECT l.*,u.display_name AS actor_name FROM repair_log l JOIN app_user u ON u.id=l.actor_id WHERE l.order_id=? ORDER BY l.id",id));
  o.put("attachments",db.queryForList("SELECT id,original_name,media_type,created_at FROM attachment WHERE order_id=? ORDER BY id",id));
  var e=db.queryForList("SELECT * FROM evaluation WHERE order_id=?",id);o.put("evaluation",e.isEmpty()?null:e.get(0));return o;
 }
 @Transactional
 public long create(Actor a,Contracts.Create c){
  a.require("STUDENT");if(a.building()==null||a.room()==null||a.room().isBlank())throw Problem.bad("尚未绑定住宿信息，请联系管理员");
  if(db.queryForObject("SELECT COUNT(*) FROM category WHERE name=? AND enabled=TRUE",Integer.class,c.category())==0)throw Problem.bad("报修类别不可用");
  String hash=hash(c.title().trim()+"\n"+c.description().trim()+"\n"+c.category()+"\n"+c.appointment().trim());
  var prior=db.queryForList("SELECT id,request_hash FROM repair_order WHERE student_id=? AND request_id=?",a.id(),c.requestId());
  if(!prior.isEmpty())return same(prior.get(0),hash);
  String no="BX"+LocalDate.now().toString().replace("-","")+UUID.randomUUID().toString().replace("-","").substring(0,10).toUpperCase();
  try {db.update("INSERT INTO repair_order(order_no,student_id,building,room,category,title,description,appointment,status,request_id,request_hash,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",no,a.id(),a.building(),a.room(),c.category(),c.title().trim(),c.description().trim(),c.appointment().trim(),"PENDING_REVIEW",c.requestId(),hash,LocalDateTime.now(),LocalDateTime.now());}
  catch(DuplicateKeyException ex){return same(db.queryForMap("SELECT id,request_hash FROM repair_order WHERE student_id=? AND request_id=?",a.id(),c.requestId()),hash);}
  long id=db.queryForObject("SELECT id FROM repair_order WHERE order_no=?",Long.class,no);log(id,a.id(),"CREATE","提交报修申请");
  for(var m:db.queryForList("SELECT id FROM app_user WHERE role='MANAGER' AND building=? AND enabled=TRUE",a.building()))event(id,((Number)m.get("id")).longValue(),"收到新报修："+c.title());
  return id;
 }
 long same(Map<String,Object> p,String hash){if(!hash.equals(p.get("request_hash")))throw new Problem(409,"同一请求编号不能提交不同内容");return ((Number)p.get("id")).longValue();}
 static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 void noteRequired(String s){if(s==null||s.trim().length()<2)throw Problem.bad("请填写至少两个字的处理说明");}
 @Transactional
 public void action(Actor a,long id,String action,Contracts.Action c){
  var o=scoped(a,id);String from=o.status,to=null;Long worker=o.assigneeId;String note=c.note()==null?"":c.note().trim();
  switch(action){
   case "approve" -> {a.require("MANAGER");if(from.equals("PENDING_REVIEW"))to="PENDING_ASSIGN";}
   case "reject" -> {a.require("MANAGER");noteRequired(note);if(from.equals("PENDING_REVIEW"))to="REJECTED";}
   case "assign" -> {a.require("MANAGER");if(c.workerId()==null)throw Problem.bad("请选择维修人员");
    var ws=db.queryForList("SELECT * FROM app_user WHERE id=? AND role='WORKER' AND enabled=TRUE",c.workerId());
    if(ws.isEmpty())throw Problem.bad("维修人员不可用");Actor w=Actor.from(ws.get(0));if(!Objects.equals(w.building(),o.building)||!("综合维修".equals(w.skill())||Objects.equals(w.skill(),o.category)))throw Problem.bad("维修人员楼栋或工种不匹配");
    if(from.equals("ASSIGNED"))noteRequired(note);if(from.equals("PENDING_ASSIGN")||from.equals("ASSIGNED")){to="ASSIGNED";worker=c.workerId();}
   }
   case "accept" -> {a.require("WORKER");if(from.equals("ASSIGNED"))to="REPAIRING";}
   case "finish" -> {a.require("WORKER");noteRequired(note);if(from.equals("REPAIRING"))to="PENDING_VERIFY";}
   case "verify" -> {a.require("STUDENT");if(c.score()==null)throw Problem.bad("请选择满意度评分");if(from.equals("PENDING_VERIFY"))to="COMPLETED";}
   case "return" -> {a.require("STUDENT");noteRequired(note);if(from.equals("PENDING_VERIFY"))to="REPAIRING";}
   case "cancel" -> {a.require("STUDENT");if(from.equals("PENDING_REVIEW")||from.equals("PENDING_ASSIGN"))to="CANCELLED";}
   default -> throw Problem.bad("未知操作");
  }
  if(to==null)throw Problem.conflict();
  String extra=action.equals("accept")?",accepted_at=COALESCE(accepted_at,CURRENT_TIMESTAMP(3))":action.equals("verify")?",finished_at=CURRENT_TIMESTAMP(3)":action.equals("return")?",round_no=round_no+1":"";
  int changed=db.update("UPDATE repair_order SET status=?,assignee_id=?,version=version+1,updated_at=CURRENT_TIMESTAMP(3)"+extra+" WHERE id=? AND status=? AND version=?",to,worker,id,from,c.version());
  if(changed!=1)throw Problem.conflict();
  if(action.equals("verify"))db.update("INSERT INTO evaluation(order_id,score,content,created_at) VALUES(?,?,?,?)",id,c.score(),note.length()>500?note.substring(0,500):note,LocalDateTime.now());
  log(id,a.id(),action.toUpperCase(),note.isBlank()?labels(action):note);
  event(id,o.studentId,labels(action)+"："+db.queryForObject("SELECT title FROM repair_order WHERE id=?",String.class,id));
  if(worker!=null&&(action.equals("assign")||action.equals("return")))event(id,worker,labels(action)+"，请查看工单");
 }
 static String labels(String a){return switch(a){case "approve"->"审核通过";case "reject"->"申请已退回";case "assign"->"任务已派单";case "accept"->"维修人员已接单";case "finish"->"维修完成，等待验收";case "verify"->"验收通过";case "return"->"申请返修";case "cancel"->"报修已取消";default->a;};}
 void log(long id,long actor,String action,String note){db.update("INSERT INTO repair_log(order_id,actor_id,action,note,created_at) VALUES(?,?,?,?,?)",id,actor,action,note,LocalDateTime.now());}
 void event(long id,long user,String msg){db.update("INSERT INTO outbox_event(id,order_id,recipient_id,message,state,next_retry_at,created_at) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID().toString(),id,user,msg,"PENDING",LocalDateTime.now(),LocalDateTime.now());}
 public Map<String,Object> stats(Actor a){List<Object> args=new ArrayList<>();String w=where(a,args);var all=db.queryForList("SELECT o.* FROM repair_order o WHERE "+w,args.toArray());Map<String,Long> counts=new LinkedHashMap<>();Map<String,Long> cats=new LinkedHashMap<>();for(var r:all){counts.merge((String)r.get("status"),1L,Long::sum);cats.merge((String)r.get("category"),1L,Long::sum);}var ratings=db.queryForList("SELECT e.score FROM evaluation e JOIN repair_order o ON o.id=e.order_id WHERE "+w,args.toArray());double avg=ratings.stream().mapToInt(r->((Number)r.get("score")).intValue()).average().orElse(0);return Map.of("total",all.size(),"counts",counts,"categories",cats,"rating",avg,"ratedCount",ratings.size());}
}
