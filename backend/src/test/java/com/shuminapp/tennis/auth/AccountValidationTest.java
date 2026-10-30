package com.shuminapp.tennis.auth;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AccountValidationTest {
    @Test void acceptsCliAdministratorAndPasswordBoundaries() {
        assertThatCode(()->AccountService.validateAccountFields("test_admin","a".repeat(12),"ADMIN")).doesNotThrowAnyException();
        assertThatCode(()->AccountService.validateAccountFields("Test_Admin","a".repeat(72),"ADMIN")).doesNotThrowAnyException();
    }
    @Test void distinguishesShortPasswordFromUtf8ByteLimitWithoutExposingPassword() {
        assertThatIllegalArgumentException().isThrownBy(()->AccountService.validateAccountFields("test_admin","a".repeat(11),"ADMIN")).withMessage("Password must contain at least 12 characters");
        assertThatIllegalArgumentException().isThrownBy(()->AccountService.validateAccountFields("test_admin","密".repeat(25),"ADMIN")).withMessage("Password must not exceed 72 UTF-8 bytes");
    }
    @Test void rejectsInvalidUsernameBeforeCreatingAccount() {
        assertThatIllegalArgumentException().isThrownBy(()->AccountService.validateAccountFields("bad name","a".repeat(12),"ADMIN")).withMessageContaining("Username must contain");
    }
}
