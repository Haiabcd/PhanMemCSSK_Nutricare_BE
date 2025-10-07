package com.hn.nutricarebe.enums;

public enum FoodTag {
    HIGH_FIBER,
    LEAN_PROTEIN, // protein nạc
    FRIED,
    SUGARY,
    PROCESSED,

    ORGAN_MEAT, // nội tạng
    RED_MEAT, // thịt đỏ
    SARDINE, // cá mòi
    ANCHOVY, // cá cơm
    SHRIMP, // tôm
    CRAB, // cua
    PURINE_HIGH_SEAFOOD, // hải sản giàu purin

    SUGARY_DRINK, // đồ uống có đường
    SODA, // nước ngọt có ga
    HFCS_DRINK, // đồ uống có nhiều đường HFCS
    ALCOHOL, // rượu
    BEER, // bia

    CHICKEN, // gà
    FISH, // cá
    LEGUMES, // các loại đậu
    LOW_FAT_DAIRY, // các sản phẩm từ sữa ít béo
    MILK_LOWFAT, // sữa ít béo
    YOGURT_LOWFAT, // sữa chua ít béo

    NON_STARCHY_VEG, // rau không chứa tinh bột
    VEGETABLES_LEAFY_GREEN, // rau lá xanh
    FRESH_FRUITS_LOW_GI, // trái cây tươi có chỉ số GI thấp
    WHOLE_GRAIN, // ngũ cốc nguyên hạt
    WHOLE_GRAIN_BREAD, // bánh mì nguyên cám
    OATS, // yến mạch
    BROWN_RICE, // gạo lứt
    WHOLE_WHEAT_PASTA, // mì ống làm từ lúa mì nguyên cám
    HIGH_FIBER_CARB, // tinh bột giàu chất xơ

    SWEET_BAKERY, // bánh ngọt
    PASTRY, // bánh nướng
    WHITE_RICE, // gạo trắng
    WHITE_BREAD, // bánh mì trắng
    REFINED_NOODLES, // mì sợi tinh chế
    REFINED_GRAINS, // ngũ cốc tinh chế

    SOLUBLE_FIBER_SOURCE, // nguồn chất xơ hòa tan
    COOKED_VEG, // rau củ đã nấu chín
    BANANA, // chuối
    APPLESAUCE, // sốt táo
    OATMEAL, // cháo yến mạch
    PUMPKIN, // bí đỏ
    CARROT, // cà rốt
    POTATO_NO_SKIN, // khoai tây bỏ vỏ

    HIGH_FODMAP_DAIRY, // các sản phẩm từ sữa nhiều FODMAP
    WHEAT, // lúa mì
    GARLIC, // tỏi
    ONION, // hành tây
    HIGH_FODMAP_FRUITS, // trái cây nhiều FODMAP
    HONEY, // mật ong

    COFFEE, // cà phê
    CARBONATED_DRINK, // đồ uống có ga
    FRIED_FOOD, // đồ chiên rán
    SPICY_FOOD, // đồ ăn cay
    BEANS_LEGUMES, // các loại đậu
    NUTS_SEEDS, // các loại hạt
    WHOLE_GRAINS_HARD, // ngũ cốc nguyên hạt cứng
    RAW_SALAD_TOUGH, // rau sống dai

    SMALL_FREQUENT_MEALS, // ăn nhiều bữa nhỏ
    EAT_SLOWLY, // ăn chậm

    FRUITS, // trái cây

    PACKAGED_FRUIT_JUICE, // nước ép trái cây đóng gói

    UNSATURATED_FAT_SOURCE, // nguồn chất béo không bão hòa
    VEGETABLE_OILS, // dầu thực vật
    OLIVE_OIL, // dầu ô liu
    CANOLA_OIL, // dầu cải
    FATTY_FISH_OMEGA3, // cá béo giàu omega-3

    WEIGHT_LOSS_GRADUAL, // giảm cân từ từ
    PHYSICAL_ACTIVITY, // hoạt động thể chất

    SOFT_EASY_TO_DIGEST, // mềm, dễ tiêu
    PORRIDGE, // cháo
    SOUP, // súp
    WELL_COOKED_CEREAL, // ngũ cốc nấu kỹ
    RIPE_BANANA, // chuối chín
    PAPAYA, // đu đủ

    ANIMAL_FAT, // mỡ động vật
    BUTTER, // bơ
    LARD, // mỡ heo
    FULL_FAT_DAIRY, // các sản phẩm từ sữa nguyên kem
    DEEP_FRIED, // chiên ngập dầu
    FAST_FOOD, // đồ ăn nhanh
    PASTRY_PROCESSED, // bánh nướng xốp, chế biến sẵn
    MARGARINE_HARD, // bơ thực vật cứng
    PALM_OIL_HEAVY, // dầu cọ, dầu dừa
    HIGH_TRANS_SNACKS, // đồ ăn vặt nhiều chất béo chuyển hóa

