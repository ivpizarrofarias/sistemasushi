package cl.ipfsoftware.backend.service;

import cl.ipfsoftware.backend.domain.Role;
import cl.ipfsoftware.backend.domain.User;
import cl.ipfsoftware.backend.dto.UserDto;
import cl.ipfsoftware.backend.dto.UserRequest;
import cl.ipfsoftware.backend.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserDto> list() {
        return userRepository.findAllByOrderByFullNameAsc().stream().map(UserDto::from).toList();
    }

    @Transactional(readOnly = true)
    public User requireByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));
    }

    @Transactional(readOnly = true)
    public User authenticate(String username, String password) {
        User user = userRepository.findByUsernameIgnoreCase(username == null ? "" : username.trim())
                .orElseThrow(() -> new BadCredentialsException("Usuario o contraseña incorrectos"));
        if (!user.isActive() || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("Usuario o contraseña incorrectos");
        }
        return user;
    }

    @Transactional
    public UserDto create(UserRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Ya existe un usuario con ese nombre");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria para crear un usuario");
        }
        validatePassword(request.password());
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setActive(request.active() == null || request.active());
        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public UserDto update(Long id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));

        String username = request.username().trim();
        if (!user.getUsername().equalsIgnoreCase(username)
                && userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Ya existe un usuario con ese nombre");
        }

        boolean active = request.active() == null ? user.isActive() : request.active();
        boolean staysAdmin = user.getRole() == Role.ADMIN && request.role() == Role.ADMIN && active;
        if (user.getRole() == Role.ADMIN && user.isActive() && !staysAdmin
                && userRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new IllegalArgumentException(
                    "No puedes desactivar ni cambiar el rol del último administrador activo");
        }

        user.setUsername(username);
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setActive(active);
        if (request.password() != null && !request.password().isBlank()) {
            validatePassword(request.password());
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        return UserDto.from(user);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("La contraseña debe contener al menos una letra mayúscula");
        }
        if (!password.matches(".*[0-9].*")) {
            throw new IllegalArgumentException("La contraseña debe contener al menos un número");
        }
    }
}
