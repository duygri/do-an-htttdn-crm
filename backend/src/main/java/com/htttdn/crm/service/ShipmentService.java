package com.htttdn.crm.service;

import com.htttdn.crm.entity.*;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.*;
import com.htttdn.crm.security.JwtTokenService;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class ShipmentService extends AdminServiceSupport {
    private static final Set<String> EVENT_STATUSES=Set.of("PICKED_UP","IN_TRANSIT","OUT_FOR_DELIVERY","DELIVERED","FAILED","RETURNED");
    private final OrderRepository orders;
    private final ShipmentRepository shipments;
    private final ShipmentEventRepository events;
    private final ShippingCarrierRepository carriers;
    private final UserRepository users;
    private final JwtTokenService jwt;
    private final NotificationService notifications;
    private final ManagerOrderQuery query;

    public ShipmentService(OrderRepository orders, ShipmentRepository shipments, ShipmentEventRepository events, ShippingCarrierRepository carriers, UserRepository users, JwtTokenService jwt, NotificationService notifications, ManagerOrderQuery query) {
        this.orders=orders;this.shipments=shipments;this.events=events;this.carriers=carriers;this.users=users;this.jwt=jwt;this.notifications=notifications;this.query=query;
    }

    public List<CarrierView> carriers() {
        return carriers.findByActiveTrueOrderBySortOrderAscCodeAsc().stream()
            .map(c->new CarrierView(c.getCode(),c.getName(),c.getTrackingUrlTemplate())).toList();
    }

    /** Creates the shipment, ships the order and returns the order view read back inside the same write transaction. */
    @Transactional public ManagerOrderQuery.View create(String authorization, Long orderId, CreateRequest input) {
        User manager=manager(authorization);
        return ship(manager,orderId,input);
    }

    /** Same rules as {@link #create} for callers that already authenticated the manager. */
    @Transactional public ManagerOrderQuery.View ship(User manager, Long orderId, CreateRequest input) {
        if (input==null) throw invalidCarrier();
        Order order=orders.findForStatusUpdate(orderId).orElseThrow(this::notFound);
        if (!Set.of("CONFIRMED","PREPARING").contains(order.getStatus())) throw conflict("Chỉ tạo vận đơn cho đơn đã xác nhận hoặc đang chuẩn bị.");
        if (order.getReturnStatus()!=null && !"NONE".equals(order.getReturnStatus())) throw conflict("Đơn đang xử lý đổi trả nên chưa thể tạo vận đơn mới.");
        String carrierCode=input.carrierCode()==null?"":input.carrierCode().trim();
        ShippingCarrier carrier=carriers.findById(carrierCode).filter(ShippingCarrier::isActive).orElseThrow(this::invalidCarrier);
        if (shipments.findByOrderId(orderId).isPresent()) throw conflict("Đơn hàng đã có vận đơn.");
        String code=input.trackingCode()==null?"":input.trackingCode().trim();
        if (code.isEmpty() || code.length()>120) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_TRACKING_CODE","Mã vận đơn là bắt buộc, tối đa 120 ký tự.");
        String note=input.note()==null?"":input.note().trim();
        if (note.length()>1000) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SHIPMENT_NOTE","Ghi chú tối đa 1.000 ký tự.");
        var before=snapshot(order);
        Shipment shipment=new Shipment();
        shipment.setOrderId(orderId);shipment.setCarrierCode(carrier.getCode());shipment.setTrackingCode(code);
        shipment.setNote(note.isEmpty()?null:note);shipment.setCreatedBy(manager.getId());shipment.setShippedAt(Instant.now());
        shipments.save(shipment);
        events.save(new ShipmentEvent(shipment.getId(),"CREATED","Tạo vận đơn qua "+carrier.getName(),manager.getId()));
        events.flush();
        order.setTrackingCode(code);order.setStatus("SHIPPED");order.setUpdatedAt(Instant.now());orders.saveAndFlush(order);
        notifications.create(order.getCustomer(),"Đơn hàng đang được giao","Đơn #"+order.getOrderCode()+" đã được giao cho "+carrier.getName()+". Mã vận đơn: "+code,"ORDER_SHIPPED",orderId);
        audit("SHIPMENT_CREATE","ORDER",orderId,before,order);
        return query.detail(orderId);
    }

    /** Records a tracking event on the shipment; DELIVERED also moves the order to DELIVERED and notifies the customer. */
    @Transactional public void addEvent(String authorization, Long orderId, Long shipmentId, EventRequest input) {
        User manager=manager(authorization);
        Order order=orders.findForStatusUpdate(orderId).orElseThrow(this::notFound);
        if (Set.of("CANCELLED","COMPLETED").contains(order.getStatus())) throw conflict("Đơn đã kết thúc, không nhận thêm cập nhật vận chuyển.");
        if (order.getReturnStatus()!=null && Set.of("APPROVED","COMPLETED").contains(order.getReturnStatus())) throw conflict("Đơn đang xử lý đổi trả.");
        Shipment shipment=shipments.findById(shipmentId).filter(s->s.getOrderId().equals(orderId))
            .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"SHIPMENT_NOT_FOUND","Không tìm thấy vận đơn."));
        if ("DELIVERED".equals(shipment.getStatus())) throw conflict("Vận đơn đã giao, không cập nhật thêm.");
        String status=input==null||input.status()==null?"":input.status().trim();
        if (!EVENT_STATUSES.contains(status)) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SHIPMENT_STATUS","Trạng thái vận chuyển không hợp lệ.");
        String note=input.note()==null?"":input.note().trim();
        if (note.length()>1000) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SHIPMENT_NOTE","Ghi chú tối đa 1.000 ký tự.");
        if (("FAILED".equals(status)||"RETURNED".equals(status)) && note.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,"SHIPMENT_NOTE_REQUIRED","Ghi chú là bắt buộc khi giao thất bại hoặc hoàn hàng.");
        Instant now=Instant.now();
        var before=snapshot(order);
        shipment.setStatus(status);shipment.setUpdatedAt(now);
        if ("DELIVERED".equals(status)) {
            shipment.setDeliveredAt(now);order.setStatus("DELIVERED");order.setUpdatedAt(now);orders.save(order);
            notifications.create(order.getCustomer(),"Đơn hàng đã giao","Đơn #"+order.getOrderCode()+" đã giao thành công. Hãy xác nhận đã nhận hàng.","ORDER_STATUS",orderId);
        }
        shipments.saveAndFlush(shipment);
        events.save(new ShipmentEvent(shipmentId,status,note.isEmpty()?null:note,manager.getId()));
        audit("SHIPMENT_UPDATE","ORDER",orderId,before,order);
    }

    private User manager(String authorization) {
        var claims=jwt.parse(authorization);
        User u=users.findById(claims.userId()).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Phiên không hợp lệ."));
        if (!"MANAGER".equals(claims.role()) || !"MANAGER".equals(u.getRole()) || u.isLocked() || u.getDeletedAt()!=null)
            throw new ApiException(HttpStatus.FORBIDDEN,"MANAGER_REQUIRED","Chỉ quản lý được tạo vận đơn.");
        return u;
    }
    private ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Không tìm thấy đơn hàng.");}
    private ApiException conflict(String message){return new ApiException(HttpStatus.CONFLICT,"SHIPMENT_CONFLICT",message);}
    private ApiException invalidCarrier(){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_CARRIER","Hãng vận chuyển không hợp lệ.");}

    public record CarrierView(String code,String name,String trackingUrlTemplate){}
    public record CreateRequest(@Size(max=30) String carrierCode,@Size(max=120) String trackingCode,@Size(max=1000) String note){}
    public record EventRequest(@Size(max=30) String status,@Size(max=1000) String note){}
}
