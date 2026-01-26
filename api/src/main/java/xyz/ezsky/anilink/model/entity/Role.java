package xyz.ezsky.anilink.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色实体类
 */
@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false, length = 50)
    private String roleCode;
    
    @Column(nullable = false, length = 100)
    private String roleName;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
