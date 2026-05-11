package com.fashion.marketplace.service;

import com.fashion.marketplace.dto.response.FactoryProfileResponse;
import com.fashion.marketplace.entity.*;
import com.fashion.marketplace.exception.ResourceNotFoundException;
import com.fashion.marketplace.repository.*;
import lombok.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class FactoryProfileService {

    private final FactoryProfileRepository factoryProfileRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public FactoryProfileResponse toResponse(FactoryProfile p) {
        return FactoryProfileResponse.builder()
                .id(p.getId())
                .factoryName(p.getFactoryName())
                .description(p.getDescription())
                .address(p.getAddress())
                .minQuantity(p.getMinQuantity())
                .maxQuantity(p.getMaxQuantity())
                .leadTimeDays(p.getLeadTimeDays())
                .ratingAvg(p.getRatingAvg())
                .totalRatings(p.getTotalRatings())
                .verifiedStatus(p.getVerifiedStatus().name())
                .verifiedAt(p.getVerifiedAt())
                .createdAt(p.getCreatedAt())
                .imageUrls(p.getImages().stream()
                        .map(img -> img.getImageUrl())
                        .toList())
                .certificates(p.getCertificates().stream()
                        .map(c -> FactoryProfileResponse.CertInfo.builder()
                                .name(c.getName())
                                .imageUrl(c.getImageUrl())
                                .issuedDate(c.getIssuedDate())
                                .expiredDate(c.getExpiredDate())
                                .build())
                        .toList())
                .build();
    }
    // ---- Xưởng: quản lý hồ sơ ----

    @Transactional
    public FactoryProfile createOrUpdate(Long userId, FactoryProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        FactoryProfile profile = factoryProfileRepository.findByUserId(userId)
                .orElse(FactoryProfile.builder().user(user)
                        .verifiedStatus(FactoryProfile.VerifiedStatus.PENDING).build());

        profile.setFactoryName(req.getFactoryName());
        profile.setDescription(req.getDescription());
        profile.setAddress(req.getAddress());
        profile.setMinQuantity(req.getMinQuantity());
        profile.setMaxQuantity(req.getMaxQuantity());
        profile.setLeadTimeDays(req.getLeadTimeDays());

        return factoryProfileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public FactoryProfileResponse getByUserId(Long userId) {
        return toResponse(factoryProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ xưởng không tồn tại")));
    }

    @Transactional(readOnly = true)
    public FactoryProfileResponse getById(Long id) {
        return toResponse(factoryProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ xưởng không tồn tại")));
    }

    // ---- Admin: xét duyệt hồ sơ ----

    public Page<FactoryProfile> getPending(Pageable pageable) {
        return factoryProfileRepository.findByVerifiedStatus(
                FactoryProfile.VerifiedStatus.PENDING, pageable);
    }

    public Page<FactoryProfile> getAll(Pageable pageable) {
        return factoryProfileRepository.findAll(pageable);
    }

    @Transactional
    public FactoryProfile approve(Long factoryId) {
        FactoryProfile profile = factoryProfileRepository.findById(factoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ xưởng không tồn tại"));
        profile.setVerifiedStatus(FactoryProfile.VerifiedStatus.APPROVED);
        profile.setVerifiedAt(java.time.LocalDateTime.now());
        FactoryProfile saved = factoryProfileRepository.save(profile);
        notificationService.push(profile.getUser().getId(),
                "Hồ sơ được duyệt", "Hồ sơ năng lực của bạn đã được phê duyệt",
                "FACTORY_VERIFIED", factoryId);
        return saved;
    }

    @Transactional
    public FactoryProfile reject(Long factoryId, String reason) {
        FactoryProfile profile = factoryProfileRepository.findById(factoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ xưởng không tồn tại"));
        profile.setVerifiedStatus(FactoryProfile.VerifiedStatus.REJECTED);
        profile.setRejectedReason(reason);
        FactoryProfile saved = factoryProfileRepository.save(profile);
        notificationService.push(profile.getUser().getId(),
                "Hồ sơ bị từ chối", "Hồ sơ năng lực bị từ chối: " + reason,
                "FACTORY_REJECTED", factoryId);
        return saved;
    }

    // ---- Thêm ảnh & chứng chỉ ----

    @Transactional
    public FactoryProfile addImage(Long userId, String imageUrl, boolean isPrimary) {
        FactoryProfile profile = factoryProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ xưởng không tồn tại"));
        FactoryImage img = FactoryImage.builder()
                .factory(profile).imageUrl(imageUrl).isPrimary(isPrimary).build();
        profile.getImages().add(img);
        return factoryProfileRepository.save(profile);
    }

    @Transactional
    public FactoryProfile addCertificate(Long userId, CertificateRequest req) {
        FactoryProfile profile = factoryProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ xưởng không tồn tại"));
        FactoryCertificate cert = FactoryCertificate.builder()
                .factory(profile).name(req.getName())
                .imageUrl(req.getImageUrl())
                .issuedDate(req.getIssuedDate())
                .expiredDate(req.getExpiredDate())
                .build();
        profile.getCertificates().add(cert);
        return factoryProfileRepository.save(profile);
    }

    // inner DTOs
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class FactoryProfileRequest {
        private String factoryName;
        private String description;
        private String address;
        private Integer minQuantity;
        private Integer maxQuantity;
        private Integer leadTimeDays;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CertificateRequest {
        private String name;
        private String imageUrl;
        private java.time.LocalDate issuedDate;
        private java.time.LocalDate expiredDate;
    }
}
