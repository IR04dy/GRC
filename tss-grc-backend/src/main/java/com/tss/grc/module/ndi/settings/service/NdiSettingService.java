package com.tss.grc.module.ndi.settings.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.settings.entity.NdiSetting;
import com.tss.grc.module.ndi.settings.repository.NdiSettingRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class NdiSettingService {

    private final NdiSettingRepository repo;

    public NdiSettingService(NdiSettingRepository repo) { this.repo = repo; }

    private Long org() { return TenantContext.getCurrentTenant(); }

    @Transactional(readOnly = true)
    public List<NdiSetting> list() {
        return repo.findByOrganisationIdOrderByCategoryAscSettingKeyAsc(org());
    }

    @Transactional(readOnly = true)
    public Map<String, List<NdiSetting>> listGrouped() {
        Map<String, List<NdiSetting>> out = new LinkedHashMap<>();
        for (NdiSetting s : list()) {
            out.computeIfAbsent(s.getCategory(), k -> new ArrayList<>()).add(s);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public NdiSetting getByKey(String key) {
        return repo.findByOrganisationIdAndSettingKey(org(), key)
            .orElseThrow(() -> new EntityNotFoundException("Setting not found: " + key));
    }

    @Transactional(readOnly = true)
    public Optional<String> stringValue(String key, String fallback) {
        return repo.findByOrganisationIdAndSettingKey(org(), key)
            .map(NdiSetting::getValueText)
            .or(() -> Optional.ofNullable(fallback));
    }

    public NdiSetting setValue(String key, String value) {
        NdiSetting s = repo.findByOrganisationIdAndSettingKey(org(), key)
            .orElseThrow(() -> new EntityNotFoundException("Setting not found: " + key));
        if (Boolean.TRUE.equals(s.getIsLocked()))
            throw new IllegalStateException("Setting " + key + " is locked");
        validateValue(s.getValueType(), value);
        s.setValueText(value);
        return repo.save(s);
    }

    public NdiSetting resetToDefault(String key) {
        NdiSetting s = repo.findByOrganisationIdAndSettingKey(org(), key)
            .orElseThrow(() -> new EntityNotFoundException("Setting not found: " + key));
        if (Boolean.TRUE.equals(s.getIsLocked()))
            throw new IllegalStateException("Setting " + key + " is locked");
        s.setValueText(s.getDefaultValue());
        return repo.save(s);
    }

    public NdiSetting create(NdiSetting body) {
        if (body.getSettingKey() == null) throw new IllegalArgumentException("settingKey is required");
        if (body.getNameEn() == null)     throw new IllegalArgumentException("nameEn is required");
        if (repo.findByOrganisationIdAndSettingKey(org(), body.getSettingKey()).isPresent())
            throw new IllegalArgumentException("Setting already exists: " + body.getSettingKey());
        body.setId(null);
        body.setOrganisationId(org());
        validateValue(body.getValueType() == null ? "STRING" : body.getValueType(), body.getValueText());
        return repo.save(body);
    }

    public void delete(Long id) {
        NdiSetting s = repo.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Setting not found: " + id));
        if (Boolean.TRUE.equals(s.getIsLocked()))
            throw new IllegalStateException("Setting is locked");
        s.setIsDeleted(true);
        repo.save(s);
    }

    private void validateValue(String type, String value) {
        if (value == null) return;
        switch (type) {
            case "INTEGER" -> {
                try { Integer.parseInt(value); }
                catch (NumberFormatException ex) { throw new IllegalArgumentException("INTEGER expected: " + value); }
            }
            case "DECIMAL" -> {
                try { Double.parseDouble(value); }
                catch (NumberFormatException ex) { throw new IllegalArgumentException("DECIMAL expected: " + value); }
            }
            case "BOOLEAN" -> {
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value))
                    throw new IllegalArgumentException("BOOLEAN expected: " + value);
            }
            // STRING and JSON pass through as text.
            default -> {}
        }
    }
}
