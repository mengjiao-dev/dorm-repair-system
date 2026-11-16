package edu.campus.dorm;
import com.baomidou.mybatisplus.annotation.*;
@TableName("repair_order")
public class RepairEntity {
 @TableId(type=IdType.AUTO) public Long id;
 public Long studentId,assigneeId; public String status,building,room,category; public Integer version;
 public Long getId(){return id;} public void setId(Long v){id=v;}
 public Long getStudentId(){return studentId;} public void setStudentId(Long v){studentId=v;}
 public Long getAssigneeId(){return assigneeId;} public void setAssigneeId(Long v){assigneeId=v;}
 public String getStatus(){return status;} public void setStatus(String v){status=v;}
 public String getBuilding(){return building;} public void setBuilding(String v){building=v;}
 public String getRoom(){return room;} public void setRoom(String v){room=v;}
 public String getCategory(){return category;} public void setCategory(String v){category=v;}
 public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
}
