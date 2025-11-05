package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.ai.DishVisionResult;
import com.hn.nutricarebe.dto.ai.IngredientBreakdown;
import com.hn.nutricarebe.dto.ai.NutritionAudit;
import com.hn.nutricarebe.dto.request.IngredientCreationRequest;
import com.hn.nutricarebe.dto.request.IngredientUpdateRequest;
import com.hn.nutricarebe.dto.response.IngredientResponse;
import com.hn.nutricarebe.entity.Ingredient;
import com.hn.nutricarebe.entity.Nutrition;
import com.hn.nutricarebe.enums.Unit;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.CdnHelper;
import com.hn.nutricarebe.mapper.IngredientMapper;
import com.hn.nutricarebe.repository.IngredientRepository;
import com.hn.nutricarebe.service.IngredientService;
import com.hn.nutricarebe.service.S3Service;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IngredientServiceImpl implements IngredientService {
    IngredientRepository ingredientRepository;
    IngredientMapper ingredientMapper;
    S3Service s3Service;
    CdnHelper cdnHelper;

    static  BigDecimal THOUSAND = new BigDecimal("1000");
    static BigDecimal HUNDRED = new BigDecimal("100");
    static int SCALE = 6;
    static  RoundingMode RM = RoundingMode.HALF_UP;
    static String CDN_BASE = "https://dyfgxhmdeg59a.cloudfront.net";


    // Tạo mới nguyên liệu
    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void saveIngredient(IngredientCreationRequest request) {
        String normalizedName = normalizeName(request.getName());
        if (ingredientRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new AppException(ErrorCode.INGREDIENT_NAME_EXISTED);
        }
        Ingredient i = ingredientMapper.toIngredient(request);
        i.setName(normalizedName);

        String objectKey = null;
        try {
            if (request.getImage() != null && !request.getImage().isEmpty()) {
                objectKey = s3Service.uploadObject(request.getImage(), "images/ingredients");
                i.setImageKey(objectKey);
            }
            ingredientRepository.save(i);
        } catch (DataIntegrityViolationException e) {
            if (objectKey != null) safeDeleteObject(objectKey);
            throw e;
        } catch (IOException e) {
            safeDeleteObject(objectKey);
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        } catch (RuntimeException e) {
            safeDeleteObject(objectKey);
            throw e;
        }
    }

    // Lấy nguyên liệu theo ID
    @Override
    public IngredientResponse getById(UUID id) {
        Ingredient ingredient = ingredientRepository.findWithCollectionsById(id)
                .orElseThrow(() -> new AppException(ErrorCode.INGREDIENT_NOT_FOUND));
        return ingredientMapper.toIngredientResponse(ingredient, cdnHelper);
    }


    // Xóa nguyên liệu theo ID
    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteById(UUID id) {
        Ingredient ing = ingredientRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.INGREDIENT_NOT_FOUND));
        String key = ing.getImageKey();
        if (key != null && !key.isBlank()) {
            try {
                s3Service.deleteObject(key);
            } catch (RuntimeException e) {
                throw new AppException(ErrorCode.DELETE_OBJECT_FAILED);
            }
        }
        try {
            ingredientRepository.deleteById(ing.getId());
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(ErrorCode.DELETE_INGREDIENT_CONFLICT);
        }
    }

    // Lấy tất cả nguyên liệu
    @Override
    public Slice<IngredientResponse> getAll(Pageable pageable) {
        Slice<Ingredient> slice = ingredientRepository.findAllBy(pageable);
        return new SliceImpl<>(
                slice.getContent().stream()
                        .map(ingredient -> ingredientMapper.toIngredientResponse(ingredient, cdnHelper))
                        .toList(),
                pageable,
                slice.hasNext()
        );
    }


    @Override
    @Transactional(readOnly = true)
    public List<IngredientResponse> autocompleteIngredients(String keyword, int limit) {
        int size = Math.max(1, Math.min(limit, 20));

        Pageable pageable = PageRequest.of(0, size);

        return ingredientRepository
                .searchByNameOrAlias(keyword.trim(), pageable)
                .getContent()
                .stream()
                .map(i -> ingredientMapper.toIngredientResponse(i, cdnHelper))
                .toList();
    }


    @Override
    public NutritionAudit audit(DishVisionResult vision, boolean strict) {
        List<IngredientBreakdown> items = new ArrayList<>();
        Nutrition total = new Nutrition();
        List<String> missing = new ArrayList<>();

        // Cho phép tạm ước lượng 1 g ≈ 1 ml khi đổi mass <-> volume
        boolean allowApproxDensity = true;

        if (vision.getIngredients() != null) {
            for (var est : vision.getIngredients()) {
                var ingOpt = resolveOne(est.getName());
                Ingredient ing = ingOpt.orElse(null);

                // Chuẩn bị mặc định
                Nutrition per100 = new Nutrition();
                BigDecimal amountInIngredientUnit = BigDecimal.ZERO;
                Nutrition sub = new Nutrition();
                boolean isMissing = (ing == null);
                String imageUrl = null;

                if (isMissing) {
                    if (strict) {
                        missing.add(est.getName());
                    }
                } else {
                    per100 = (ing.getPer100() != null) ? ing.getPer100() : new Nutrition();
                    amountInIngredientUnit = convert(
                            est.getAmount(),   // số lượng AI trả về
                            est.getUnit(),     // đơn vị AI trả về (MG/G/ML/L)
                            ing.getUnit(),     // đơn vị gốc trong DB
                            allowApproxDensity
                    );
                    sub = mulPer100ByAmount(per100, amountInIngredientUnit);
                    add(total, sub);
                    if (ing.getImageKey() != null && !ing.getImageKey().isBlank()) {
                        imageUrl = buildCdnUrl(ing.getImageKey());
                    }
                }

                items.add(IngredientBreakdown.builder()
                        .requestedName(est.getName())
                        .ingredientId(ing != null ? ing.getId() : null)
                        .matchedName(ing != null ? ing.getName() : null)
                        .aliasMatched(null)
                        .gram(amountInIngredientUnit)
                        .per100(per100)
                        .subtotal(sub)
                        .imageUrl(imageUrl)
                        .missing(isMissing)
                        .build());
            }
        }

        if (strict && !missing.isEmpty()) {
            throw new AppException(
                    ErrorCode.INGREDIENT_NOT_FOUND);
        }

        return NutritionAudit.builder()
                .items(items)
                .totalFromDB(total)
                .dishName(vision.getDishName())
                .servingName(vision.getServingName())
                .servingGram(vision.getServingGram())
                .build();
    }


    // Đếm tổng số nguyên liệu
    @Override
    public long countIngredients() {
        return ingredientRepository.count();
    }

    // Đếm số nguyên liệu mới trong tuần này
    @Override
    public long countNewIngredientsThisWeek() {
        return ingredientRepository.countNewThisWeek();
    }


    //Cập nhật nguyên liệu
    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void updateIngredient(UUID id, IngredientUpdateRequest request) {
        // 1) Tìm bản ghi
        Ingredient ing = ingredientRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.INGREDIENT_NOT_FOUND));

        // 2) Chuẩn hoá tên và kiểm tra trùng
        String normalizedName = normalizeName(request.getName());
        if (normalizedName == null || normalizedName.isBlank()) {
            throw new AppException(ErrorCode.NAME_EMPTY);
        }

        // Nếu tên thay đổi, kiểm tra trùng với bản ghi khác
        if (!normalizedName.equalsIgnoreCase(ing.getName())) {
            ingredientRepository.findByNameIgnoreCase(normalizedName)
                    .filter(other -> !other.getId().equals(ing.getId()))
                    .ifPresent(other -> {throw new AppException(ErrorCode.INGREDIENT_NAME_EXISTED); });
        }

        // 3) Map per100
        Nutrition per100 = new Nutrition();
        if (request.getPer100() != null) {
            per100.setKcal(request.getPer100().getKcal());
            per100.setProteinG(request.getPer100().getProteinG());
            per100.setCarbG(request.getPer100().getCarbG());
            per100.setFatG(request.getPer100().getFatG());
            per100.setFiberG(request.getPer100().getFiberG());
            per100.setSodiumMg(request.getPer100().getSodiumMg());
            per100.setSugarMg(request.getPer100().getSugarMg());
        }

        // 4) Upload ảnh mới (nếu có)
        String oldKey = ing.getImageKey();
        String newKey = null;
        boolean hasNewImage = request.getImage() != null && !request.getImage().isEmpty();

        if (hasNewImage) {
            try {
                newKey = s3Service.uploadObject(request.getImage(), "images/ingredients");
            } catch (IOException e) {
                throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
            }
        }

        // 5) Gán các trường lên entity
        ing.setName(normalizedName);
        ing.setPer100(per100);
        ing.setUnit(request.getUnit() == null ? Unit.G : request.getUnit());
        Set<String> aliases = request.getAliases() == null ? new HashSet<>() : request.getAliases();
        ing.setAliases(aliases);

        if (newKey != null) {
            ing.setImageKey(newKey);
        }

        // 6) Lưu
        try {
            ingredientRepository.save(ing);
        } catch (DataIntegrityViolationException e) {
            // Rollback: xóa ảnh mới đã upload
            if (newKey != null) {
                safeDeleteObject(newKey);
            }
            throw e;
        }

        // 7) Xóa ảnh cũ sau khi lưu thành công
        if (newKey != null && oldKey != null && !oldKey.isBlank()) {
            try {
                s3Service.deleteObject(oldKey);
            } catch (RuntimeException e) {
                log.warn("Failed to delete old image object: {}", oldKey, e);
            }
        }
    }



    public Optional<Ingredient> resolveOne(String requestedName) {
        if (requestedName == null || requestedName.isBlank()) return Optional.empty();

        // 1) Exact theo name
        var byName = ingredientRepository.findByNameIgnoreCase(requestedName);
        if (byName.isPresent()) return byName;

        // 2) Exact theo alias
        var byAlias = ingredientRepository.findByAliasIgnoreCase(requestedName);
        if (byAlias.isPresent()) return byAlias;

        // 3) Fallback gần đúng (unaccent, contains) — lấy 1 bản ghi đầu
        var page = ingredientRepository.searchByNameOrAlias(requestedName, PageRequest.of(0, 1));
        if (page.hasContent()) return Optional.of(page.getContent().getFirst());

        return Optional.empty();
    }
    private String buildCdnUrl(String key) {
        if (key == null || key.isBlank()) return null;
        // tránh trùng dấu '/'
        if (CDN_BASE.endsWith("/")) {
            return CDN_BASE + key.replaceFirst("^/+", "");
        } else {
            return CDN_BASE + "/" + key.replaceFirst("^/+", "");
        }
    }
    private boolean isMass(Unit u)   { return u == Unit.MG || u == Unit.G; }
    private boolean isVolume(Unit u) { return u == Unit.ML || u == Unit.L; }
    /** Chuẩn hóa về G cho khối lượng, ML cho thể tích; rồi đổi hệ nếu cần; cuối cùng đổi sang đơn vị đích. */
    private BigDecimal convert(BigDecimal amount, Unit from, Unit to, boolean allowApproxDensity) {
        if (amount == null) return BigDecimal.ZERO;
        if (from == null || to == null || from == to) return amount;
        BigDecimal fromBase;
        if (isMass(from)) {
            fromBase = (from == Unit.MG)
                    ? amount.divide(THOUSAND, 6, RoundingMode.HALF_UP)
                    : amount;
        } else if (isVolume(from)) {
            fromBase = (from == Unit.L)
                    ? amount.multiply(THOUSAND)
                    : amount;
        } else {
            return amount;
        }
        BigDecimal toBase;
        if (isMass(from) && isVolume(to)) {
            if (!allowApproxDensity) {
                throw new AppException(ErrorCode.CONVERSION_REQUIRES_DENSITY);
            }
            toBase = fromBase;
        } else if (isVolume(from) && isMass(to)) {
            if (!allowApproxDensity) {
                throw new AppException(ErrorCode.CONVERSION_REQUIRES_DENSITY);
            }
            toBase = fromBase;
        } else {
            toBase = fromBase;
        }

        if (isMass(to)) {
            return (to == Unit.MG)
                    ? toBase.multiply(THOUSAND)
                    : toBase;
        } else if (isVolume(to)) {
            return (to == Unit.L)
                    ? toBase.divide(THOUSAND, 6, RoundingMode.HALF_UP)
                    : toBase;
        } else {
            return amount;
        }
    }
    /** per100 là dinh dưỡng trên 100 đơn vị gốc (theo Ingredient.unit).
     *  amountInIngredientUnit là lượng đã quy đổi về đúng đơn vị gốc của Ingredient.
     */
    private Nutrition mulPer100ByAmount(Nutrition per100, BigDecimal amountInIngredientUnit) {
        BigDecimal factor = (amountInIngredientUnit == null)
                ? BigDecimal.ZERO
                : amountInIngredientUnit.divide(HUNDRED, SCALE, RM);
        Nutrition n = new Nutrition();
        n.setKcal(z(per100.getKcal()).multiply(factor));
        n.setProteinG(z(per100.getProteinG()).multiply(factor));
        n.setCarbG(z(per100.getCarbG()).multiply(factor));
        n.setFatG(z(per100.getFatG()).multiply(factor));
        n.setFiberG(z(per100.getFiberG()).multiply(factor));
        n.setSodiumMg(z(per100.getSodiumMg()).multiply(factor));
        n.setSugarMg(z(per100.getSugarMg()).multiply(factor));
        return n;
    }
    private void add(Nutrition a, Nutrition b) {
        a.setKcal(z(a.getKcal()).add(z(b.getKcal())));
        a.setProteinG(z(a.getProteinG()).add(z(b.getProteinG())));
        a.setCarbG(z(a.getCarbG()).add(z(b.getCarbG())));
        a.setFatG(z(a.getFatG()).add(z(b.getFatG())));
        a.setFiberG(z(a.getFiberG()).add(z(b.getFiberG())));
        a.setSodiumMg(z(a.getSodiumMg()).add(z(b.getSodiumMg())));
        a.setSugarMg(z(a.getSugarMg()).add(z(b.getSugarMg())));
    }
    private BigDecimal z(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private void safeDeleteObject(String objectKey) {
        if (objectKey != null) {
            try { s3Service.deleteObject(objectKey); } catch (Exception ignored) {}
        }
    }
    private String normalizeName(String input) {
        if (input == null) return null;
        return input.trim().replaceAll("\\s+", " ");
    }
}


