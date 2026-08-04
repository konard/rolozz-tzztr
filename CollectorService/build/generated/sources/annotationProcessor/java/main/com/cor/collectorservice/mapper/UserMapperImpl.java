package com.cor.collectorservice.mapper;

import com.cor.collectorservice.dto.auth.UserResponse;
import com.cor.collectorservice.entity.User;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-28T22:21:29+0300",
    comments = "version: 1.5.5.Final, compiler: IncrementalProcessingEnvironment from gradle-language-java-8.14.5.jar, environment: Java 21.0.4 (Oracle Corporation)"
)
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public UserResponse toResponse(User user) {
        if ( user == null ) {
            return null;
        }

        UserResponse.UserResponseBuilder userResponse = UserResponse.builder();

        userResponse.id( user.getId() );
        userResponse.username( user.getUsername() );
        userResponse.role( user.getRole() );
        userResponse.createAt( user.getCreateAt() );
        userResponse.updateAt( user.getUpdateAt() );

        return userResponse.build();
    }
}
