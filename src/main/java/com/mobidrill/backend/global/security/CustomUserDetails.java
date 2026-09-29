package com.mobidrill.backend.global.security;

import com.mobidrill.backend.global.security.module.UserAuthDto;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {

    private final UserAuthDto userAuthDto;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(userAuthDto.role().name()));
    }

    @Override
    public String getPassword() {
        return userAuthDto.password();
    }

    @Override
    public String getUsername() {
        return userAuthDto.userId().toString();
    }
}
