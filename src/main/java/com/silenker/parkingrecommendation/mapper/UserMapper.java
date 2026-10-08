package com.silenker.parkingrecommendation.mapper;

import com.silenker.parkingrecommendation.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User selectById(@Param("userId") String userId);

    User selectByUsername(@Param("username") String username);

    int insert(User user);

    int update(User user);
}
