package app.pathwise.web.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** JavaBean for the sign-up form and the {@code POST /api/v1/auth/register} body. */
public class RegisterForm {
    @NotBlank(message = "Tell us what to call you.")
    @Size(min = 2, max = 60, message = "Use 2 to 60 characters.")
    private String displayName;

    @NotBlank(message = "Enter your email address.")
    @Email(message = "That doesn't look like an email address.")
    @Size(max = 254, message = "That email is too long.")
    private String email;

    @NotBlank(message = "Choose a password.")
    @Size(max = 128, message = "Use at most 128 characters.")
    private String password;

    @Pattern(regexp = "student|teacher", message = "Choose student or teacher.")
    private String role = "student";

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isTeacher() {
        return "teacher".equals(role);
    }
}
