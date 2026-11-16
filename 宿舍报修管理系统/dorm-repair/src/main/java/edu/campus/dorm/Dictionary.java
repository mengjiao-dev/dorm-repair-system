package edu.campus.dorm;
import java.util.*;import java.time.Duration;
import org.springframework.stereotype.Service;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.data.redis.core.StringRedisTemplate;import org.springframework.beans.factory.annotation.Value;import com.fasterxml.jackson.databind.ObjectMapper;import com.fasterxml.jackson.core.type.TypeReference;
@Service
public class Dictionary {
 final JdbcTemplate db;final StringRedisTemplate redis;final ObjectMapper json;final boolean infra;
 public Dictionary(JdbcTemplate d,StringRedisTemplate r,ObjectMapper j,@Value("${app.infrastructure}")boolean i){db=d;redis=r;json=j;infra=i;}
 public List<Map<String,Object>> categories(){if(infra)try{String v=redis.opsForValue().get("dorm:categories");if(v!=null)return json.readValue(v,new TypeReference<>(){});}catch(Exception ignored){}
  var list=db.queryForList("SELECT id,name FROM category WHERE enabled=TRUE ORDER BY id");if(infra)try{redis.opsForValue().set("dorm:categories",json.writeValueAsString(list),Duration.ofSeconds(60+new Random().nextInt(20)));}catch(Exception ignored){}return list;}
 public void evict(){if(infra)try{redis.delete("dorm:categories");}catch(Exception ignored){}}
}
