package edu.campus.dorm;
import org.springframework.context.annotation.*;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.amqp.core.*;
@Configuration @ConditionalOnProperty(name="app.infrastructure",havingValue="true") @EnableRedisHttpSession(maxInactiveIntervalInSeconds=1800,redisNamespace="dorm:session")
public class InfrastructureConfig {
 @Bean DirectExchange repairExchange(){return new DirectExchange("dorm.events",true,false);}
 @Bean Queue repairQueue(){return QueueBuilder.durable("dorm.notify").deadLetterExchange("dorm.dead").deadLetterRoutingKey("failed").build();}
 @Bean Binding repairBinding(){return BindingBuilder.bind(repairQueue()).to(repairExchange()).with("notify");}
 @Bean DirectExchange deadExchange(){return new DirectExchange("dorm.dead",true,false);}
 @Bean Queue deadQueue(){return new Queue("dorm.notify.dead",true);}
 @Bean Binding deadBinding(){return BindingBuilder.bind(deadQueue()).to(deadExchange()).with("failed");}
}
