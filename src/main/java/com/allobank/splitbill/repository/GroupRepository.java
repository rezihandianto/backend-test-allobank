package com.allobank.splitbill.repository;

import com.allobank.splitbill.domain.BillGroup;
import java.util.Optional;
import java.util.UUID;

public interface GroupRepository {

    BillGroup save(BillGroup group);

    Optional<BillGroup> findById(UUID id);
}
