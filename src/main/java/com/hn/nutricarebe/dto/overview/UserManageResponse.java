package com.hn.nutricarebe.dto.overview;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserManageResponse {
    long totalUsers;
    long getNewUsersInLast7Days;
    Map<String, Long> getUserRoleCounts;
    Map<String, Long> getGoalStats;
    List<TopUserDto> getTopUsersByLogCount;
    Map<String, Long> countUsersByStatus;
}
