package com.example.application.security;

import com.example.application.domain.CompanyMembership;
import com.example.application.domain.Permission;
import com.example.application.domain.User;
import com.example.application.repository.CompanyMembershipRepository;
import com.example.application.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final CompanyMembershipRepository membershipRepository;

    public UserDetailsServiceImpl(UserRepository userRepository,
                                   CompanyMembershipRepository membershipRepository) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        if (user.getStatus() != User.Status.ACTIVE) {
            throw new UsernameNotFoundException("User is not active: " + email);
        }

        return new org.springframework.security.core.userdetails.User(
            user.getEmail(),
            user.getPasswordHash() != null ? user.getPasswordHash() : "",
            user.getStatus() == User.Status.ACTIVE,
            true, // account non-expired
            true, // credentials non-expired
            true, // account non-locked
            getAuthorities(user)
        );
    }

    private Collection<? extends GrantedAuthority> getAuthorities(User user) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        // Get all active memberships for the user
        List<CompanyMembership> memberships = membershipRepository.findActiveByUser(user);

        for (CompanyMembership membership : memberships) {
            // Add role-based authority
            authorities.add(new SimpleGrantedAuthority("ROLE_" + membership.getRole().getName()));

            // Add permission-based authorities
            for (Permission permission : membership.getRole().getPermissions()) {
                authorities.add(new SimpleGrantedAuthority(permission.getName()));
            }
        }

        return authorities;
    }
}
