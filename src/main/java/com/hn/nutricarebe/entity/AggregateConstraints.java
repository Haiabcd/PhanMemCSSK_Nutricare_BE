package com.hn.nutricarebe.entity;

import java.math.BigDecimal;

public class AggregateConstraints {
    // gram/day
    public BigDecimal dayProteinMin;    // g/day
    public BigDecimal dayProteinMax;    // g/day
    public BigDecimal dayCarbMin;       // g/day
    public BigDecimal dayCarbMax;       // g/day
    public BigDecimal dayFatMin;        // g/day
    public BigDecimal dayFatMax;        // g/day
    public BigDecimal dayFiberMin;      // g/day
    public BigDecimal dayFiberMax;      // g/day

    // mg/day
    public BigDecimal daySodiumMax;     // mg/day
    public BigDecimal daySugarMax;      // mg/day

    // ml/day (chọn ml để dùng thẳng, không cần *1000 ở service)
    public BigDecimal dayWaterMin;
}
