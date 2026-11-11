package com.allobank.splitbill.repository;

import com.allobank.splitbill.domain.BillGroup;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryGroupRepository implements GroupRepository {

    private final ConcurrentHashMap<UUID, BillGroup> groups = new ConcurrentHashMap<>();

    @Override
    public BillGroup save(BillGroup group) {
        groups.put(group.id(), group);
        return group;
    }

    @Override
    public Optional<BillGroup> findById(UUID id) {
        return Optional.ofNullable(groups.get(id));
    }
}
