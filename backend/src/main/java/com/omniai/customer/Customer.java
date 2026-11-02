package com.omniai.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Khách hàng hợp nhất (1 người, nhiều danh tính kênh). Owner: LucVT. */
@Entity
@Table(name = "customer")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name")
    private String displayName;

    @Column(unique = true)
    private String email;

    @Column(unique = true, length = 20)
    private String phone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
