package com.apigen.domain;
import jakarta.persistence.*;
@Entity
@Table(name = "consumer_clients")
public class ConsumerClient {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long profileId;
    @Column(nullable = false, length = 100) public String clientId;
    @Column(nullable = false, length = 100) public String secretHash;
    public boolean active = true;
}
