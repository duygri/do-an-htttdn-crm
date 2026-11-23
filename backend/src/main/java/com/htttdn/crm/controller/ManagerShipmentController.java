package com.htttdn.crm.controller;

import com.htttdn.crm.service.ManagerOrderQuery;
import com.htttdn.crm.service.ShipmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/manager")
public class ManagerShipmentController {
    private final ShipmentService service;
    private final ManagerOrderQuery query;
    public ManagerShipmentController(ShipmentService service,ManagerOrderQuery query){this.service=service;this.query=query;}
    @GetMapping("/shipping/carriers") public List<ShipmentService.CarrierView> carriers(){return service.carriers();}
    @PostMapping("/orders/{id}/shipments") public ManagerOrderQuery.View create(@RequestHeader(value="Authorization",required=false) String authorization,@PathVariable Long id,@Valid @RequestBody ShipmentService.CreateRequest request){
        return service.create(authorization,id,request);
    }
    @PostMapping("/orders/{id}/shipments/{shipmentId}/events") public ManagerOrderQuery.View addEvent(@RequestHeader(value="Authorization",required=false) String authorization,@PathVariable Long id,@PathVariable Long shipmentId,@Valid @RequestBody ShipmentService.EventRequest request){
        service.addEvent(authorization,id,shipmentId,request);return query.detail(id);
    }
}
