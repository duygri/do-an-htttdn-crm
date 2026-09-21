package com.htttdn.crm.controller;

import com.htttdn.crm.entity.Survey;
import com.htttdn.crm.entity.SurveyAnswer;
import com.htttdn.crm.entity.SurveyQuestion;
import com.htttdn.crm.entity.SurveyResponse;
import com.htttdn.crm.entity.User;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.SurveyRepository;
import com.htttdn.crm.repository.SurveyResponseRepository;
import com.htttdn.crm.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/surveys")
public class SurveyController {
    private final SurveyRepository surveys;
    private final SurveyResponseRepository responses;
    private final AuthService auth;

    public SurveyController(SurveyRepository surveys, SurveyResponseRepository responses, AuthService auth) {
        this.surveys = surveys;
        this.responses = responses;
        this.auth = auth;
    }

    @GetMapping
    public List<Survey> active() {
        Instant now = Instant.now();
        return surveys.findByStatusOrderByCreatedAtDesc("PUBLISHED")
                .stream()
                .filter(survey -> available(survey, now))
                .toList();
    }

    @GetMapping("/mine")
    public List<SurveyView> mine(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User customer = auth.requireCustomer(authorization);
        return active().stream()
                .map(survey -> new SurveyView(
                        survey.getId(),
                        survey.getTitle(),
                        survey.getDescription(),
                        survey.getStartsAt(),
                        survey.getEndsAt(),
                        responses.existsByCustomerIdAndSurveyId(customer.getId(), survey.getId()),
                        survey.getQuestions()))
                .toList();
    }

    @GetMapping("/{id:\\d+}")
    public Survey get(@PathVariable Long id) {
        Survey survey = surveys.findByIdAndStatus(id, "PUBLISHED")
                .orElseThrow(this::unavailable);
        if (!available(survey, Instant.now())) {
            throw unavailable();
        }
        return survey;
    }

    /** REST endpoint used by the current storefront UI. */
    @PostMapping("/{id:\\d+}/responses")
    public ResponseEntity<?> respond(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @Valid @RequestBody ResponseRequest request) {
        return saveResponse(auth.requireCustomer(authorization), id, request.answers());
    }

    /** Compatibility endpoint required by issue IV-05. */
    @PostMapping("/submit")
    public ResponseEntity<?> submit(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody SubmitRequest request) {
        return saveResponse(auth.requireCustomer(authorization), request.surveyId(), request.answers());
    }

    private ResponseEntity<?> saveResponse(User customer, Long surveyId, List<AnswerRequest> submittedAnswers) {
        Survey survey = get(surveyId);
        if (responses.existsByCustomerIdAndSurveyId(customer.getId(), surveyId)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_RESPONSE", "Bạn đã hoàn thành khảo sát này.");
        }

        Map<Long, String> answers = new HashMap<>();
        if (submittedAnswers != null) {
            for (AnswerRequest answer : submittedAnswers) {
                if (answer == null || answer.questionId() == null || answer.value() == null || answer.value().isBlank()) {
                    continue;
                }
                answers.put(answer.questionId(), answer.value().trim());
            }
        }

        for (SurveyQuestion question : survey.getQuestions()) {
            if (question.isRequired() && !answers.containsKey(question.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Vui lòng trả lời đầy đủ các câu hỏi bắt buộc.");
            }
        }

        SurveyResponse response = new SurveyResponse();
        response.setCustomer(customer);
        response.setSurvey(survey);
        for (SurveyQuestion question : survey.getQuestions()) {
            String answerValue = answers.get(question.getId());
            if (answerValue == null) {
                continue;
            }
            SurveyAnswer answer = new SurveyAnswer();
            answer.setResponse(response);
            answer.setQuestion(question);
            answer.setAnswer(answerValue);
            response.getAnswers().add(answer);
        }

        try {
            responses.saveAndFlush(response);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_RESPONSE", "Bạn đã hoàn thành khảo sát này.");
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Cảm ơn bạn đã hoàn thành khảo sát.", "surveyId", surveyId));
    }

    private boolean available(Survey survey, Instant now) {
        return "PUBLISHED".equals(survey.getStatus())
                && (survey.getStartsAt() == null || !now.isBefore(survey.getStartsAt()))
                && (survey.getEndsAt() == null || now.isBefore(survey.getEndsAt()));
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.NOT_FOUND, "SURVEY_NOT_AVAILABLE", "Khảo sát không còn khả dụng.");
    }

    public record SurveyView(Long id, String title, String description, Instant startsAt, Instant endsAt,
                             boolean completed, List<SurveyQuestion> questions) {
    }

    public record ResponseRequest(List<AnswerRequest> answers) {
    }

    public record SubmitRequest(@NotNull Long surveyId, List<AnswerRequest> answers) {
    }

    public record AnswerRequest(@NotNull Long questionId, @Size(max = 4000) String value) {
    }
}
