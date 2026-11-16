package edu.campus.dorm;
public class Problem extends RuntimeException {
 final int status; public Problem(int status,String message){super(message);this.status=status;}
 public static Problem bad(String m){return new Problem(400,m);}
 public static Problem conflict(){return new Problem(409,"工单已变化或当前操作不允许，请刷新后重试");}
}
