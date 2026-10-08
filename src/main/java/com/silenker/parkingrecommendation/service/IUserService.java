package com.silenker.parkingrecommendation.service;

import com.silenker.parkingrecommendation.model.dto.LoginDTO;
import com.silenker.parkingrecommendation.model.dto.RegisterDTO;
import com.silenker.parkingrecommendation.model.entity.User;

public interface IUserService {
    User getById(String userId);
    User getByUsername(String username);
    User login(LoginDTO dto);
    User register(RegisterDTO dto);
    int create(User user);
    int update(User user);
}
