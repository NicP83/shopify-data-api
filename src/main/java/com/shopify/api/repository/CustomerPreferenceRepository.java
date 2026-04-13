package com.shopify.api.repository;

import com.shopify.api.model.CustomerPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerPreferenceRepository extends JpaRepository<CustomerPreference, Long> {

    Optional<CustomerPreference> findByCustomerEmail(String customerEmail);
}
