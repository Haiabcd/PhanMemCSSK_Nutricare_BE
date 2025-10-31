package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.overview.*;
import com.hn.nutricarebe.dto.request.FoodCreationRequest;
import com.hn.nutricarebe.dto.request.FoodPatchRequest;
import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.entity.Food;
import com.hn.nutricarebe.enums.MealSlot;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.CdnHelper;
import com.hn.nutricarebe.mapper.FoodMapper;
import com.hn.nutricarebe.mapper.UserResolver;
import com.hn.nutricarebe.repository.FoodRepository;
import com.hn.nutricarebe.service.FoodService;
import com.hn.nutricarebe.service.S3Service;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FoodServiceImpl implements FoodService {
    FoodRepository foodRepository;
    FoodMapper foodMapper;
    UserResolver userResolver;
    S3Service s3Service;
    CdnHelper cdnHelper;

    // Default threshold (có thể đổi tuỳ hệ thống)
    private static final BigDecimal DEFAULT_HIGH = new BigDecimal("800"); // >800 kcal
    private static final BigDecimal DEFAULT_LOW  = new BigDecimal("300"); // <300 kcal

    // Tạo món ăn mới
    @Override
    @Transactional
    public FoodResponse saveFood(FoodCreationRequest request) {
        String normalizedName = normalizeName(request.getName());
        if (foodRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new AppException(ErrorCode.FOOD_NAME_EXISTED);
        }
        Food food = foodMapper.toFood(request);
        food.setName(normalizedName);

        String objectKey = null;
        try {
            if (request.getImage() != null && !request.getImage().isEmpty()) {
                objectKey = s3Service.uploadObject(request.getImage(), "images/foods");
                food.setImageKey(objectKey);
            }
            Food saved = foodRepository.save(food);
            return foodMapper.toFoodResponse(saved, cdnHelper);
        } catch (DataIntegrityViolationException e) {
            if (objectKey != null) s3Service.deleteObject(objectKey);
            throw e;
        } catch (IOException e) {
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        } catch (RuntimeException e) {
            if (objectKey != null) s3Service.deleteObject(objectKey);
            throw e;
        }
    }

    // Lấy thông tin món ăn theo ID
    @Override
    public FoodResponse getById(UUID id) {
        Food food = foodRepository.findWithCollectionsById(id)
                .orElseThrow(() -> new AppException(ErrorCode.FOOD_NOT_FOUND));
        return foodMapper.toFoodResponse(food, cdnHelper);
    }

    // Xoá món ăn theo ID
    @Override
    @Transactional
    public void deleteById(UUID id) {
        Food food = foodRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.FOOD_NOT_FOUND));
        String key = food.getImageKey();
        if (key != null && !key.isBlank()) {
            try {
                s3Service.deleteObject(key);
            } catch (RuntimeException e) {
                throw new AppException(ErrorCode.DELETE_OBJECT_FAILED);
            }
        }

        try {
            foodRepository.delete(food);
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(ErrorCode.DELETE_CONFLICT);
        }
    }

    // Tìm món ăn theo khung bữa ăn (MealSlot) với phân trang
    @Override
    public Slice<FoodResponse> findByMealSlot(MealSlot mealSlot, Pageable pageable) {
        Slice<Food> slice = foodRepository.findByMealSlot(mealSlot, pageable);
        return new SliceImpl<>(
                slice.getContent().stream()
                        .map(food -> foodMapper.toFoodResponse(food, cdnHelper))
                        .toList(),
                pageable,
                slice.hasNext()
        );
    }

    // Tìm món ăn theo tên 
    @Override
    public Slice<FoodResponse> searchByName(String name, Pageable pageable) {
        String keyword = (name == null) ? "" : name.trim();
        if (keyword.length() < 2) {
            throw new AppException(ErrorCode.NAME_EMPTY);
        }

         Slice<Food> slice = foodRepository.findByNameContainingIgnoreCase(keyword, pageable);

        return new SliceImpl<>(
                slice.getContent().stream()
                        .map(food -> foodMapper.toFoodResponse(food, cdnHelper))
                        .toList(),
                pageable,
                slice.hasNext()
        );
    }

    // Lấy tất cả món ăn với phân trang
    @Override
    public Slice<FoodResponse> getAll(Pageable pageable) {
        Slice<Food> slice = foodRepository.findAllBy(pageable);
        return new SliceImpl<>(
                slice.getContent().stream()
                        .map(food -> foodMapper.toFoodResponse(food, cdnHelper))
                        .toList(),
                pageable,
                slice.hasNext()
        );
    }

    @Override
    @Transactional
    public FoodResponse patchUpdate(UUID id, FoodPatchRequest req) {
        Food food = foodRepository.findWithCollectionsById(id)
                .orElseThrow(() -> new AppException(ErrorCode.FOOD_NOT_FOUND));

        // 2) Đổi tên nếu request có name
        String newName = normalizeName(req.getName());
        if(newName != null && !newName.equalsIgnoreCase(food.getName())
                && !foodRepository.existsByNameIgnoreCase(newName)) {
            food.setName(newName);
        }
        // 3) Ảnh: nếu có file mới -> upload trước, set key mới lên entity
        String oldKey = food.getImageKey();
        String newKey = null;
        MultipartFile file = req.getImage();
        if (file != null && !file.isEmpty()) {
            try {
                newKey = s3Service.uploadObject(file, "images/foods");
                if (newKey != null && !newKey.isBlank()) {
                   food.setImageKey(newKey);
                }
            } catch (IOException e) {
                throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
            }
        }
        // 4) Patch các field còn lại (null sẽ bị bỏ qua)
        foodMapper.patch(food, req);
        // 5) Lưu DB
        Food saved = foodRepository.save(food);
        // 6) Nếu có ảnh mới -> sau khi DB ok, thử xóa ảnh cũ (không rollback nếu xóa fail)
        if (newKey != null && oldKey != null && !oldKey.isBlank() && !oldKey.equals(newKey)) {
            try {
                s3Service.deleteObject(oldKey);
            } catch (Exception ex) {
               throw new AppException(ErrorCode.DELETE_OBJECT_FAILED);
            }
        }
        return foodMapper.toFoodResponse(saved, cdnHelper);
    }


    @Override
    @Transactional(readOnly = true)
    public List<FoodResponse> autocompleteFoods(String keyword, int limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }
        Pageable pageable = PageRequest.of(0, Math.min(limit, 20));
        return  foodRepository.searchByNameUnaccent(keyword.trim(), pageable)
                .getContent()
                .stream()
                .map(food -> foodMapper.toFoodResponse(food, cdnHelper))
                .toList();
    }


    // Chuẩn hoá tên: loại bỏ khoảng trắng thừa
    private String normalizeName(String input) {
        if (input == null) return null;
        return input.trim().replaceAll("\\s+", " ");
    }

    @Override
    public long getTotalFoods() {
        return foodRepository.count();
    }

    @Override
    public List<MonthlyCountDto> getNewFoodsByMonth() {
        String tz = "Asia/Ho_Chi_Minh";
        ZoneId zone = ZoneId.of(tz);

        int y = ZonedDateTime.now(zone).getYear();

        // [startOfYear, startOfNextYear) theo TZ
        LocalDate startDate = LocalDate.of(y, 1, 1);
        LocalDate endDate   = startDate.plusYears(1);

        Instant start = startDate.atStartOfDay(zone).toInstant();
        Instant end   = endDate.atStartOfDay(zone).toInstant();

        // Lấy tất cả thời điểm tạo món trong năm
        List<Instant> times = foodRepository.findCreatedAtBetween(start, end);

        // Đếm theo YearMonth (theo TZ)
        Map<YearMonth, Long> counter = new HashMap<>();
        for (Instant t : times) {
            LocalDate d = t.atZone(zone).toLocalDate();
            YearMonth ym = YearMonth.from(d);
            counter.merge(ym, 1L, Long::sum);
        }

        // Fill đủ 12 tháng
        List<MonthlyCountDto> result = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(y, m);
            long total = counter.getOrDefault(ym, 0L);
            result.add(new MonthlyCountDto("Tháng " + m, m, total, ym));
        }
        return result;
    }

    // Đếm số món ăn mới trong tuần hiện tại (theo múi giờ VN)
    @Override
    public long countNewFoodsInLastWeek() {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");

        // Thời điểm hiện tại theo VN
        ZonedDateTime now = ZonedDateTime.now(zone);

        // 7 ngày trước
        ZonedDateTime startOfWeek = now.minusDays(7);

        Instant start = startOfWeek.toInstant();
        Instant end = now.toInstant();

        return foodRepository.countFoodsCreatedBetween(start, end);
    }

    // Lấy 10 món ăn có kcal cao nhất
    @Override
    public List<FoodTopKcalDto> getTop10HighKcalFoods() {
        return foodRepository.findTop10FoodsByKcalNative()
                .stream()
                .map(row -> new FoodTopKcalDto(
                        (String) row[0],
                        row[1] != null ? ((Number) row[1]).doubleValue() : 0.0
                ))
                .collect(Collectors.toList());
    }

    // Lấy 10 món ăn có protein cao nhất
    @Override
    public List<FoodTopProteinDto> getTop10HighProteinFoods() {
        return foodRepository.findTop10FoodsByProteinNative()
                .stream()
                .map(row -> new FoodTopProteinDto(
                        (String) row[0],
                        row[1] != null ? ((Number) row[1]).doubleValue() : 0.0
                ))
                .collect(Collectors.toList());
    }


    // Đếm số món ăn có kcal < 300
    @Override
    public Long countFoodsWithLowKcal() {
        return foodRepository.countFoodsWithLowKcal();
    }


    // Đếm số món ăn có kcal > 800
    @Override
    public Long countFoodsWithHighKcal() {
        return foodRepository.countFoodsWithHighKcal();
    }


    // Đếm số món ăn có đầy đủ 5 nhóm dinh dưỡng chính
    @Override
    public long countFoodsWithComplete5() {
        return foodRepository.countFoodsWithComplete5();
    }

    // Tỷ lệ hoàn thiện dữ liệu dinh dưỡng (5 chỉ số chính)
    @Override
    public double getDataCompletenessRate() {
        long countFoodsWithComplete5 = countFoodsWithComplete5();
        long totalFoods = getTotalFoods();

        if (totalFoods == 0) return 0.0;

        double rate = ((double) countFoodsWithComplete5 / totalFoods) * 100.0;
        return Math.round(rate * 100.0) / 100.0; // làm tròn 2 chữ số thập phân
    }

    // Thống kê biểu đồ histogram năng lượng món ăn (kcal)
    @Override
    public EnergyHistogramDto getEnergyHistogramFixed() {
        Object row = foodRepository.countEnergyBinsRaw();
        // native query trả 1 hàng, trong Spring sẽ là Object[] với 8 phần tử
        Object[] cols = (Object[]) row;

        long b1 = toLong(cols[0]);
        long b2 = toLong(cols[1]);
        long b3 = toLong(cols[2]);
        long b4 = toLong(cols[3]);
        long b5 = toLong(cols[4]);
        long b6 = toLong(cols[5]);
        long b7 = toLong(cols[6]);
        long missing = toLong(cols[7]);

        List<EnergyBinDto> bins = new ArrayList<>();
        bins.add(new EnergyBinDto("0–200",     0,    200,   b1));
        bins.add(new EnergyBinDto("200–400",   200,  400,   b2));
        bins.add(new EnergyBinDto("400–600",   400,  600,   b3));
        bins.add(new EnergyBinDto("600–800",   600,  800,   b4));
        bins.add(new EnergyBinDto("800–1000",  800,  1000,  b5));
        bins.add(new EnergyBinDto("1000–1200", 1000, 1200,  b6));
        bins.add(new EnergyBinDto(">1200",     1200, null,  b7));
        bins.add(new EnergyBinDto("Thiếu kcal", null, null, missing));

        long total = b1 + b2 + b3 + b4 + b5 + b6 + b7 + missing;
        long max   = Math.max(b1, Math.max(b2, Math.max(b3, Math.max(b4, Math.max(b5, Math.max(b6, Math.max(b7, missing)))))));

        return new EnergyHistogramDto(bins, total, max);
    }

    private long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        if (v instanceof BigInteger bi) return bi.longValue();
        return Long.parseLong(v.toString());
    }
}
