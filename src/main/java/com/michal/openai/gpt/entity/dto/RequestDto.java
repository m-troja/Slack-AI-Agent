package com.michal.openai.gpt.entity.dto;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
public class RequestDto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private String userSlackId;
    private String realName;
    private String role;

    @Column(columnDefinition = "text")
    private String content;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime timestamp;

    public RequestDto() {}

    public RequestDto(String content, String userSlackId, String realName, String role) {
        this.userSlackId = userSlackId;
        this.realName = realName;
        this.role = role;
        this.content = content;
    }
}
