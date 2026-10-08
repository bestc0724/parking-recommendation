package com.silenker.parkingrecommendation.service.impl;

import com.silenker.parkingrecommendation.mapper.UserMapper;
import com.silenker.parkingrecommendation.model.dto.LoginDTO;
import com.silenker.parkingrecommendation.model.dto.RegisterDTO;
import com.silenker.parkingrecommendation.model.entity.User;
import com.silenker.parkingrecommendation.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {

    private final UserMapper userMapper;

    @Override
    public User getById(String userId) {
        return userMapper.selectById(userId);
    }

    @Override
    public User getByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    @Override
    public User login(LoginDTO dto) {
        User user = userMapper.selectByUsername(dto.getUsername());
        if (user == null) {
            throw new RuntimeException("用户名不存在");
        }
        if (!user.getPassword().equals(dto.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        return user;
    }

    @Override
    public User register(RegisterDTO dto) {
        User existing = userMapper.selectByUsername(dto.getUsername());
        if (existing != null) {
            throw new RuntimeException("用户名已存在");
        }

        User user = new User();
        user.setUserId("U" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        user.setUsername(dto.getUsername());
        user.setPassword(dto.getPassword());
        user.setRole("driver");

        userMapper.insert(user);
        return user;
    }

    @Override
    public int create(User user) {
        return userMapper.insert(user);
    }

    @Override
    public int update(User user) {
        return userMapper.update(user);
    }
}
