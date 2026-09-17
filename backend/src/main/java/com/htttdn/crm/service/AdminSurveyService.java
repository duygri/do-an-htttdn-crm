package com.htttdn.crm.service;

import com.htttdn.crm.dto.admin.AdminDtos.QuestionRequest;
import com.htttdn.crm.dto.admin.AdminDtos.SurveyRequest;
import com.htttdn.crm.entity.Survey;
import com.htttdn.crm.entity.SurveyQuestion;
import com.htttdn.crm.repository.SurveyRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class AdminSurveyService extends AdminServiceSupport {
    private final SurveyRepository surveys;
    public AdminSurveyService(SurveyRepository surveys) { this.surveys = surveys; }
    public Page<Survey> list(String status, int page, int size) { return status == null ? surveys.findAll(page(page, size)) : surveys.findByStatus(status, page(page, size)); }
    @Transactional public Survey create(SurveyRequest request) { Survey survey = new Survey(); survey.setTitle(request.title()); survey.setDescription(request.description()); survey.setStartsAt(request.startsAt()); survey.setEndsAt(request.endsAt()); List<QuestionRequest> questions = request.questions(); for (int i = 0; i < questions.size(); i++) { QuestionRequest q = questions.get(i); SurveyQuestion question = new SurveyQuestion(); question.setSurvey(survey); question.setText(q.text()); question.setType(q.type()); question.setDisplayOrder(i); question.setOptionsJson(q.optionsJson()); survey.getQuestions().add(question); } return surveys.save(survey); }
    @Transactional public Survey publish(Long id, boolean value) { Survey survey = get(id); survey.setStatus(value ? "PUBLISHED" : "DRAFT"); return surveys.save(survey); }
    @Transactional public void delete(Long id) { if (!surveys.existsById(id)) throw new NoSuchElementException("Survey not found"); surveys.deleteById(id); }
    private Survey get(Long id) { return surveys.findById(id).orElseThrow(); }
}
