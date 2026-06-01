package com.visualspider.service;

import com.visualspider.entity.CrawlConfig;
import com.visualspider.entity.CrawlField;
import com.visualspider.exception.ConfigNotFoundException;
import com.visualspider.repository.CrawlConfigRepository;
import com.visualspider.repository.CrawlFieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CrawlFieldService {

    private final CrawlFieldRepository fieldRepository;
    private final CrawlConfigRepository configRepository;

    @Transactional
    public CrawlField addField(Long configId, CrawlField field) {
        CrawlConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new ConfigNotFoundException(configId));
        field.setConfig(config);
        return fieldRepository.save(field);
    }

    @Transactional
    public CrawlField update(Long fieldId, CrawlField updates) {
        CrawlField existing = fieldRepository.findById(fieldId)
                .orElseThrow(() -> new ConfigNotFoundException(fieldId));
        existing.setFieldName(updates.getFieldName());
        existing.setFieldType(updates.getFieldType());
        existing.setSelector(updates.getSelector());
        existing.setPageType(updates.getPageType());
        return fieldRepository.save(existing);
    }

    @Transactional
    public void deleteById(Long fieldId) {
        if (!fieldRepository.existsById(fieldId)) {
            throw new ConfigNotFoundException(fieldId);
        }
        fieldRepository.deleteById(fieldId);
    }

    @Transactional(readOnly = true)
    public List<CrawlField> listByConfigId(Long configId) {
        if (!configRepository.existsById(configId)) {
            throw new ConfigNotFoundException(configId);
        }
        return fieldRepository.findByConfigIdOrderByCreatedAtAsc(configId);
    }
}
