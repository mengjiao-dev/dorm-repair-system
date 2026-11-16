package edu.campus.dorm;
import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import org.springframework.boot.test.context.SpringBootTest;import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.test.web.servlet.MockMvc;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.mock.web.MockMultipartFile;import java.util.*;import java.util.concurrent.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","app.dispatch-delay=3600000","app.uploads=./target/test-uploads","spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"}) @AutoConfigureMockMvc
class WorkflowTests {
 @Autowired RepairService s;@Autowired JdbcTemplate db;@Autowired MockMvc mvc;@Autowired NotificationWriter notifications;
 Actor student,manager,worker;
 @BeforeEach void actors(){student=s.actor("student");manager=s.actor("manager");worker=s.actor("worker");}
 long create(){return s.create(student,new Contracts.Create("测试报修","现场设施出现损坏需要检修","水电维修","周五下午",UUID.randomUUID().toString()));}
 Contracts.Action a(int version){return new Contracts.Action(version,"测试处理说明",worker.id(),5);}
 @Test void completeWorkflowAndReturn(){long id=create();s.action(manager,id,"approve",a(0));s.action(manager,id,"assign",a(1));s.action(worker,id,"accept",a(2));s.action(worker,id,"finish",a(3));s.action(student,id,"return",a(4));s.action(worker,id,"finish",a(5));s.action(student,id,"verify",a(6));var d=s.detail(student,id);assertEquals("COMPLETED",d.get("status"));assertEquals(2,((Number)d.get("round_no")).intValue());assertEquals(8,((List<?>)d.get("logs")).size());assertNotNull(d.get("evaluation"));assertThrows(Problem.class,()->s.action(student,id,"verify",a(7)));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM evaluation WHERE order_id=?",Integer.class,id));}
 @Test void duplicateRequestAndConflictingPayload(){String r=UUID.randomUUID().toString();var c=new Contracts.Create("重复请求","同一请求重复发送测试","水电维修","周五",r);long id=s.create(student,c);assertEquals(id,s.create(student,c));assertThrows(Problem.class,()->s.create(student,new Contracts.Create("内容变化","同一请求重复发送测试","水电维修","周五",r)));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM repair_order WHERE request_id=?",Integer.class,r));}
 @Test void foreignStudentAndBuildingCannotAccess(){long id=create();assertThrows(Problem.class,()->s.detail(s.actor("student2"),id));assertThrows(Problem.class,()->s.action(s.actor("manager2"),id,"approve",a(0)));assertEquals("PENDING_REVIEW",s.detail(student,id).get("status"));}
 @Test void rolesAndStatesCannotBeSkipped(){long id=create();assertThrows(Problem.class,()->s.action(student,id,"approve",a(0)));assertThrows(Problem.class,()->s.action(manager,id,"assign",a(0)));assertThrows(Problem.class,()->s.action(worker,id,"finish",a(0)));}
 @Test void onlyOneConcurrentAssignment()throws Exception{long id=create();s.action(manager,id,"approve",a(0));ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);Callable<Boolean> job=()->{start.await();try{s.action(manager,id,"assign",a(1));return true;}catch(Problem e){return false;}};try{var x=pool.submit(job);var y=pool.submit(job);start.countDown();assertNotEquals(x.get(10,TimeUnit.SECONDS),y.get(10,TimeUnit.SECONDS));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM repair_log WHERE order_id=? AND action='ASSIGN'",Integer.class,id));}finally{pool.shutdownNow();}}
 @Test void unassignedWorkerCannotRead(){long id=create();s.action(manager,id,"approve",a(0));s.action(manager,id,"assign",a(1));assertThrows(Problem.class,()->s.detail(s.actor("electrician"),id));}
 @Test void rejectAndCancelRequireValidState(){long id=create();s.action(student,id,"cancel",a(0));assertEquals("CANCELLED",s.detail(student,id).get("status"));long other=create();assertThrows(Problem.class,()->s.action(manager,other,"reject",new Contracts.Action(0,"",null,null)));s.action(manager,other,"reject",a(0));assertEquals("REJECTED",s.detail(student,other).get("status"));}
 @Test void staleVersionRollsBack(){long id=create();s.action(manager,id,"approve",a(0));int before=db.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE order_id=?",Integer.class,id);assertThrows(Problem.class,()->s.action(manager,id,"assign",a(0)));assertEquals("PENDING_ASSIGN",s.detail(student,id).get("status"));assertEquals(before,db.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE order_id=?",Integer.class,id));}
 @Test void notificationIsIdempotent(){long id=create();String event=db.queryForObject("SELECT id FROM outbox_event WHERE order_id=? LIMIT 1",String.class,id);notifications.consume(event);notifications.consume(event);assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM notification WHERE id=?",Integer.class,event));}
 @Test void anonymousAndCsrfAreBlocked()throws Exception{mvc.perform(get("/api/repairs")).andExpect(status().isUnauthorized());mvc.perform(post("/api/repairs").with(user("student")).contentType("application/json").content("{}")).andExpect(status().isForbidden());}
 @Test void malformedPayloadIsRejected()throws Exception{mvc.perform(post("/api/repairs").with(user("student")).with(csrf()).contentType("application/json").content("{\"title\":\"\"}")).andExpect(status().isBadRequest());}
 @Test void fakeImageRejected()throws Exception{long id=create();mvc.perform(multipart("/api/repairs/"+id+"/attachments").file(new MockMultipartFile("file","test.png","image/png","not an image".getBytes())).with(user("student")).with(csrf())).andExpect(status().isBadRequest());assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM attachment WHERE order_id=?",Integer.class,id));}
 @Test void studentCannotUseAdminApi()throws Exception{mvc.perform(get("/api/admin/users").with(user("student"))).andExpect(status().isForbidden());}
 @Test void disabledAccountCannotUseExistingSession()throws Exception{db.update("UPDATE app_user SET enabled=FALSE WHERE username='student2'");try{mvc.perform(get("/api/me").with(user("student2"))).andExpect(status().isUnauthorized());}finally{db.update("UPDATE app_user SET enabled=TRUE WHERE username='student2'");}}
 @Test void paginationAndDataScope(){var data=s.list(student,"","",1,500);assertEquals(50,data.get("pageSize"));for(Object row:(List<?>)data.get("items"))assertEquals(student.id(),((Number)((Map<?,?>)row).get("student_id")).longValue());}

 @Test void realLoginSessionAndWrongPassword()throws Exception{
  mvc.perform(post("/api/login").with(csrf()).param("username","student").param("password","wrong")).andExpect(status().isUnauthorized());
  var result=mvc.perform(post("/api/login").with(csrf()).param("username","student").param("password","Demo@2026")).andExpect(status().isOk()).andReturn();
  var session=(org.springframework.mock.web.MockHttpSession)result.getRequest().getSession(false);
  mvc.perform(get("/api/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT"));
  mvc.perform(post("/api/logout").session(session).with(csrf())).andExpect(status().isNoContent());
 }
 @Test void validImageUploadAndScopedDownload()throws Exception{
  long id=create();var buffer=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(3,3,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",buffer);
  mvc.perform(multipart("/api/repairs/"+id+"/attachments").file(new MockMultipartFile("file","fixture.png","image/png",buffer.toByteArray())).with(user("student")).with(csrf())).andExpect(status().isOk());
  long fileId=db.queryForObject("SELECT id FROM attachment WHERE order_id=?",Long.class,id);
  mvc.perform(get("/api/attachments/"+fileId).with(user("student"))).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
  mvc.perform(get("/api/attachments/"+fileId).with(user("student2"))).andExpect(status().isNotFound());
 }
 @Test void logFailureRollsBackAssignment(){
  long id=create();s.action(manager,id,"approve",a(0));int before=db.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE order_id=?",Integer.class,id);
  db.execute("ALTER TABLE repair_log ADD CONSTRAINT test_failure CHECK (action <> 'ASSIGN' OR order_id <> "+id+")");
  try{assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->s.action(manager,id,"assign",a(1)));assertEquals("PENDING_ASSIGN",s.detail(student,id).get("status"));assertEquals(1,((Number)s.detail(student,id).get("version")).intValue());assertEquals(before,db.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE order_id=?",Integer.class,id));}finally{db.execute("ALTER TABLE repair_log DROP CONSTRAINT test_failure");}
 }
 @Test void disabledCategoryCannotBeUsed(){
  db.update("INSERT INTO category(name,enabled) VALUES('停用测试类别',FALSE)");
  assertThrows(Problem.class,()->s.create(student,new Contracts.Create("停用类别","测试停用类别无法报修","停用测试类别","下午",UUID.randomUUID().toString())));
 }
}
