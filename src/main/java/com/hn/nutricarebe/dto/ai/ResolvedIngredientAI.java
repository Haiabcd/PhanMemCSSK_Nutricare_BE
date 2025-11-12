package com.hn.nutricarebe.dto.ai;

import com.hn.nutricarebe.entity.Nutrition;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResolvedIngredientAI {
    public enum Source { DB, ESTIMATED }
    private String requestedName;         // tên AI gửi vào
    private String matchedName;           // tên match trong DB (nếu có)
    private Source source;                // DB | ESTIMATED
    private Double amountGram;            // gram/ml
    private Nutrition per100;             // nếu DB có -> per100 từ DB; nếu không -> estimatedPer100 (nếu có)
}
