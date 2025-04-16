package org.example.model;

import lombok.Data;
import javax.persistence.*;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserType userType;

    @Column(nullable = false)
    private String address;

    private Long exchangeId;

    @Override
    public String toString() {
        return "User{" +
            "id=" + id +
            ", username='" + username + '\'' +
            ", address='" + address + '\'' +
            ", exchangeId=" + exchangeId +
            '}';
    }

    public enum UserType {
        GOVERNMENT_REGULATOR,
        MINTER,
        COMPANY
    }
} 