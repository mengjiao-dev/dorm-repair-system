package edu.campus.dorm;
import org.springframework.stereotype.Component;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.amqp.rabbit.annotation.RabbitListener;
@Component @ConditionalOnProperty(name="app.infrastructure",havingValue="true")
public class RabbitConsumer {
 final NotificationWriter writer;public RabbitConsumer(NotificationWriter w){writer=w;}
 @RabbitListener(queues="dorm.notify") public void consume(String eventId){writer.consume(eventId);}
}