    BARLEY, // lúa mạch
    CITRUS_FRUITS, // trái cây họ cam quýt
    APPLE, // táo
    PSYLLIUM, // hạt mã đề
    PLANT_STEROL_FORTIFIED, // thực phẩm bổ sung sterol thực vật

    CHICKEN_SKINLESS, // gà bỏ da

    LARGE_MEAL, // bữa ăn lớn
    OVEREATING, // ăn quá no

    LYING_AFTER_MEAL, // nằm sau bữa ăn

    HIGH_FAT_FOOD, // đồ ăn nhiều chất béo
    CHOCOLATE, // sô cô la
    PEPPERMINT, // bạc hà

    CITRUS, // cam, chanh, quýt
    CITRUS_JUICE, // nước ép cam, chanh, quýt
    TOMATO, // cà chua
    TOMATO_JUICE, // nước ép cà chua
    CAFFEINE_DRINK, // đồ uống chứa cafein
    TEA_STRONG, // trà đặc
    WINE, // rượu vang

    HIGH_FAT_PROCESSED, // thực phẩm chế biến nhiều chất béo

    PORTION_CONTROL, // kiểm soát khẩu phần

    EGGS, // trứng
    TOFU, // đậu phụ

    PROCESSED_MEAT, // thịt chế biến
    DESSERTS, // món tráng miệng
    SPIRITS, // đồ uống có cồn mạnh

    PROCESSED_FOODS_HIGH_SODIUM, // thực phẩm chế biến nhiều natri
    HIGH_FAT_CHEESE, // phô mai nhiều béo

    SEAFOOD, // hải sản
    HIGH_PROTEIN_DAIRY, // các sản phẩm từ sữa giàu đạm

    TEMPEH, // đậu nành

    PEANUTS, // đậu phộng
    SPINACH, // rau chân vịt
    BEET, // củ dền

    CALCIUM_RICH_LEAFY_GREENS, // rau lá xanh giàu canxi
    BONE_BROTH, // nước hầm xương

    HIGH_SODIUM_PROCESSED, // thực phẩm chế biến nhiều natri
    PROCESSED_MEATS, // các loại thịt chế biến
    INSTANT_NOODLES, // mì ăn liền
    PICKLES, // dưa muối
    SOY_SAUCE, // xì dầu
    FISH_SAUCE, // nước mắm

    SWEET_POTATO, // khoai lang
    ORANGE, // cam
    POTATO, // khoai tây
    TOMATO_SAUCE, // sốt cà

    HIGH_P_PHOSPHATE_CHEESE, // phô mai nhiều P (phosphorus)
    COLA_PHOSPHATE, // nước ngọt có ga (cola) nhiều P
    PHOSPHATE_ADDITIVES, // phụ gia nhiều P
    PROCESSED_CHEESE, // phô mai chế biến

    FISH_LEAN, // cá ít béo

    COMPLEX_CARBS, // carbohydrate phức hợp

    ENERGY_DRINK, // nước tăng lực

    PHYSICAL_ACTIVITY_LIGHT, // hoạt động thể chất nhẹ

    ENERGY_DENSE_HEALTHY, // thực phẩm giàu năng lượng và lành mạnh
    ORAL_NUTRITION_SUPPLEMENTS, // thực phẩm bổ sung dinh dưỡng đường uống
    AVOCADO, // bơ (quả)

    NIGHT_SNACK, // ăn đêm

    IODIZED_SALT, // muối i-ốt

    SEAWEED, // rong biển
    MILK, // sữa
    YOGURT, // sữa chua

    RAW_CRUCIFEROUS_VEG, // rau họ cải sống
    RAW_SOYBEAN, // đậu nành sống

    CANDY, // kẹo
    JUICE_BOX, // nước ép đóng hộp
    WHITE_NOODLES, // mì sợi trắng

    PLATE_METHOD, // phương pháp trình bày đĩa ăn

    WEIGHT_MANAGEMENT, // quản lý cân nặng

    CHEESE, // phô mai
    SMALL_BONY_FISH, // cá nhỏ có xương
    DARK_LEAFY_GREENS, // rau lá xanh đậm
    CALCIUM_FORTIFIED_FOODS, // thực phẩm bổ sung canxi

    EGG_YOLK, // lòng đỏ trứng
    FORTIFIED_DAIRY, // các sản phẩm từ sữa bổ sung vi chất
    FORTIFIED_PLANT_MILK, // sữa thực vật bổ sung vi chất

}
