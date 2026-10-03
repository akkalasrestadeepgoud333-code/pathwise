package app.pathwise.web.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** JavaBean for the sign-in form and the {@code POST /api/v1/auth/login} body. */
public class LoginForm {
    @NotBlank(message = "Enter your email address.")
    @Size(max = 254, message = "That email is too long.")
    private String email;

    @NotBlank(message = "Enter your password.")
    @Size(max = 128, message = "That password is too long.")
    private String password;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
