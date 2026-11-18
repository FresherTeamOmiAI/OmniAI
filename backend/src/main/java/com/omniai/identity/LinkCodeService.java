package com.omniai.identity;

import com.omniai.catalog.ProductErrors.FieldViolation;
import com.omniai.catalog.ProductErrors.Invalid;
import com.omniai.common.ChannelType;
import com.omniai.customer.ChannelIdentity;
import com.omniai.customer.ChannelIdentityRepository;
import com.omniai.customer.Customer;
import com.omniai.customer.CustomerRepository;
import com.omniai.identity.LinkErrors.CustomerGone;
import com.omniai.identity.LinkErrors.IdentityAlreadyLinked;
import com.omniai.identity.LinkErrors.LinkCodeExpired;
import com.omniai.identity.LinkErrors.LinkCodeInvalid;
import com.omniai.identity.LinkErrors.LinkError;
import com.omniai.identity.LinkErrors.LinkLocked;
import com.omniai.identity.LinkErrors.LinkRequestNotFound;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Mã liên kết OMNI-XXXXXX (US-22, SRS UC-08, NFR-08, G-01, decisions #49).
 * <ol>
 *   <li>{@link #issue}: khách đã đăng nhập web lấy mã, hạn 15 phút. Lấy mã mới thì mã cũ chưa dùng bị thu hồi.</li>
 *   <li>{@link #claim}: khách gõ mã trong chat sàn (T-22.3 gọi qua API nội bộ). Mã dùng 1 lần. Danh tính kênh
 *       gõ thất bại 5 lần trong 1 giờ thì bị khoá nhập mã.</li>
 *   <li>{@link #confirm}: khách xác nhận + tick consent trên web trong 15 phút kể từ lúc gõ mã thì danh tính sàn
 *       mới được gắn ({@code linked_by = LINK_CODE}). Danh tính đã gắn khách khác thì từ chối, không gộp.</li>
 * </ol>
 */
@Service
public class LinkCodeService {

    static final Duration CODE_TTL = Duration.ofMinutes(15);
    static final Duration CONFIRM_WINDOW = Duration.ofMinutes(15);
    static final Duration ATTEMPT_WINDOW = Duration.ofHours(1);
    static final int MAX_ATTEMPTS = 5;
    private static final int GENERATE_TRIES = 3;

    public record LinkCodeIssued(String code, Instant expiresAt, long expiresInSeconds) {
    }

    public record Claimed(String status, Instant confirmBefore) {
        static final String PENDING = "PENDING_CONFIRMATION";
    }

    public record LinkRequestView(Long id, ChannelType channel, String channelUser, Instant claimedAt,
                                  Instant confirmBefore) {
    }

    public record Linked(ChannelType channel, String channelUser, Instant linkedAt) {
    }

    private final LinkCodeRepository codes;
    private final LinkCodeAttemptRepository attempts;
    private final ChannelIdentityRepository identities;
    private final CustomerRepository customers;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public LinkCodeService(LinkCodeRepository codes, LinkCodeAttemptRepository attempts,
                           ChannelIdentityRepository identities, CustomerRepository customers, Clock clock) {
        this.codes = codes;
        this.attempts = attempts;
        this.identities = identities;
        this.customers = customers;
        this.clock = clock;
    }

    @Transactional
    public LinkCodeIssued issue(long customerId) {
        requireCustomer(customerId);
        Instant now = clock.instant();
        codes.revokeUnclaimed(customerId, now);

        String code = null;
        String hash = null;
        for (int i = 0; i < GENERATE_TRIES && hash == null; i++) {
            code = LinkCodes.generate(random);
            String candidate = LinkCodes.hash(code);
            if (!codes.existsByCodeHash(candidate)) {
                hash = candidate;
            }
        }
        if (hash == null) {
            throw new IllegalStateException("Không sinh được mã liên kết không trùng sau " + GENERATE_TRIES + " lần");
        }

        LinkCode link = new LinkCode();
        link.setCustomerId(customerId);
        link.setCodeHash(hash);
        link.setCreatedAt(now);
        link.setExpiresAt(now.plus(CODE_TTL));
        codes.save(link);
        return new LinkCodeIssued(code, link.getExpiresAt(), CODE_TTL.toSeconds());
    }

    /** Lần gõ thất bại vẫn commit để lưu bộ đếm khoá. */
    @Transactional(noRollbackFor = LinkError.class)
    public Claimed claim(ChannelType channel, String externalId, String rawCode) {
        if (channel == ChannelType.WEB) {
            throw new Invalid(List.of(new FieldViolation("channel", "chỉ nhận mã từ kênh sàn (SHOPEE, TIKTOK)")));
        }
        Instant now = clock.instant();
        Instant windowStart = now.minus(ATTEMPT_WINDOW);
        long failed = attempts.countByChannelAndExternalIdAndAttemptedAtAfter(channel, externalId, windowStart);
        if (failed >= MAX_ATTEMPTS) {
            throw locked(channel, externalId, now);
        }
        if (identities.findByChannelAndExternalId(channel, externalId).filter(i -> i.getCustomer() != null).isPresent()) {
            throw new IdentityAlreadyLinked();
        }

        String code = LinkCodes.normalize(rawCode);
        LinkCode link = code == null ? null : codes.findByCodeHash(LinkCodes.hash(code)).orElse(null);
        if (link == null || !link.isClaimableAt(now)) {
            attempts.save(new LinkCodeAttempt(channel, externalId, now));
            int left = (int) (MAX_ATTEMPTS - failed - 1);
            if (left <= 0) {
                throw locked(channel, externalId, now);
            }
            if (link == null) {
                throw new LinkCodeInvalid(left);
            }
            throw new LinkCodeExpired("Mã đã hết hạn hoặc đã được dùng, hãy lấy mã mới trên web", left);
        }

        link.setClaimedChannel(channel);
        link.setClaimedExternalId(externalId);
        link.setClaimedAt(now);
        return new Claimed(Claimed.PENDING, link.confirmBefore(CONFIRM_WINDOW));
    }

    @Transactional(readOnly = true)
    public List<LinkRequestView> pending(long customerId) {
        requireCustomer(customerId);
        Instant now = clock.instant();
        return codes.findByCustomerIdAndClaimedAtNotNullAndResolutionIsNullOrderByClaimedAtDesc(customerId).stream()
                .filter(c -> c.isPendingAt(now, CONFIRM_WINDOW))
                .map(c -> new LinkRequestView(c.getId(), c.getClaimedChannel(), LinkCodes.mask(c.getClaimedExternalId()),
                        c.getClaimedAt(), c.confirmBefore(CONFIRM_WINDOW)))
                .toList();
    }

    /** {@code consent} đã được kiểm là true ở controller. */
    @Transactional
    public Linked confirm(long customerId, long linkId) {
        Customer customer = requireCustomer(customerId);
        Instant now = clock.instant();
        LinkCode link = pendingOf(customerId, linkId, now);

        ChannelIdentity identity = identities.findByChannelAndExternalId(link.getClaimedChannel(), link.getClaimedExternalId())
                .orElse(null);
        if (identity != null && identity.getCustomer() != null
                && !Objects.equals(identity.getCustomer().getId(), customerId)) {
            throw new IdentityAlreadyLinked();
        }
        if (identity == null) {
            identity = new ChannelIdentity();
            identity.setChannel(link.getClaimedChannel());
            identity.setExternalId(link.getClaimedExternalId());
            identity.setCreatedAt(now);
        }
        identity.setCustomer(customer);
        identity.setLinkedBy(ChannelIdentity.LinkMethod.LINK_CODE);
        identity.setConfidence(1.0);
        identity.setConsentAt(now);
        if (identity.getId() == null) {
            // Flush ngay: 2 khách cùng xác nhận 1 danh tính thì người sau dính unique (channel, external_id) -> 409.
            identities.saveAndFlush(identity);
        }

        link.setResolution(LinkCode.Resolution.CONFIRMED);
        link.setResolvedAt(now);
        return new Linked(link.getClaimedChannel(), LinkCodes.mask(link.getClaimedExternalId()), now);
    }

    @Transactional
    public void reject(long customerId, long linkId) {
        requireCustomer(customerId);
        Instant now = clock.instant();
        LinkCode link = pendingOf(customerId, linkId, now);
        link.setResolution(LinkCode.Resolution.REJECTED);
        link.setResolvedAt(now);
    }

    private LinkCode pendingOf(long customerId, long linkId, Instant now) {
        LinkCode link = codes.findByIdAndCustomerId(linkId, customerId)
                .filter(c -> c.getClaimedAt() != null)
                .orElseThrow(() -> new LinkRequestNotFound(linkId));
        if (!link.isPendingAt(now, CONFIRM_WINDOW)) {
            throw new LinkCodeExpired("Yêu cầu liên kết đã được xử lý hoặc quá hạn xác nhận", null);
        }
        return link;
    }

    private LinkLocked locked(ChannelType channel, String externalId, Instant now) {
        Instant oldest = attempts.findFirstByChannelAndExternalIdAndAttemptedAtAfterOrderByAttemptedAtAsc(
                        channel, externalId, now.minus(ATTEMPT_WINDOW))
                .map(LinkCodeAttempt::getAttemptedAt).orElse(now);
        Duration wait = Duration.between(now, oldest.plus(ATTEMPT_WINDOW));
        return new LinkLocked(Math.max(1, (wait.toMillis() + 999) / 1000));
    }

    private Customer requireCustomer(long customerId) {
        return customers.findById(customerId).orElseThrow(CustomerGone::new);
    }
}
