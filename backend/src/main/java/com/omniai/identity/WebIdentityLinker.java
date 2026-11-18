package com.omniai.identity;

import com.omniai.common.ChannelType;
import com.omniai.customer.ChannelIdentity;
import com.omniai.customer.ChannelIdentityRepository;
import com.omniai.customer.Customer;
import com.omniai.customer.CustomerRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Sau khi xác thực OTP: tìm hoặc tạo {@link Customer} theo email, gắn danh tính kênh WEB
 * ({@code external_id} = customer id, {@code linked_by = OTP}). Email đã xác thực là bằng chứng,
 * consent là ô khách tick trên form (data-model: chỉ gắn khi có bằng chứng + consent).
 */
@Component
class WebIdentityLinker {

    record Linked(Customer customer, boolean newCustomer) {
    }

    private final CustomerRepository customers;
    private final ChannelIdentityRepository identities;

    WebIdentityLinker(CustomerRepository customers, ChannelIdentityRepository identities) {
        this.customers = customers;
        this.identities = identities;
    }

    Linked link(String email, Instant now) {
        Customer customer = customers.findByEmail(email).orElse(null);
        boolean created = customer == null;
        if (created) {
            customer = new Customer();
            customer.setEmail(email);
            customer.setCreatedAt(now);
            customer = customers.save(customer);
        }
        String externalId = customer.getId().toString();
        if (identities.findByChannelAndExternalId(ChannelType.WEB, externalId).isEmpty()) {
            ChannelIdentity identity = new ChannelIdentity();
            identity.setCustomer(customer);
            identity.setChannel(ChannelType.WEB);
            identity.setExternalId(externalId);
            identity.setLinkedBy(ChannelIdentity.LinkMethod.OTP);
            identity.setConfidence(1.0);
            identity.setConsentAt(now);
            identity.setCreatedAt(now);
            identities.save(identity);
        }
        return new Linked(customer, created);
    }
}
