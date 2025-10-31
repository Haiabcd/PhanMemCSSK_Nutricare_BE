package com.hn.nutricarebe.service;


import com.hn.nutricarebe.dto.overview.DailyCountDto;
import com.hn.nutricarebe.dto.response.HeaderResponse;
import com.hn.nutricarebe.dto.response.InfoResponse;
import com.hn.nutricarebe.dto.response.UserCreationResponse;
import com.hn.nutricarebe.entity.User;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface UserService {
    User saveOnboarding(String device);
    User getUserById(UUID id);
    UserCreationResponse saveGG(User user);
    InfoResponse getUserByToken();
    HeaderResponse getHeader();
    long getTotalUsers();
    List<DailyCountDto> getNewUsersThisWeek();
    long getNewUsersInLast7Days();
    Map<String, Long> getUserRoleCounts();
    Map<String, Long> countUsersByStatus();
}
