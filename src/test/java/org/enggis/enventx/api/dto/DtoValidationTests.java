package org.enggis.enventx.api.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.enggis.enventx.api.domain.enums.StageDecision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DtoValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void registerAcceptsAllowedDomainsCaseInsensitively() {
        for (var email : List.of("a@enggis.com", "B@NexSys.CO.IN", "c@Stability.co")) {
            var request = new RegisterRequest("Name", email, "password123");
            assertTrue(validator.validate(request).isEmpty(), email);
        }
    }

    @Test
    void registerRejectsOtherDomains() {
        for (var email : List.of("a@gmail.com", "a@enggis.com.evil.io", "a@fake-enggis.com")) {
            var request = new RegisterRequest("Name", email, "password123");
            assertFalse(validator.validate(request).isEmpty(), email);
        }
    }

    @Test
    void ideaValidatesTitleCategoryAndTeamSize() {
        var member = new TeamMemberDto("M", "m@enggis.com", "Eng", "Dev");
        var valid = new IdeaInputDto("Good title", "s", "d", "ROBOTICS", null, null, List.of(member, member, member));
        assertTrue(validator.validate(valid).isEmpty());

        var tooMany = IntStream.range(0, 4).mapToObj(i -> member).toList();
        var invalid = new IdeaInputDto("ab", "s", "d", "NOPE", null, null, tooMany);
        assertEquals(3, validator.validate(invalid).size());
    }

    @Test
    void stageTransitionRequiresReasonOfFiveChars() {
        var tooShort = new StageTransitionRequest(StageDecision.APPROVE, "abc", null);
        assertFalse(validator.validate(tooShort).isEmpty());
        var ok = new StageTransitionRequest(StageDecision.APPROVE, "good reason", null);
        assertTrue(validator.validate(ok).isEmpty());
    }
}
