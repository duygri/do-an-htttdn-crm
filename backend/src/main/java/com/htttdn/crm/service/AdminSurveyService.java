package com.htttdn.crm.service;
import com.htttdn.crm.dto.admin.AdminDtos.SurveyRequest;
import com.htttdn.crm.entity.*;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.SurveyRepository;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Set;
@Service
public class AdminSurveyService extends AdminServiceSupport {
 private final SurveyRepository surveys;
 @org.springframework.beans.factory.annotation.Autowired private com.htttdn.crm.repository.UserRepository users;
 @org.springframework.beans.factory.annotation.Autowired private NotificationService notifications;
 public AdminSurveyService(SurveyRepository surveys){this.surveys=surveys;}
 public Page<Survey> list(String status,int page,int size){return status==null||status.isBlank()?surveys.findByDeletedAtIsNull(page(page,size)):surveys.findByStatusAndDeletedAtIsNull(status,page(page,size));}
 @Transactional public Survey create(SurveyRequest r){
  Survey s=new Survey();s.setTitle(SurveyValidation.text(r.title(),255,"Tiêu đề"));
  String audience=r.audience()==null?"ALL":r.audience();
  if(!Set.of("ALL","SELECTED").contains(audience))throw SurveyValidation.invalid("Đối tượng khảo sát không hợp lệ.");
  s.setAudience(audience);
  if("SELECTED".equals(audience)){
   if(r.customerIds()==null||r.customerIds().isEmpty())throw SurveyValidation.invalid("Hãy chọn ít nhất một khách hàng.");
   for(Long id:r.customerIds()){User u=users.findById(id).orElseThrow(()->SurveyValidation.invalid("Không tìm thấy khách hàng."));if(!"CUSTOMER".equals(u.getRole())||u.isLocked()||u.getDeletedAt()!=null)throw SurveyValidation.invalid("Khách hàng không hoạt động.");s.getRecipients().add(id);}
  }
  SurveyRewardService.validate(r.reward());s.setReward(r.reward()==null?new SurveyReward():r.reward());
  if(r.description()!=null&&r.description().length()>4000)throw SurveyValidation.invalid("Mô tả tối đa 4.000 ký tự.");
  s.setDescription(r.description());s.setStartsAt(r.startsAt());s.setEndsAt(r.endsAt());
  if(r.startsAt()!=null&&r.endsAt()!=null&&!r.endsAt().isAfter(r.startsAt()))throw SurveyValidation.invalid("Thời gian kết thúc phải sau bắt đầu.");
  if(r.questions().isEmpty())throw SurveyValidation.invalid("Cần ít nhất một câu hỏi.");
  for(var q:r.questions()){
   if(q==null)throw SurveyValidation.invalid("Câu hỏi không hợp lệ.");
   String type="SINGLE".equals(q.type())?"SINGLE_CHOICE":q.type();
   if(type==null||!Set.of("SINGLE_CHOICE","MULTIPLE_CHOICE").contains(type))throw SurveyValidation.invalid("Kiểu câu hỏi không hợp lệ.");
   SurveyQuestion question=new SurveyQuestion();question.setSurvey(s);question.setText(SurveyValidation.text(q.text(),1000,"Câu hỏi"));question.setType(type);question.setOptionsJson(SurveyValidation.options(q.optionsJson()));question.setRequired(q.required()==null||q.required());question.setDisplayOrder(s.getQuestions().size());s.getQuestions().add(question);
  }return surveys.save(s);
 }
 private Survey locked(Long id){return surveys.findForUpdate(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"SURVEY_NOT_FOUND","Không tìm thấy khảo sát."));}
 @Transactional public Survey reward(Long id,SurveyReward reward){Survey s=locked(id);if(s.getDeletedAt()!=null)throw SurveyValidation.invalid("Khảo sát đã bị xóa.");SurveyRewardService.validate(reward);s.setReward(reward==null?new SurveyReward():reward);return surveys.save(s);}
 @Transactional public Survey publish(Long id,boolean value){Survey s=locked(id);if(s.getDeletedAt()!=null)throw new ApiException(HttpStatus.NOT_FOUND,"SURVEY_NOT_FOUND","Không tìm thấy khảo sát.");if(value){SurveyRewardService.validate(s.getReward());if(s.getQuestions().isEmpty())throw SurveyValidation.invalid("Không thể phát hành khảo sát chưa có câu hỏi.");}s.setStatus(value?"PUBLISHED":"DRAFT");
 if(value && users!=null){
 int pageNumber=0;org.springframework.data.domain.Page<User> batch;
 do{batch=users.findActiveCustomers("",org.springframework.data.domain.PageRequest.of(pageNumber++,100));for(User u:batch){
 if(!u.isLocked()&&("ALL".equals(s.getAudience())||s.getRecipients().contains(u.getId()))&&s.getNotified().add(u.getId()))notifications.create(u,"Khảo sát mới",s.getTitle(),"SURVEY_PUBLISHED",null);
 }}while(batch.hasNext());
 }return surveys.save(s);}
 @Transactional public void delete(Long id){Survey s=locked(id);if(s.getDeletedAt()==null){s.setDeletedAt(Instant.now());surveys.save(s);}}
}
