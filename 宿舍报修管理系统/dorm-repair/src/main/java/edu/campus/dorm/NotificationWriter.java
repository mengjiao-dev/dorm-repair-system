package edu.campus.dorm;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.dao.DuplicateKeyException;
@Service
public class NotificationWriter {
 final JdbcTemplate db;public NotificationWriter(JdbcTemplate d){db=d;}
 @Transactional public void consume(String eventId){var rows=db.queryForList("SELECT * FROM outbox_event WHERE id=?",eventId);if(rows.isEmpty())throw new IllegalArgumentException("Unknown event");var e=rows.get(0);
  try{db.update("INSERT INTO notification(id,user_id,order_id,message,created_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP)",eventId,e.get("recipient_id"),e.get("order_id"),e.get("message"));}catch(DuplicateKeyException ignored){}
  db.update("UPDATE outbox_event SET state='DELIVERED',last_error=NULL WHERE id=?",eventId);
 }
}
