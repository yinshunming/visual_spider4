package com.visualspider.service;

import com.visualspider.entity.CrawlConfig;
import com.visualspider.entity.CrawlField;
import com.visualspider.enums.ConfigStatus;
import com.visualspider.exception.ConfigNotFoundException;
import com.visualspider.repository.CrawlConfigRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CrawlConfigService {

    private final CrawlConfigRepository repository;

    public CrawlConfigService(CrawlConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CrawlConfig create(CrawlConfig config) {
        if (config.getStatus() == null) {
            config.setStatus(ConfigStatus.STOPPED);
        }
        if (config.getStartUrl() != null) {
            UrlGuard.validate(config.getStartUrl(), "startUrl");
        }
        return repository.save(config);
    }

    @Transactional(readOnly = true)
    public Page<CrawlConfig> list(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public CrawlConfig getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ConfigNotFoundException(id));
    }

    /**
     * 加载 CrawlConfig 并立即加载 fields(@EntityGraph)。
     * 用于 WebSocket / 后台线程等"Service 调用结束后才用 fields"的场景,
     * 避免触发 LazyInitializationException。
     */
    @Transactional(readOnly = true)
    public CrawlConfig getByIdWithFields(Long id) {
        return repository.findByIdWithFields(id)
                .orElseThrow(() -> new ConfigNotFoundException(id));
    }

    @Transactional
    public void deleteById(Long id) {
        CrawlConfig config = repository.findById(id)
                .orElseThrow(() -> new ConfigNotFoundException(id));
        repository.delete(config);
    }

    @Transactional
    public CrawlConfig updateWithFields(Long id, String name, String startUrl,
                                        com.visualspider.enums.PageType pageType,
                                        com.visualspider.enums.SelectorType selectorType,
                                        List<CrawlField> newFields) {
        CrawlConfig config = repository.findById(id)
                .orElseThrow(() -> new ConfigNotFoundException(id));
        if (name != null) config.setName(name);
        if (startUrl != null) {
            UrlGuard.validate(startUrl, "startUrl");
            config.setStartUrl(startUrl);
        }
        if (pageType != null) config.setPageType(pageType);
        if (selectorType != null) config.setSelectorType(selectorType);
        // 原子全量替换：清空旧字段，添加新字段
        config.getFields().clear();
        if (newFields != null) {
            for (CrawlField f : newFields) {
                f.setConfig(config);
                config.getFields().add(f);
            }
        }
        return repository.save(config);
    }
}
