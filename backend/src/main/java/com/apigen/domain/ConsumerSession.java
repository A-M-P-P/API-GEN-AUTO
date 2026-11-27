package com.apigen.domain;
import jakarta.persistence.*;
@Entity
@Table(name = "consumer_sessions")
public class ConsumerSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long profileId;
    @Column(length = 200) public String subject;
    public Long clientId;
    @Column(nullable = false, length = 64, unique = true) public String accessHash;
    @Column(nullable = false, length = 64, unique = true) public String refreshHash;
    public java.time.Instant accessExpires;
    public java.time.Instant refreshExpires;
    public boolean revoked;
    @Column(columnDefinition = "CLOB") public String apiIds;
}
