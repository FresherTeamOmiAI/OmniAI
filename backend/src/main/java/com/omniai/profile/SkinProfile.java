package com.omniai.profile;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ListIndexBase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Hồ sơ da của 1 khách (V20, US-19). Dùng chung mọi kênh đã liên kết với khách. Owner: LucVT. */
@Entity
@Table(name = "skin_profile")
@Getter
@Setter
@NoArgsConstructor
public class SkinProfile {

    public enum Source { FORM, CHAT }

    @Id
    @Column(name = "customer_id")
    private Long customerId;

    /** Mã bảng skin_type (oily, dry, combination, sensitive, normal); null nếu khách chưa nói. */
    @Column(name = "skin_type", length = 20)
    private String skinType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "skin_profile_concern", joinColumns = @JoinColumn(name = "customer_id"))
    @Column(name = "concern_code", length = 30)
    private Set<String> skinConcerns = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "skin_profile_allergy", joinColumns = @JoinColumn(name = "customer_id"))
    @OrderColumn(name = "position")
    @ListIndexBase(1)
    @Column(name = "name", length = 60)
    private List<String> allergies = new ArrayList<>();

    /** Mang thai hoặc cho con bú. */
    @Column(nullable = false)
    private boolean pregnant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Source source;

    @Column(name = "health_consent_at", nullable = false)
    private Instant healthConsentAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    SkinProfile(Long customerId) {
        this.customerId = customerId;
    }
}
