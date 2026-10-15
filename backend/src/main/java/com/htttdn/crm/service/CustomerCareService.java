package com.htttdn.crm.service;

import com.htttdn.crm.entity.*;
import com.htttdn.crm.repository.UserRepository;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.security.JwtTokenService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @Transactional(readOnly=true)
public class CustomerCareService {
    private final JdbcTemplate db;
    private final EntityManager em;
    private final UserRepository users;
    private final JwtTokenService jwt;
    public CustomerCareService(JdbcTemplate db,EntityManager em,UserRepository users,JwtTokenService jwt) {this.db=db;this.em=em;this.users=users;this.jwt=jwt;}
    public record Note(long id,String content,long authorId,String authorName,Instant createdAt) {}
    public record Staff(long id,String fullName,String email) {}
    public record Task(long id,long customerId,String customerName,long assigneeId,String assigneeName,boolean assigneeActive,
        String title,String description,String status,Instant dueAt,boolean overdue,Instant createdAt,Instant updatedAt,Instant completedAt,long version) {}
    public record CreateTask(Long customerId,Long assigneeId,String title,String description,Instant dueAt) {}
    public record UpdateTask(Long assigneeId,String title,String description,Instant dueAt,String status,Long version) {}
    public record NoteInput(String content) {}
    public record Filters(String q,Long customerId,Long assigneeId,String status,Boolean overdue,int page,int size) {}
    private ApiException bad(String message) {return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_CARE_INPUT",message);}
    private ApiException missing() {return new ApiException(HttpStatus.NOT_FOUND,"CARE_NOT_FOUND","Không tìm thấy khách hàng hoặc công việc chăm sóc.");}
    private void page(int page,int size) {if(page<0||size<1||size>100)throw bad("Phân trang không hợp lệ.");}
    private User customer(Long id) {return id==null?failCustomer():users.findById(id).filter(u->"CUSTOMER".equals(u.getRole())&&u.getDeletedAt()==null).orElseThrow(this::missing);}
    private User failCustomer(){throw bad("Hãy chọn khách hàng.");}
    private User assignee(Long id) {if(id==null)throw bad("Hãy chọn người phụ trách.");return users.findById(id).filter(u->"MANAGER".equals(u.getRole())&&!u.isLocked()&&u.getDeletedAt()==null).orElseThrow(()->bad("Người phụ trách phải là quản lý đang hoạt động."));}
    private User actor(String auth) {var claims=jwt.parse(auth);var u=assignee(claims.userId());if(!"MANAGER".equals(claims.role()))throw new ApiException(HttpStatus.FORBIDDEN,"MANAGER_REQUIRED","Chỉ quản lý được chăm sóc khách hàng.");return u;}
    private String text(String value,int limit,boolean required,String label) {String result=value==null?"":value.trim();if(result.length()>limit||required&&result.isEmpty())throw bad(label+" "+(required?"không được trống và ":"")+"tối đa "+limit+" ký tự.");return result;}
    private void deadline(Instant due) {if(due==null)throw bad("Hãy chọn hạn xử lý.");}
    @Transactional public Note addNote(String auth,long customerId,NoteInput input) {
        User manager=actor(auth);customer(customerId);
        var n=new CustomerCareNote();n.customerId=customerId;n.authorId=manager.getId();n.authorName=manager.getFullName();n.content=text(input.content(),4000,true,"Ghi chú");n.createdAt=Instant.now();em.persist(n);em.flush();
        return new Note(n.id,n.content,n.authorId,n.authorName,n.createdAt);
    }
    public CustomerProfileService.Page<Note> notes(long customerId,int page,int size) {
        customer(customerId);page(page,size);long count=db.queryForObject("select count(*) from customer_care_notes where customer_id=?",Long.class,customerId);
        var rows=db.query("select * from customer_care_notes where customer_id=? order by created_at desc,id desc limit ? offset ?",(r,n)->new Note(r.getLong("id"),r.getString("content"),r.getLong("author_id"),r.getString("author_name"),r.getTimestamp("created_at").toInstant()),customerId,size,(long)page*size);
        return new CustomerProfileService.Page<>(rows,count,(count+size-1)/size,page,size);
    }
    public CustomerProfileService.Page<Staff> staff(String q,int page,int size) {
        page(page,size);String search="%"+text(q,255,false,"Từ khóa").toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        String from=" from customers where role='MANAGER' and deleted_at is null and locked=false and (lower(full_name) like ? escape '!' or lower(email) like ? escape '!')";
        long count=db.queryForObject("select count(*)"+from,Long.class,search,search);
        var rows=db.query("select customer_id,full_name,email"+from+" order by full_name,customer_id limit ? offset ?",(r,n)->new Staff(r.getLong(1),r.getString(2),r.getString(3)),search,search,size,(long)page*size);
        return new CustomerProfileService.Page<>(rows,count,(count+size-1)/size,page,size);
    }
    @Transactional public Task create(String auth,CreateTask input) {
        User manager=actor(auth);customer(input.customerId());assignee(input.assigneeId());deadline(input.dueAt());
        var t=new CustomerCareTask();t.customerId=input.customerId();t.assigneeId=input.assigneeId();t.title=text(input.title(),255,true,"Tiêu đề");t.description=text(input.description(),4000,false,"Nội dung");t.dueAt=input.dueAt();t.status="TODO";t.createdBy=manager.getId();t.updatedBy=manager.getId();t.createdAt=Instant.now();t.updatedAt=t.createdAt;em.persist(t);em.flush();return detail(t.id);
    }
    @Transactional public Task update(String auth,long id,UpdateTask input) {
        User manager=actor(auth);var t=em.find(CustomerCareTask.class,id,LockModeType.PESSIMISTIC_WRITE);if(t==null)throw missing();customer(t.customerId);
        if(input.version()==null||input.version()!=t.version)throw new ApiException(HttpStatus.CONFLICT,"CARE_CHANGED","Công việc đã thay đổi. Hãy tải lại trước khi lưu.");
        if("COMPLETED".equals(t.status))throw bad("Công việc đã hoàn thành, không thể sửa lại.");
        if(!Set.of("TODO","IN_PROGRESS","COMPLETED").contains(input.status()==null?"":input.status())||"IN_PROGRESS".equals(t.status)&&"TODO".equals(input.status()))throw bad("Trạng thái chỉ chuyển tiến tới hoàn thành.");
        // Existing inactive assignment may be kept to finish a task; assigning another person requires an active manager.
        if(!Objects.equals(input.assigneeId(),t.assigneeId))assignee(input.assigneeId());
        deadline(input.dueAt());t.title=text(input.title(),255,true,"Tiêu đề");t.description=text(input.description(),4000,false,"Nội dung");t.assigneeId=input.assigneeId();t.dueAt=input.dueAt();t.status=input.status();t.updatedBy=manager.getId();t.updatedAt=Instant.now();if("COMPLETED".equals(t.status))t.completedAt=t.updatedAt;em.flush();return detail(id);
    }
    public Task detail(long id) {return tasks(new Filters("",null,null,null,null,0,1),id).content().stream().findFirst().orElseThrow(this::missing);}
    public CustomerProfileService.Page<Task> tasks(Filters filters) {return tasks(filters,null);}
    public CustomerProfileService.Page<Task> tasks(Filters filters,java.time.LocalDate from,java.time.LocalDate to){return tasks(filters,null,from,to);}
    private CustomerProfileService.Page<Task> tasks(Filters f,Long id) {
        return tasks(f,id,null,null);
    }
    private CustomerProfileService.Page<Task> tasks(Filters f,Long id,java.time.LocalDate start,java.time.LocalDate end) {
        if(start!=null&&end!=null&&start.isAfter(end))throw bad("Khoảng ngày không hợp lệ.");
        page(f.page(),f.size());if(f.customerId()!=null)customer(f.customerId());
        if(f.status()!=null&&!Set.of("TODO","IN_PROGRESS","COMPLETED").contains(f.status()))throw bad("Trạng thái không hợp lệ.");
        if(f.assigneeId()!=null&&f.assigneeId()<1)throw bad("Người phụ trách không hợp lệ.");
        List<Object> args=new ArrayList<>();StringBuilder from=new StringBuilder(" from customer_care_tasks t join customers c on c.customer_id=t.customer_id join customers a on a.customer_id=t.assignee_id where c.role='CUSTOMER' and c.deleted_at is null");
        if(id!=null)add(from,args,"t.id=?",id);
        if(start!=null)add(from,args,"t.due_at>=?",java.sql.Timestamp.from(start.atStartOfDay(CustomerLifecycle.ZONE).toInstant()));
        if(end!=null)add(from,args,"t.due_at<?",java.sql.Timestamp.from(end.plusDays(1).atStartOfDay(CustomerLifecycle.ZONE).toInstant()));
        String q=text(f.q(),255,false,"Từ khóa");if(!q.isEmpty()){String search="%"+q.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";from.append(" and (lower(t.title) like ? escape '!' or lower(c.full_name) like ? escape '!' or lower(c.email) like ? escape '!')");args.add(search);args.add(search);args.add(search);}
        if(f.customerId()!=null)add(from,args,"t.customer_id=?",f.customerId());if(f.assigneeId()!=null)add(from,args,"t.assignee_id=?",f.assigneeId());if(f.status()!=null)add(from,args,"t.status=?",f.status());
        Instant now=Instant.now();if(f.overdue()!=null)add(from,args,f.overdue()?"(t.status<>'COMPLETED' and t.due_at < ?)":"(t.status='COMPLETED' or t.due_at >= ?)",java.sql.Timestamp.from(now));
        long count=db.queryForObject("select count(*)"+from,Long.class,args.toArray());args.add(f.size());args.add((long)f.page()*f.size());
        var rows=db.query("select t.*,c.full_name customer_name,a.full_name assignee_name,a.role assignee_role,a.locked assignee_locked,a.deleted_at assignee_deleted"+from+" order by t.due_at asc,t.id desc limit ? offset ?",(r,n)->{
            Instant due=r.getTimestamp("due_at").toInstant();String status=r.getString("status");var completed=r.getTimestamp("completed_at");
            return new Task(r.getLong("id"),r.getLong("customer_id"),r.getString("customer_name"),r.getLong("assignee_id"),r.getString("assignee_name"),"MANAGER".equals(r.getString("assignee_role"))&&!r.getBoolean("assignee_locked")&&r.getTimestamp("assignee_deleted")==null,r.getString("title"),r.getString("description"),status,due,!"COMPLETED".equals(status)&&due.isBefore(now),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant(),completed==null?null:completed.toInstant(),r.getLong("version"));
        },args.toArray());return new CustomerProfileService.Page<>(rows,count,(count+f.size()-1)/f.size(),f.page(),f.size());
    }
    private void add(StringBuilder from,List<Object> args,String condition,Object value){from.append(" and ").append(condition);args.add(value);}
}
