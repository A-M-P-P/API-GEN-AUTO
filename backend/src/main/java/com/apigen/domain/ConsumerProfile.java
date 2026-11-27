package com.apigen.domain;
import jakarta.persistence.*;
@Entity
@Table(name = "consumer_profiles")
public class ConsumerProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long organizationId;
    @Column(nullable = false, length = 60) public String code;
    @Column(nullable = false, length = 150) public String name;
    public boolean active;
    @Column(name = "auth_mode", length = 20) public String mode = "LOCAL";
    @Column(columnDefinition = "CLOB") public String pipeline = "[]";
    @Column(columnDefinition = "CLOB") public String apiIds = "[]";
}
