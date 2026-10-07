package com.htttdn.crm.service;
import com.htttdn.crm.entity.*;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.security.JwtTokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:careTests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc @Transactional
class CustomerCareIntegrationTest {
 @Autowired CustomerCareService service;@Autowired EntityManager em;@Autowired JwtTokenService jwt;@Autowired MockMvc mvc;
 @Autowired CareDashboardService dashboard;
 @Test void dormantCustomersExcludeOpenCareDeletedAndUncompletedPurchases(){
  var now=java.time.LocalDate.now(CustomerLifecycle.ZONE);
  for(int i=0;i<12;i++){
   var u=user("Dormant"+i,"CUSTOMER");var o=new Order();o.setCustomer(u);o.setOrderCode(80000L+i);o.setStatus("COMPLETED");o.setPaymentMethod("COD");o.setPaymentStatus("COD");o.setTotalAmount(java.math.BigDecimal.TEN);o.setCreatedAt(now.minusDays(30).atTime(23,59).atZone(CustomerLifecycle.ZONE).toInstant());em.persist(o);
  }em.flush();assertEquals(12,dashboard.dormant(1,10).totalElements());assertEquals(2,dashboard.dormant(1,10).content().size());
  var id=dashboard.dormant(0,10).content().getFirst().id();service.create(auth(),new CustomerCareService.CreateTask(id,other.getId(),"Call","",Instant.now()));assertEquals(11,dashboard.dashboard(null,null,null).dormantWithoutOpenTask());
  var deleted=em.find(User.class,dashboard.dormant(0,10).content().getFirst().id());deleted.setDeletedAt(Instant.now());em.flush();assertEquals(10,dashboard.dormant(0,10).totalElements());
 }
 @Test void dashboardCountsMatchDrillDownAndEnforcePermissions() throws Exception {
  var due=java.time.LocalDate.now(CustomerLifecycle.ZONE).atStartOfDay(CustomerLifecycle.ZONE).toInstant();
  var first=service.create(auth(),create("First",due.minusSeconds(1)));
  service.create(auth(),create("Second",due.plusSeconds(3600)));
  service.update(auth(),first.id(),update(first,"COMPLETED"));
  var result=dashboard.dashboard(null,null,other.getId());assertEquals(2,result.counts().total());assertEquals(1,result.counts().completed());assertEquals(1,result.counts().todo());assertEquals(1,result.staff().size());
  var today=java.time.LocalDate.now(CustomerLifecycle.ZONE);var scoped=dashboard.dashboard(today,today,other.getId());assertEquals(1,scoped.counts().total());
  assertEquals(scoped.counts().total(),service.tasks(filters("",other.getId(),null,null,0,10),today,today).totalElements());
  assertThrows(ApiException.class,()->dashboard.dashboard(today,today.minusDays(1),null));
  assertThrows(ApiException.class,()->dashboard.dashboard(null,null,-1L));
  mvc.perform(get("/api/manager/care/dashboard").header("Authorization",auth())).andExpect(status().isOk()).andExpect(jsonPath("$.counts.total").value(2));
  for(var u:new User[]{customer,admin})mvc.perform(get("/api/manager/care/dashboard").header("Authorization","Bearer "+jwt.issue(u.getId(),u.getRole()))).andExpect(status().isForbidden());
  mvc.perform(get("/api/manager/care/dormant").header("Authorization",auth())).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
 }
 User customer,manager,other,admin;
 User user(String name,String role){var u=new User();u.setFullName(name);u.setEmail(name+"@care.test");u.setRole(role);u.setPasswordHash("secret-not-returned");em.persist(u);em.flush();return u;}
 @BeforeEach void setup(){customer=user("Customer","CUSTOMER");manager=user("Manager","MANAGER");other=user("Other","MANAGER");admin=user("Admin","ADMIN");}
 String auth(){return "Bearer "+jwt.issue(manager.getId(),manager.getRole());}
 CustomerCareService.CreateTask create(String title,Instant due){return new CustomerCareService.CreateTask(customer.getId(),other.getId(),title,"Contact customer",due);}
 CustomerCareService.UpdateTask update(CustomerCareService.Task t,String status){return new CustomerCareService.UpdateTask(t.assigneeId(),t.title(),t.description(),t.dueAt(),status,t.version());}
 CustomerCareService.Filters filters(String q,Long assignee,String status,Boolean overdue,int page,int size){return new CustomerCareService.Filters(q,null,assignee,status,overdue,page,size);}
 @Test void appendOnlyNotesKeepAuthorAndPaginateForCorrectCustomer(){
  var n=service.addNote(auth(),customer.getId(),new CustomerCareService.NoteInput("  Call back tomorrow  "));assertEquals("Call back tomorrow",n.content());assertEquals("Manager",n.authorName());
  service.addNote(auth(),customer.getId(),new CustomerCareService.NoteInput("Second note"));
  assertEquals(2,service.notes(customer.getId(),1,1).totalElements());assertEquals(1,service.notes(customer.getId(),1,1).content().size());
  var empty=user("Empty","CUSTOMER");assertEquals(0,service.notes(empty.getId(),0,10).totalElements());
  assertThrows(ApiException.class,()->service.addNote(auth(),admin.getId(),new CustomerCareService.NoteInput("test")));
  assertThrows(ApiException.class,()->service.addNote(auth(),customer.getId(),new CustomerCareService.NoteInput(" ")));
  assertThrows(ApiException.class,()->service.addNote(auth(),customer.getId(),new CustomerCareService.NoteInput("x".repeat(4001))));
 }
 @Test void taskLifecycleOverdueAndStaleEditsAreRejected(){
  var t=service.create(auth(),create("Call",Instant.now().minusSeconds(60)));assertEquals("TODO",t.status());assertTrue(t.overdue());
  var started=service.update(auth(),t.id(),update(t,"IN_PROGRESS"));assertTrue(started.version()>t.version());
  assertEquals(409,assertThrows(ApiException.class,()->service.update(auth(),t.id(),update(t,"COMPLETED"))).status().value());
  assertThrows(ApiException.class,()->service.update(auth(),t.id(),update(started,"TODO")));
  var done=service.update(auth(),t.id(),update(started,"COMPLETED"));assertFalse(done.overdue());assertNotNull(done.completedAt());
  assertThrows(ApiException.class,()->service.update(auth(),t.id(),update(done,"IN_PROGRESS")));
 }
 @Test void filterBeforePaginationAndKeepInactiveAssigneeHistory(){
  for(int i=0;i<15;i++)service.create(auth(),create("Matching "+i,Instant.now().minusSeconds(3600+i)));
  service.create(auth(),create("Future",Instant.now().plusSeconds(3600)));
  var result=service.tasks(filters("Matching",other.getId(),"TODO",true,1,10));assertEquals(15,result.totalElements());assertEquals(2,result.totalPages());assertEquals(5,result.content().size());
  assertEquals(1,service.tasks(filters("",null,null,false,0,10)).totalElements());
  other.setLocked(true);em.flush();assertEquals(1,service.staff("Manager",0,10).totalElements());
  var t=service.detail(result.content().getFirst().id());assertFalse(t.assigneeActive());service.update(auth(),t.id(),update(t,"COMPLETED"));
  assertThrows(ApiException.class,()->service.create(auth(),create("Locked",Instant.now())));
  customer.setDeletedAt(Instant.now());em.flush();assertEquals(0,service.tasks(filters("",null,null,null,0,10)).totalElements());assertThrows(ApiException.class,()->service.notes(customer.getId(),0,10));
 }
 @Test void validationCustomerLockAndNoInternalAssignment(){
  customer.setLocked(true);em.flush();assertNotNull(service.create(auth(),create("Locked customer follow-up",Instant.now())).id());
  assertThrows(ApiException.class,()->service.create(auth(),new CustomerCareService.CreateTask(customer.getId(),admin.getId(),"Call","",Instant.now())));
  assertThrows(ApiException.class,()->service.create(auth(),create("x".repeat(256),Instant.now())));
  assertThrows(ApiException.class,()->service.create(auth(),create("Call",null)));
  assertThrows(ApiException.class,()->service.tasks(filters("",null,"BAD",null,0,10)));
  assertThrows(ApiException.class,()->service.tasks(filters("",null,null,null,-1,10)));
 }
 @Test void managerOnlyApiAndDtosDoNotExposeAuthenticationData() throws Exception {
  String base="/api/manager/care/tasks";mvc.perform(get(base)).andExpect(status().isUnauthorized());
  for(User wrong:new User[]{customer,admin})mvc.perform(get(base).header("Authorization","Bearer "+jwt.issue(wrong.getId(),wrong.getRole()))).andExpect(status().isForbidden());
  service.create(auth(),create("API task",Instant.now().plusSeconds(3600)));
  String body=mvc.perform(get(base).header("Authorization",auth())).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].status").value("TODO")).andReturn().getResponse().getContentAsString();assertFalse(body.contains("password"));assertFalse(body.contains("token"));
  mvc.perform(post("/api/manager/users/"+customer.getId()+"/care/notes").header("Authorization",auth()).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"API note\"}")).andExpect(status().isCreated());
  mvc.perform(get("/api/manager/care/staff").header("Authorization",auth())).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
  String due=Instant.now().plusSeconds(7200).toString();
  String request="{\"customerId\":"+customer.getId()+",\"assigneeId\":"+other.getId()+",\"title\":\"HTTP task\",\"description\":\"Care\",\"dueAt\":\""+due+"\"}";
  for(User wrong:new User[]{customer,admin})mvc.perform(post(base).header("Authorization","Bearer "+jwt.issue(wrong.getId(),wrong.getRole())).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isForbidden());
  String created=mvc.perform(post(base).header("Authorization",auth()).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(0)).andReturn().getResponse().getContentAsString();
  Number id=com.jayway.jsonpath.JsonPath.read(created,"$.id");
  String patch="{\"assigneeId\":"+other.getId()+",\"title\":\"HTTP task\",\"description\":\"Care\",\"dueAt\":\""+due+"\",\"status\":\"COMPLETED\",\"version\":0}";
  mvc.perform(patch(base+"/"+id).header("Authorization",auth()).contentType(MediaType.APPLICATION_JSON).content(patch)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.overdue").value(false));
 }
}
