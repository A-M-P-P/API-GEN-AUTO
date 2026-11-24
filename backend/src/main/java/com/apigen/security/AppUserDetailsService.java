package com.apigen.security;

import com.apigen.domain.Role;
import com.apigen.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsernameIgnoreCase(username)
                .map(user -> new User(
                        user.getUsername(),
                        user.getPasswordHash(),
                        user.isEnabled(),
                        true,
                        true,
                        true,
                        List.of(new SimpleGrantedAuthority("ROLE_" + (user.getRole() == null ? Role.VIEWER : user.getRole()).name()))
                ))
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable : " + username));
    }
}
