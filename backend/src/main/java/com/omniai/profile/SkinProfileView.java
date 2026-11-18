package com.omniai.profile;

import java.time.Instant;
import java.util.List;

/** Hồ sơ da trả cho web và ai-brain (api-contracts.md, mục "Hồ sơ da"). */
public record SkinProfileView(Long customerId, String skinType, List<String> skinConcerns, List<String> allergies,
                              boolean pregnant, SkinProfile.Source source, Instant healthConsentAt, Instant updatedAt) {

    static SkinProfileView of(SkinProfile p) {
        return new SkinProfileView(p.getCustomerId(), p.getSkinType(), List.copyOf(p.getSkinConcerns()),
                List.copyOf(p.getAllergies()), p.isPregnant(), p.getSource(), p.getHealthConsentAt(), p.getUpdatedAt());
    }
}
