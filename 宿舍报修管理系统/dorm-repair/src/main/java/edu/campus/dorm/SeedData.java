package edu.campus.dorm;
import org.springframework.stereotype.Component;import org.springframework.boot.ApplicationRunner;import org.springframework.boot.ApplicationArguments;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.security.crypto.password.PasswordEncoder;import org.springframework.beans.factory.annotation.Value;import java.time.*;import java.util.*;
@Component
public class SeedData implements ApplicationRunner {
 final JdbcTemplate db;final PasswordEncoder encoder;final RepairService service;final boolean demo;
 public SeedData(JdbcTemplate d,PasswordEncoder e,RepairService s,@Value("${app.demo}")boolean demo){db=d;encoder=e;service=s;this.demo=demo;}
 public void run(ApplicationArguments args){
  if(db.queryForObject("SELECT COUNT(*) FROM category",Integer.class)==0)for(String c:List.of("水电维修","门窗家具","网络设施","其他设施"))db.update("INSERT INTO category(name) VALUES(?)",c);
  if(db.queryForObject("SELECT COUNT(*) FROM app_user",Integer.class)>0)return;
  if(!demo){String pw=System.getenv("ADMIN_PASSWORD");if(pw==null||pw.length()<12)throw new IllegalStateException("首次完整部署请设置至少12位 ADMIN_PASSWORD");user("admin","系统管理员","ADMIN",null,null,null,pw);return;}
  String pw="Demo@2026";
  user("student","林同学","STUDENT","知行1号楼","302",null,pw);user("manager","陈老师","MANAGER","知行1号楼",null,null,pw);user("worker","王师傅","WORKER","知行1号楼",null,"综合维修",pw);user("admin","系统管理员","ADMIN",null,null,null,pw);
  user("student2","周同学","STUDENT","知行2号楼","205",null,pw);user("manager2","李老师","MANAGER","知行2号楼",null,null,pw);
  user("electrician","刘师傅","WORKER","知行1号楼",null,"水电维修",pw);
  Actor st=service.actor("student"),ma=service.actor("manager"),wo=service.actor("worker");
  long a=create(st,"卫生间水龙头持续滴水","卫生间洗手池水龙头关闭后仍持续滴水，希望检查阀芯。","水电维修");
  service.action(ma,a,"approve",new Contracts.Action(0,"信息完整，安排处理",null,null));service.action(ma,a,"assign",new Contracts.Action(1,"安排综合维修人员检查",wo.id(),null));service.action(wo,a,"accept",new Contracts.Action(2,"已联系同学，按预约上门",null,null));
  long b=create(st,"书桌抽屉滑轨松动","靠窗书桌抽屉右侧滑轨松动，拉开时容易脱落。","门窗家具");
  long c=create(st,"宿舍照明灯闪烁","宿舍中间照明灯间歇闪烁，影响晚间学习。","水电维修");
  service.action(ma,c,"approve",new Contracts.Action(0,"核实情况",null,null));service.action(ma,c,"assign",new Contracts.Action(1,"检查灯具线路",wo.id(),null));service.action(wo,c,"accept",new Contracts.Action(2,"准备维修",null,null));service.action(wo,c,"finish",new Contracts.Action(3,"更换损坏灯管并检查线路，照明恢复正常。",null,null));
  long d=create(st,"宿舍网口连接不稳定","书桌墙面网口偶尔断开，换网线后仍出现问题。","网络设施");
  service.action(ma,d,"approve",new Contracts.Action(0,"确认检查网口",null,null));service.action(ma,d,"assign",new Contracts.Action(1,"排查连接模块",wo.id(),null));service.action(wo,d,"accept",new Contracts.Action(2,"现场检查",null,null));service.action(wo,d,"finish",new Contracts.Action(3,"重新压接网口并测试连接。",null,null));service.action(st,d,"verify",new Contracts.Action(4,"网络恢复正常，处理及时。",null,5));
  create(service.actor("student2"),"房门把手松动","房门内侧把手固定螺丝松动，关门不方便。","门窗家具");
 }
 long create(Actor a,String t,String desc,String cat){return service.create(a,new Contracts.Create(t,desc,cat,"工作日 16:00—18:00",UUID.randomUUID().toString()));}
 void user(String u,String n,String r,String b,String room,String skill,String pw){db.update("INSERT INTO app_user(username,password_hash,display_name,role,building,room,skill) VALUES(?,?,?,?,?,?,?)",u,encoder.encode(pw),n,r,b,room,skill);}
}
