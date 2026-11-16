package edu.campus.dorm;
import jakarta.validation.constraints.*;
public class Contracts {
 public record Create(@NotBlank @Size(max=80) String title,@NotBlank @Size(min=5,max=1000) String description,@NotBlank @Size(max=40) String category,@NotBlank @Size(max=100) String appointment,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,64}") String requestId){}
 public record Action(@Min(0) int version,@Size(max=1000) String note,Long workerId,@Min(1) @Max(5) Integer score){}
 public record NewUser(@Pattern(regexp="[a-zA-Z0-9_]{4,30}") @NotBlank String username,@Size(min=8,max=50) @NotBlank String password,@NotBlank @Size(max=40) String name,@Pattern(regexp="STUDENT|MANAGER|WORKER") @NotBlank String role,@NotBlank @Size(max=40) String building,@Size(max=20) String room,@Size(max=40) String skill){}
}
