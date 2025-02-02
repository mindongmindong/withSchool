package com.withSchool.entity.payment;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PaymentFail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentFailId;

    private LocalDate failDate;
    private String failReason;

    @ColumnDefault("1")
    private int attempts;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;
}
