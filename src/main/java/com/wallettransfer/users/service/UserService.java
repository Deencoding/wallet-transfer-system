package com.wallettransfer.users.service;

import com.wallettransfer.users.exception.EmailAlreadyRegisteredException;
import com.wallettransfer.users.exception.UserNotFoundException;
import com.wallettransfer.users.model.EmailAddress;
import com.wallettransfer.users.model.Role;
import com.wallettransfer.users.model.RoleName;
import com.wallettransfer.users.model.User;
import com.wallettransfer.users.repository.RoleRepository;
import com.wallettransfer.users.repository.UserRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final Clock clock;

    public UserService(UserRepository users, RoleRepository roles, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.clock = clock;
    }

    @Transactional
    public User createCustomer(String rawEmail, String passwordHash) {
        EmailAddress email = new EmailAddress(rawEmail);
        if (users.findByEmail(email.value()).isPresent()) {
            throw new EmailAlreadyRegisteredException();
        }
        Role customerRole = roles.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Required CUSTOMER role is missing"));
        try {
            return users.saveAndFlush(new User(UUID.randomUUID(), email, passwordHash, customerRole, clock.instant()));
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    @Transactional(readOnly = true)
    public Optional<User> findForAuthentication(String rawEmail) {
        try {
            return users.findByEmail(new EmailAddress(rawEmail).value());
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return users.findById(id).orElseThrow(UserNotFoundException::new);
    }

    @Transactional
    public void recordSuccessfulLogin(UUID id) {
        User user = users.findById(id).orElseThrow(UserNotFoundException::new);
        user.recordLogin(clock.instant());
    }
}
