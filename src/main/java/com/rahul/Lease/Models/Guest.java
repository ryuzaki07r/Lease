package com.rahul.Lease.Models;

import com.rahul.Lease.Enums.Gender;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "Guest")
public class Guest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String name;

    private Integer age;

    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    private Gender gender;
}
