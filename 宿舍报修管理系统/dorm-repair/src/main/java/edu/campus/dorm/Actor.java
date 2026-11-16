package edu.campus.dorm;
import java.util.Map;
public record Actor(long id,String username,String name,String role,String building,String room,String skill) {
 static Actor from(Map<String,Object> r){return new Actor(((Number)r.get("id")).longValue(),(String)r.get("username"),(String)r.get("display_name"),(String)r.get("role"),(String)r.get("building"),(String)r.get("room"),(String)r.get("skill"));}
 public boolean is(String r){return role.equals(r);} public void require(String r){if(!is(r))throw new Problem(403,"没有此操作权限");}
}
