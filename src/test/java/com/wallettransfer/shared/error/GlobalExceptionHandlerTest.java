package com.wallettransfer.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallettransfer.authentication.dto.LoginRequest;
import com.wallettransfer.authentication.exception.InvalidCredentialsException;
import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.exception.DomainException;
import jakarta.validation.Valid;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");

    @Test
    void mapsEveryDomainErrorCodeToItsPublicHttpContract() {
        for (DomainErrorCode code : DomainErrorCode.values()) {
            var response = handler.domain(new TestDomainException(code), request);

            assertThat(response.getStatusCode()).as(code.name()).isEqualTo(expectedStatus(code));
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo(code.name());
            assertThat(response.getBody().message()).isEqualTo("safe message");
        }
    }

    @Test
    void returnsAUsefulButNonEnumeratingCredentialFailure() {
        var response = handler.domain(new InvalidCredentialsException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(new ApiError("INVALID_CREDENTIALS", "Email or password is incorrect"));
    }

    @Test
    void returnsOnlyTheEmailErrorWhenEmailAndPasswordAreInvalid() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ValidationController())
                .setControllerAdvice(handler)
                .build();

        mvc.perform(
                        post("/validation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"email":"invalid","password":"short"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content()
                        .json(
                                """
                        {"code":"VALIDATION_FAILED","message":"Email must be a valid email address"}
                        """,
                                JsonCompareMode.STRICT));
    }

    @Test
    void returnsThePasswordErrorAfterEmailIsValid() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ValidationController())
                .setControllerAdvice(handler)
                .build();

        mvc.perform(
                        post("/validation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"email":"person@example.com","password":"short"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content()
                        .json(
                                """
                        {"code":"VALIDATION_FAILED","message":"Password must contain between 12 and 72 characters"}
                        """,
                                JsonCompareMode.STRICT));
    }

    @ParameterizedTest
    @MethodSource("loginValidationScenarios")
    void coversLoginValidationScenarios(String body, String expectedMessage) throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ValidationController())
                .setControllerAdvice(handler)
                .build();

        mvc.perform(post("/validation").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content()
                        .json(
                                "{\"code\":\"VALIDATION_FAILED\",\"message\":\"" + expectedMessage + "\"}",
                                JsonCompareMode.STRICT));
    }

    private static Stream<Arguments> loginValidationScenarios() {
        return Stream.of(
                Arguments.of("{\"email\":\"\",\"password\":\"valid password value\"}", "Email is required"),
                Arguments.of("{\"email\":\"person@example.com\",\"password\":\"\"}", "Password is required"),
                Arguments.of(
                        "{\"email\":\"" + "a".repeat(321) + "\",\"password\":\"valid password value\"}",
                        "Email must not exceed 320 characters"),
                Arguments.of(
                        "{\"email\":\"person@example.com\",\"password\":\"" + "p".repeat(73) + "\"}",
                        "Password must contain between 12 and 72 characters"));
    }

    private HttpStatus expectedStatus(DomainErrorCode code) {
        return switch (code) {
            case USER_NOT_FOUND,
                    WALLET_NOT_FOUND,
                    TRANSFER_NOT_FOUND,
                    EXTERNAL_TRANSFER_NOT_FOUND,
                    RECONCILIATION_CASE_NOT_FOUND,
                    LEDGER_ACCOUNT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case EMAIL_ALREADY_REGISTERED,
                    WALLET_ALREADY_EXISTS,
                    INVALID_WALLET_STATE_TRANSITION,
                    WALLET_HAS_BALANCE,
                    CONCURRENT_WALLET_UPDATE,
                    INSUFFICIENT_FUNDS,
                    SENDER_WALLET_UNAVAILABLE,
                    RECEIVER_WALLET_UNAVAILABLE,
                    CURRENCY_MISMATCH,
                    LEDGER_CURRENCY_MISMATCH,
                    INVALID_TRANSFER_STATE,
                    WALLET_BUSY,
                    IDEMPOTENCY_KEY_CONFLICT,
                    TRANSFER_NOT_REVERSIBLE,
                    REVERSAL_ALREADY_EXISTS,
                    RECONCILIATION_ALREADY_RUNNING,
                    RECONCILIATION_REPAIR_CONFLICT,
                    UNSAFE_RECONCILIATION_REPAIR,
                    WEBHOOK_REPLAY_CONFLICT -> HttpStatus.CONFLICT;
            case SAME_WALLET_TRANSFER, INVALID_TRANSFER_AMOUNT, UNBALANCED_JOURNAL, INVALID_LEDGER_ENTRY ->
                HttpStatus.UNPROCESSABLE_ENTITY;
            case AUTHENTICATION_FAILED,
                    INVALID_CREDENTIALS,
                    INVALID_TOKEN,
                    REFRESH_TOKEN_REVOKED,
                    INVALID_WEBHOOK_SIGNATURE -> HttpStatus.UNAUTHORIZED;
            case INVALID_IDEMPOTENCY_KEY -> HttpStatus.BAD_REQUEST;
        };
    }

    private static final class TestDomainException extends DomainException {
        private TestDomainException(DomainErrorCode code) {
            super(code, "safe message");
        }
    }

    @RestController
    private static final class ValidationController {
        @PostMapping("/validation")
        void validate(@Valid @RequestBody LoginRequest request) {}
    }
}
