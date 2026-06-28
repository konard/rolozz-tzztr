package com.cor.collectorservice.mapper;

import com.cor.collectorservice.dto.auth.UserResponse;
import com.cor.collectorservice.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
