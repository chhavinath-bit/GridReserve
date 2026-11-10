package com.gridreserve.common.web.error;

import com.gridreserve.common.web.request.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-05T10:15:30Z");

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler(fixed))
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    @DisplayName("API-05: a DomainException becomes the standard error JSON with its status and code")
    void domainExceptionUsesStandardFormat() throws Exception {
        mvc.perform(get("/test/domain").header(RequestIdFilter.HEADER, "abc-123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No such venue."))
                .andExpect(jsonPath("$.path").value("/test/domain"))
                .andExpect(jsonPath("$.requestId").value("abc-123"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    @DisplayName("Spike S4: Jackson 3 writes the timestamp as an ISO-8601 UTC string")
    void timestampIsIsoString() throws Exception {
        mvc.perform(get("/test/domain"))
                .andExpect(jsonPath("$.timestamp").value("2026-11-05T10:15:30Z"));
    }

    @Test
    @DisplayName("API-05: validation failures return 400 VALIDATION_FAILED with field errors")
    void validationFailureListsFields() throws Exception {
        mvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    @DisplayName("API-05: a check only the service can make answers like Bean Validation: 400 VALIDATION_FAILED with every field")
    void serviceFieldChecksUseValidationFormat() throws Exception {
        mvc.perform(get("/test/fields"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Some fields are invalid."))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("timeZone"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("openingHours[1]"));
    }

    @Test
    @DisplayName("API-06: a query parameter in the wrong format returns 400 VALIDATION_FAILED naming it")
    void wrongParameterFormat() throws Exception {
        mvc.perform(get("/test/date").param("date", "tomorrow"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("date"));
    }

    @Test
    @DisplayName("API-06: a missing required query parameter returns 400 VALIDATION_FAILED naming it")
    void missingParameter() throws Exception {
        mvc.perform(get("/test/date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("date"));
    }

    @Test
    @DisplayName("FS-22: malformed JSON returns 400 MALFORMED_REQUEST")
    void malformedJson() throws Exception {
        mvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("FS-23: unexpected exceptions return a generic 500 without internal details")
    void unexpectedErrorHidesDetails() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Something went wrong. Please try again."))
                .andExpect(header().exists(RequestIdFilter.HEADER));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/domain")
        String domain() {
            throw new DomainException(CommonErrorCode.NOT_FOUND, "No such venue.");
        }

        @GetMapping("/test/fields")
        void fields() {
            throw new FieldValidationException(List.of(new ErrorResponse.FieldError("timeZone", "Is not a zone of this country."),
                    new ErrorResponse.FieldError("openingHours[1]", "Opens after it closes.")));
        }

        @PostMapping("/test/validate")
        String validate(@Valid @RequestBody NameRequest request) {
            return request.name();
        }

        @GetMapping("/test/date")
        String date(@RequestParam LocalDate date) {
            return date.toString();
        }

        @GetMapping("/test/boom")
        String boom() {
            throw new IllegalStateException("database password is hunter2");
        }
    }

    record NameRequest(@NotBlank String name) {
    }
}
