package edu.campus.dorm;
import org.springframework.stereotype.Component;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.beans.factory.annotation.Value;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.amqp.rabbit.core.RabbitTemplate;import org.springframework.amqp.rabbit.connection.CorrelationData;import org.springframework.amqp.core.MessageDeliveryMode;import java.time.*;import java.util.concurrent.TimeUnit;
@Component
public class OutboxDispatcher {
 final JdbcTemplate db;final RabbitTemplate rabbit;final NotificationWriter writer;final boolean infra;
 public OutboxDispatcher(JdbcTemplate d,RabbitTemplate r,NotificationWriter w,@Value("${app.infrastructure}")boolean i){db=d;rabbit=r;writer=w;infra=i;}
 @Scheduled(fixedDelayString="${app.dispatch-delay:3000}") public void dispatch(){
  var rows=db.queryForList("SELECT * FROM outbox_event WHERE state IN ('PENDING','PROCESSING','SENT') AND next_retry_at<=CURRENT_TIMESTAMP ORDER BY created_at LIMIT 20");
  for(var e:rows){String id=(String)e.get("id");int n=((Number)e.get("attempts")).intValue();
   if(db.update("UPDATE outbox_event SET state='PROCESSING',attempts=attempts+1,next_retry_at=? WHERE id=? AND state IN ('PENDING','PROCESSING','SENT') AND next_retry_at<=CURRENT_TIMESTAMP",LocalDateTime.now().plusSeconds(60),id)!=1)continue;
   try{if(infra){var c=new CorrelationData(id);rabbit.convertAndSend("dorm.events","notify",id,m->{m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);return m;},c);var confirm=c.getFuture().get(5,TimeUnit.SECONDS);if(!confirm.isAck()||c.getReturned()!=null)throw new IllegalStateException("Broker rejected or unroutable");db.update("UPDATE outbox_event SET state='SENT',next_retry_at=? WHERE id=? AND state='PROCESSING'",LocalDateTime.now().plusMinutes(2),id);}else writer.consume(id);}
   catch(Exception ex){db.update("UPDATE outbox_event SET state=?,last_error=?,next_retry_at=? WHERE id=? AND state<>'DELIVERED'",n>=4?"FAILED":"PENDING","通知投递失败："+ex.getClass().getSimpleName(),LocalDateTime.now().plusSeconds(Math.min(60,(n+1)*5)),id);}
  }
 }
}
