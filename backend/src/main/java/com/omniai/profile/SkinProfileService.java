package com.omniai.profile;

import com.omniai.catalog.ProductErrors.FieldViolation;
import com.omniai.catalog.ProductErrors.Invalid;
import com.omniai.catalog.SkinConcernRepository;
import com.omniai.catalog.SkinTypeRepository;
import com.omniai.customer.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Đọc/ghi hồ sơ da (US-19, T-19.3, SRS UC-13). Owner: LucVT.
 * Web ghi với nguồn FORM, ai-brain ghi với nguồn CHAT sau khi khách xác nhận trong chat (T-19.1). Mọi lần ghi cần
 * {@code consent = true} vì đây là dữ liệu sức khoẻ (Luật 91/2025); thời điểm đồng ý đầu tiên được giữ lại.
 */
@Service
public class SkinProfileService {

    static final int MAX_ALLERGIES = 10;
    static final int MAX_ALLERGY_LENGTH = 60;

    private final SkinProfileRepository profiles;
    private final CustomerRepository customers;
    private final SkinTypeRepository skinTypes;
    private final SkinConcernRepository skinConcerns;
    private final Clock clock;

    @Autowired
    public SkinProfileService(SkinProfileRepository profiles, CustomerRepository customers, SkinTypeRepository skinTypes,
                              SkinConcernRepository skinConcerns) {
        this(profiles, customers, skinTypes, skinConcerns, Clock.systemUTC());
    }

    SkinProfileService(SkinProfileRepository profiles, CustomerRepository customers, SkinTypeRepository skinTypes,
                       SkinConcernRepository skinConcerns, Clock clock) {
        this.profiles = profiles;
        this.customers = customers;
        this.skinTypes = skinTypes;
        this.skinConcerns = skinConcerns;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SkinProfileView get(long customerId) {
        requireCustomer(customerId);
        return profiles.findById(customerId).map(SkinProfileView::of).orElseThrow(ProfileNotFound::new);
    }

    @Transactional
    public SkinProfileView save(long customerId, SkinProfileRequest request, SkinProfile.Source source) {
        requireCustomer(customerId);
        Clean clean = validate(request);
        Instant now = clock.instant();
        SkinProfile existing = profiles.findById(customerId).orElse(null);
        SkinProfile profile = existing != null ? existing : new SkinProfile(customerId);
        profile.setSkinType(clean.skinType());
        profile.getSkinConcerns().clear();
        profile.getSkinConcerns().addAll(clean.concerns());
        profile.getAllergies().clear();
        profile.getAllergies().addAll(clean.allergies());
        profile.setPregnant(clean.pregnant());
        profile.setSource(source);
        if (profile.getHealthConsentAt() == null) {
            profile.setHealthConsentAt(now);
        }
        profile.setUpdatedAt(now);
        // Chỉ save (merge) hồ sơ mới. Gọi merge lên entity đang quản lý sau khi xoá phần tử của Set làm Hibernate bỏ
        // thay đổi của Set đó (đo được khi viết T-19.3: JSON rỗng nhưng skin_profile_concern còn dòng cũ).
        return SkinProfileView.of(existing != null ? existing : profiles.save(profile));
    }

    /** Khách rút consent hoặc xoá hồ sơ. Không có hồ sơ thì thôi (xoá lặp lại không lỗi). */
    @Transactional
    public void delete(long customerId) {
        requireCustomer(customerId);
        profiles.findById(customerId).ifPresent(profiles::delete);
    }

    private void requireCustomer(long customerId) {
        if (!customers.existsById(customerId)) {
            throw new CustomerNotFound(customerId);
        }
    }

    private record Clean(String skinType, Set<String> concerns, List<String> allergies, boolean pregnant) {
    }

    private Clean validate(SkinProfileRequest r) {
        List<FieldViolation> errors = new ArrayList<>();
        if (r == null) {
            throw new Invalid(List.of(new FieldViolation("consent", "phải đồng ý cho shop lưu hồ sơ da")));
        }
        if (!Boolean.TRUE.equals(r.consent())) {
            errors.add(new FieldViolation("consent", "phải đồng ý cho shop lưu hồ sơ da"));
        }

        String skinType = r.skinType() == null || r.skinType().isBlank() ? null : r.skinType().trim().toLowerCase(Locale.ROOT);
        if (skinType != null && !skinTypes.existsById(skinType)) {
            errors.add(new FieldViolation("skinType", "loại da không hợp lệ: " + skinType));
        }

        Set<String> concerns = new LinkedHashSet<>();
        List<String> rawConcerns = r.skinConcerns() == null ? List.of() : r.skinConcerns();
        for (int i = 0; i < rawConcerns.size(); i++) {
            String code = rawConcerns.get(i) == null ? "" : rawConcerns.get(i).trim().toLowerCase(Locale.ROOT);
            if (code.isEmpty() || !skinConcerns.existsById(code)) {
                errors.add(new FieldViolation("skinConcerns[" + i + "]", "vấn đề da không hợp lệ: " + code));
            } else {
                concerns.add(code);
            }
        }

        Map<String, String> allergies = new LinkedHashMap<>();
        List<String> rawAllergies = r.allergies() == null ? List.of() : r.allergies();
        for (int i = 0; i < rawAllergies.size(); i++) {
            String name = rawAllergies.get(i) == null ? "" : rawAllergies.get(i).trim().replaceAll("\\s+", " ");
            if (name.isEmpty() || name.length() > MAX_ALLERGY_LENGTH) {
                errors.add(new FieldViolation("allergies[" + i + "]", "mỗi dị ứng dài 1 đến " + MAX_ALLERGY_LENGTH + " ký tự"));
            } else {
                allergies.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
            }
        }
        if (allergies.size() > MAX_ALLERGIES) {
            errors.add(new FieldViolation("allergies", "tối đa " + MAX_ALLERGIES + " dị ứng"));
        }

        if (!errors.isEmpty()) {
            throw new Invalid(errors);
        }
        return new Clean(skinType, concerns, List.copyOf(allergies.values()), Boolean.TRUE.equals(r.pregnant()));
    }

    public static class ProfileNotFound extends RuntimeException {
        public ProfileNotFound() {
            super("Khách chưa khai hồ sơ da");
        }
    }

    public static class CustomerNotFound extends RuntimeException {
        public CustomerNotFound(long customerId) {
            super("Không có khách id=" + customerId);
        }
    }
}
