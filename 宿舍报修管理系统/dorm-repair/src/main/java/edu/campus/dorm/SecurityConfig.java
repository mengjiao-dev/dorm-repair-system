package edu.campus.dorm;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
@Configuration
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean UserDetailsService users(JdbcTemplate db){return name->{
  var rows=db.queryForList("SELECT * FROM app_user WHERE username=?",name);
  if(rows.isEmpty())throw new UsernameNotFoundException("账号或密码错误");
  var u=rows.get(0);return User.withUsername(name).password((String)u.get("password_hash")).roles((String)u.get("role")).disabled(!Boolean.TRUE.equals(u.get("enabled"))).build();
 };}
 @Bean SecurityFilterChain filter(HttpSecurity h)throws Exception {
  h.authorizeHttpRequests(a->a.requestMatchers("/","/index.html","/app.js","/style.css","/api/csrf","/api/meta","/error").permitAll().anyRequest().authenticated())
   .csrf(c->c.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
   .formLogin(f->f.loginProcessingUrl("/api/login").successHandler((req,res,a)->{res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"ok\":true}");}).failureHandler((req,res,e)->{res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"message\":\"账号或密码错误\"}");}))
   .logout(l->l.logoutUrl("/api/logout").logoutSuccessHandler((req,res,a)->res.setStatus(204)))
   .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->{res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"message\":\"请先登录\"}");}).accessDeniedHandler((req,res,ex)->{res.setStatus(403);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"message\":\"无权操作或会话已变化，请刷新\"}");}));
  return h.build();
 }
}
