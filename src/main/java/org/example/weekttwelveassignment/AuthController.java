package org.example.weekttwelveassignment;


import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signup(@Valid @RequestBody SignupRequest request) {
        return UserResponse.from(authService.signup(request.username(), request.password()));
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        return new MeResponse(authentication.getName(),
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList());
    }
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }
}

