package com.interview.policyimport.service;

import com.interview.policyimport.entity.ImportRow;
import com.interview.policyimport.repository.ImportRowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ImportRowPersistenceService {

    private final ImportRowRepository repository;

    public ImportRowPersistenceService(
            ImportRowRepository repository
    ) {
        this.repository = repository;
    }

    @Transactional
    public void saveBatch(List<ImportRow> rows) {
        repository.saveAll(rows);
    }

    @Transactional
    public void save(ImportRow row) {
        repository.save(row);
    }
}